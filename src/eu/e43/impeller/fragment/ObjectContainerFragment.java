package eu.e43.impeller.fragment;

import eu.e43.impeller.R;
import eu.e43.impeller.activity.MainActivity;
import eu.e43.impeller.content.ContentUpdateReceiver;
import eu.e43.impeller.content.PumpContentProvider;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;

import org.json.JSONException;
import org.json.JSONObject;

public class ObjectContainerFragment extends Fragment implements LoaderManager.LoaderCallbacks<Cursor> {
    public static final String TAG       = "ObjectContainerFragment";
    public static final String PARAM_ID   = "eu.e43.impeller.ObjectContainerFragment.ID";
    public static final String PARAM_MODE = "eu.e43.impeller.ObjectContainerFragment.MODE";
    String              m_id;
    JSONObject          m_object;
    ObjectFragment      m_child;
    MainActivity.Mode   m_mode;

    public static ObjectContainerFragment newInstance(String id, MainActivity.Mode mode) {
        ObjectContainerFragment fragment = new ObjectContainerFragment();
        Bundle args = new Bundle();
        args.putString(PARAM_ID, id);
        args.putSerializable(PARAM_MODE, mode);
        fragment.setArguments(args);
        return fragment;
    }

    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    public ObjectContainerFragment() {
        // Required empty public constructor
    }

    public MainActivity.Mode getMode() {
        return m_mode;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args == null) {
            throw new RuntimeException("Bad launch of fragment");
        } else {
            m_id = args.getString(PARAM_ID);
            m_mode = (MainActivity.Mode) args.getSerializable(PARAM_MODE);
        }

        if(savedInstanceState != null) {
            try {
                m_object = new JSONObject(savedInstanceState.getString("object"));
            } catch (JSONException e) {
                // We encoded the damn object!
                throw new RuntimeException(e);
            }
        }

        getLoaderManager().initLoader(0, null, this);
        queryForObjectUpdate();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        getMainActivity().onShowObjectFragment(this);
        LayoutInflater flate = LayoutInflater.from(getActivity());
        return flate.inflate(R.layout.fragment_object, null);

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if(savedInstanceState != null) {
            String type = m_object.optString("objectType");

            if(type.equals("person")) {
                m_child = new PersonObjectFragment();
            } else {
                m_child = new StandardObjectFragment();
            }
            m_child = ObjectFragment.prepare(m_child, m_id);
            m_child.setInitialSavedState((SavedState) savedInstanceState.getParcelable("child"));
            getChildFragmentManager().beginTransaction()
                    .add(R.id.objectDisplayFragment, m_child)
                    .commit();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        onObjectUpdated();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString("object", m_object.toString());

        if(m_child != null) {
            outState.putParcelable("child", getChildFragmentManager().saveFragmentInstanceState(m_child));
        }
    }

    public void onObjectUpdated() {
        if(m_object == null)
            return;

        View rootView = getView();
        if(rootView != null) {
            ViewSwitcher sw = (ViewSwitcher) getView().findViewById(R.id.switcher);
            if(m_child != null) {
                if(sw.getCurrentView().getId() != R.id.objectDisplayFragment)
                    sw.showNext();

                // Notify the child
                m_child.objectUpdated(m_object);
                return;
            }

            // Construct the child fragment
            ObjectFragment frag = null;
            if("person".equals(m_object.optString("objectType"))) {
                frag = new PersonObjectFragment();
            } else {
                frag = new StandardObjectFragment();
            }

            FragmentManager mgr = getChildFragmentManager();
            m_child = ObjectFragment.prepare(frag, m_id);

            mgr.beginTransaction()
                    .add(R.id.objectDisplayFragment, m_child)
                    .commit();

            sw.showNext();
        } else {
            // Nothing to do - our UI hasn't yet been created
            // We will update when we have a child
        }
    }

    public void queryForObjectUpdate()
    {
        /*
        BroadcastReceiver updateRx = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(getResultCode() != Activity.RESULT_OK) return;

                Bundle ex = getResultExtras(true);
                JSONObject obj = null;
                try {
                    obj = new JSONObject(ex.getString("object"));
                    m_object = obj;
                    onObjectUpdated();
                } catch (JSONException e) {
                    Log.e(TAG, "Updated object and got bad data", e);
                }
            }
        };
        */

        getActivity().sendOrderedBroadcast(new Intent(
                ContentUpdateReceiver.UPDATE_OBJECT, Uri.parse(m_id),
                getActivity(), ContentUpdateReceiver.class
        ).putExtra("account", getMainActivity().getAccount()), null,
                null/*updateRX*/, null, Activity.RESULT_OK, null, null);
    }

    public JSONObject getObject()
    {
        return m_object;
    }

    @Override
    public void onStop() {
        super.onStop();
        getMainActivity().onHideObjectFragment(this);
    }

    // ================
    // Loader callbacks
    // ================
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = Uri.parse(PumpContentProvider.OBJECT_URL)
                .buildUpon()
                .appendPath(m_id)
                .build();

        return new CursorLoader(getActivity(), uri,
                new String[] { "_json" },
                null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if(loader != null && data != null) {
            data.setNotificationUri(
                    getActivity().getContentResolver(),
                    ((CursorLoader) loader).getUri());
        }

        if(data == null || data.getCount() == 0) {
            if(m_object == null) {
                Toast.makeText(getActivity(), "Unable to fetch object", Toast.LENGTH_SHORT);
                getFragmentManager().popBackStack();
            } else {
                Log.w(TAG, "No rows returned for object query? " + m_id);
            }
        } else {
            data.moveToFirst();
            String objJSON = data.getString(0);
            JSONObject obj = null;
            try {
                obj = new JSONObject(objJSON);
            } catch (JSONException e) {
                throw new RuntimeException("Database contained invalid JSON", e);
            }
            m_object = obj;
            onObjectUpdated();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Do nothing.
    }
}

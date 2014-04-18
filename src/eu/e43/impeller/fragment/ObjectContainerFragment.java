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
                m_child = (ObjectFragment) getChildFragmentManager().getFragment(savedInstanceState, "child");
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
    public void onStart() {
        super.onStart();
        if(m_object != null)
            onObjectUpdated();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if(m_object != null) {
            outState.putString("object", m_object.toString());
        }

        if(m_child != null) {
            getChildFragmentManager().putFragment(outState, "child", m_child);
        }
    }

    public void onObjectUpdated() {
        Log.i(TAG, "Object " + m_id + " updated");

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
            if(m_object == null) {
                frag = new NullObjectFragment();
            } else if("person".equals(m_object.optString("objectType"))) {
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
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
            if(m_object != null) {
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
        }

        onObjectUpdated();
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // Do nothing.
    }
}

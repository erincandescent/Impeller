package eu.e43.impeller;

import android.accounts.Account;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.ProgressBar;

import org.json.JSONObject;

import eu.e43.impeller.content.PumpContentProvider;

/**
 * Created by OShepherd on 28/07/13.
 */
public class FeedFragment
        extends ListFragment
        implements LoaderManager.LoaderCallbacks<Cursor>,
        SyncStatusObserver
{
    Account             m_account;
    ActivityAdapter     m_adapter;
    Menu                m_menu = null;
    boolean             m_jumpToSelection   = false;
    int                 m_selection         = -1;
    Object              m_statusHandle      = null;

    // Activity IDs
    private static final int ACTIVITY_SELECT_PHOTO = 1;

    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.containsKey("account")) {
            m_account = savedInstanceState.getParcelable("account");
        } else {
            m_account = getMainActivity().getAccount();
        }

        m_adapter = new ActivityAdapter(getMainActivity());
        getLoaderManager().initLoader(0, null, this);
        setListAdapter(m_adapter);

        getMainActivity().onAddFeedFragment(this);

        if(savedInstanceState != null && savedInstanceState.containsKey("selection")) {
            setSelection(savedInstanceState.getInt("selection"));
        }

        if(getMainActivity().isTwoPane()) {
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            m_jumpToSelection = true;
        }

        m_statusHandle = getActivity().getContentResolver().addStatusChangeListener(
                  ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                | ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this);
    }

    @Override
    public void onDestroyView() {
        getActivity().getContentResolver().removeStatusChangeListener(m_statusHandle);

        getMainActivity().onRemoveFeedFragment(this);
        super.onDestroyView();
    }

    public void setSelectedItem(Uri id) {
        int sel = m_adapter.findItemById(id.toString());
        if(sel != m_selection) {
            getListView().setItemChecked(sel, true);
            m_selection = sel;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        state.putInt("selection", m_selection);
        state.putParcelable("account", m_account);
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        showItemByPosition(position);
        m_selection = position;
    }

    public void showItemByPosition(int position) {
        JSONObject act = (JSONObject) m_adapter.getItem(position);
        String url = null;

        if(url == null) {
            JSONObject obj = act.optJSONObject("object");
            if(obj != null) {
                url = obj.optString("id");
            }
        }

        if(url != null) {
            getMainActivity().showObject(Uri.parse(url));
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Uri uri =
                Uri.parse(PumpContentProvider.FEED_URL).buildUpon()
                        .appendPath(m_account.name)
                        .build();

        return new CursorLoader(getActivity(), uri,
                new String[] { "_json", "object" },
                "verb='share' OR (verb='post' AND object.objectType<>'comment')", null,
                "object.updated DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> objectLoader, Cursor o) {
        if(objectLoader != null && o != null) {
            o.setNotificationUri(
                    getActivity().getContentResolver(),
                    ((CursorLoader) objectLoader).getUri());
        }
        m_adapter.updateCursor(o);

        if(m_jumpToSelection) {
            int pos = getSelectedItemPosition();
            if(pos >= 0) showItemByPosition(pos);
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> objectLoader) {
        m_adapter.updateCursor(null);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.feed, menu);
        m_menu = menu;
        onStatusChanged(0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                refresh(item);
                return true;

            case R.id.action_post:
                Intent postIntent = new Intent(getActivity(), PostActivity.class);
                postIntent.putExtra("account", m_account);
                startActivity(postIntent);
                return true;

            case R.id.action_post_image:
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, ACTIVITY_SELECT_PHOTO);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void refresh(MenuItem itm) {
        getActivity().getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case ACTIVITY_SELECT_PHOTO:
                if(resultCode == Activity.RESULT_OK){
                    Uri selectedImage = data.getData();
                    Intent postIntent = new Intent(getActivity(), PostActivity.class);
                    postIntent.setType("image/*");
                    postIntent.putExtra(Intent.EXTRA_STREAM, selectedImage);
                    postIntent.putExtra("account", m_account);
                    startActivity(postIntent);
                }
                return;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onStatusChanged(int i) {
        //if(m_menu == null) return;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MenuItem itm = m_menu.findItem(R.id.action_refresh);
                if(itm == null) return;

                boolean syncing = getActivity().getContentResolver().isSyncActive(
                        m_account, PumpContentProvider.AUTHORITY);

                if(syncing) {
                    if(itm.getActionView() != null) return;
                    Context themedContext = getActivity().getActionBar().getThemedContext();
                    ProgressBar pbar = new ProgressBar(themedContext);
                    pbar.setIndeterminate(true);
                    itm.setActionView(pbar);
                } else {
                    itm.setActionView(null);
                }
            }
        });
    }
}

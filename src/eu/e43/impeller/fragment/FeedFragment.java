package eu.e43.impeller.fragment;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;

import org.json.JSONObject;
import org.lucasr.twowayview.ItemClickSupport;
import org.lucasr.twowayview.widget.StaggeredGridLayoutManager;
import org.lucasr.twowayview.widget.TwoWayView;

import eu.e43.impeller.api.Constants;
import eu.e43.impeller.api.Content;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.CheckinActivity;
import eu.e43.impeller.uikit.ActivityAdapter;
import eu.e43.impeller.R;
import eu.e43.impeller.activity.MainActivity;
import eu.e43.impeller.activity.PostActivity;

/**
 * Created by OShepherd on 28/07/13.
 */
public class FeedFragment
        extends Fragment
        implements LoaderManager.LoaderCallbacks<Cursor>,
            SyncStatusObserver,
            SwipeRefreshLayout.OnRefreshListener,
            ItemClickSupport.OnItemClickListener, ViewTreeObserver.OnGlobalLayoutListener {
    Account             m_account;
    ActivityAdapter     m_adapter;
    Menu                m_menu              = null;
    boolean             m_jumpToSelection   = false;
    int                 m_selection         = -1;
    Object              m_statusHandle      = null;
    Constants.FeedID    m_feedId            = null;
    SwipeRefreshLayout  m_swipeRefreshView  = null;
    TwoWayView          m_list              = null;

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

        if(getArguments() != null && getArguments().containsKey("feed")) {
            m_feedId = (Constants.FeedID) getArguments().getSerializable("feed");
        } else {
            m_feedId = Constants.FeedID.MAJOR_FEED;
        }
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
        if(m_list != null)
            m_list.setAdapter(m_adapter);

        getMainActivity().onAddFeedFragment(this);

        //if(savedInstanceState != null && savedInstanceState.containsKey("selection")) {
        //    m_list.setSelection(savedInstanceState.getInt("selection"));
        //}

        //if(getMainActivity().isTwoPane()) {
            //getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            //m_jumpToSelection = true;
        //}

        m_statusHandle = getActivity().getContentResolver().addStatusChangeListener(
                  ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
                | ContentResolver.SYNC_OBSERVER_TYPE_PENDING
                | ContentResolver.SYNC_OBSERVER_TYPE_SETTINGS, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_feed, null);

        m_list = (TwoWayView) root.findViewById(android.R.id.list);
        m_swipeRefreshView = (SwipeRefreshLayout) root.findViewById(R.id.swipeRefresh);
        root.getViewTreeObserver().addOnGlobalLayoutListener(this);

        StaggeredGridLayoutManager layout = new StaggeredGridLayoutManager(getActivity());
        layout.setOrientation(StaggeredGridLayoutManager.Orientation.VERTICAL);
        m_list.setLayoutManager(layout);
        m_list.setHasFixedSize(false);
        m_list.setItemAnimator(new DefaultItemAnimator());
        onGlobalLayout();

        if(m_adapter != null)
            m_list.setAdapter(m_adapter);

        ItemClickSupport.addTo(m_list).setOnItemClickListener(this);

        m_swipeRefreshView.setOnRefreshListener(this);
        m_swipeRefreshView.setColorSchemeResources(R.color.im_primary, R.color.im_accent, R.color.im_primary, R.color.im_accent);
        return root;
    }

    @Override
    public void onGlobalLayout() {
        int width = Utils.dipFromPx(getActivity(), m_list.getWidth());
        StaggeredGridLayoutManager sglm = (StaggeredGridLayoutManager) m_list.getLayoutManager();
        sglm.setNumColumns(width / 320);
    }

    public Constants.FeedID getFeedId() {
        return m_feedId;
    }

    @Override
    public void onDestroyView() {
        getActivity().getContentResolver().removeStatusChangeListener(m_statusHandle);

        getMainActivity().onRemoveFeedFragment(this);
        super.onDestroyView();
    }

    public void setSelectedItem(Uri id) {
        if(id == null) return;

        int sel = m_adapter.findItemById(id.toString());
        if(sel != m_selection) {
            //getListView().setItemChecked(sel, true);
            m_selection = sel;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        state.putInt("selection", m_selection);
        state.putParcelable("account", m_account);
    }


    @Override
    public void onItemClick(RecyclerView parent, View view, int position, long id) {
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
            getMainActivity().showObjectInMode(MainActivity.Mode.FEED_OBJECT, Uri.parse(url));
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        MainActivity context = getMainActivity();
        Uri uri = context.getContentUris().feedUri;

        switch(m_feedId) {
            case MAJOR_FEED:
                return new CursorLoader(context, uri,
                        new String[] { "_ID", "object.id", "_json", "replies", "likes", "shares" },
                        "verb='share' OR (verb='post' AND object.objectType<>'comment')", null,
                        "feed_entries._ID DESC");

            case MINOR_FEED:
                return new CursorLoader(context, uri,
                        new String[] { "_ID", "object.id", "_json", "replies", "likes", "shares" },
                        "NOT (verb='share' OR (verb='post' AND object.objectType<>'comment'))", null,
                        "feed_entries._ID DESC");

            case DIRECT_FEED:
                AccountManager am = AccountManager.get(context);
                String id = am.getUserData(m_account, "id");
                return new CursorLoader(context, uri,
                        new String[] { "_ID", "object.id", "_json", "replies", "likes", "shares" },
                        "(SELECT COUNT(*) FROM recipients WHERE recipients.activity=activity._ID " +
                        "AND recipients.recipient=(SELECT _ID FROM objects WHERE id=?))",
                        new String[] { id }, "feed_entries._ID DESC");

            default:
                throw new RuntimeException("Bad ID");
        }
    }

    @Override
    public void onLoadFinished(Loader<Cursor> objectLoader, Cursor o) {
        if(objectLoader != null && o != null) {
            o.setNotificationUri(
                    getActivity().getContentResolver(),
                    ((CursorLoader) objectLoader).getUri());
        }

        if(m_feedId == Constants.FeedID.DIRECT_FEED) {
            if(o.moveToFirst()) {
                Intent notice = new Intent(Constants.ACTION_DIRECT_INBOX_OPENED);
                notice.putExtra(Constants.EXTRA_ACCOUNT, m_account);
                notice.putExtra(Constants.EXTRA_FEED_ENTRY_ID, o.getInt(0));
                getActivity().sendBroadcast(notice);
            }
        }

        m_adapter.updateCursor(o);

        if(m_jumpToSelection) {
            //int pos = getSelectedItemPosition();
            //if(pos >= 0) showItemByPosition(pos);
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
            case R.id.action_post:
                Intent postIntent = new Intent(getActivity(), PostActivity.class);
                postIntent.putExtra(Constants.EXTRA_ACCOUNT, m_account);
                startActivity(postIntent);
                return true;

            case R.id.action_checkin:
                Intent checkinIntent = new Intent(getActivity(), CheckinActivity.class);
                checkinIntent.putExtra(Constants.EXTRA_ACCOUNT, m_account);
                startActivity(checkinIntent);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case ACTIVITY_SELECT_PHOTO:
                if(resultCode == Activity.RESULT_OK){
                    Uri selectedImage = data.getData();
                    Intent postIntent = new Intent(getActivity(), PostActivity.class);
                    postIntent.setType("image/*");
                    postIntent.putExtra(Intent.EXTRA_STREAM, selectedImage);
                    postIntent.putExtra(Constants.EXTRA_ACCOUNT, m_account);
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
                if(m_swipeRefreshView == null)
                    return;

                if(getActivity() == null) return;
                boolean syncing = getActivity().getContentResolver().isSyncActive(
                        m_account, Content.AUTHORITY);

                m_swipeRefreshView.setRefreshing(syncing);
            }
        });
    }

    @Override
    public void onRefresh() {
        getActivity().getContentResolver().requestSync(m_account, Content.AUTHORITY, new Bundle());
    }

    public Account getAccount() {
        return m_account;
    }
}

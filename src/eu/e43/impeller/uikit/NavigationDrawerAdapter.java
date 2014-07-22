package eu.e43.impeller.uikit;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.content.Context;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckedTextView;
import android.widget.TextView;

import eu.e43.impeller.Constants;
import eu.e43.impeller.R;
import eu.e43.impeller.account.Authenticator;
import eu.e43.impeller.activity.ActivityWithAccount;

/**
 * Created by oshepherd on 28/02/14.
 */
public class NavigationDrawerAdapter extends BaseAdapter {
    Context m_context;
    Constants.FeedID[]   m_feeds = {
            Constants.FeedID.MAJOR_FEED,
            Constants.FeedID.MINOR_FEED,
            Constants.FeedID.DIRECT_FEED,
    };

    public NavigationDrawerAdapter(Context ctx) {
        m_context = ctx;
    }

    @Override
    public int getCount() {
        return m_feeds.length;
    }

    @Override
    public Object getItem(int i) {
        return m_feeds[i];
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LayoutInflater vi = LayoutInflater.from(m_context);

        if (view == null) {
            view = vi.inflate(android.R.layout.simple_list_item_1, null);
        }

        ((TextView)view).setText(((Constants.FeedID)getItem(i)).getNameString());

        return view;
    }
}

package eu.e43.impeller.uikit;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
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
public class NavigationDrawerAdapter extends BaseAdapter implements OnAccountsUpdateListener {
    ActivityWithAccount     m_activity;
    AccountManager          m_accountManager;
    int                     m_observerCount = 0;
    Constants.FeedID[]   m_feeds = {
            Constants.FeedID.MAJOR_FEED,
            Constants.FeedID.MINOR_FEED,
            Constants.FeedID.DIRECT_FEED,
    };
    Account[]               m_accounts = null;

    public NavigationDrawerAdapter(ActivityWithAccount mainActivity) {
        m_activity = mainActivity;
        m_accountManager = AccountManager.get(mainActivity);
        m_accounts = new Account[] {};
    }

    @Override
    public int getCount() {
        return m_feeds.length + m_accounts.length + 1;
    }

    @Override
    public Object getItem(int i) {
        int feedsLength = m_feeds.length;
        if(i < feedsLength) {
            return m_feeds[i];
        } else if(i == feedsLength) {
            return null;
        } else {
            return m_accounts[i - feedsLength - 1];
        }
    }

    @Override
    public long getItemId(int i) {
        int feedsLength = m_feeds.length;
        if(i == feedsLength) {
            return 0;
        } else if(i > feedsLength) {
            return i - feedsLength - m_accounts.length - 1;
        } else {
            return i + 1;
        }
    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    private static final int ACCOUNT_TYPE = 0;
    private static final int SEPARATOR_TYPE = 1;
    private static final int FEED_TYPE = 2;

    @Override
    public int getItemViewType(int position) {
        int feedsLength = m_feeds.length;
        if(position > feedsLength) {
            return ACCOUNT_TYPE;
        } else if(position == feedsLength) {
            return SEPARATOR_TYPE;
        } else {
            return FEED_TYPE;
        }
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        LayoutInflater vi = LayoutInflater.from(m_activity);

        switch(getItemViewType(i)) {
            case ACCOUNT_TYPE:
                if (view == null) {
                    view = vi.inflate(android.R.layout.simple_list_item_single_choice, null);
                }

                CheckedTextView ctv = (CheckedTextView) view;
                Account acct = (Account) getItem(i);
                ctv.setText(acct.name);
                boolean checked = false;
                if(m_activity.getAccount() != null) {
                    checked = acct.name.equals(m_activity.getAccount().name);
                }
                ctv.setChecked(checked);
                break;

            case SEPARATOR_TYPE:
                if (view == null) {
                    view = vi.inflate(android.R.layout.preference_category, null);
                    ((TextView)view).setText(R.string.account_category);
                }
                break;

            case FEED_TYPE:
                if (view == null) {
                    view = vi.inflate(android.R.layout.simple_list_item_1, null);
                }
                ((TextView)view).setText(((Constants.FeedID)getItem(i)).getNameString());
        }
        return view;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        super.registerDataSetObserver(observer);
        m_observerCount++;
        if(m_observerCount == 1) {
            m_accountManager.addOnAccountsUpdatedListener(this, null, false);
            onAccountsUpdated(m_accountManager.getAccountsByType(Authenticator.ACCOUNT_TYPE));
        }
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        super.unregisterDataSetObserver(observer);
        m_observerCount--;
        if(m_observerCount == 0) {
            m_accountManager.removeOnAccountsUpdatedListener(this);
        }
    }

    @Override
    public void onAccountsUpdated(Account[] accounts) {
        m_accounts = accounts;
        notifyDataSetChanged();
    }
}

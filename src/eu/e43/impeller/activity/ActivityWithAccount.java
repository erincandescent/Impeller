package eu.e43.impeller.activity;

import java.io.File;
import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.net.http.HttpResponseCache;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import eu.e43.impeller.Constants;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.uikit.ImageLoader;
import eu.e43.impeller.account.Authenticator;

import static android.os.Build.*;

public abstract class ActivityWithAccount extends ActionBarActivity {
	public static final int LOGIN_REQUEST_CODE = 65535;
    private static final int LOGIN_REQUEST_CODE_STARTUP = 65534;
	private static final String TAG = "ActivityWithAccount";

    public    AccountManager 	m_accountManager 	= null;
    private   Account           m_newAccount        = null; // For resume dance
	public    Account           m_account           = null;
	private   ImageLoader       m_imageLoader		= null;
    private   Intent            m_startIntent       = null;

	public ActivityWithAccount() {
		super();
	}

    public Account getAccount() {
        return m_account;
    }

	protected abstract void onCreateEx(Bundle savedInstanceState);
    protected void onStartIntent(Intent startIntent) {}

	@Override
	protected final void onCreate(Bundle savedInstanceState) {
        Intent startIntent = getIntent();
        if(savedInstanceState != null && savedInstanceState.containsKey("account")) {
            m_account = savedInstanceState.getParcelable("account");
        } else if(startIntent.hasExtra(Constants.EXTRA_ACCOUNT)) {
            Account a = (Account) startIntent.getParcelableExtra(Constants.EXTRA_ACCOUNT);
            if(a.type.equals(Authenticator.ACCOUNT_TYPE)) {
                m_account = a;
            }
        }

		super.onCreate(savedInstanceState);
		m_accountManager = AccountManager.get(this);

        if(savedInstanceState == null)
            m_startIntent = startIntent;

		onCreateEx(savedInstanceState);

        if(m_account != null) {
            haveGotAccount(m_account);
            return;
        } else queryForAccount(QueryReason.Startup);
    }

    public enum QueryReason {
        Startup,
        User
    }

    /** Query the user for an account (asynchronously). Default implementation uses a chooser. Call
     *  haveGotAccount when you are successful (else finish)
     */
    protected void queryForAccount(QueryReason reason) {
	    // No account passed or account is invalid
        Intent chooseIntent = new Intent(this, AccountPickerActivity.class);
        this.startActivityForResult(chooseIntent, reason == QueryReason.Startup ? LOGIN_REQUEST_CODE_STARTUP : LOGIN_REQUEST_CODE);
	}

    /** Save the account */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(m_account != null) outState.putParcelable("account", m_account);
    }

    /** Looks at the response of the account chooser intent */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == LOGIN_REQUEST_CODE || requestCode == LOGIN_REQUEST_CODE_STARTUP) {
			if(resultCode == RESULT_OK) {
				String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
				Log.i(TAG, "Logged in " + accountName);

                Account acct = new Account(accountName, accountType);
                if(requestCode != LOGIN_REQUEST_CODE_STARTUP) {
                    haveGotAccount(acct);
                } else {
                    m_newAccount = acct;
                }
			} else {
                if(requestCode == LOGIN_REQUEST_CODE_STARTUP) {
                    finish();
                }
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

    // Calling gotAccount inside onActivityResult makes some things impossible. For example,
    // fragment transactions, because we haven't resumed yet. So, in those cases, delay the response
    // until we are resumed.
    @Override
    protected void onResume() {
        super.onResume();
        if(m_newAccount != null) {
            Account a = m_newAccount;
            m_newAccount = null;
            haveGotAccount(a);
        }
    }

    /** If overriding queryAccount, call this when you have found an account */
    protected void haveGotAccount(Account a) {
        m_account = a;

        // No AB on dialog style activities
        ActionBar ab = getSupportActionBar();
        if(ab != null) ab.setSubtitle(a.name);

        gotAccount(a);

        if(m_startIntent != null) {
            Intent i = m_startIntent;
            m_startIntent = null;
            onStartIntent(i);
        }
    }

	protected void gotAccount(Account a) {}
	
	public ImageLoader getImageLoader() {
		if(m_imageLoader == null) {
			m_imageLoader = new ImageLoader(this, m_account);
		}
		return m_imageLoader;
	}

    private PumpContentProvider.Uris m_cachedUris;
    public PumpContentProvider.Uris getContentUris() {
        if(m_cachedUris == null || m_cachedUris.account != m_account) {
            m_cachedUris = PumpContentProvider.Uris.get(m_account);
        }
        return m_cachedUris;
    }

}
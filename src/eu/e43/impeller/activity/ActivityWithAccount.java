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
import android.support.v7.app.ActionBarActivity;
import android.util.Log;

import eu.e43.impeller.uikit.ImageLoader;
import eu.e43.impeller.account.Authenticator;

import static android.os.Build.*;

public abstract class ActivityWithAccount extends ActionBarActivity {
	public static final int LOGIN_REQUEST_CODE = 65535;
	private static final String TAG = "ActivityWithAccount";
	public    AccountManager 	m_accountManager 	= null;
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
		super.onCreate(savedInstanceState);
		m_accountManager = AccountManager.get(this);

        try {
            Class.forName("android.net.http.HttpResponseCache");
            tryInstallResponseCache();
        } catch(ClassNotFoundException ex) {
            Log.v(TAG, "Device doesn't support HttpResponseCache. Disabled.");
        }

        Intent startIntent = getIntent();
        if(savedInstanceState != null && savedInstanceState.containsKey("account")) {
            m_account = savedInstanceState.getParcelable("account");
        } else if(startIntent.hasExtra("account")) {
            Account a = (Account) startIntent.getParcelableExtra("account");
            if(a.type.equals(Authenticator.ACCOUNT_TYPE)) {
                m_account = a;
            }
        }

        if(savedInstanceState == null)
            m_startIntent = startIntent;

		onCreateEx(savedInstanceState);

        if(m_account != null) {
            haveGotAccount(m_account);
            return;
        } else queryForAccount();
    }

    /** Query the user for an account (asynchronously). Default implementation uses a chooser. Call
     *  haveGotAccount when you are successful (else finish)
     */
    protected void queryForAccount() {
	    // No account passed or account is invalid
        Intent chooseIntent = new Intent(this, AccountPickerActivity.class);
        this.startActivityForResult(chooseIntent, LOGIN_REQUEST_CODE);
	}

    /** Save the account */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(m_account != null) outState.putParcelable("account", m_account);
    }

    protected void onStop() {
		super.onStop();

        try {
            Class.forName("android.net.http.HttpResponseCache");

            uninstallResponseCache();
        } catch(ClassNotFoundException ex) {
            Log.v(TAG, "Device doesn't support HttpResponseCache. Disabled.");
        }
	}

    /** Looks at the response of the account chooser intent */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == LOGIN_REQUEST_CODE) {
			if(resultCode == RESULT_OK) {
				String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
				Log.i(TAG, "Logged in " + accountName);
			
				m_account = new Account(accountName, accountType);
				haveGotAccount(m_account);
			} else {
				finish();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

    /** If overriding queryAccount, call this when you have found an account */
    protected void haveGotAccount(Account a) {
        m_account = a;
        getSupportActionBar().setSubtitle(a.name);
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

    private void tryInstallResponseCache() {
        if(HttpResponseCache.getInstalled() == null) {
            File cacheDir = new File(getCacheDir(), "http");
            try {
                HttpResponseCache.install(cacheDir, 10 * 1024 * 1024);
            } catch (IOException e) {
                Log.w(TAG, "Creating response cache", e);
            }
        }
    }

    private void uninstallResponseCache() {
        HttpResponseCache cache = HttpResponseCache.getInstalled();
        if (cache != null) {
            cache.flush();
        }
    }

}
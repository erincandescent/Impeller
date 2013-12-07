package eu.e43.impeller.activity;

import java.io.File;
import java.io.IOException;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.net.http.HttpResponseCache;
import android.os.Bundle;
import android.util.Log;

import eu.e43.impeller.uikit.ImageLoader;
import eu.e43.impeller.account.Authenticator;

public abstract class ActivityWithAccount extends Activity {
	public static final int LOGIN_REQUEST_CODE = 65536;
	private static final String TAG = "ActivityWithAccount";
	public    AccountManager 	m_accountManager 	= null;
	public    Account           m_account           = null;
	private ImageLoader m_imageLoader		= null;

	public ActivityWithAccount() {
		super();
	}

    public Account getAccount() {
        return m_account;
    }

	protected abstract void onCreateEx(Bundle savedInstanceState);

	@Override
	protected final void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		m_accountManager = AccountManager.get(this);
		
		if(HttpResponseCache.getInstalled() == null) {
			File cacheDir = new File(getCacheDir(), "http");
			try {
				HttpResponseCache.install(cacheDir, 10 * 1024 * 1024);
			} catch (IOException e) {
				Log.w(TAG, "Creating response cache", e);
			}
		}

        if(savedInstanceState != null && savedInstanceState.containsKey("account")) {
            m_account = savedInstanceState.getParcelable("account");
        }

		onCreateEx(savedInstanceState);

        if(m_account != null) {
            gotAccount(m_account, savedInstanceState);
            return;
        }
				
		Intent startIntent = getIntent();
		if(startIntent.hasExtra("account")) {
			Account a = (Account) startIntent.getParcelableExtra("account");
			if(a.type.equals(Authenticator.ACCOUNT_TYPE)) {
				m_account = a;
				_gotAccount(a, savedInstanceState);
				return;
			}
		}
	    
	    // No account passed or account is invalid
	    String[] accountTypes = new String[] { Authenticator.ACCOUNT_TYPE };
	    String[] features = new String[0];
	    Bundle extras = new Bundle();
	    Intent chooseIntent = AccountManager.newChooseAccountIntent(null, null, accountTypes, false, null, "", features, extras);
	    this.startActivityForResult(chooseIntent, LOGIN_REQUEST_CODE);
	}

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if(m_account != null) outState.putParcelable("account", m_account);
    }

    protected void onStop() {
		super.onStop();
		
		HttpResponseCache cache = HttpResponseCache.getInstalled();
		if (cache != null) {
			cache.flush();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == LOGIN_REQUEST_CODE) {
			if(resultCode == RESULT_OK) {
				String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
				Log.i(TAG, "Logged in " + accountName);
			
				m_account = new Account(accountName, accountType);
				_gotAccount(m_account, null);
			} else {
				finish();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}

    private void _gotAccount(Account a, Bundle savedInstanceState) {
        Intent i = getIntent();
        i.putExtra("account", a);
        setIntent(i);
        gotAccount(a, savedInstanceState);
    }

    protected void gotAccount(Account a, Bundle savedInstanceState) { gotAccount(a); }
	protected void gotAccount(Account a) {}
	
	public ImageLoader getImageLoader() {
		if(m_imageLoader == null) {
			m_imageLoader = new ImageLoader(this, m_account);
		}
		return m_imageLoader;
	}

}
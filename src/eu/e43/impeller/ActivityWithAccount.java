package eu.e43.impeller;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import eu.e43.impeller.account.Authenticator;

public abstract class ActivityWithAccount extends Activity {
	private static final String TAG = "ActivityWithAccount";
	
	protected AccountManager 	m_accountManager 	= null;
	protected SharedPreferences m_prefs				= null;

	public ActivityWithAccount() {
		super();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
		m_prefs = PreferenceManager.getDefaultSharedPreferences(this);
		m_accountManager = AccountManager.get(this);
	    
	    String accountName = m_prefs.getString("accountName", null);
	    if(accountName != null) {
	    	Account[] accts = m_accountManager.getAccountsByType(Authenticator.ACCOUNT_TYPE);
	    	for(Account a : accts) {
	    		if(a.name == accountName) {
	    			gotAccount(a);
	    			return;
	    		}
	    	}
	    }
	    
	    // No account saved or account is invalid
	    // Request a new account from the user
	    String[] accountTypes = new String[] { Authenticator.ACCOUNT_TYPE };
	    String[] features = new String[0];
	    Bundle extras = new Bundle();
	    Intent chooseIntent = AccountManager.newChooseAccountIntent(null, null, accountTypes, false, null, "", features, extras);
	    this.startActivityForResult(chooseIntent, 0);
	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == RESULT_OK) {
			String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
			String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
			Log.i(TAG, "Logged in " + accountName);
			
			Editor e = m_prefs.edit();
			e.putString("accountName", accountName);
			e.apply();
			
			gotAccount(new Account(accountName, accountType));
		} else {
			finish();
		}
	}
	
	protected abstract void gotAccount(Account a);

}
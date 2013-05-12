package eu.e43.impeller.account;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import eu.e43.impeller.account.LoginActivity;

public class Authenticator extends AbstractAccountAuthenticator {
	private static final String TAG = "Authenticator"; 
	public  static final String ACCOUNT_TYPE     = "eu.e43.impeller";
	
	Context 		m_context;
	AccountManager	m_accountManager;
	
	public Authenticator(Context context) {
		super(context);
		m_context = context;
		m_accountManager = AccountManager.get(m_context);
	}

	@Override
	public Bundle addAccount(AccountAuthenticatorResponse resp, 
			String accountType, String authTokenType, String[] requiredFeatures, Bundle options)
			throws NetworkErrorException {
		Log.v(TAG, "addAccount(" + accountType + ", " + authTokenType + ")");
		
		if(!ACCOUNT_TYPE.equals(accountType))
			throw new IllegalArgumentException("Bad account type");
				
		final Intent i = new Intent(m_context, LoginActivity.class);
		i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, resp);
		
		final Bundle b = new Bundle();
		b.putParcelable(AccountManager.KEY_INTENT, i);
		
		return b;
	}

	@Override
	public Bundle confirmCredentials(AccountAuthenticatorResponse response, 
			Account account, Bundle options) throws NetworkErrorException {
		Log.v(TAG, "confirmCredentials()");
		return null;
	}

	@Override
	public Bundle editProperties(AccountAuthenticatorResponse arg0, String arg1) {
		Log.v(TAG, "editProperties");
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle getAuthToken(AccountAuthenticatorResponse resp, Account acct,
			String tokenType, Bundle options) throws NetworkErrorException {
		Log.v(TAG, "getAuthToken(" + tokenType + ")");
		throw new IllegalArgumentException("Bad token type");
	}

	@Override
	public String getAuthTokenLabel(String type) {
		return null;
	}

	@Override
	public Bundle hasFeatures(AccountAuthenticatorResponse resp, Account acct, String[] features) throws NetworkErrorException {
        Log.v(TAG, "hasFeatures()");
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
	}

	@Override
	public Bundle updateCredentials(AccountAuthenticatorResponse resp, 
			Account acct, 
			String tokenType, 
			Bundle options) throws NetworkErrorException {
		Log.v(TAG, "updateCredentials()");
		return null;
	}

}

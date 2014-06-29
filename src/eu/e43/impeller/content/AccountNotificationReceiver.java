package eu.e43.impeller.content;

import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import eu.e43.impeller.account.Authenticator;

public class AccountNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "AccountNotificationReceiver";
    public AccountNotificationReceiver() {}

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction() != AccountManager.LOGIN_ACCOUNTS_CHANGED_ACTION) {
            Log.w(TAG, "Unexpected intent " + intent);
        }

        context.getContentResolver().call(PumpContentProvider.PROVIDER_URI, "updateAccounts", null, null);
    }
}

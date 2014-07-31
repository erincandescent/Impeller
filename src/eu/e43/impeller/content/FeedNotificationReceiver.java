package eu.e43.impeller.content;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import eu.e43.impeller.Constants;
import eu.e43.impeller.account.Authenticator;

public class FeedNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "FeedNotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "Got " + intent);
        if(intent.getAction().equals(Constants.ACTION_NEW_FEED_ENTRY)) {
            processEntry(context, intent);
        } else if(intent.getAction().equals(Constants.ACTION_DIRECT_INBOX_OPENED)) {
            updateInboxState(context, intent);
        } else if(intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED) || intent.getAction().equals(Constants.ACTION_REFRESH_NOTIFICATIONS)) {
            AccountManager mgr = AccountManager.get(context);
            Account[] accts = mgr.getAccountsByType(Authenticator.ACCOUNT_TYPE);
            for(Account acct : accts) {
                Intent serviceIntent = new Intent(
                        FeedNotificationService.ACTION_NOTIFY_DIRECT, null,
                        context, FeedNotificationService.class);
                serviceIntent.putExtra(Constants.EXTRA_ACCOUNT, acct);
                context.startService(serviceIntent);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported intent");
        }
    }

    private void updateInboxState(Context context, Intent intent) {
        Intent serviceIntent = (Intent) intent.clone();
        serviceIntent.setClass(context, FeedNotificationService.class);
        context.startService(serviceIntent);
    }

    private void processEntry(Context context, Intent intent) {
        Account acct   = intent.getParcelableExtra(Constants.EXTRA_ACCOUNT);

        Intent serviceIntent = new Intent(
                FeedNotificationService.ACTION_NOTIFY_DIRECT, null,
                context, FeedNotificationService.class);
        serviceIntent.putExtra(Constants.EXTRA_ACCOUNT, acct);
        context.startService(serviceIntent);
    }
}

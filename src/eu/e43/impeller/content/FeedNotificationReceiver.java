package eu.e43.impeller.content;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FeedNotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "FeedNotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "Got " + intent);
        if(intent.getAction().equals(PumpContentProvider.ACTION_NEW_FEED_ENTRY)) {
            processEntry(context, intent);
        } else throw new UnsupportedOperationException("Unsupported intent");
    }

    private void processEntry(Context context, Intent intent) {
        Account acct   = intent.getParcelableExtra("account");
        Uri contentUri = intent.getParcelableExtra("contentUri");

        String acctId = AccountManager.get(context).getUserData(acct, "id");

        Cursor res = context.getContentResolver().query(contentUri,
                new String[] { "_json" },
                null, null, null);

        try {
            if(res.getCount() == 0) {
                Log.e(TAG, "Lookup for " + contentUri + " failed in feed entry notification?");
                setResultCode(Activity.RESULT_CANCELED);
                return;
            }

            res.moveToFirst();
            JSONObject activity = new JSONObject(res.getString(0));
            JSONArray to = activity.optJSONArray("to");
            if(to != null) for(int i = 0; i < to.length(); i++) {
                JSONObject recipient = to.optJSONObject(i);
                if(recipient != null) {
                    if(acctId.equals(recipient.optString("id"))) {
                        Intent serviceIntent = new Intent(
                                FeedNotificationService.ACTION_NOTIFY_DIRECT, contentUri,
                                context, FeedNotificationService.class);
                        serviceIntent.putExtra("account", acct);
                        serviceIntent.putExtra("activity", res.getString(0));

                        context.startService(serviceIntent);
                        break;
                    }
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Invalid JSON", e);
        } finally {
            res.close();
        }
    }
}

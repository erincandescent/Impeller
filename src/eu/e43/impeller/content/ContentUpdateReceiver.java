package eu.e43.impeller.content;

import android.accounts.Account;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import eu.e43.impeller.Utils;
import eu.e43.impeller.account.OAuth;

public class ContentUpdateReceiver extends BroadcastReceiver {
    private final static String TAG = "ContentUpdateReceiver";
    public  final static String UPDATE_REPLIES = "eu.e43.impeller.UpdateReplies";

    @Override
    public void onReceive(final Context context, final Intent intent) {
        final PendingResult res = goAsync();
        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                if(UPDATE_REPLIES.equals(intent.getAction())) {
                    updateReplies(res, context, (Account) intent.getParcelableExtra("account"), intent.getData());
                }

                return null;
            }
        }.execute();
    }

    private void updateReplies(PendingResult result, Context context, Account acct, Uri uri) {
        Log.i(TAG, "updateReplies: Starting update for object " + uri);
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Uri.parse(PumpContentProvider.OBJECT_URL),
                new String[] { "_json" },
                "id=?", new String[] { uri.toString() },
                null);
        try {
            if(c.getCount() == 0) {
                Log.e(TAG, "Not in database");
                result.setResultCode(Activity.RESULT_CANCELED);
                return;
            }
            c.moveToFirst();
            String objJSON = c.getString(0);
            JSONObject obj = new JSONObject(objJSON);
            JSONObject collection = obj.optJSONObject("replies");
            if(collection == null) {
                Log.e(TAG, "No replies information");
                result.setResultCode(Activity.RESULT_CANCELED);
                return;
            }

            String fetchURL = collection.optString("url", null);
            JSONObject pumpIo = collection.optJSONObject("pump_io");
            if(pumpIo != null) {
                fetchURL = pumpIo.optString("proxyURL", fetchURL);
            }

            if(fetchURL == null || fetchURL.length() == 0) {
                result.setResultCode(Activity.RESULT_CANCELED);
                return;
            }

            // Drop the items stanza to prevent recursion when we merge the data into the
            // fetched objects
            collection.remove("items");

            Log.i(TAG, "Fetch from URL " + fetchURL);
            URL fetchURLObj = new URL(fetchURL);
            HttpURLConnection conn = OAuth.fetchAuthenticated(context, acct, fetchURLObj);

            String collJSON = Utils.readAll(conn.getInputStream());
            collection = new JSONObject(collJSON);

            JSONArray items = collection.getJSONArray("items");
            Log.i(TAG, "Got " + items.length() + " replies");
            ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
            for(int i = 0; i < items.length(); i++) {
                JSONObject reply = items.getJSONObject(i);
                reply.put("inReplyTo", obj);
                operations.add(
                        ContentProviderOperation.newInsert(Uri.parse(PumpContentProvider.OBJECT_URL))
                        .withValue("_json", reply.toString())
                        .build());
            }
            res.applyBatch(PumpContentProvider.AUTHORITY, operations);
            Log.i(TAG, "updateReplies: Finished for object " + uri);

            result.setResultCode(Activity.RESULT_OK);
        } catch(Exception ex) {
            result.setResultCode(Activity.RESULT_CANCELED);
            Log.e(TAG, "updateReplies: For object " + uri, ex);
        } finally {
            c.close();
            result.finish();
        }
    }
}

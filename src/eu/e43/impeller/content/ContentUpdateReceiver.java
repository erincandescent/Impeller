package eu.e43.impeller.content;

import android.accounts.Account;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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
    public  final static String UPDATE_REPLIES  = "eu.e43.impeller.UpdateReplies";
    public  final static String UPDATE_OBJECT   = "eu.e43.impeller.UpdateObject";
    public  final static String FETCH_USER_FEED = "eu.e43.impeller.FetchUserFeed";

    private class ResultData {
        public ResultData(int code_) {
            code = code_;
        }

        public int      code;
        public String   data;
        public Bundle   extras;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            final PendingResult res = goAsync();
            AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    try {
                        ResultData data = handleIntent(context, intent);

                        res.setResult(data.code, data.data, data.extras);
                    } catch(RuntimeException ex) {
                        Log.e(TAG, "Error", ex);
                        res.setResultCode(Activity.RESULT_CANCELED);
                    } finally {
                        res.finish();
                    }
                    return null;
                }
            }.execute();
        } else {
            ResultData data = handleIntent(context, intent);
            setResult(data.code, data.data, data.extras);
        }
    }

    private ResultData handleIntent(Context context, final Intent intent) {
        if(UPDATE_OBJECT.equals(intent.getAction())) {
            return updateObject(context, (Account) intent.getParcelableExtra("account"), intent.getData());
        } else if(UPDATE_REPLIES.equals(intent.getAction())) {
            return updateReplies(context, (Account) intent.getParcelableExtra("account"), intent.getData());
        } else if(FETCH_USER_FEED.equals(intent.getAction())) {
            return fetchUserFeed(context, (Account) intent.getParcelableExtra("account"), intent.getData());
        } else return new ResultData(Activity.RESULT_CANCELED);
    }

    private ResultData updateObject(Context context, Account acct, Uri uri) {
        Log.i(TAG, "updateObject: Starting update for object " + uri);
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Uri.parse(PumpContentProvider.OBJECT_URL),
                new String[] { "_json" },
                "id=?", new String[] { uri.toString() },
                null);
        try {
            if(c.getCount() == 0) {
                Log.e(TAG, "Not in database");
                return new ResultData(Activity.RESULT_CANCELED);
            }
            c.moveToFirst();
            String objJSON = c.getString(0);
            JSONObject obj = new JSONObject(objJSON);

            String fetchURL = obj.optString("url", null);
            JSONObject pumpIo = obj.optJSONObject("pump_io");
            if(pumpIo != null) {
                fetchURL = pumpIo.optString("proxyURL", fetchURL);
            }

            if(fetchURL == null || fetchURL.length() == 0) {
                return new ResultData(Activity.RESULT_CANCELED);
            }

            Log.i(TAG, "Fetch from URL " + fetchURL);
            URL fetchURLObj = new URL(fetchURL);
            HttpURLConnection conn = OAuth.fetchAuthenticated(context, acct, fetchURLObj);

            objJSON = Utils.readAll(conn.getInputStream());

            ContentValues vals = new ContentValues();
            vals.put("_json", objJSON);
            res.insert(Uri.parse(PumpContentProvider.OBJECT_URL), vals);

            Log.i(TAG, "updateObject: Finished for object " + uri);


            ResultData result = new ResultData(Activity.RESULT_OK);
            Bundle extras = new Bundle();
            extras.putString("object", objJSON);
            result.extras = extras;
            return result;
        } catch(Exception ex) {
            Log.e(TAG, "updateObject: For object " + uri, ex);
            return new ResultData(Activity.RESULT_CANCELED);
        } finally {
            c.close();
        }
    }

    private ResultData updateReplies(Context context, Account acct, Uri uri) {
        Log.i(TAG, "updateReplies: Starting update for object " + uri);
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Uri.parse(PumpContentProvider.OBJECT_URL),
                new String[] { "_json" },
                "id=?", new String[] { uri.toString() },
                null);
        try {
            if(c.getCount() == 0) {
                Log.e(TAG, "Not in database");
                return new ResultData(Activity.RESULT_CANCELED);
            }
            c.moveToFirst();
            String objJSON = c.getString(0);
            JSONObject obj = new JSONObject(objJSON);
            JSONObject collection = obj.optJSONObject("replies");
            if(collection == null) {
                Log.e(TAG, "No replies information");
                return new ResultData(Activity.RESULT_CANCELED);
            }

            String fetchURL = collection.optString("url", null);
            JSONObject pumpIo = collection.optJSONObject("pump_io");
            if(pumpIo != null) {
                fetchURL = pumpIo.optString("proxyURL", fetchURL);
            }

            if(fetchURL == null || fetchURL.length() == 0) {
                return new ResultData(Activity.RESULT_CANCELED);
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

            return new ResultData(Activity.RESULT_OK);
        } catch(Exception ex) {
            Log.e(TAG, "updateReplies: For object " + uri, ex);
            return new ResultData(Activity.RESULT_CANCELED);
        } finally {
            c.close();
        }
    }


    private ResultData fetchUserFeed(Context context, Account acct, Uri uri) {
        Log.i(TAG, "fetchUserFeed: Fetch feed for user " + uri);
        ContentResolver res = context.getContentResolver();
        Cursor c = res.query(Uri.parse(PumpContentProvider.OBJECT_URL),
                new String[] { "_json" },
                "id=?", new String[] { uri.toString() },
                null);
        try {
            if(c.getCount() == 0) {
                Log.e(TAG, "Not in database");
                return new ResultData(Activity.RESULT_CANCELED);
            }
            c.moveToFirst();
            String objJSON = c.getString(0);
            JSONObject obj = new JSONObject(objJSON);

            JSONObject links = obj.optJSONObject("links");
            if(links == null) {
                Log.e(TAG, "No links information");
                return new ResultData(Activity.RESULT_CANCELED);
            }

            JSONObject feedLink = links.optJSONObject("activity-outbox");
            if(feedLink == null) {
                Log.e(TAG, "No feed information");
                return new ResultData(Activity.RESULT_CANCELED);
            }

            String fetchURL = feedLink.optString("href", null);
            if(fetchURL == null || fetchURL.length() == 0) {
                Log.e(TAG, "No feed link");
                return new ResultData(Activity.RESULT_CANCELED);
            }

            Log.i(TAG, "Fetch from URL " + fetchURL);
            URL fetchURLObj = new URL(fetchURL);
            HttpURLConnection conn = OAuth.fetchAuthenticated(context, acct, fetchURLObj);

            String feedJSON = Utils.readAll(conn.getInputStream());
            JSONObject feed = new JSONObject(feedJSON);

            JSONArray items = feed.getJSONArray("items");
            Log.i(TAG, "Got " + items.length() + " activities");
            ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
            for(int i = 0; i < items.length(); i++) {
                JSONObject activity = items.getJSONObject(i);
                operations.add(
                        ContentProviderOperation.newInsert(Uri.parse(PumpContentProvider.ACTIVITY_URL))
                                .withValue("_json", activity.toString())
                                .build());
            }
            res.applyBatch(PumpContentProvider.AUTHORITY, operations);
            Log.i(TAG, "fetchUserFeed: Finished for " + uri);

            return new ResultData(Activity.RESULT_OK);
        } catch(Exception ex) {
            Log.e(TAG, "fetchUserFeed: For object " + uri, ex);
            return new ResultData(Activity.RESULT_CANCELED);
        } finally {
            c.close();
        }
    }
}

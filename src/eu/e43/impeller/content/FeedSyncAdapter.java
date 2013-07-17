package eu.e43.impeller.content;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import eu.e43.impeller.Utils;
import eu.e43.impeller.account.OAuth;

/**
 * Created by OShepherd on 27/06/13.
 */
public class FeedSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "FeedSyncAdapter";
    Context             m_context;
    SharedPreferences   m_syncState;

    FeedSyncAdapter(Context context) {
        super(context, true, true);
        Log.i(TAG, "Created");
        m_context = context;

        m_syncState = m_context.getSharedPreferences("FeedSync", Context.MODE_PRIVATE);
    }

    private Uri getFeedUri(Account account) {
        Uri.Builder b = Uri.parse(PumpContentProvider.FEED_URL).buildUpon();
        b.appendPath(account.name);
        return b.build();
    }

    private String getLastId(ContentResolver res, Uri feed) {
        String id = m_syncState.getString(feed.getLastPathSegment(), null);
        if(id != null) {
            return id;
        } else {
            // For upgraders
            Cursor c = res.query(
                feed,
                new String[] { "id" },
                null,
                null,
                "feed_entries.published DESC");
            if(c.getCount() > 0) {
                c.moveToFirst();
                id = c.getString(0);
                c.close();
                return id;
            } else {
                c.close();
                return null;
            }
        }
    }

    @Override
    public void onPerformSync(Account account,
                              Bundle bundle,
                              String s,
                              ContentProviderClient contentProviderClient,
                              SyncResult syncResult) {
        try {
            JSONArray items;
            AccountManager am = (AccountManager) m_context.getSystemService(Context.ACCOUNT_SERVICE);
            ContentResolver res = m_context.getContentResolver();
            Uri feedContentUri = getFeedUri(account);

            do {
                Uri.Builder b = Utils.getUserUri(m_context, account, "inbox").buildUpon();
                String lastId = getLastId(res, feedContentUri);
                if(lastId != null)
                    b.appendQueryParameter("since", lastId);
                b.appendQueryParameter("count", "50");
                Uri uri = b.build();

                Log.i(TAG, "Beginning sync from " + uri);

                HttpURLConnection conn = OAuth.fetchAuthenticated(m_context, account, new URL(uri.toString()), true);
                String jsonString = Utils.readAll(conn.getInputStream());
                JSONObject collection = new JSONObject(jsonString);
                items = collection.getJSONArray("items");

                ArrayList<ContentProviderOperation> actions = new ArrayList<ContentProviderOperation>();

                // Process backwards for linear history order
                for(int i = items.length() - 1; i >= 0; i--) {
                    JSONObject item = items.getJSONObject(i);
                    actions.add(
                            ContentProviderOperation.newInsert(feedContentUri)
                            .withValue("_json", item.toString())
                            .build());
                    syncResult.stats.numEntries++;
                }
                res.applyBatch(PumpContentProvider.AUTHORITY, actions);

                SharedPreferences.Editor e = m_syncState.edit();
                if(items.length() != 0)
                    e.putString(account.name, items.getJSONObject(0).getString("id"));
            } while(items.length() == 50);
        } catch(Exception e) {
            Log.e(TAG, "Sync exception", e);
            syncResult.databaseError = true;
        }
    }
}

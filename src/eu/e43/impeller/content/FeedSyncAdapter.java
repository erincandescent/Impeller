package eu.e43.impeller.content;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;

import eu.e43.impeller.AppConstants;
import eu.e43.impeller.api.Constants;
import eu.e43.impeller.api.Content;
import eu.e43.impeller.Utils;
import eu.e43.impeller.account.OAuth;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;

/**
 * Created by OShepherd on 27/06/13.
 */
public class FeedSyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "FeedSyncAdapter";
    Context             m_context;
    SharedPreferences   m_syncState;

    FeedSyncAdapter(Context context) {
        super(context, true);
        Log.i(TAG, "Created");
        m_context = context;

        m_syncState = m_context.getSharedPreferences("FeedSync", Context.MODE_PRIVATE);
    }

    private String getLastId(ContentResolver res, Uri feed) {
        /*
        String id = m_syncState.getString(feed.getLastPathSegment(), null);
        if(id != null) {
            return id;
        } else {*/
            Cursor c = res.query(
                feed,
                new String[] { "id" },
                null,
                null,
                "feed_entries._ID DESC");
            if(c.getCount() > 0) {
                c.moveToFirst();
                String id = c.getString(0);
                c.close();
                return id;
            } else {
                c.close();
                return null;
            }
        //}
    }

    private JSONObject doUpload(Account acct, URL url, Intent status, String type, AssetFileDescriptor file) throws Exception {
        OAuthConsumer cons = OAuth.getConsumerForAccount(getContext(), acct);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", type);

        long length = file.getLength();
        if(length == AssetFileDescriptor.UNKNOWN_LENGTH || length >= Integer.MAX_VALUE) {
            conn.setChunkedStreamingMode(4096);
        } else {
            conn.setFixedLengthStreamingMode((int) length);
        }

        cons.sign(conn);
        InputStream  is = file.createInputStream();
        OutputStream os = conn.getOutputStream();
        byte[] buf = new byte[4096];
        long progress = 0;
        int read = is.read(buf);

        status.putExtra(Constants.EXTRA_OUTBOX_UPLOAD_SIZE, length);

        while(read > 0) {
            progress += read;
            status.putExtra(Constants.EXTRA_OUTBOX_UPLOAD_UPLOADED, progress);
            getContext().sendBroadcast(status, Constants.PERMISSION_WRITE_STREAM);
            os.write(buf, 0, read);
            read = is.read(buf);
        }
        os.close();

        if(conn.getResponseCode() != 200) {
            Log.e(TAG, "Error posting: " + Utils.readAll(conn.getErrorStream()));
            return null;
        }

        JSONObject result = new JSONObject(Utils.readAll(conn.getInputStream()));

        return result;
    }

    private JSONObject doPost(Account acct, URL url, JSONObject activity) throws Exception {
        Log.i(TAG, "Posting " + activity);
        OAuthConsumer cons = OAuth.getConsumerForAccount(getContext(), acct);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        cons.sign(conn);

        OutputStream os = conn.getOutputStream();
        OutputStreamWriter wr = new OutputStreamWriter(os);
        wr.write(activity.toString());
        wr.close();

        if(conn.getResponseCode() != 200) {
            Log.e(TAG, "Error posting: " + Utils.readAll(conn.getErrorStream()));
            return null;
        }

        JSONObject result = new JSONObject(Utils.readAll(conn.getInputStream()));

        return result;
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
            Content.Uris uris = Content.Uris.get(account);

            // Sync up any content to the server
            {
                Cursor c = res.query(uris.outboxUri, new String[] {"_ID", "_json", "mediaType" }, "status==" + Integer.toString(Content.OUTBOX_STATUS_READY), null, null);
                try {
                    while(c.moveToNext()) {
                        int _ID = c.getInt(0);
                        try {
                            // Grab object
                            JSONObject act = new JSONObject(c.getString(1));
                            String mediaType = c.getString(2);

                            // Notify watchers
                            Intent status = new Intent(Constants.ACTION_OUTBOX_UPLOAD_PROGRESS);
                            status.putExtra(Constants.EXTRA_OUTBOX_ID, _ID);
                            status.putExtra(Constants.EXTRA_OUTBOX_UPLOAD_SIZE, 0);
                            status.putExtra(Constants.EXTRA_OUTBOX_UPLOAD_UPLOADED, 0);

                            URL postUrl = new URL(Utils.getUserUri(getContext(), account, "outbox").toString());
                            if (mediaType != null) {
                                AssetFileDescriptor afd = res.openAssetFileDescriptor(uris.outboxEntry(_ID), "r");
                                if (afd == null) {
                                    Log.w(TAG, "Outbox entry " + _ID + " is ready but has no blob, when one would be expected");
                                    continue;
                                }
                                Uri uploadUri = Utils.getUserUri(getContext(), account, "uploads");
                                JSONObject upAct = doUpload(account, new URL(uploadUri.toString()), status, mediaType, afd);

                                act.put("id", upAct.getString("id"));

                                JSONObject updateAct = new JSONObject();
                                for (Iterator<String> i = act.keys(); i.hasNext(); ) {
                                    String k = i.next();
                                    updateAct.put(k, act.get(k));
                                }

                                updateAct.put("verb", "update");
                                doPost(account, postUrl, updateAct);
                            }

                            act = doPost(account, postUrl, act);
                            res.delete(uris.outboxEntry(_ID), null, null);

                            Intent done = new Intent(Constants.ACTION_OUTBOX_SUBMITTED);
                            done.putExtra(Constants.EXTRA_OUTBOX_ID, _ID);
                            done.putExtra(Constants.EXTRA_AS_ACTIVITY, act.toString());
                            done.putExtra(Constants.EXTRA_AS_ACTIVITY_ID, act.getString("id"));
                            getContext().sendBroadcast(done, Constants.PERMISSION_WRITE_STREAM);
                        } catch(Exception e) {
                            Log.e(TAG, "Error processing entry " + _ID, e);
                            Intent error = new Intent(Constants.ACTION_OUTBOX_FAILURE);
                            error.putExtra(Constants.EXTRA_OUTBOX_ID, _ID);
                            getContext().sendBroadcast(error, Constants.PERMISSION_WRITE_STREAM);
                        }
                    }
                } finally {
                    c.close();
                }
            }

            // Sync down any content from the server
            do {
                Uri.Builder b = Utils.getUserUri(m_context, account, "inbox").buildUpon();
                String lastId = getLastId(res, uris.feedUri);
                if(lastId != null)
                    b.appendQueryParameter("since", lastId);
                b.appendQueryParameter("count", "200");
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
                            ContentProviderOperation.newInsert(uris.feedUri)
                            .withValue("_json", item.toString())
                            .build());
                    syncResult.stats.numEntries++;
                }

                ContentProviderResult[] results = res.applyBatch(Content.AUTHORITY, actions);
                for(ContentProviderResult result : results) {
                    Intent noticeIntent = new Intent(Constants.ACTION_NEW_FEED_ENTRY);
                    noticeIntent.putExtra(Constants.EXTRA_ACCOUNT, account);
                    noticeIntent.putExtra(Constants.EXTRA_CONTENT_URI, result.uri);

                    Log.i(TAG, "Sending notification " + noticeIntent);
                    getContext().sendBroadcast(noticeIntent);
                }

                SharedPreferences.Editor e = m_syncState.edit();
                if(items.length() != 0)
                    e.putString(account.name, items.getJSONObject(0).getString("id"));
                e.apply();
            } while(items.length() == 50);
        } catch(Exception e) {
            Log.e(TAG, "Sync exception", e);
            syncResult.databaseError = true;
        }
    }
}

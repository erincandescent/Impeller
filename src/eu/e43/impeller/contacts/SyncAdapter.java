package eu.e43.impeller.contacts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import eu.e43.impeller.Utils;
import eu.e43.impeller.account.OAuth;

/**
 * Created by OShepherd on 27/06/13.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "contacts.SyncAdapter";

    Context m_context;

    SyncAdapter(Context context) {
        super(context, true, true);
        Log.i(TAG, "Created");
        m_context = context;
    }

    private void makeContactsVisible(Account account) {
        ContentValues values = new ContentValues();
        values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type);
        values.put(ContactsContract.RawContacts.ACCOUNT_NAME, account.name);
        values.put(ContactsContract.Settings.UNGROUPED_VISIBLE, true);

        m_context.getContentResolver().insert(ContactsContract.Settings.CONTENT_URI, values);
    }

    private void syncPerson(Account account,
                            JSONObject person,
                            ContentResolver resolver,
                            List<ContentProviderOperation> operations) throws Exception {

        SyncOperation op = new SyncOperation(m_context, resolver, account, person.getString("id"), operations);
        op.setNickname(person.getString("preferredUsername"));
        if(person.has("displayName")) op.setDisplayName(person.getString("displayName"));
        if(person.has("image")) {
            op.setPhotoUri(Utils.getImageUrl(person.getJSONObject("image")));
        }
    }

    @Override
    public void onPerformSync(Account account,
                              Bundle bundle,
                              String s,
                              ContentProviderClient contentProviderClient,
                              SyncResult syncResult) {
        try {
            AccountManager am = (AccountManager) m_context.getSystemService(Context.ACCOUNT_SERVICE);

            Uri.Builder b = new Uri.Builder();
            b.scheme("https");
            b.authority(am.getUserData(account, "host"));
            b.appendPath("api");
            b.appendPath("user");
            b.appendPath(am.getUserData(account, "username"));
            b.appendPath("following");
            Uri uri = b.build();

            Log.i(TAG, "Beginning sync from " + uri);

            HttpURLConnection conn = OAuth.fetchAuthenticated(m_context, account, new URL(uri.toString()), true);
            String jsonString = Utils.readAll(conn.getInputStream());
            JSONObject collection = new JSONObject(jsonString);
            JSONArray items = collection.getJSONArray("items");

            ContentResolver resolver = m_context.getContentResolver();
            ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
            for(int i = 0; i < items.length(); i++) {
                JSONObject person = items.getJSONObject(i);
                Log.i(TAG, "Syncing contact " + person.optString("id"));

                syncPerson(account, person, resolver, operations);
            }

            resolver.applyBatch(ContactsContract.AUTHORITY, operations);

        } catch(Exception e) {
            Log.e(TAG, "Sync exception", e);
            syncResult.databaseError = true;
        }
    }
}

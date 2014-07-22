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
import android.content.res.AssetFileDescriptor;
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
import eu.e43.impeller.account.Authenticator;
import eu.e43.impeller.account.OAuth;
import eu.e43.impeller.content.PumpContentProvider;

/**
 * Created by OShepherd on 27/06/13.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {
    private static final String TAG = "contacts.SyncAdapter";

    Context m_context;

    SyncAdapter(Context context) {
        super(context, true);
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
            JSONObject image = person.getJSONObject("image");
            JSONObject pump_io = person.optJSONObject("pump_io");
            if(pump_io != null) {
                JSONObject fullImage = pump_io.optJSONObject("fullImage");
                if(fullImage != null) {
                    image = fullImage;
                } else Log.v(TAG, "No fullImage");
            } else Log.v(TAG, "No pump_io");
            op.setPhotoUri(Utils.getImageUrl(m_context, account, image));
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
            operations.clear();

            // Now update all photos which are out of date
            Cursor c = resolver.query(ContactsContract.RawContacts.CONTENT_URI,
                    new String[] { ContactsContract.RawContacts._ID, ContactsContract.RawContacts.SOURCE_ID },
                    ContactsContract.RawContacts.ACCOUNT_TYPE + "=? AND "
                    + ContactsContract.RawContacts.ACCOUNT_NAME + "=?",
                    new String[] { account.type, account.name }, null);

            while(c.moveToNext()) {
                long id = c.getLong(0);
                Cursor pic_c = resolver.query(ContactsContract.Data.CONTENT_URI,
                    new String[] {
                        ContactsContract.Data._ID,
                        ContactsContract.Data.SYNC1,
                        ContactsContract.Data.SYNC2
                    },
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[] {
                            String.valueOf(id),
                            ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    }, null);

                if(pic_c.moveToFirst()) {
                    // SYNC1 = URL just returned by the JSON fetch
                    // SYNC2 = URL currently stored
                    // If they differ -> do a download
                    long rowId    = pic_c.getLong(0);
                    String newUrl = pic_c.getString(1);
                    String oldUrl = pic_c.getString(2);
                    Log.v(TAG, rowId + " " + newUrl + " " + oldUrl);

                    if(newUrl != null && !newUrl.equals(oldUrl)) {
                        try {
                            Log.i(TAG, "Updating display picture from " + newUrl);
                            Uri rawContactPhotoUri = Uri.withAppendedPath(
                                ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, id),
                                ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY);

                            conn = OAuth.fetchAuthenticated(m_context, account, new URL(newUrl), true);

                            AssetFileDescriptor fd =
                                    resolver.openAssetFileDescriptor(rawContactPhotoUri, "rw");
                            Utils.copyBytes(fd.createOutputStream(), conn.getInputStream());

                            ContentValues cv = new ContentValues();
                            cv.put(ContactsContract.Data.SYNC2, newUrl);
                            resolver.update(
                                    ContactsContract.Data.CONTENT_URI,
                                    cv,
                                    ContactsContract.Data._ID + "=?",
                                    new String[] { String.valueOf(rowId) }
                            );
                        } catch(Exception e) {
                            Log.w(TAG, "Error updating user photograph ", e);
                        }
                    }
                }
                pic_c.close();
            }
        } catch(Exception e) {
            Log.e(TAG, "Sync exception", e);
            syncResult.databaseError = true;
        }
    }
}

package eu.e43.impeller.contacts;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import eu.e43.impeller.Utils;
import eu.e43.impeller.account.OAuth;

/**
 * Created by OShepherd on 27/06/13.
 */
public final class SyncOperation {
    private static final String TAG = "contacts.SyncOperation";

    private Context                         m_context;
    private ContentResolver                 m_resolver;
    private Account                         m_account;
    private String                          m_id;
    private boolean                         m_isNew;
    private long                            m_rawContactId;
    private int                             m_rawContactInsertIndex;
    private List<ContentProviderOperation>  m_operations;

    public SyncOperation(Context context, ContentResolver resolver, Account account, String id,
                         List<ContentProviderOperation> operations) {
        m_context       = context;
        m_resolver      = resolver;
        m_account       = account;
        m_id            = id;
        m_operations    = operations;

        Uri rawContactsUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon()
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                .appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                .appendQueryParameter(ContactsContract.RawContacts.SOURCE_ID, id)
                .build();

        Cursor c = resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                new String[] { ContactsContract.RawContacts._ID },
                ContactsContract.RawContacts.ACCOUNT_TYPE + "=? AND " +
                ContactsContract.RawContacts.ACCOUNT_NAME + "=? AND " +
                ContactsContract.RawContacts.SOURCE_ID    + "=?",
                new String[] {
                        account.type,
                        account.name,
                        id
                },
                null);
        if(!c.moveToFirst()) {
            m_rawContactInsertIndex = m_operations.size();
            operations.add(ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name)
                    .withValue(ContactsContract.RawContacts.SOURCE_ID, m_id)
                    .build());

            m_isNew = true;
        } else {
            m_isNew = false;
            m_rawContactId = c.getLong(0);
        }
        c.close();

        // Add the identity (pump.io account reference, i.e. acct:person@example.com)
        // Used by Android to unique contacts
        operations.add(
                setDataOfType(ContactsContract.CommonDataKinds.Identity.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Identity.NAMESPACE, "io.pump")
                .withValue(ContactsContract.CommonDataKinds.Identity.IDENTITY, id)
                .build());

        // Add the profile entry
        operations.add(
                setDataOfType("vnd.android.cursor.item/vnd.e43.impeller.profile")
                .withValue(ContactsContract.Data.DATA1, id)
                .build());
    }

    private ContentProviderOperation.Builder withContact(ContentProviderOperation.Builder b) {
        if(m_isNew) {
            return b.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, m_rawContactInsertIndex);
        } else {
            return b.withValue(ContactsContract.Data.RAW_CONTACT_ID, m_rawContactId);
        }
    }

    private ContentProviderOperation.Builder setDataOfType(String mimetype) {
        if(!m_isNew) {
            Cursor c = m_resolver.query(ContactsContract.Data.CONTENT_URI,
                    new String[]{ ContactsContract.Data._ID },
                    ContactsContract.Data.RAW_CONTACT_ID + "=? AND " + ContactsContract.Data.MIMETYPE + "=?",
                    new String[] { String.valueOf(m_rawContactId), mimetype}, null);
            if(c.moveToFirst()) {
                long id = c.getLong(0);
                Log.v(TAG, "Already have a " + mimetype + " with ID - update" + id);
                c.close();
                return withContact(ContentProviderOperation.newUpdate(ContactsContract.Data.CONTENT_URI))
                        .withSelection(ContactsContract.Data._ID + "=?", new String[]{String.valueOf(id)});
            }
            c.close();
        }

        return withContact(ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI))
                .withValue(ContactsContract.Data.MIMETYPE, mimetype);
    }

    public void setNickname(String value) {
        m_operations.add(setDataOfType(ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.Nickname.TYPE, ContactsContract.CommonDataKinds.Nickname.TYPE_DEFAULT)
                        .withValue(ContactsContract.CommonDataKinds.Nickname.NAME, value)
                        .build());
    }

    public void setDisplayName(String displayName) {
        m_operations.add(setDataOfType(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                        .build());
    }

    public void setPhotoUri(String photoUri) throws Exception {
        Log.v(TAG, "Set photo to " + photoUri);
        m_operations.add(setDataOfType(ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                        .withValue(ContactsContract.Data.SYNC1, photoUri)
                        .build());
    }
}

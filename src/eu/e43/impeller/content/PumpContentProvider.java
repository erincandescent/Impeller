package eu.e43.impeller.content;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.util.LruCache;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.account.Authenticator;

/**
 * Created by OShepherd on 01/07/13.
 */
public class PumpContentProvider extends ContentProvider {
    public static final int RECIPIENT_TO  = 0;
    public static final int RECIPIENT_CC  = 1;
    public static final int RECIPIENT_BTO = 2;
    public static final int RECIPIENT_BCC = 3;
    public static final String[] RECIPIENT_KEYS = new String[] {
            "to", "cc", "bto", "bcc"
    };

    public static final String AUTHORITY = "eu.e43.impeller.content";
    public static final Uri PROVIDER_URI = Uri.parse("content://eu.e43.impeller.content/");

    public static class Uris {
        private static LruCache<Account, Uris> ms_uriCache = new LruCache(2);
        public static Uris get(Account acct) {
            Uris v = ms_uriCache.get(acct);
            if(v == null) {
                v = new Uris(acct);
                ms_uriCache.put(acct, v);
            }
            return v;
        }

        protected Uris(Account acct) {
            account = acct;
            baseUri = PROVIDER_URI.buildUpon().appendPath(acct.name).build();
            feedUri = baseUri.buildUpon().appendPath("feed").build();
            activitiesUri = baseUri.buildUpon().appendPath("activity").build();
            objectsUri = baseUri.buildUpon().appendPath("object").build();
        }

        public final Account account;
        public final Uri baseUri;
        public final Uri feedUri;
        public final Uri activitiesUri;
        public final Uri objectsUri;

        public Uri activityUri(int id) {
            return activitiesUri.buildUpon().appendPath(Integer.toString(id)).build();
        }

        public Uri objectUri(int id) {
            return objectsUri.buildUpon().appendPath(Integer.toString(id)).build();
        }

        public Uri repliesUri(int id) {
            return objectsUri.buildUpon()
                    .appendPath(Integer.toString(id))
                    .appendPath("replies")
                    .build();
        }
    }

    private static final String TAG = "PumpContentProvider";
    private static final UriMatcher ms_uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final Map<String,String>  ms_objectProjection
            = new HashMap<String, String>();
    private static final Map<String, String> ms_activityProjection
            = new HashMap<String, String>();
    private static final Map<String, String> ms_feedProjection
            = new HashMap<String, String>();

    private PumpDatabaseManager m_mgr;

    /* URIs */
    private static final int OBJECTS    = 1;
    private static final int OBJECT     = 2;
    private static final int REPLIES    = 3;
    private static final int ACTIVITIES = 4;
    private static final int ACTIVITY   = 5;
    private static final int FEED       = 6;

    private static void addStateProjections(Map<String, String> proj, String idField) {
        proj.put("replies",     "(SELECT COUNT(*) from objects as _rob WHERE _rob.inReplyTo=" + idField +")");
        proj.put("likes",       "(SELECT COUNT(*) from activities as _lac WHERE _lac.object=" + idField + " AND _lac.verb IN ('like', 'favorite'))");
        proj.put("shares",      "(SELECT COUNT(*) from activities as _sac WHERE _sac.object=" + idField + " AND _sac.verb='share')");
    }

    static {
        ms_uriMatcher.addURI(AUTHORITY, "*/object",             OBJECTS);
        ms_uriMatcher.addURI(AUTHORITY, "*/object/#",           OBJECT);
        ms_uriMatcher.addURI(AUTHORITY, "*/object/#/replies",   REPLIES);
        ms_uriMatcher.addURI(AUTHORITY, "*/activity",           ACTIVITIES);
        ms_uriMatcher.addURI(AUTHORITY, "*/activity/#",         ACTIVITY);
        ms_uriMatcher.addURI(AUTHORITY, "*/feed",               FEED);

        ms_objectProjection.put("_ID", "_ID");
        ms_objectProjection.put("id", "id");
        ms_objectProjection.put("objectType", "objectType");
        ms_objectProjection.put("author", "author");
        ms_objectProjection.put("published", "published");
        ms_objectProjection.put("updated", "updated");
        ms_objectProjection.put("inReplyTo", "inReplyTo");
        ms_objectProjection.put("_json", "_json");

        ms_activityProjection.put("_ID",            "activity._ID");
        ms_activityProjection.put("id",             "activity.id");
        ms_activityProjection.put("object.id",      "object.id");
        ms_activityProjection.put("verb",           "activity.verb");
        ms_activityProjection.put("actor",          "activity.actor");
        ms_activityProjection.put("object",         "activity.object");
        ms_activityProjection.put("target",         "activity.target");
        ms_activityProjection.put("published",      "activity.published");
        ms_activityProjection.put("updated",        "object.updated");
        ms_activityProjection.put("objectType",     "object.objectType");
        ms_activityProjection.put("_json",          "activity_object._json");

        ms_feedProjection.put("_ID",                "feed_entries._ID");
        ms_feedProjection.put("id",                 "activity.id");
        ms_feedProjection.put("object.id",          "object.id");
        ms_feedProjection.put("published",          "activity.published");
        ms_feedProjection.put("verb",               "activity.verb");
        ms_feedProjection.put("actor",              "activity.actor");
        ms_feedProjection.put("object",             "activity.object");
        ms_feedProjection.put("target",             "activity.target");
        ms_feedProjection.put("published",          "activity.published");
        ms_feedProjection.put("updated",            "object.updated");
        ms_feedProjection.put("objectType",         "object.objectType");
        ms_feedProjection.put("_json",              "activity_object._json");

        addStateProjections(ms_objectProjection,    "objects._ID");
        addStateProjections(ms_activityProjection,  "object._ID");
        addStateProjections(ms_feedProjection,      "object._ID");
    }

    @Override
    public boolean onCreate() {
        m_mgr = new PumpDatabaseManager(this);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        SQLiteDatabase db = m_mgr.getReadableDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            qb.setStrict(true);

        List<String> uriSegments = uri.getPathSegments();
        Uris uris;

        // Placate the IDE...
        String strId = "";
        int id = 0;

        if(uriSegments.isEmpty()) {
            throw new IllegalArgumentException("Bad path");
        } else {
            Account acct = new Account(uriSegments.get(0), Authenticator.ACCOUNT_TYPE);
            uris = Uris.get(acct);

            if(uriSegments.size() >= 3) {
                strId = uriSegments.get(2);
                id = Integer.parseInt(strId);
            }
        }

        switch(ms_uriMatcher.match(uri)) {
            case ACTIVITY:
                qb.appendWhere("activity._ID=");
                qb.appendWhere(strId);
                qb.appendWhere(" AND ");
            case ACTIVITIES:
                qb.appendWhere("activity.account=(SELECT _ID FROM accounts WHERE name=");
                qb.appendWhereEscapeString(uris.account.name);
                qb.appendWhere(")");

                qb.setTables(
                        "activities AS activity "
                      + "LEFT OUTER JOIN objects AS activity_object ON (activity._ID=activity_object._ID) "
                      + "LEFT OUTER JOIN objects AS object          ON (activity.object=object._ID) ");
                qb.setProjectionMap(ms_activityProjection);
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

            case OBJECT:
                qb.appendWhere("objects._ID=");
                qb.appendWhere(strId);
                qb.appendWhere(" AND ");

            case OBJECTS:
                qb.appendWhere("objects.account=(SELECT _ID FROM accounts WHERE name=");
                qb.appendWhereEscapeString(uris.account.name);
                qb.appendWhere(")");

                qb.setTables("objects");
                qb.setProjectionMap(ms_objectProjection);
                Log.i(TAG, "Query " + qb.buildQuery(projection, selection, null, null, sortOrder, null));
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

            case REPLIES:
                qb.appendWhere("objects.inReplyTo=");
                qb.appendWhere(strId);
                qb.appendWhere(" AND ");
                qb.appendWhere("objects.account=(SELECT _ID FROM accounts WHERE name=");
                qb.appendWhereEscapeString(uris.account.name);
                qb.appendWhere(")");

                qb.setTables("objects");
                qb.setProjectionMap(ms_objectProjection);
                Log.i(TAG, "Replies " + qb.buildQuery(projection, selection, null, null, sortOrder, null));
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

            case FEED:
                qb.appendWhere("feed_entries.account=(SELECT _ID FROM accounts WHERE name=");
                qb.appendWhereEscapeString(uris.account.name);
                qb.appendWhere(")");

                qb.setTables(
                        "feed_entries "
                      + "LEFT OUTER JOIN activities AS activity        ON (feed_entries.activity=activity._ID) "
                      + "LEFT OUTER JOIN objects    AS activity_object ON (feed_entries.activity=activity_object._ID) "
                      + "LEFT OUTER JOIN objects    AS object          ON (activity.object=object._ID) ");
                qb.setProjectionMap(ms_feedProjection);
                Log.i(TAG, "Feed " + qb.buildQuery(projection, selection, null, null, sortOrder, null));
                return qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

            default:
                throw new IllegalArgumentException("Bad URI");
        }
    }

    @Override
    public String getType(Uri uri) {
        switch(ms_uriMatcher.match(uri)) {
            case ACTIVITY:      return "vnd.android.cursor.item/vnd.e43.impeller.activity";
            case ACTIVITIES:    return "vnd.android.cursor.dir/vnd.e43.impeller.activity";
            case OBJECT:        return "vnd.android.cursor.item/vnd.e43.impeller.object";
            case OBJECTS:       return "vnd.android.cursor.dir/vnd.e43.impeller.object";
            case FEED:          return "vnd.android.cursor.dir/vnd.e43.impeller.activity";
            default: return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        SQLiteDatabase db = m_mgr.getWritableDatabase();
        if(!contentValues.containsKey("_json"))
            throw new IllegalArgumentException("Must provide JSON version");

        List<String> uriSegments = uri.getPathSegments();
        Uris uris;

        // Assign "default" values to placate the ID
        String strId = null;
        int id = 0;
        int accountId = 0;

        if(uriSegments.isEmpty()) {
            throw new IllegalArgumentException("Bad path");
        } else {
            Account acct = new Account(uriSegments.get(0), Authenticator.ACCOUNT_TYPE);
            uris = Uris.get(acct);

            // Get the account ID
            Cursor c = db.query("accounts", new String[] {"_ID"},
                    "name=?", new String[] {acct.name}, null, null, null);
            try {
                c.moveToFirst();
                accountId = c.getInt(0);
            } catch(CursorIndexOutOfBoundsException ex) {
                Log.e(TAG, "Missing user account " + acct.name, ex);
                throw ex;
            } finally {
                c.close();
            }

            if(uriSegments.size() >= 3) {
                strId = uriSegments.get(2);
                id = Integer.parseInt(strId);
            }
        }

        JSONObject obj;
        try {
            obj = new JSONObject(contentValues.getAsString("_json"));
        } catch (JSONException e) {
            Log.e(TAG, "Bad JSON on insert", e);
            throw new IllegalArgumentException("Bad JSON: " + e.getMessage());
        }
        try {
            db.beginTransaction();
            ContentResolver res = getContext().getContentResolver();

            Uri path;
            int match = ms_uriMatcher.match(uri);
            switch(match) {
                case FEED:
                case ACTIVITIES:
                    if(obj.has("objectType")) {
                        if(!"activity".equals(obj.optString("objectType")))
                            throw new IllegalArgumentException("Attempt to pass non-activity");
                    }

                    id = ensureActivity(db, obj, accountId);

                    db.setTransactionSuccessful();
                    path = uris.activityUri(id);
                    res.notifyChange(path, null);
                    res.notifyChange(uris.objectUri(id), null);

                    if(match == FEED) {
                        insertFeedEntry(db, id, accountId);
                    }
                    break;

                case OBJECTS:
                    id = ensureObject(db, obj, accountId);

                    db.setTransactionSuccessful();
                    path = uris.objectUri(id);
                    res.notifyChange(path, null);

                    break;

                default: throw new IllegalArgumentException("Bad URI");
            }

            res.notifyChange(uri, null);
            res.notifyChange(uris.activitiesUri, null);
            res.notifyChange(uris.objectsUri, null);
            res.notifyChange(uris.feedUri, null);
            return path;
        } finally {
            db.endTransaction();
        }
    }

    private void insertFeedEntry(SQLiteDatabase db, int id, int account) {
        ContentValues vals = new ContentValues();
        vals.put("activity", id);
        vals.put("account",  account);

        db.insertOrThrow("feed_entries", null, vals);
    }

    private JSONObject mergeJSON(JSONObject oldObj, JSONObject newObj) throws JSONException {
        for(Iterator<String> i = newObj.keys(); i.hasNext();) {
            String key = i.next();

            Object obj = newObj.get(key);
            if(obj instanceof JSONObject) {
                JSONObject jobj = (JSONObject) obj;
                Object oobj = oldObj.opt(key);
                if(oobj != null && oobj instanceof JSONObject) {
                    oldObj.put(key, mergeJSON((JSONObject) oobj, jobj));
                } else {
                    oldObj.put(key, jobj);
                }
            } else {
                oldObj.put(key, obj);
            }
        }
        return oldObj;
    }

    private JSONObject mergeEntry(SQLiteDatabase db, JSONObject newObj, int account) {
        Cursor c = db.query(
                "objects",
                new String[] { "_json" },
                "account=? AND id=?",
                new String[] { Integer.toString(account), newObj.optString("id") },
                null, null, null, null);

        try {
            if(c.moveToFirst()) {
                JSONObject oldObj = new JSONObject(c.getString(0));

                return mergeJSON(oldObj, newObj);
            } else {
                return newObj;
            }
        } catch (JSONException e) {
            Log.e(TAG, "Database parse error", e);
            return newObj;
        } finally {
            c.close();
        }
    }

    private int ensureEntry(SQLiteDatabase db, String table, ContentValues vals, int account) {
        vals.put("account", account);
        String[] sel = new String[] { Integer.toString(account), vals.getAsString("id") };
        Cursor c = db.query(table, new String[]{"_ID"}, "account=? AND id=?", sel,
                null, null, null);
        try {
            if(c.moveToFirst()) {
                int id = c.getInt(0);
                sel = new String[] { Integer.toString(id) };
                db.update(table, vals, "_ID=?", sel);

                return id;
            } else {
                return (int) db.insertOrThrow(table, null, vals);
            }
        } finally {
            c.close();
        }
    }

    private int ensureActivity(SQLiteDatabase db, JSONObject act, int account) {
        try {
            act = mergeEntry(db, act, account);

            String id           = act.getString("id");
            String verb         = act.optString("verb", "post");
            long published      = Utils.parseDate(act.optString("published"));

            JSONObject obj = act.optJSONObject("object");
            if(obj != null) {
                if(!obj.has("author") && "post".equals(act.optString("verb", "post")))
                    obj.put("author", act.optJSONObject("actor"));
            }

            Integer actor        = ensureObject(db, act.optJSONObject("actor"), account);
            Integer object       = ensureObject(db, obj, account);
            Integer target       = ensureObject(db, act.optJSONObject("target"), account);

            act.put("objectType", "activity");
            if(!act.has("author"))
                act.put("author", act.opt("actor"));

            int _id = ensureObject(db, act, account);

            db.delete("recipients", "activity=?", new String[] { Integer.toString(_id) });
            String[] keys = PumpContentProvider.RECIPIENT_KEYS;
            for(int i = 0; i < keys.length; i++) {
                JSONArray list = act.optJSONArray(keys[i]);
                if(list != null) for(int j = 0; j < list.length(); j++) {
                    JSONObject person = list.getJSONObject(j);
                    int recipient = ensureObject(db, person, account);

                    ContentValues cv = new ContentValues();
                    cv.put("recipient", recipient);
                    cv.put("activity",  _id);
                    cv.put("type", i);
                    db.insert("recipients", null, cv);
                }
            }

            ContentValues vals = new ContentValues();
            vals.put("_ID",         _id);
            vals.put("id",          id);
            vals.put("account",     account);
            vals.put("verb",        verb.toLowerCase());
            vals.put("published",   published);
            if(actor  != null) vals.put("actor",       actor);
            if(object != null) vals.put("object",      object);
            if(target != null) vals.put("target",      target);

            db.insertWithOnConflict("activities", null, vals,
                    SQLiteDatabase.CONFLICT_REPLACE);

            return _id;
        } catch(JSONException e) {
            Log.e(TAG, "Bad activity", e);
            throw new IllegalArgumentException("Bad activity");
        }
    }

    /** Ensure the object is in the database */
    Integer ensureObject(SQLiteDatabase db, JSONObject obj, int account) {
        if(obj == null)
            return null;

        try {
            Integer author    = ensureObject(db, obj.optJSONObject("author"), account);
            Integer inReplyTo = ensureObject(db, obj.optJSONObject("inReplyTo"), account);

            if(obj.has("replies")) {
                JSONObject replies = obj.getJSONObject("replies");
                JSONArray items = replies.optJSONArray("items");
                if(items != null) {
                    replies.remove("items"); // prevent recursion, quickly stale

                    for(int i = 0; i < items.length(); i++) {
                        JSONObject reply = items.getJSONObject(i);
                        reply.put("inReplyTo", obj);
                        ensureObject(db, reply, account);
                    }
                }
            }

            obj = mergeEntry(db, obj, account);

            String id           = obj.getString("id");
            String objectType   = obj.optString("objectType", "note");
            String publishedStr = obj.optString("published");
            long published      = Utils.parseDate(publishedStr);
            long updated        = Utils.parseDate(obj.optString("updated", publishedStr));

            ContentValues vals = new ContentValues();
            vals.put("_json",       obj.toString());
            vals.put("id",          id);
            vals.put("objectType",  objectType);
            vals.put("published",   published);
            vals.put("updated",     updated);
            if(author    != null) vals.put("author",      author);
            if(inReplyTo != null) vals.put("inReplyTo",   inReplyTo);

            int nid = ensureEntry(db, "objects", vals, account);
            Log.i(TAG, "Insert object " + id + " " + nid + " inReplyTo " + inReplyTo);
            return nid;
        } catch(JSONException e) {
            Log.e(TAG, "Bad object", e);
            throw new IllegalArgumentException("Bad object");
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if(method.equals("updateAccounts")) {
            SQLiteDatabase db = m_mgr.getWritableDatabase();
            AccountManager am = AccountManager.get(getContext());
            Account[] accts = am.getAccountsByType(Authenticator.ACCOUNT_TYPE);
            Set<String> accounts = new HashSet<String>();
            for(Account acct : accts)
                accounts.add(acct.name);

            Cursor c = db.query("accounts", new String[] {"_ID", "name"},
                    null, null, null, null, null);
            try {
                while(c.moveToNext()) {
                    if(accounts.contains(c.getString(1))) {
                        accounts.remove(c.getString(1));
                    } else {
                        removeAccount(db, c.getInt(0));
                    }
                }
            } finally {
                c.close();
            }

            for(String acct : accounts) {
                ContentValues cv = new ContentValues();
                cv.put("name", acct);
                db.insertOrThrow("accounts", null, cv);
            }
        }
        return null;
    }

    private void removeAccount(SQLiteDatabase db, int account) {
        db.beginTransaction();
        try {
            String[] args = new String[]{Integer.toString(account)};
            db.delete("recipients",
                    "(SELECT account FROM objects WHERE objects._ID=recipients.activity) = ?", args);

            db.delete("feed_entries", "account=?", args);
            db.delete("activities", "account=?", args);
            db.delete("objects", "account=?", args);
            db.delete("accounts", "_ID=?", args);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public int delete(Uri uri, String s, String[] strings) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String s, String[] strings) {
        throw new UnsupportedOperationException();
    }
}

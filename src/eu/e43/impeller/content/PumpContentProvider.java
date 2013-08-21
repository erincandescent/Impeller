package eu.e43.impeller.content;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;

/**
 * Created by OShepherd on 01/07/13.
 */
public class PumpContentProvider extends ContentProvider {
    public static final String AUTHORITY = "eu.e43.impeller.content";
    public static final String URL = "content://eu.e43.impeller.content";
    public static final String FEED_URL     = "content://eu.e43.impeller.content/feed";
    public static final String ACTIVITY_URL = "content://eu.e43.impeller.content/activity";
    public static final String OBJECT_URL   = "content://eu.e43.impeller.content/object";

    private static final String TAG = "PumpContentProvider";
    private static final UriMatcher ms_uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final Map<String,String>  ms_objectProjection
            = new HashMap<String, String>();
    private static final Map<String, String> ms_activityProjection
            = new HashMap<String, String>();
    private static final Map<String, String> ms_feedProjection
            = new HashMap<String, String>();

    private SQLiteDatabase m_database;

    /* URIs */
    private static final int OBJECTS    = 1;
    private static final int OBJECT     = 2;
    private static final int ACTIVITIES = 3;
    private static final int ACTIVITY   = 4;
    private static final int FEED       = 5;

    private static void addStateProjections(Map<String, String> proj, String idField) {
        proj.put("replies",     "(SELECT COUNT(*) from objects as _rob WHERE _rob.inReplyTo=" + idField +")");
        proj.put("likes",       "(SELECT COUNT(*) from activities as _lac WHERE _lac.object=" + idField + " AND _lac.verb IN ('like', 'favorite'))");
        proj.put("shares",      "(SELECT COUNT(*) from activities as _sac WHERE _sac.object=" + idField + " AND _sac.verb='share')");
    }

    static {
        ms_uriMatcher.addURI(AUTHORITY, "object",     OBJECTS);
        ms_uriMatcher.addURI(AUTHORITY, "object/*",   OBJECT);
        ms_uriMatcher.addURI(AUTHORITY, "activity",   ACTIVITIES);
        ms_uriMatcher.addURI(AUTHORITY, "activity/*", ACTIVITY);
        ms_uriMatcher.addURI(AUTHORITY, "feed/*",     FEED);

        ms_objectProjection.put("id", "id");
        ms_objectProjection.put("objectType", "objectType");
        ms_objectProjection.put("author", "author");
        ms_objectProjection.put("published", "published");
        ms_objectProjection.put("updated", "updated");
        ms_objectProjection.put("inReplyTo", "inReplyTo");
        ms_objectProjection.put("_json", "_json");
        addStateProjections(ms_objectProjection, "id");

        ms_activityProjection.put("id",             "activity.id");
        ms_activityProjection.put("verb",           "activity.verb");
        ms_activityProjection.put("actor",          "activity.actor");
        ms_activityProjection.put("object",         "activity.object");
        ms_activityProjection.put("target",         "activity.target");
        ms_activityProjection.put("published",      "activity.published");
        ms_activityProjection.put("updated",        "object.updated");
        ms_activityProjection.put("objectType",     "object.objectType");
        ms_activityProjection.put("_json",          "activity_object._json");
        addStateProjections(ms_activityProjection,  "activity.object");

        ms_feedProjection.put("id",                 "feed_entries.id");
        ms_feedProjection.put("published",          "feed_entries.published");
        ms_feedProjection.put("verb",               "activity.verb");
        ms_feedProjection.put("actor",              "activity.actor");
        ms_feedProjection.put("object",             "activity.object");
        ms_feedProjection.put("target",             "activity.target");
        ms_feedProjection.put("published",          "activity.published");
        ms_feedProjection.put("updated",            "object.updated");
        ms_feedProjection.put("objectType",         "object.objectType");
        ms_feedProjection.put("_json",              "activity_object._json");
        addStateProjections(ms_feedProjection,      "activity.object");
    }

    @Override
    public boolean onCreate() {
        m_database = getContext().openOrCreateDatabase("eu.e43.impeller.content", 0, null);
        if(m_database == null) return false;

        m_database.beginTransaction();
        try {
            int version = m_database.getVersion();
            Log.i(TAG, "Database opened - version is " + version);
            switch(version) {
                case 0:
                    Log.i(TAG, "Initializing database");
                    String sql;
                    try {
                        sql = Utils.readAll(getContext().getResources().openRawResource(R.raw.init_content));

                    } catch(IOException e) {
                        throw new RuntimeException(e);
                    }

                    String[] queries = sql.split(";(\\\\s)*[\\n\\r]");
                    for(int i = 0; i < queries.length; i++) {
                        m_database.execSQL(queries[i]);
                    }

                    m_database.setVersion(1);
                case 1:
                    Log.i(TAG, "Performing database migration to v2");
                    m_database.execSQL(
                        "UPDATE activities SET verb=LOWER(verb)");
                    m_database.setVersion(2);

                case 2:
                    Log.i(TAG, "Performing database migration to v3");
                    m_database.execSQL(
                        "CREATE INDEX ix_activities_related ON activities (object, verb)");
                    m_database.execSQL(
                        "CREATE INDEX ix_objects_inReplyTo ON objects (inReplyTo)");
                    m_database.setVersion(3);
                case 3:
                    break;
                default:
                    throw new RuntimeException("Unsupported database version");
            }

            m_database.setTransactionSuccessful();

            Cursor c = m_database.rawQuery("SELECT name, sql FROM sqlite_master WHERE type='table';", null);
            while(c.moveToNext()) {
                Log.i(TAG, "Have table " + c.getString(0) + " with SQL " + c.getString(1));
            }

            return true;
        } finally {
            m_database.endTransaction();
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        switch(ms_uriMatcher.match(uri)) {
            case ACTIVITY:
                qb.appendWhere("activities.id=");
                qb.appendWhereEscapeString(uri.getLastPathSegment());
            case ACTIVITIES:
                qb.setTables(
                        "activities "
                      + "LEFT OUTER JOIN objects AS activity_object ON (activities.id=activity_object.id) "
                      + "LEFT OUTER JOIN objects AS object          ON (activities.object=object.id) ");
                qb.setProjectionMap(ms_activityProjection);
                return qb.query(m_database, projection, selection, selectionArgs, null, null, sortOrder);

            case OBJECT:
                qb.appendWhere("objects.id=");
                qb.appendWhereEscapeString(uri.getLastPathSegment());
            case OBJECTS:
                qb.setTables("objects");
                qb.setProjectionMap(ms_objectProjection);
                return qb.query(m_database, projection, selection, selectionArgs, null, null, sortOrder);

            case FEED:
                qb.setTables(
                        "feed_entries "
                      + "LEFT OUTER JOIN activities AS activity     ON (feed_entries.id=activity.id) "
                      + "LEFT OUTER JOIN objects AS activity_object ON (feed_entries.id=activity_object.id) "
                      + "LEFT OUTER JOIN objects AS object          ON (activity.object=object.id) ");
                qb.setProjectionMap(ms_feedProjection);
                qb.appendWhere("account=");
                qb.appendWhereEscapeString(uri.getLastPathSegment());

                return qb.query(m_database, projection, selection, selectionArgs, null, null, sortOrder);

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
        if(!contentValues.containsKey("_json"))
            throw new IllegalArgumentException("Must provide JSON version");

        JSONObject obj;
        try {
            obj = new JSONObject(contentValues.getAsString("_json"));
        } catch (JSONException e) {
            Log.e(TAG, "Bad JSON on insert", e);
            throw new IllegalArgumentException("Bad JSON: " + e.getMessage());
        }
        try {
            m_database.beginTransaction();
            Uri path;
            switch(ms_uriMatcher.match(uri)) {
                case FEED:
                    insertFeedEntry(obj, uri.getLastPathSegment());

                    // FALLTHROUGH
                case ACTIVITIES:
                    if(obj.has("objectType")) {
                        if(!"activity".equals(obj.optString("objectType")))
                            throw new IllegalArgumentException("Attempt to pass non-activity");
                    }

                    String id = ensureActivity(obj);

                    m_database.setTransactionSuccessful();
                    path = new Uri.Builder()
                            .scheme("content")
                            .authority(AUTHORITY)
                            .appendPath("activity")
                            .appendPath(id)
                            .build();


                    break;

                case OBJECTS:
                    id = ensureObject(obj);

                    m_database.setTransactionSuccessful();
                    path = new Uri.Builder()
                            .scheme("content")
                            .authority(AUTHORITY)
                            .appendPath("object")
                            .appendPath(id)
                            .build();

                    break;

                default: throw new IllegalArgumentException("Bad URI");
            }

            ContentResolver res = getContext().getContentResolver();
            res.notifyChange(path, null);
            res.notifyChange(uri, null);
            res.notifyChange(Uri.parse(ACTIVITY_URL), null);
            res.notifyChange(Uri.parse(OBJECT_URL), null);
            res.notifyChange(Uri.parse(FEED_URL), null);
            return path;
        } finally {
            m_database.endTransaction();
        }
    }

    private static long parseDate(String date) {
        if(date == null)
            return new Date().getTime();

        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("Zulu"));
        try {
            return df.parse(date).getTime();
        } catch (ParseException e) {
            return new Date().getTime();
        }
    }

    private void insertFeedEntry(JSONObject obj, String account) {
        try {
            String id           = obj.getString("id");
            long published      = parseDate(obj.optString("published"));

            ContentValues vals = new ContentValues();
            vals.put("id",          id);
            vals.put("published",   published);
            vals.put("account",     account);

            m_database.insertOrThrow("feed_entries", null, vals);
        } catch(JSONException e) {
            Log.e(TAG, "Bad activity", e);
            throw new IllegalArgumentException("Bad activity");
        }
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

    private JSONObject mergeEntry(JSONObject newObj) {
        Cursor c = m_database.query(
                "objects",
                new String[] { "_json" },
                "id=?",
                new String[] { newObj.optString("id") },
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

    private void ensureEntry(String table, ContentValues vals) {
        try {
            m_database.insertOrThrow(table, null, vals);
        } catch(SQLiteConstraintException ex) {
            // Already exists
            m_database.update(table, vals, "id=?", new String[] { vals.getAsString("id") });
        }
    }

    private String ensureActivity(JSONObject act) {
        try {
            act = mergeEntry(act);

            String id           = act.getString("id");
            String verb         = act.optString("verb", "post");
            long published      = parseDate(act.optString("published"));

            JSONObject obj = act.optJSONObject("object");
            if(obj != null) {
                if(!obj.has("author"))
                    obj.put("author", act.optJSONObject("actor"));
            }

            String actor        = ensureObject(act.optJSONObject("actor"));
            String object       = ensureObject(obj);
            String target       = ensureObject(act.optJSONObject("target"));

            ContentValues vals = new ContentValues();
            vals.put("id",          id);
            vals.put("verb",        verb.toLowerCase());
            vals.put("published",   published);
            if(actor  != null) vals.put("actor",       actor);
            if(object != null) vals.put("object",      object);
            if(target != null) vals.put("target",      target);

            ensureEntry("activities", vals);

            act.put("objectType", "activity");
            if(!act.has("author"))
                act.put("author", act.opt("actor"));
            ensureObject(act);

            return id;
        } catch(JSONException e) {
            Log.e(TAG, "Bad activity", e);
            throw new IllegalArgumentException("Bad activity");
        }
    }

    /** Ensure the object is in the database */
    private String ensureObject(JSONObject obj) {
        if(obj == null)
            return null;

        try {
            String author       = ensureObject(obj.optJSONObject("author"));
            String inReplyTo    = ensureObject(obj.optJSONObject("inReplyTo"));

            if(obj.has("replies")) {
                JSONObject replies = obj.getJSONObject("replies");
                JSONArray items = replies.optJSONArray("items");
                if(items != null) {
                    replies.remove("items"); // prevent recursion, quickly stale

                    for(int i = 0; i < items.length(); i++) {
                        JSONObject reply = items.getJSONObject(i);
                        reply.put("inReplyTo", obj);
                        ensureObject(reply);
                    }
                }
            }

            obj = mergeEntry(obj);

            String id           = obj.getString("id");
            String objectType   = obj.optString("objectType", "note");
            String publishedStr = obj.optString("published");
            long published      = parseDate(publishedStr);
            long updated        = parseDate(obj.optString("updated", publishedStr));

            ContentValues vals = new ContentValues();
            vals.put("_json",       obj.toString());
            vals.put("id",          id);
            vals.put("objectType",  objectType);
            vals.put("published",   published);
            vals.put("updated",     updated);
            if(author    != null) vals.put("author",      author);
            if(inReplyTo != null) vals.put("inReplyTo",   inReplyTo);

            ensureEntry("objects", vals);

            return id;
        } catch(JSONException e) {
            Log.e(TAG, "Bad object", e);
            throw new IllegalArgumentException("Bad object");
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

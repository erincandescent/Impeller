package eu.e43.impeller.content;

import android.content.ContentProvider;
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

    static {
        ms_uriMatcher.addURI(AUTHORITY, "object",     OBJECTS);
        ms_uriMatcher.addURI(AUTHORITY, "object/*",   OBJECT);
        ms_uriMatcher.addURI(AUTHORITY, "activity",   ACTIVITIES);
        ms_uriMatcher.addURI(AUTHORITY, "activity/*", ACTIVITY);
        ms_uriMatcher.addURI(AUTHORITY, "feed/*",     FEED);

        ms_activityProjection.put("id",        "activity.id");
        ms_activityProjection.put("verb",      "activity.verb");
        ms_activityProjection.put("actor",     "activity.actor");
        ms_activityProjection.put("object",    "activity.object");
        ms_activityProjection.put("target",    "activity.target");
        ms_activityProjection.put("published", "activity.published");
        ms_activityProjection.put("updated",   "object.updated");
        ms_activityProjection.put("objectType","object.objectType");
        ms_activityProjection.put("_json",     "activity_object._json");

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
    }

    @Override
    public boolean onCreate() {
        m_database = getContext().openOrCreateDatabase("eu.e43.impeller.content", 0, null);
        if(m_database == null) return false;

        m_database.beginTransaction();
        try {
            int version = m_database.getVersion();
            switch(version) {
                case 0:
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

                    getContext().getContentResolver().notifyChange(path, null);
                    getContext().getContentResolver().notifyChange(uri, null);
                    return path;

                case OBJECTS:
                    id = ensureObject(obj);

                    m_database.setTransactionSuccessful();
                    path = new Uri.Builder()
                            .scheme("content")
                            .authority(AUTHORITY)
                            .appendPath("activity")
                            .appendPath(id)
                            .build();

                    getContext().getContentResolver().notifyChange(path, null);
                    getContext().getContentResolver().notifyChange(uri, null);
                    return path;
                default: throw new IllegalArgumentException("Bad URI");
            }
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

    private void ensureEntry(String table, ContentValues vals) {
        try {
            m_database.insertOrThrow(table, null, vals);
        } catch(SQLiteConstraintException ex) {
            // Already exists
            m_database.update(table, vals, "id=?", new String[] { vals.getAsString("id") });
        }
    }

    private String ensureActivity(JSONObject obj) {
        try {
            String id           = obj.getString("id");
            String verb         = obj.optString("verb", "post");
            long published      = parseDate(obj.optString("published"));
            String actor        = ensureObject(obj.optJSONObject("actor"));
            String object       = ensureObject(obj.optJSONObject("object"));
            String target       = ensureObject(obj.optJSONObject("target"));

            ContentValues vals = new ContentValues();
            vals.put("id",          id);
            vals.put("verb",        verb);
            vals.put("published",   published);
            vals.put("actor",       actor);
            vals.put("object",      object);
            vals.put("target",      target);

            ensureEntry("activities", vals);

            obj.put("objectType", "activity");
            if(!obj.has("author"))
                obj.put("author", obj.opt("actor"));
            ensureObject(obj);

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
            String id           = obj.getString("id");
            String objectType   = obj.optString("objectType", "note");
            String publishedStr = obj.optString("published");
            long published      = parseDate(publishedStr);
            long updated        = parseDate(obj.optString("updated", publishedStr));
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

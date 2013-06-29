package com.atlassian.jconnect.droid.persistence;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * This class is responsible for giving us a handle into the JMC Database.
 * However, class operates on a much lower layer than we would really prefer. As
 * a result it is package private and should remain that way. If you want to
 * operate on this database then write a helper function to do so in
 * {@link IssuePersister} to do the heavy lifting for you. This is especially
 * important as it will make sure that you close your open Database connections
 * properly and, in general, write code that is easiery for others to read.
 * 
 * @author rmassaioli
 * 
 */
class IssuePersisterDatabase extends SQLiteOpenHelper {
    private static final String TAG = IssuePersisterDatabase.class.getName();

    // Database Information
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "jmcdatabase";

    // Table Names
    public static final String ISSUES_TABLE_NAME = "issues";
    public static final String COMMENTS_TABLE_NAME = "issue_comments";

    // Create Table Strings
    private static final String CREATE_ISSUES_TABLE = "CREATE TABLE "
            + ISSUES_TABLE_NAME
            + " (id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, key TEXT NOT NULL, title TEXT, status TEXT, description TEXT, dateUpdated TEXT, hasUpdates INT, isCrashReport INT);";
    private static final String CREATE_COMMENTS_TABLE = "CREATE TABLE " + COMMENTS_TABLE_NAME + " (" + "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, "
            + "issue_id INTEGER, " + "username TEXT, " + "text TEXT, " + "date TEXT, " + "systemUser INT, "
            + "FOREIGN KEY (issue_id) REFERENCES issues(id) ON DELETE CASCADE);";

    public IssuePersisterDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(TAG, "Creating the required tables in the JMC database.");
        db.execSQL(CREATE_ISSUES_TABLE);
        db.execSQL(CREATE_COMMENTS_TABLE);
        Log.i(TAG, "Created the tables required by the JMC database.");
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL("PRAGMA foreign_keys=ON;");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // There is currently only one version of the database. No need to
        // upgrade.
    }

}

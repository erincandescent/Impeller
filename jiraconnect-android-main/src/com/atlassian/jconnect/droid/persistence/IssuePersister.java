package com.atlassian.jconnect.droid.persistence;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.IOUtils;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.atlassian.jconnect.droid.Api;
import com.atlassian.jconnect.droid.jira.Comment;
import com.atlassian.jconnect.droid.jira.Issue;
import com.atlassian.jconnect.droid.jira.Issue.Builder;
import com.atlassian.jconnect.droid.jira.IssueParser;
import com.atlassian.jconnect.droid.jira.IssuesWithComments;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

/**
 * Responsible for local storage of all data related to issues. This class uses
 * a database behind the scenes and there is only a few small rules that you
 * should use when using this class.<br/>
 * <ul>
 * <li>Only public functions are allowed to open database connections. Private
 * function should get a database passed into them to work with.</li>
 * <li>The function that opens the database connection is responsible for
 * closing the database connection</li>
 * </ul>
 * And that is it. Follow those conventions and this class should stay pretty
 * clean.
 * 
 * @since 1.0
 */
public class IssuePersister {
    private static final String TAG = "IssuePersister";

    private static final String PREFERENCES_NAME = "com.atlassian.jconnect.droid.persistence.IssuePersister";
    private static final String LAST_SERVER_CHECK = "lastServerCheck";

    private final Context context;
    private final IssuePersisterDatabase issuePersisterDatabase;

    public IssuePersister(Context context) {
        this.context = context;
        this.issuePersisterDatabase = new IssuePersisterDatabase(context);
    }

    private SharedPreferences getPreferences() {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    public long getLastServerCheck() {
        return getPreferences().getLong(LAST_SERVER_CHECK, 0);
    }

    public void setLastServerCheck(long lastCheck) {
        getPreferences().edit().putLong(LAST_SERVER_CHECK, lastCheck).commit();
    }

    /**
     * This function adds an issue to persistent storage and also performs
     * duplicate checking to make sure that it does not overwrite anything.
     * 
     * @param issue
     *            The issue that you wish to persist in this Android
     *            Application.
     */
    public void addCreatedIssue(Issue issue) {
        SQLiteDatabase db = issuePersisterDatabase.getWritableDatabase();
        if (issueKeyExists(db, issue.getKey())) {
            Log.i(TAG, "This issue has already been added to the local JMC Database before. Ignoring the addition of '" + issue.getKey() + "'.");
        } else {
            saveIssue(db, issue);
            List<Comment> issueComments = issue.getComments();
            if (issueComments != null) {
                String issueId = getIssueId(db, issue.getKey());
                for (Comment comment : issue.getComments()) {
                    saveComment(db, issueId, comment);
                }
            }
        }
        if (db != null) db.close();
    }

    private String getIssueId(SQLiteDatabase db, String issueKey) {
        String result = null;
        String getIssueQuery = "SELECT id FROM " + IssuePersisterDatabase.ISSUES_TABLE_NAME + " WHERE key = ?";
        Cursor issueCursor = db.rawQuery(getIssueQuery, new String[] { issueKey });
        if (issueCursor.moveToFirst()) {
            result = issueCursor.getString(0);
        }
        // Don't forget to close that cursor
        issueCursor.close();
        return result;
    }

    private boolean issueKeyExists(SQLiteDatabase db, String issueKey) {
        String getIssueCount = "SELECT id FROM " + IssuePersisterDatabase.ISSUES_TABLE_NAME + " WHERE key = ?";
        Cursor issuesCursor = db.rawQuery(getIssueCount, new String[] { issueKey });
        int count = issuesCursor.getCount();
        issuesCursor.close();
        return count > 0;
    }

    private void saveIssue(SQLiteDatabase db, Issue issue) {
        ContentValues values = new ContentValues(7);
        values.put("key", issue.getKey());
        values.put("title", issue.getTitle());
        values.put("status", issue.getStatus());
        values.put("description", issue.getDescription());
        DateFormat dateFormat = new SimpleDateFormat(dateFormatString, Locale.US);
        values.put("dateUpdated", dateFormat.format(issue.getDateUpdated()));
        values.put("hasUpdates", Boolean.FALSE);
        values.put("isCrashReport", Boolean.TRUE);
        db.insert(IssuePersisterDatabase.ISSUES_TABLE_NAME, null, values);
    }

    /**
     * This persists a Comment in this Android App. It does no checking to make
     * sure that the comment has not been added before. It assumes that the
     * given issueKey must belong to an {@link Issue} that has already been
     * persisted in this Android App via
     * {@link IssuePersister#addCreatedIssue(Issue)}.
     * 
     * @param issueKey
     *            The issue that this comment will be persisted against.
     * @param comment
     *            The comment to be saved.
     */
    public void addCreatedComment(String issueKey, Comment comment) {
        SQLiteDatabase db = issuePersisterDatabase.getWritableDatabase();

        String issueID = getIssueId(db, issueKey);
        if (issueID != null) {
            saveComment(db, issueID, comment);
        } else {
            Log.e(TAG, "We could not find the issue for the key '" + issueKey + "' in the JMC Database. Regretfully ignoring the comment made on the issue.");
        }
        db.close();
    }

    private static final String dateFormatString = "yyyy.MM.dd G 'at' HH:mm:ss z";

    private void saveComment(SQLiteDatabase db, String issueID, Comment comment) {
        ContentValues values = new ContentValues();
        values.put("issue_id", issueID);
        values.put("username", comment.getUsername());
        values.put("text", comment.getText());
        DateFormat dateFormat = new SimpleDateFormat(dateFormatString, Locale.US);
        values.put("date", dateFormat.format(comment.getDate()));
        values.put("systemUser", comment.isSystemUser());
        db.insert(IssuePersisterDatabase.COMMENTS_TABLE_NAME, null, values);
    }

    public void updateUsingIssuesWithComments(IssuesWithComments issuesWithComments) {
        SQLiteDatabase db = issuePersisterDatabase.getWritableDatabase();
        if (db != null) {
            for (Issue issue : issuesWithComments.issues()) {
                String issueID = getIssueId(db, issue.getKey());
                if (issueID == null) {
                    saveIssue(db, issue);
                    issueID = getIssueId(db, issue.getKey());
                    if (issueID == null) {
                        Log.e(TAG, "Created an issue and it still did not exist after I created it. Aborting and reporting error.");
                        // TODO Perform CONNECT-173 here
                    }
                }

                if (issueID != null && issue.getComments() != null) {
                    for (Comment comment : issue.getComments()) {
                        if (!commentExists(db, comment)) saveComment(db, issueID, comment);
                    }
                }
            }
            db.close();
        }
    }

    private static boolean commentExists(SQLiteDatabase db, Comment comment) {
        String query = "SELECT id FROM " + IssuePersisterDatabase.COMMENTS_TABLE_NAME + " WHERE username = ? AND text = ? AND date = ?";
        DateFormat dateFormat = new SimpleDateFormat(dateFormatString, Locale.US);
        Cursor commentCountQuery = db.rawQuery(query, new String[] { comment.getUsername(), comment.getText(), dateFormat.format(comment.getDate()) });
        int count = commentCountQuery.getCount();
        commentCountQuery.close();
        return count > 0;
    }

    public IssuesWithComments getIssues() {
        SQLiteDatabase db = issuePersisterDatabase.getReadableDatabase();
        String issuesQuery = "SELECT id, key, title, status, description, dateUpdated FROM " + IssuePersisterDatabase.ISSUES_TABLE_NAME;
        String commentQuery = "SELECT username, text, date, systemUser FROM issue_comments WHERE issue_id = ?";
        Cursor issueCursor = db.rawQuery(issuesQuery, new String[] {});

        List<Issue.Builder> issues = new ArrayList<Issue.Builder>();
        issueCursor.moveToFirst();
        DateFormat dateFormat = new SimpleDateFormat(dateFormatString, Locale.US);
        while (!issueCursor.isAfterLast()) {
            Issue.Builder issue = new Issue.Builder(issueCursor.getString(1));
            issue.title(issueCursor.getString(2));
            issue.status(issueCursor.getString(3));
            issue.description(issueCursor.getString(4));
            try {
                issue.dateUpdated(dateFormat.parse(issueCursor.getString(5)));
            } catch (ParseException e1) {
                // TODO Perform CONNECT-173 here
                issue.dateUpdated(new Date());
                Log.w(TAG, "Attempted to parse the issue date out of the database and failed. Setting to the current date.", e1);
                e1.printStackTrace();
            }
            Cursor commentCursor = db.rawQuery(commentQuery, new String[] { issueCursor.getString(0) });
            commentCursor.moveToFirst();
            while (!commentCursor.isAfterLast()) {
                try {
                    final String commentUsername = commentCursor.getString(0);
                    final String commentText = commentCursor.getString(1);
                    final Date commentCreatedDate = dateFormat.parse(commentCursor.getString(2));
                    final boolean isSystemUser = commentCursor.getInt(3) != 0;
                    final Comment comment = new Comment(commentUsername, commentText, commentCreatedDate, isSystemUser);
                    issue.addComment(comment);
                } catch (ParseException e) {
                    // TODO Perform CONNECT-173 here
                    Log.e(TAG, "Encountered a problem parsing an issue from the database.", e);
                    e.printStackTrace();
                }
                commentCursor.moveToNext();
            }
            commentCursor.close();
            issues.add(issue);
            issueCursor.moveToNext();
        }

        if (issueCursor != null) issueCursor.close();

        if (db != null) db.close();

        return new IssuesWithComments(Lists.transform(issues, new Function<Issue.Builder, Issue>() {
            @Override
            public Issue apply(Builder issueBuilder) {
                return issueBuilder.build();
            }
        }), System.currentTimeMillis());
    }

    private static final String OLD_ISSUES_CACHE_FILE = "issueswithcomments.json";

    /**
     * The entire purpose of this function is to recover the issues that were in
     * the old version of the library and then to delete the remaining file when
     * we are done with it. A user should never need to call this function, this
     * gets automatically run for them in
     * {@link Api#init(android.app.Application)}.
     */
    public void recoverOldIssues() {
        Log.d(TAG, "Attempting to recover old feeback for the benefit of the user.");
        FileInputStream oldCacheFile = null;
        try {
            // Load the old data from a file
            oldCacheFile = context.openFileInput(OLD_ISSUES_CACHE_FILE);
            final String oldIssuesWithCommentsJSON = IOUtils.toString(oldCacheFile);
            // Parse the Data - The old json save contained IssuesWithComments
            final IssuesWithComments oldIssues = new IssueParser(TAG).parseIssues(oldIssuesWithCommentsJSON);
            // Save the data in the new database.
            for (Issue issue : oldIssues.issues()) {
                addCreatedIssue(issue);
            }
        } catch (FileNotFoundException e) {
            Log.i(TAG, "There was no old version of the issues cache lying around. Don't need to recover anything.");
        } catch (IOException e) {
            Log.i(TAG, "Encountered problems handling the old cache file", e);
        } finally {
            if (oldCacheFile != null) {
                try {
                    oldCacheFile.close();
                    oldCacheFile = null;
                } catch (IOException e) {
                    Log.wtf(TAG, "Could not close a file that we already had opened.", e);
                }
                // Remove the file now that we are done with it.
                context.deleteFile(OLD_ISSUES_CACHE_FILE);
            }
        }
    }
}

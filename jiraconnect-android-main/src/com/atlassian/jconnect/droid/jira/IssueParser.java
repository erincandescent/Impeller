package com.atlassian.jconnect.droid.jira;

import java.io.IOException;
import java.util.Collections;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.google.common.collect.ImmutableList;

public final class IssueParser {

    private final String logTag;

    public IssueParser(String tag) {
        logTag = tag;
    }

    public IssuesWithComments parseIssues(String json) {
        // TODO CONNECT-174
        final ImmutableList.Builder<Issue> issues = ImmutableList.builder();
        try {
            JSONObject issuesWithCommentsObject = new JSONObject(json);
            JSONArray jsonIssues = issuesWithCommentsObject.getJSONArray("issuesWithComments");
            for (int i = 0; i < jsonIssues.length(); i++) {
                issues.add(parse(jsonIssues.getJSONObject(i)));
            }
            return new IssuesWithComments(issues.build(), issuesWithCommentsObject.getLong("sinceMillis"));
        } catch (JSONException e) {
            Log.e(logTag, "Failed to unmarshall response json: " + json, e);
            return IssuesWithComments.DUMMY;
        }
    }

    public Issue parse(JSONObject issueObject) throws JSONException {
        return new Issue.Builder(issueObject.getString("key")).title(issueObject.getString("summary"))
                .description(issueObject.optString("description", null))
                .status(issueObject.optString("status", null))
                .hasUpdates(issueObject.optBoolean("hasUpdates"))
                .dateUpdated(getDateSafe(issueObject, "dateUpdated"))
                .comments(parseComments(issueObject.optJSONArray("comments")))
                .build();
    }

    public Iterable<Comment> parseComments(JSONArray commentsObject) throws JSONException {
        if (commentsObject == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<Comment> comments = ImmutableList.builder();
        for (int i = 0; i < commentsObject.length(); i++) {
            comments.add(parseComment(commentsObject.getJSONObject(i)));
        }
        return comments.build();
    }

    public Comment parseComment(String jsonComment) {
        try {
            return parseComment(new JSONObject(jsonComment));
        } catch (JSONException e) {
            Log.e(logTag, "Failed to unmarshall JSON string: " + jsonComment, e);
            return Comment.EMPTY;
        }
    }

    public Comment parseComment(JSONObject commentObject) throws JSONException {
        return new Comment(commentObject.getString("username"), commentObject.getString("text"), getDateSafe(commentObject, "date"),
                commentObject.optBoolean("systemUser"));
    }

    public String toJsonString(IssuesWithComments issuesWithComments) {
        try {
            return toJson(issuesWithComments).toString();
        } catch (JSONException e) {
            Log.e(logTag, "Failed to marshall issues to JSON string: " + issuesWithComments, e);
            return null;
        }
    }

    public JSONObject toJson(IssuesWithComments issuesWithComments) throws JSONException {
        final JSONObject issuesObject = new JSONObject();
        issuesObject.put("sinceMillis", issuesWithComments.lastUpdated());
        issuesObject.put("issuesWithComments", new JSONArray());
        for (Issue issue : issuesWithComments.issues()) {
            issuesObject.accumulate("issuesWithComments", toJson(issue));
        }
        return issuesObject;
    }

    public static String parseIssueKey(HttpEntity entity) throws IOException, JSONException {
        JSONObject issue = new JSONObject(EntityUtils.toString(entity));
        return issue.getString("key");
    }

    private JSONObject toJson(Issue issue) throws JSONException {
        final JSONObject issueObject = new JSONObject();
        issueObject.put("key", issue.getKey());
        issueObject.put("summary", issue.getTitle());
        issueObject.putOpt("description", issue.getDescription());
        issueObject.putOpt("status", issue.getStatus());
        issueObject.put("hasUpdates", issue.hasUpdates());
        issueObject.putOpt("dateUpdated", asLong(issue.getDateUpdated()));
        issueObject.put("comments", new JSONArray());
        for (Comment comment : issue.getComments()) {
            if (!Comment.isEmpty(comment)) {
                issueObject.accumulate("comments", toJson(comment));
            }
        }
        return issueObject;
    }

    private JSONObject toJson(Comment comment) throws JSONException {
        final JSONObject commentObject = new JSONObject();
        commentObject.put("username", comment.getUsername());
        commentObject.put("text", comment.getText());
        commentObject.putOpt("date", asLong(comment.getDate()));
        commentObject.put("systemUser", comment.isSystemUser());
        return commentObject;
    }

    private Date getDateSafe(JSONObject issueObject, String key) throws JSONException {
        final long val = issueObject.optLong(key, Long.MIN_VALUE);
        return val != Long.MIN_VALUE ? new Date(val) : null;
    }

    private Long asLong(Date date) {
        return date != null ? date.getTime() : null;
    }
}

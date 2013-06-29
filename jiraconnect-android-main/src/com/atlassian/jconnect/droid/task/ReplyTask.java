package com.atlassian.jconnect.droid.task;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

import com.atlassian.jconnect.droid.jira.Comment;
import com.atlassian.jconnect.droid.jira.IssueParser;
import com.atlassian.jconnect.droid.net.RestURLGenerator;
import com.atlassian.jconnect.droid.net.params.ReplyTaskParams;
import com.atlassian.jconnect.droid.service.ServiceCallback;
import com.google.common.base.Preconditions;

public class ReplyTask extends AsyncTask<ReplyTaskParams, Void, ReplyTask.Result> {

    private static final String LOG_TAG = ReplyTask.class.getSimpleName();

    private static final String UTF_8 = "UTF-8";

    // TODO add attachment handling

    private final ServiceCallback<Result> callback;

    public ReplyTask(ServiceCallback<Result> serviceCallback) {
        this.callback = Preconditions.checkNotNull(serviceCallback, "serviceCallback");
    }

    public static final class Result {
        private final String issueKey;
        private final Comment reply;

        public Result(String issueKey, Comment reply) {
            this.issueKey = issueKey;
            this.reply = reply;
        }

        public String issueKey() {
            return issueKey;
        }

        public Comment reply() {
            return reply;
        }
    }

    @Override
    protected Result doInBackground(ReplyTaskParams... paramsArray) {
        if (paramsArray.length != 1) {
            throw new IllegalArgumentException("Should have exactly 1 params object");
        }
        ReplyTaskParams params = paramsArray[0];
        MultipartEntity entity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);
        ContentBody issuePart = getIssuePart(params);
        if (issuePart == null) {
            Log.e(LOG_TAG, "Could not get the issue part of the entity in the ReplyTask.");
            return null;
        }
        final AndroidHttpClient client = AndroidHttpClient.newInstance("JIRA Connect Android Client");
        String commentJson = null;
        try {
            try {
                final HttpPost post = RestURLGenerator.createIssueComment(params);
                Log.d(LOG_TAG, "Request URI: " + post.getURI().toString());
                entity.addPart("issue", issuePart);
                post.setEntity(entity);
                final HttpResponse resp = client.execute(post);
                StatusLine status = resp.getStatusLine();
                String responseString = EntityUtils.toString(resp.getEntity());
                if (status.getStatusCode() == 200) {
                    commentJson = responseString;
                } else {
                    Log.e(LOG_TAG, String.format("Received %s (%s): %s", status.getStatusCode(), status.getReasonPhrase(), responseString));
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to add comment to " + params.getIssueKey(), e);
            }
        } finally {
            client.close();
        }
        return new Result(params.getIssueKey(), commentJson != null ? new IssueParser(LOG_TAG).parseComment(commentJson) : Comment.EMPTY);
    }

    private ContentBody getIssuePart(ReplyTaskParams params) {
        try {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("uuid", params.getUuid());
            jsonObj.put("description", params.getReply());
            return new StringBody(jsonObj.toString(), "application/json", Charset.forName(UTF_8));
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to marshal JSON", e);
            return null;
        } catch (UnsupportedEncodingException e) {
            Log.wtf(LOG_TAG, "UTF-8 is not installed!?", e);
            return null;
        }
    }

    @Override
    protected void onPostExecute(Result result) {
        if (result != null && !Comment.isEmpty(result.reply)) {
            callback.onResult(ServiceCallback.Status.SUCCESS, result);
        } else {
            callback.onResult(ServiceCallback.Status.FAILURE, result);
        }
    }
}

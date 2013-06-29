package com.atlassian.jconnect.droid.task;

import static java.lang.String.format;

import java.io.IOException;
import java.lang.ref.WeakReference;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

import com.atlassian.jconnect.droid.jira.Issue;
import com.atlassian.jconnect.droid.jira.IssueParser;
import com.atlassian.jconnect.droid.net.RestURLGenerator;
import com.atlassian.jconnect.droid.net.params.CreateIssueParams;
import com.atlassian.jconnect.droid.service.FeedbackAttachment;
import com.atlassian.jconnect.droid.service.RemoteFeedbackService;

public class CreateFeedbackTask extends AsyncTask<CreateIssueParams, Void, Issue> {

    private static final String LOG_TAG = CreateFeedbackTask.class.getSimpleName();

    final WeakReference<RemoteFeedbackService> contextRef;

    public CreateFeedbackTask(RemoteFeedbackService service) {
        this.contextRef = new WeakReference<RemoteFeedbackService>(service);
    }

    @Override
    protected Issue doInBackground(CreateIssueParams... paramsArray) {
        if (paramsArray.length != 1) {
            throw new IllegalArgumentException("Should have exactly 1 params object");
        }

        final CreateIssueParams params = paramsArray[0];
        final MultipartEntity entity = params.toMultipartEntity();
        if (entity == null) return null;

        Issue issue = null;
        final AndroidHttpClient client = AndroidHttpClient.newInstance("JIRA Connect Android Client");
        try {
            final HttpPost post = RestURLGenerator.getIssueCreateRequest(params);
            post.setEntity(entity);
            final HttpResponse resp = client.execute(post);
            final StatusLine status = resp.getStatusLine();
            final String responseAsString = EntityUtils.toString(resp.getEntity());
            if (status.getStatusCode() == 200) {
                issue = (new IssueParser(LOG_TAG)).parse(new JSONObject(responseAsString));
            } else {
                Log.e(LOG_TAG, format("Queried %s and Received %s: %s: %s", post.getURI(), status.getStatusCode(), status.getReasonPhrase(), responseAsString));
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to create JIRA issue", e);
        } catch (JSONException e) {
            Log.e(LOG_TAG, "Failed to parse create issue json response", e);
        } finally {
            client.close();
            cleanUpAttachments(params);
        }

        return issue;
    }

    /**
     * Clean up any temporary attachments once we're done.
     * 
     * @param params
     *            task params
     */
    private void cleanUpAttachments(CreateIssueParams params) {
        for (FeedbackAttachment attachment : params.attachments) {
            if (attachment.isTemporary() && attachment.exists()) {
                FileUtils.deleteQuietly(attachment.getSource());
            }
        }
    }

    @Override
    protected void onPostExecute(Issue issue) {
        final RemoteFeedbackService owner = contextRef.get();
        if (owner != null) {
            if (issue != null) {
                owner.onFeedbackCreated(issue);
            } else {
                owner.onFeedbackFailed();
            }
        } else {
            Log.w(LOG_TAG, "Context is gone!");
        }

    }
}

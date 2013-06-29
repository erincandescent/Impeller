package com.atlassian.jconnect.droid.task;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Date;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.util.Log;

import com.atlassian.jconnect.droid.jira.IssueParser;
import com.atlassian.jconnect.droid.jira.IssuesWithComments;
import com.atlassian.jconnect.droid.net.RestURLGenerator;
import com.atlassian.jconnect.droid.net.params.GetFeedbackItemsParams;
import com.atlassian.jconnect.droid.service.ServiceCallback;

/**
 * Async task that retrieves feedback items (issues) from JIRA.
 * 
 * @since 1.0
 */
public class GetFeedbackItemsTask extends AsyncTask<GetFeedbackItemsParams, Void, GetFeedbackItemsTask.FeedbackItemsResult> {

    private static final String LOG_TAG = GetFeedbackItemsTask.class.getSimpleName();

    private final ServiceCallback<FeedbackItemsResult> callback;

    public GetFeedbackItemsTask(ServiceCallback<FeedbackItemsResult> callback) {
        this.callback = callback;
    }

    public static final class FeedbackItemsResult {
        public final String json;
        public final IssuesWithComments issues;

        FeedbackItemsResult(String json, IssuesWithComments issues) {
            this.json = json;
            this.issues = issues;
        }

        @Override
        public String toString() {
            return "FeedbackItemsResult[" + "json=" + json + ",timestamp=" + new Date(issues.lastUpdated()) + "]";
        }
    }

    @Override
    protected FeedbackItemsResult doInBackground(GetFeedbackItemsParams... paramsArray) {
        if (paramsArray.length != 1) {
            throw new IllegalArgumentException("Should have exactly 1 params object");
        }
        final GetFeedbackItemsParams params = paramsArray[0];
        logParams(params);
        final AndroidHttpClient client = AndroidHttpClient.newInstance("JIRA Connect Android Client");
        String json = null;
        try {
            final HttpGet get = RestURLGenerator.getIssueUpdatesRequest(params);
            final HttpResponse resp = client.execute(get);
            StatusLine status = resp.getStatusLine();
            if (status.getStatusCode() == 200) {
                json = EntityUtils.toString(resp.getEntity(), "UTF-8");
            } else {
                Log.e(LOG_TAG, format("Received %s  (%s): %s", status.getStatusCode(), status.getReasonPhrase(), EntityUtils.toString(resp.getEntity())));
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to retrieve JIRA issues JSON", e);
        } finally {
            client.close();
        }
        return json != null ? new FeedbackItemsResult(json, new IssueParser(LOG_TAG).parseIssues(json)) : null;
    }

    private void logParams(GetFeedbackItemsParams params) {
        Log.d(LOG_TAG, "Executing for params " + params);
    }

    @Override
    protected void onPostExecute(FeedbackItemsResult result) {
        logResult(result);
        callback.onResult(result != null ? ServiceCallback.Status.SUCCESS : ServiceCallback.Status.FAILURE, result);
    }

    private void logResult(FeedbackItemsResult result) {
        Log.d(LOG_TAG, "Result: " + result);
    }
}

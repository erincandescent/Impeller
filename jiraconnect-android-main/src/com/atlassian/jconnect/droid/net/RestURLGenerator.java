package com.atlassian.jconnect.droid.net;

import java.io.UnsupportedEncodingException;
import java.util.LinkedList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import com.atlassian.jconnect.droid.net.params.CreateIssueParams;
import com.atlassian.jconnect.droid.net.params.GetFeedbackItemsParams;
import com.atlassian.jconnect.droid.net.params.ReplyTaskParams;

public class RestURLGenerator {
    private static final String TAG = RestURLGenerator.class.getName();

    private final String baseUrl;
    private final String restUrl;
    private final List<NameValuePair> params;

    RestURLGenerator(String baseUrl, String restUrl, List<NameValuePair> params) {
        super();
        this.baseUrl = baseUrl;
        this.restUrl = restUrl;
        this.params = params;
    }

    public static class Builder {
        private final String baseUrl;
        private final String restUrl;
        private final List<NameValuePair> params;

        public Builder(String baseUrl, String restUrl) {
            super();
            this.baseUrl = baseUrl;
            this.restUrl = restUrl;
            this.params = new LinkedList<NameValuePair>();
        }

        public Builder addParameter(String name, Object value) {
            params.add(new BasicNameValuePair(name, value.toString()));
            return this;
        }

        public Builder addParameterIfNotBlank(String name, String value) {
            if (value != null && !value.trim().equals("")) {
                params.add(new BasicNameValuePair(name, value));
            }
            return this;
        }

        public RestURLGenerator build() {
            return new RestURLGenerator(baseUrl, restUrl, params);
        }
    }

    private String toQueryString() {
        final StringBuilder sb = new StringBuilder().append(baseUrl).append(restUrl);
        if (!params.isEmpty()) {
            sb.append('?');
            sb.append(URLEncodedUtils.format(params, "UTF-8"));
        }
        return sb.toString();
    }

    public HttpGet asGet() {
        return new HttpGet(toQueryString());
    }

    public HttpPost asPost() {
        return new HttpPost(toQueryString());
    }

    public static HttpPost getIssueCreateRequest(CreateIssueParams params) throws UnsupportedEncodingException {
        return new RestURLGenerator.Builder(params.url, "/rest/jconnect/latest/issue/create").addParameter("project", params.project)
                .addParameterIfNotBlank("apikey", params.apiKey)
                .build()
                .asPost();
    }

    public static HttpGet getIssueUpdatesRequest(GetFeedbackItemsParams params) {
        return new RestURLGenerator.Builder(params.getBaseUrl(), "/rest/jconnect/latest/issue/updates").addParameter("project", params.getProject())
                .addParameter("uuid", params.getUuid())
                .addParameter("sinceMillis", params.getLastCheckInMillis())
                .addParameter("apikey", params.getApiKey())
                .build()
                .asGet();
    }

    public static HttpPost createIssueComment(ReplyTaskParams params) {
        return new RestURLGenerator.Builder(params.getBaseUrl(), "/rest/jconnect/latest/issue/comment/" + params.getIssueKey()).addParameter(
                "apikey",
                params.getApiKey())
                .build()
                .asPost();
    }
}

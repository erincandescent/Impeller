package com.atlassian.jconnect.droid.net.params;

import java.util.Date;

import com.atlassian.jconnect.droid.config.BaseConfig;

public final class GetFeedbackItemsParams {
    private final String baseUrl;
    private final String project;
    private final String apiKey;
    private final String uuid;
    private final long lastCheckInMillis;

    public GetFeedbackItemsParams(String baseUrl, String project, String apiKey, String uuid, long lastCheckInMillis) {
        this.baseUrl = baseUrl;
        this.project = project;
        this.apiKey = apiKey;
        this.uuid = uuid;
        this.lastCheckInMillis = lastCheckInMillis;
    }

    public GetFeedbackItemsParams(BaseConfig baseConfig, long lastCheckInMillis) {
        this.baseUrl = baseConfig.getServerUrl();
        this.project = baseConfig.getProjectKey();
        this.apiKey = baseConfig.getApiKey();
        this.uuid = baseConfig.uniqueId().getUuid();
        this.lastCheckInMillis = lastCheckInMillis;
    }

    @Override
    public String toString() {
        return "RequestParams[" + "baseUrl=" + baseUrl + "," + "project=" + project + "," + "apiKey=" + apiKey + "," + "uuid=" + uuid + "," + "lastCheck="
                + new Date(lastCheckInMillis).toString() + "]";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getProject() {
        return project;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getUuid() {
        return uuid;
    }

    public long getLastCheckInMillis() {
        return lastCheckInMillis;
    }
}
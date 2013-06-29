package com.atlassian.jconnect.droid.net.params;

import com.atlassian.jconnect.droid.config.BaseConfig;

/**
 * Base class for parameters of tasks executing remote connection to the JIRA
 * server.
 * 
 * @since 1.0
 */
public abstract class AbstractRemoteTaskParams {
    protected final String baseUrl;
    protected final String project;
    protected final String apiKey;
    protected final String uuid;
    protected final String udid;

    protected AbstractRemoteTaskParams(String baseUrl, String project, String apiKey, String uuid, String udid) {
        this.baseUrl = baseUrl;
        this.project = project;
        this.apiKey = apiKey;
        this.uuid = uuid;
        this.udid = udid;
    }

    protected AbstractRemoteTaskParams(BaseConfig config) {
        this(config.getServerUrl(), config.getProjectKey(), config.getApiKey(), config.uniqueId().getUuid(), config.uniqueId().getUdid());
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

    public String getUdid() {
        return udid;
    }
}

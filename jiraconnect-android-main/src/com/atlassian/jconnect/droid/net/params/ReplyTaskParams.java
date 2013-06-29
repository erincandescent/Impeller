package com.atlassian.jconnect.droid.net.params;

import com.atlassian.jconnect.droid.config.BaseConfig;

public final class ReplyTaskParams extends AbstractRemoteTaskParams {

    final String issueKey;
    final String reply;

    public ReplyTaskParams(BaseConfig config, String issueKey, String reply) {
        super(config);
        this.issueKey = issueKey;
        this.reply = reply;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public String getReply() {
        return reply;
    }
}
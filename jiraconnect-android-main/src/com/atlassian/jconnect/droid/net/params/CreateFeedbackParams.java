package com.atlassian.jconnect.droid.net.params;

import com.atlassian.jconnect.droid.service.FeedbackAttachment;
import com.atlassian.jconnect.droid.task.CreateFeedbackTask;
import com.google.common.collect.ImmutableList;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Bean holding parameters for the {@link CreateFeedbackTask}.
 * 
 * @since v1.0
 */
public class CreateFeedbackParams {

    public static class Builder {
        private final String url;
        private final String project;
        private final String apiKey;
        private String uuid;
        private String udid;
        private String feedback;
        private String type;
        private final ImmutableList.Builder<FeedbackAttachment> attachments = ImmutableList.builder();

        public Builder(String url, String project) {
            this(url, project, null);
        }

        public Builder(String url, String project, String apiKey) {
            this.url = checkNotNull(url);
            this.project = checkNotNull(project);
            this.apiKey = apiKey;
        }

        public Builder uuid(String uuid) {
            this.uuid = checkNotNull(uuid);
            return this;
        }

        public Builder udid(String udid) {
            this.udid = checkNotNull(udid);
            return this;
        }

        public Builder feedback(String feedback) {
            this.feedback = checkNotNull(feedback);
            return this;
        }

        public Builder type(String type) {
            this.type = checkNotNull(type);
            return this;
        }

        public Builder addAttachments(Iterable<FeedbackAttachment> attachments) {
            this.attachments.addAll(checkNotNull(attachments));
            return this;
        }

        public Builder addAttachment(FeedbackAttachment attachment) {
            this.attachments.add(checkNotNull(attachment, "attachment"));
            return this;
        }

        public CreateFeedbackParams build() {
            checkNotNull(uuid);
            checkNotNull(udid);
            return new CreateFeedbackParams(this);
        }
    }

    public final String url;
    public final String apiKey;
    public final String uuid;
    public final String udid;
    public final String project;
    public final String feedback;
    public final String type;
    public final Iterable<FeedbackAttachment> attachments;

    private CreateFeedbackParams(Builder builder) {
        this.uuid = builder.uuid;
        this.udid = builder.udid;
        this.url = builder.url;
        this.apiKey = builder.apiKey;
        this.project = builder.project;
        this.feedback = builder.feedback;
        this.type = builder.type;
        this.attachments = builder.attachments.build();
    }
}

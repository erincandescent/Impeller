package com.atlassian.jconnect.droid.net.params;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Locale;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.json.JSONException;
import org.json.JSONObject;

import android.os.Build;
import android.util.Log;

import com.atlassian.jconnect.droid.service.FeedbackAttachment;
import com.atlassian.jconnect.droid.task.CreateFeedbackTask;
import com.google.common.collect.ImmutableList;

/**
 * Bean holding parameters for the {@link CreateFeedbackTask}.
 * 
 * @since v1.0
 */
// TODO Make this extend the AbstractRemoteTaskParams
public class CreateIssueParams {
    private static final String TAG = CreateIssueParams.class.getName();

    public static class Builder {
        private final String url;
        private final String project;
        private final String apiKey;
        private String uuid;
        private String udid;
        private String summary;
        private String description;
        private String appPackageName;
        private String appVersion;
        private String type;
        private Boolean isCrash;
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

        public Builder summary(String summary) {
            this.summary = checkNotNull(summary);
            return this;
        }

        public Builder description(String description) {
            this.description = checkNotNull(description);
            return this;
        }

        public Builder appVersion(String appVersion) {
            this.appVersion = checkNotNull(appVersion);
            return this;
        }

        public Builder appPackageName(String appPackageName) {
            this.appPackageName = appPackageName;
            return this;
        }

        public Builder type(String type) {
            this.type = checkNotNull(type);
            return this;
        }

        public Builder isCrash(boolean isCrash) {
            this.isCrash = Boolean.valueOf(isCrash);
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

        public CreateIssueParams build() {
            checkNotNull(uuid);
            checkNotNull(udid);
            checkNotNull(appVersion);
            checkNotNull(appPackageName);
            return new CreateIssueParams(this);
        }
    }

    public final String url;
    public final String apiKey;
    public final String uuid;
    public final String udid;
    public final String project;
    public final String summary;
    public final String description;
    public final String appVersion;
    public final String appPackageName;
    public final String issueType;
    private final Boolean isCrash;
    public final Iterable<FeedbackAttachment> attachments;

    private CreateIssueParams(Builder builder) {
        this.uuid = builder.uuid;
        this.udid = builder.udid;
        this.url = builder.url;
        this.apiKey = builder.apiKey;
        this.project = builder.project;
        this.summary = builder.summary;
        this.description = builder.description;
        this.appVersion = builder.appVersion;
        this.appPackageName = builder.appPackageName;
        this.issueType = builder.type;
        this.isCrash = builder.isCrash;
        this.attachments = builder.attachments.build();
    }

    private static final String ANDROID_OS = "Android OS";

    // TODO CONNECT-174
    private JSONObject generateJSONfromParams() throws JSONException {
        final JSONObject json = new JSONObject();

        json.put("uuid", this.uuid);
        json.put("udid", this.udid);
        json.put("devName", Build.DEVICE);
        json.put("systemName", ANDROID_OS);
        json.put("systemVersion", Build.VERSION.RELEASE);
        json.put("model", Build.MODEL);
        json.put("appVersion", this.appVersion);
        json.put("appName", this.project);
        json.put("appId", this.appPackageName);
        json.put("language", Locale.getDefault().getDisplayLanguage());
        json.put("description", this.description);
        json.put("summary", this.summary);
        json.put("isCrash", (this.isCrash == null ? Boolean.FALSE : this.isCrash));
        if (this.issueType != null) {
            json.put("issueType", this.issueType);
        }

        return json;
    }

    public MultipartEntity toMultipartEntity() {
        final MultipartEntity entity = new MultipartEntity(HttpMultipartMode.STRICT);
        ContentBody jsonPart;

        try {
            JSONObject json = this.generateJSONfromParams();
            jsonPart = new StringBody(json.toString(), "application/json", Charset.forName("UTF-8"));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to marshal JSON", e);
            return null;
        } catch (UnsupportedEncodingException e) {
            Log.e(TAG, "UTF-8 is not installed!?", e);
            return null;
        }

        entity.addPart("issue", jsonPart);

        for (FeedbackAttachment attachmentEntry : this.attachments) {
            if (attachmentEntry.exists()) {
                entity.addPart(attachmentEntry.getName(), new FileBody(attachmentEntry.getSource()));
            }
        }

        return entity;
    }
}

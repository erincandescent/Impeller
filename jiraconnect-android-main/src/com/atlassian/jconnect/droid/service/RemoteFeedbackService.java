package com.atlassian.jconnect.droid.service;

import static com.google.common.base.Preconditions.checkNotNull;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.atlassian.jconnect.droid.R;
import com.atlassian.jconnect.droid.config.BaseConfig;
import com.atlassian.jconnect.droid.config.UniqueId;
import com.atlassian.jconnect.droid.jira.Comment;
import com.atlassian.jconnect.droid.jira.Issue;
import com.atlassian.jconnect.droid.jira.IssuesWithComments;
import com.atlassian.jconnect.droid.net.params.CreateIssueParams;
import com.atlassian.jconnect.droid.net.params.GetFeedbackItemsParams;
import com.atlassian.jconnect.droid.net.params.ReplyTaskParams;
import com.atlassian.jconnect.droid.persistence.IssuePersister;
import com.atlassian.jconnect.droid.task.CreateFeedbackTask;
import com.atlassian.jconnect.droid.task.GetFeedbackItemsTask;
import com.atlassian.jconnect.droid.task.ReplyTask;
import com.atlassian.jconnect.droid.ui.UiUtil;

/**
 * Local service to manage feedback related communication with remote JIRA
 * server.
 * 
 * @since 1.0
 */
public class RemoteFeedbackService extends Service {
    private static final String LOG_TAG = "RemoteFeedbackService";

    private Binding binding;

    private UniqueId uniqueId;
    private BaseConfig baseConfig;
    private IssuePersister issuePersister;

    public final class Binding extends Binder {

        public RemoteFeedbackService getService() {
            return RemoteFeedbackService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        binding = new Binding();
        uniqueId = new UniqueId(this);
        baseConfig = new BaseConfig(this);
        issuePersister = new IssuePersister(this);
        Log.i(LOG_TAG, "created");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binding;
    }

    /**
     * <p/>
     * Create feedback item for given user feedback and list of attachments.
     * 
     * <p/>
     * Attachments will only be sent if the corresponding files exist.
     * Attachments marked as temporary will be removed after the operation
     * succeeds.
     * 
     * @param feedback
     *            user feedback
     * @param attachments
     *            attachments
     */
    /*
     * TODO use ServiceCallback (need to refactor CreateFeedbackTask) (Dariuz to
     * turn into issue)
     */
    public void createFeedback(String feedback, Iterable<FeedbackAttachment> attachments) {
        final String text = getString(R.string.jconnect_droid_sending_feedback, getPackageManager().getApplicationLabel(getApplicationInfo()));
        UiUtil.alert(this, text);
        new CreateFeedbackTask(this).execute(buildParams(feedback, attachments));
    }

    public void retrieveFeedbackItems(final ServiceCallback<IssuesWithComments> callback) {
        final ServiceCallback<GetFeedbackItemsTask.FeedbackItemsResult> wrappingCallback = new ServiceCallback<GetFeedbackItemsTask.FeedbackItemsResult>() {

            @Override
            public void onResult(Status status, GetFeedbackItemsTask.FeedbackItemsResult result) {
                if (status == Status.SUCCESS) {
                    issuePersister.setLastServerCheck(result.issues.lastUpdated());
                    if (result.issues.hasIssues()) {
                        issuePersister.updateUsingIssuesWithComments(result.issues);
                    }
                } else {
                    UiUtil.alert(RemoteFeedbackService.this, R.string.jconnect_droid_feedback_retrieving_failed);
                }
                if (callback != null && result != null) {
                    callback.onResult(status, result.issues);
                }
            }
        };
        new GetFeedbackItemsTask(wrappingCallback).execute(new GetFeedbackItemsParams(baseConfig, issuePersister.getLastServerCheck()));
    }

    /**
     * Post a new reply to an issue asynchronously to the remote server.
     * 
     * @param issue
     *            issue to reply to
     * @param reply
     *            reply contents
     * @param callback
     *            callback that will be called when reply is finished. May be
     *            <code>null</code>
     */
    public void reply(Issue issue, String reply, final ServiceCallback<Comment> callback) {
        checkNotNull(issue, "issue");
        checkNotNull(reply, "reply");
        final ServiceCallback<ReplyTask.Result> wrappingCallback = new ServiceCallback<ReplyTask.Result>() {
            @Override
            public void onResult(Status status, ReplyTask.Result result) {
                if (status == Status.SUCCESS) {
                    issuePersister.addCreatedComment(result.issueKey(), result.reply());
                    UiUtil.alert(RemoteFeedbackService.this, R.string.jconnect_droid_reply_sent);
                } else {
                    UiUtil.alert(RemoteFeedbackService.this, R.string.jconnect_droid_reply_sending_failed);
                }
                if (callback != null) {
                    callback.onResult(status, result.reply());
                }
            }
        };
        new ReplyTask(wrappingCallback).execute(new ReplyTaskParams(baseConfig, issue.getKey(), reply));
    }

    private static final int MAX_SUMMARY_LENGTH = 35;

    private CreateIssueParams buildParams(String description, Iterable<FeedbackAttachment> attachments) {
        String summary = description;
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            summary = summary.substring(0, MAX_SUMMARY_LENGTH) + "...";
        }
        CreateIssueParams.Builder builder = new CreateIssueParams.Builder(baseConfig.getServerUrl(), baseConfig.getProjectKey(), baseConfig.getApiKey()).summary(
                summary)
                .description(description)
                .uuid(uniqueId.getUuid())
                .udid(uniqueId.getUdid())
                .addAttachments(attachments)
                .appPackageName(getApplicationContext().getPackageName())
                .appVersion(getAppVersion())
                .isCrash(false);

        return builder.build();
    }

    // TODO use Callbacks (Dariuz to turn into issue)
    public void onFeedbackCreated(Issue issue) {
        UiUtil.alert(this, R.string.jconnect_droid_feedback_sent);
        issuePersister.addCreatedIssue(issue);
    }

    public void onFeedbackFailed() {
        UiUtil.alert(this, R.string.jconnect_droid_feedback_sending_failed);
    }

    public String getAppVersion() {
        PackageInfo info = getPackageInfo();
        if (info != null) {
            return info.versionName;
        } else {
            return "0.0";
        }
    }

    private PackageInfo getPackageInfo() {
        try {
            return getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}

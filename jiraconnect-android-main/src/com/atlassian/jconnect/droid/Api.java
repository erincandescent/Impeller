package com.atlassian.jconnect.droid;

import org.acra.ACRA;
import org.acra.ErrorReporter;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

import com.atlassian.jconnect.droid.acra.JiraReportSender;
import com.atlassian.jconnect.droid.activity.FeedbackActivity;
import com.atlassian.jconnect.droid.activity.FeedbackInboxActivity;
import com.atlassian.jconnect.droid.activity.ViewFeedbackActivity;
import com.atlassian.jconnect.droid.config.BaseConfig;
import com.atlassian.jconnect.droid.config.UniqueId;
import com.atlassian.jconnect.droid.jira.Issue;
import com.atlassian.jconnect.droid.persistence.IssuePersister;

/**
 * Public constants to communicate with the JiraConnect Android module.
 * 
 * @since v1.0
 */
public final class Api {

    private Api() {
        throw new AssertionError("Don't instantiate me");
    }

    public static final String ISSUE_EXTRA = "com.atlassian.jconnect.droid.issue";

    public static Intent createFeedbackIntent(Context context) {
        return new Intent(context, FeedbackActivity.class);
    }

    /**
     * This function must be called in the application onCreate call of every
     * single app that uses this JMC Api and it must be called BEFORE the
     * super.onCreate() function is.
     * 
     * @param application
     */
    public static void init(Application application) {
        ACRA.init(application);

        SharedPreferences prefs = ACRA.getACRASharedPreferences();
        Editor acraPrefEditor = prefs.edit();
        acraPrefEditor.putBoolean(ACRA.PREF_DISABLE_ACRA, false);
        acraPrefEditor.putBoolean(ACRA.PREF_ENABLE_ACRA, true);
        acraPrefEditor.putBoolean(ACRA.PREF_ALWAYS_ACCEPT, false);
        acraPrefEditor.commit();

        JiraReportSender jiraReportSender = new JiraReportSender(application.getApplicationContext());
        ErrorReporter instance = ErrorReporter.getInstance();
        instance.setReportSender(jiraReportSender);

        BaseConfig baseConfig = new BaseConfig(application);
        String serverUrl = baseConfig.getServerUrl();
        instance.putCustomData(JiraReportSender.CF_SERVER_URL, serverUrl);
        instance.putCustomData(JiraReportSender.CF_PROJECT_KEY, baseConfig.getProjectKey());
        instance.putCustomData(JiraReportSender.CF_API_KEY, baseConfig.getApiKey());
        UniqueId uniqueId = new UniqueId(application);
        instance.putCustomData(JiraReportSender.CF_UUID, uniqueId.getUuid());
        instance.putCustomData(JiraReportSender.CF_UDID, uniqueId.getUdid());

        // Upgrade from old version of the API, we can leave this in until we
        // are ready to deprecate it.
        new IssuePersister(application).recoverOldIssues();
    }

    public static Intent viewFeedbackInboxIntent(Context context) {
        return new Intent(context, FeedbackInboxActivity.class);
    }

    public static Intent viewFeedbackIntent(Context context, Issue issue) {
        final Intent intent = new Intent(context, ViewFeedbackActivity.class);
        intent.putExtra(ISSUE_EXTRA, issue);
        return intent;
    }

    public static Issue getIssue(Intent viewFeedbackIntent) {
        return viewFeedbackIntent.getParcelableExtra(ISSUE_EXTRA);
    }

    /**
     * This handles an exception by attempting to send it back to your JIRA
     * Instance. If successful, it will create a JIRA issue for the exception.
     * 
     * @param throwable
     *            The throwable to handle and attempt to use as the basis for
     *            the JIRA issue.
     */
    public static void handleException(Throwable throwable) {
        ErrorReporter.getInstance().handleException(throwable);
    }
}

package com.atlassian.jconnect.droid.service;

import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.atlassian.jconnect.droid.Api;
import com.atlassian.jconnect.droid.R;
import com.atlassian.jconnect.droid.jira.IssuesWithComments;

/**
 * Responsible for polling the JIRA server for feedback updates.
 * 
 * @since 1.0
 */
public class FeedbackPollingService extends Service {

    private static final String LOG_TAG = "FeedbackPollingService";
    private static final int POLL_INTERVAL_IN_SECONDS = 15 * 60; // poll every
                                                                 // 15mins

    private volatile RemoteFeedbackServiceBinder remoteFeedbackServiceBinder;

    private final Handler handler = new Handler();

    private final Runnable pollerTask = new Runnable() {
        @Override
        public void run() {
            Log.i(LOG_TAG, "Polling for updates from JIRA server");
            remoteFeedbackServiceBinder.getService().retrieveFeedbackItems(new PollCallback(FeedbackPollingService.this));
            handler.postDelayed(this, TimeUnit.SECONDS.toMillis(POLL_INTERVAL_IN_SECONDS));
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        if (remoteFeedbackServiceBinder == null) {
            remoteFeedbackServiceBinder = new RemoteFeedbackServiceBinder(this);
            remoteFeedbackServiceBinder.init();
            handler.postDelayed(pollerTask, TimeUnit.SECONDS.toMillis(60));
        }
        Log.i(LOG_TAG, "Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollerTask);
        remoteFeedbackServiceBinder.destroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // me not binding!
        return null;
    }

    private static final class PollCallback extends AbstractWrappingServiceCallback<FeedbackPollingService, IssuesWithComments> {
        public PollCallback(FeedbackPollingService owner) {
            super(owner);
        }

        @Override
        protected void onSuccess(FeedbackPollingService owner, IssuesWithComments result) {
            if (result.hasIssues()) {
                raiseNotification(owner);
            }
        }

        @Override
        protected void onFailure(FeedbackPollingService owner, IssuesWithComments result) {
            // Currently do nothing on failure
        }

        private void raiseNotification(FeedbackPollingService owner) {
            NotificationManager notificationManager = (NotificationManager) owner.getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(R.id.jconnect_droid_id_new_feedback_notification, createNewFeedbackNotification(owner));
        }

        private Notification createNewFeedbackNotification(FeedbackPollingService owner) {
            final Notification notification = new Notification(R.drawable.icon, "", System.currentTimeMillis()); // TODO
                                                                                                                 // real
                                                                                                                 // icon
            final Intent inbox = Api.viewFeedbackInboxIntent(owner);
            // TODO i18n, App name in title, 'New feedback available' in
            // description
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notification.setLatestEventInfo(owner, "New Feedback Available", "", PendingIntent.getActivity(owner, 0, inbox, 0));
            return notification;
        }
    }
}

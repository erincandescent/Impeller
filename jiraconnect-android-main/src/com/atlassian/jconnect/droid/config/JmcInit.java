package com.atlassian.jconnect.droid.config;

import android.content.Context;
import android.content.Intent;
import com.atlassian.jconnect.droid.service.FeedbackPollingService;
import com.atlassian.jconnect.droid.service.RemoteFeedbackService;

/**
 * Initializes the JMC Android within given application
 * 
 * @since 1.0
 */
public final class JmcInit {
    private JmcInit() {
        throw new AssertionError("Don't instantiate me");
    }

    /**
     * Initialize JMC within an application, using any context from the app.
     * 
     * @param context
     *            any context running within the application
     */
    public static void start(Context context) {
        // start services
        context.startService(new Intent(context, RemoteFeedbackService.class));
        context.startService(new Intent(context, FeedbackPollingService.class));
    }
}

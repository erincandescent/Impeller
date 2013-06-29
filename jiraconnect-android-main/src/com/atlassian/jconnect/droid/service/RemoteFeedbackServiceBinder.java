package com.atlassian.jconnect.droid.service;

import static com.google.common.base.Preconditions.checkState;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

/**
 * Usual Android boilerplate. They're really gooood at making people write
 * boilerplate!
 * 
 * @since 1.0
 */
public class RemoteFeedbackServiceBinder {

    private final Context context;

    private volatile RemoteFeedbackService service;

    public RemoteFeedbackServiceBinder(Context context) {
        this.context = context;
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RemoteFeedbackServiceBinder.this.service = ((RemoteFeedbackService.Binding) service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            RemoteFeedbackServiceBinder.this.service = null;
        }
    };

    public RemoteFeedbackService getService() {
        checkState(service != null, "Service not bound");
        return service;
    }

    public void init() {
        context.startService(new Intent(context, RemoteFeedbackService.class));
        context.bindService(new Intent(context, RemoteFeedbackService.class), connection, Context.BIND_AUTO_CREATE);
    }

    public void destroy() {
        context.unbindService(connection);
    }

}

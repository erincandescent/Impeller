package eu.e43.impeller.content;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by OShepherd on 27/06/13.
 */
public class FeedSyncService extends Service {
    static final String TAG = "FeedSyncService";
    private static final Object ms_adapterLock = new Object();
    private static FeedSyncAdapter ms_adapter = null;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        synchronized(ms_adapterLock) {
            if(ms_adapter == null)
                ms_adapter = new FeedSyncAdapter(getApplicationContext());
        }
    }

    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        synchronized(ms_adapterLock) {
            return ms_adapter.getSyncAdapterBinder();
        }
    }
}

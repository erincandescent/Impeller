package eu.e43.impeller.contacts;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by OShepherd on 27/06/13.
 */
public class SyncService extends Service {
    static final String TAG = "contacts.SyncService";
    private static final Object ms_adapterLock = new Object();
    private static SyncAdapter ms_adapter = null;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate()");
        synchronized(ms_adapterLock) {
            if(ms_adapter == null)
                ms_adapter = new SyncAdapter(getApplicationContext());
        }
    }

    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind()");
        synchronized(ms_adapterLock) {
            return ms_adapter.getSyncAdapterBinder();
        }
    }
}

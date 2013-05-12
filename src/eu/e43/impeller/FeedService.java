/* Copyright 2013 Owen Shepherd. A part of Impeller.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.e43.impeller;
import java.util.HashMap;
import java.util.Map;

import android.accounts.Account;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

public class FeedService extends Service {
	private static final String CLEAR_UNREAD_INTENT = "eu.e43.impeller.FeedService.ClearUnread";
	private static final String TAG = "FeedService";
	
	private HandlerThread 			m_hThread;
	private Handler       			m_h;
	private Handler					m_mH;

	@SuppressWarnings("deprecation") // getNotification->build @ level 16
	@Override
	public void onCreate() {
		m_hThread = new HandlerThread("FeedService");
		m_hThread.start();
		m_h = new Handler(m_hThread.getLooper());
		Log.i(TAG, "Starting");

		/*
		m_notify = new Notification.Builder(this);
		m_notify.setSmallIcon(R.drawable.ic_launcher);
		m_notify.setContentTitle("New updates");
		m_notify.setContentText("(Missing caption)");
		m_notify.setAutoCancel(true);
		Intent clearIntent = new Intent(CLEAR_UNREAD_INTENT, null, this, FeedService.class);
		m_notify.setDeleteIntent(PendingIntent.getService(this, 0, clearIntent, PendingIntent.FLAG_UPDATE_CURRENT));

		Intent feedIntent = new Intent(Intent.ACTION_MAIN, null, this, FeedActivity.class);
		feedIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		m_notify.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, feedIntent, PendingIntent.FLAG_UPDATE_CURRENT));
		*/
	}
	
	@Override
	public void onDestroy() {
		Log.i(TAG, "Shutting down");
		m_hThread.quit();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "Bind " + intent);
		Account a = (Account) intent.getExtras().getParcelable("account");
		return new Feed(this, m_h, a, intent.getData());
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "onStartCommand " + intent);
		/*if(CLEAR_UNREAD_INTENT.equals(intent.getAction())) {
			synchronized(this) { m_unreadCount = 0; }

			return START_NOT_STICKY;
		} else {*/
			return START_REDELIVER_INTENT;
		//}
	}
}

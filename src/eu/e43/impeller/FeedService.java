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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.e43.impeller.account.Authenticator;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

public class FeedService extends Service implements OnAccountsUpdateListener {
	private static final String CLEAR_UNREAD_INTENT = "eu.e43.impeller.FeedService.ClearUnread";
	private static final String TAG = "FeedService";
	
	private AccountManager			m_accountManager;
	private HandlerThread 			m_hThread;
	private Handler       			m_h;
	private Handler					m_mH;
	private Map<Uri, Feed>			m_feeds;
	private Map<Account, AccountFeedConnection>
									m_accountFeeds;
	private boolean					m_started = false;

	@Override
	public void onCreate() {
		m_accountManager = AccountManager.get(this);
		m_accountFeeds = new HashMap<Account, AccountFeedConnection>();
		m_hThread = new HandlerThread("FeedService");
		m_hThread.start();
		m_h = new Handler(m_hThread.getLooper());
		m_feeds = new HashMap<Uri, Feed>();
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
		m_accountManager.removeOnAccountsUpdatedListener(this);
		m_hThread.quit();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		Log.i(TAG, "Bind " + intent);
		Feed f = m_feeds.get(intent.getData());
		if(f != null)
			return f;
		
		Account a = (Account) intent.getExtras().getParcelable("account");
		f = new Feed(this, m_h, a, intent.getData());
		m_feeds.put(intent.getData(), f);
		return f;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		Log.i(TAG, "Unbind " + intent);
		m_feeds.get(intent.getData()).onUnbind();
		m_feeds.remove(intent.getData());		
        return false;
    }
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.i(TAG, "onStartCommand " + intent);
		
		if(!m_started) {
			m_started = true;
			m_accountManager.addOnAccountsUpdatedListener(this, null, true);
		}
		
		return START_REDELIVER_INTENT;
	}

	/** Does nothing. Exists to keep the Feed alive */
	private class AccountFeedConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder bind) {}
		@Override
		public void onServiceDisconnected(ComponentName name) {}
	}
	
	@Override
	public void onAccountsUpdated(Account[] accts) {
		List<Account> accounts = Arrays.asList(accts);
		
		for(Map.Entry<Account, AccountFeedConnection> e : m_accountFeeds.entrySet()) {
			if(!accounts.contains(e.getKey())) {
				Log.i(TAG, "Unbinding feed for account change: " + e.getKey().name);
				unbindService(e.getValue());
			}
		}


		for(Account a : accounts) {
			if(a.type.equals(Authenticator.ACCOUNT_TYPE) && !m_accountFeeds.containsKey(a)) {
				Log.i(TAG, "Binding feed for new account: " + a.name);
				Intent i = new Intent(Intent.ACTION_VIEW, Feed.getMainFeedUri(this, a), this, FeedService.class);
				i.putExtra("account", a);
				AccountFeedConnection conn = new AccountFeedConnection();
				bindService(i, conn, BIND_AUTO_CREATE);
				m_accountFeeds.put(a,  conn);
			}
		}
		
	}
}

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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.exception.OAuthException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.widget.Toast;

public class FeedService extends Service {
	public interface Listener {
		public void updateStarted(FeedService feed);
		public void feedUpdated(FeedService feed, int items);
	}
	
	private HandlerThread 			m_hThread;
	private Handler       			m_h;
	private Handler					m_mH;
	private IBinder		  			m_binder = new LocalBinder();
	private Uri			  			m_feedUri;
	private int						m_pollInterval;	///< Poll interval in seconds
	private int						m_unreadCount;
	private List<JSONObject> 		m_items;		///< List of items
	private Notification.Builder	m_notify;
	private NotificationManager		m_notificationManager;
	private OAuthConsumer 			m_oauth;
	private SharedPreferences		m_prefs;
	private JSONObject  			m_whoami;
	private List<Listener>			m_listeners;
	
	private Runnable			m_pollFeedR = new Runnable() {
		@Override
		public void run() {
			try {
				pollFeed();
			} catch(Exception ex) {
				onError(ex);
			}
		}
	};
	
	public class LocalBinder extends Binder {
		FeedService getService() {
			return FeedService.this;        
		}    
	}
	
	public int getItemCount()
	{ 
		synchronized(this) {
			return m_items.size(); 
		}
	}
	
	public JSONObject getItem(int idx)
	{
		synchronized(this) {
			return m_items.get(idx);
		}
	}

	@SuppressWarnings("deprecation") // getNotification->build @ level 16
	@Override
	public void onCreate() {
		m_hThread = new HandlerThread("FeedService");
		m_hThread.start();
		m_h = new Handler(m_hThread.getLooper());
		m_mH = new Handler();
		m_notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		m_listeners = new ArrayList<Listener>();
		m_unreadCount = 0;
		
		m_notify = new Notification.Builder(this);
		m_notify.setSmallIcon(R.drawable.ic_launcher);
		m_notify.setContentTitle("New updates");
		m_notify.setContentText("Yay?");
		m_notify.setNumber(0);
		m_notify.setAutoCancel(true);
		Intent feedIntent = new Intent(Intent.ACTION_MAIN, null, this, FeedActivity.class);
		feedIntent.addCategory(Intent.CATEGORY_LAUNCHER);
		m_notify.setContentIntent(PendingIntent.getActivity(getApplicationContext(), 0, feedIntent, PendingIntent.FLAG_UPDATE_CURRENT));
		m_notificationManager.notify("Feed", 0, m_notify.getNotification());
		m_prefs = PreferenceManager.getDefaultSharedPreferences(this);
		m_items = new ArrayList<JSONObject>();
		
		m_pollInterval = Integer.parseInt(m_prefs.getString("sync_frequency", "15")) * 60;
		
		System.out.println(m_prefs.getAll());
		
		bindService(new Intent(this, OAuthService.class), new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder bind) {						
				OAuthService svc = ((OAuthService.LocalBinder)bind).getService();
				if(!svc.isAuthorized()) {
					stopSelf();
				} else {
					m_oauth = svc.getAuthenticatedConsumer();
					try {
						m_whoami = svc.whoAmI();
						
						m_feedUri = Uri.parse(m_whoami
								.getJSONObject("links")
								.getJSONObject("activity-inbox")
								.getString("href"));
					} catch (JSONException e) {
						onError(e);
						stopSelf();
					}
					m_h.post(m_pollFeedR);
				}
			}

			@Override
			public void onServiceDisconnected(ComponentName arg0) {
				m_oauth = null;
			}
		}, BIND_AUTO_CREATE);
	}
	
	@Override
	public void onDestroy() {
		m_hThread.quit();
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return m_binder;
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_REDELIVER_INTENT;
	}
	
	public void addListener(Listener l) {
		synchronized(this) {
			m_listeners.add(l);
		}
	}
	
	public void removeListener(Listener l) {
		synchronized(this) {
			m_listeners.remove(l);
		}
	}
	
	public void pollNow()
	{
		m_h.removeCallbacks(m_pollFeedR);
		if(m_oauth != null)
			m_h.post(m_pollFeedR);
	}
	
	@SuppressWarnings("deprecation")
	private void pollFeed() throws IOException, JSONException
	{
		m_mH.post(new Runnable() {
			public void run() {
				synchronized(this) {
					for(Listener l : m_listeners) {
						l.updateStarted(FeedService.this);
					}
				}
			}
		});
		
		Uri uri = m_feedUri;
		Uri.Builder b = uri.buildUpon();
		synchronized(this) {
			if(m_items.size() != 0) {
				b.appendQueryParameter("since", m_items.get(0).getString("id"));
			}
		}
		b.appendQueryParameter("count", "200");
		uri = b.build();
		URL url = new URL(uri.toString());
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			m_oauth.sign(conn);
		} catch (OAuthException e) {
			onError(e);
		}
		conn.connect();
		
		JSONObject coll = new JSONObject(Utils.readAll(conn.getInputStream()));
		JSONArray items = coll.getJSONArray("items");
		synchronized(this) {
			m_unreadCount += items.length();
			for(int i = items.length() - 1; i >= 0; i--) {
				JSONObject activity = items.getJSONObject(i);
				m_items.add(0, activity);
			}
			
			if(m_unreadCount > 0) {
				m_notify.setNumber(m_unreadCount);
				m_notificationManager.notify("Feed", 0, m_notify.getNotification());
			}
		}
		
		m_mH.post(new Runnable() {
			public void run() {
				synchronized(this) {
					for(Listener l : m_listeners) {
						l.feedUpdated(FeedService.this, m_unreadCount);
					}
				}
			}
		});
		
		if(m_pollInterval > 0) m_h.postDelayed(m_pollFeedR, m_pollInterval * 1000);
	}
	
	private void onError(Exception ex) {
		Toast.makeText(getBaseContext(), "Error retriving feed: " + ex.getMessage(), Toast.LENGTH_LONG).show();
	}
}

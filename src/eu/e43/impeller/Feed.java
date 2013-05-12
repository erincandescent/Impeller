package eu.e43.impeller;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import oauth.signpost.OAuthConsumer;

import org.json.JSONArray;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import eu.e43.impeller.ObjectService.ObjectCache;
import eu.e43.impeller.account.OAuth;

public class Feed extends Binder {
	static final String TAG = "Feed";
	static final String[] OBJECT_NAMES = new String[] {
		"actor",
		"generator",
		"object",
		"provider",
		"target"
	};
	
	private FeedService				m_svc;
	private Handler					m_mH;
	private Handler					m_h;
	private Uri			  			m_feedUri;
	private int						m_pollInterval;	///< Poll interval in seconds
	private int						m_unreadCount;
	private List<JSONObject> 		m_items;		///< List of items
	private OAuthConsumer 			m_oauth;
	private List<Listener>			m_listeners;
	private ObjectCache				m_cache;
	private ServiceConnection		m_cacheConn;
	
	public interface Listener {
		public void updateStarted(Feed feed);
		public void feedUpdated(Feed feed, int items);
	}
	
	private Runnable m_pollFeedR = new Runnable() {
		@Override public void run() { pollFeed(); }
	};
	
	public static Uri getMainFeedUri(Context ctx, Account user) {
		AccountManager am = AccountManager.get(ctx);
		String host     = am.getUserData(user, "host");
		String username = am.getUserData(user, "username");
		
		Uri.Builder b = new Uri.Builder();
		b.scheme("https");
		b.authority(host);
		b.appendPath("api");
		b.appendPath("user");
		b.appendPath(username);
		b.appendPath("inbox");
		b.appendPath("major");
		
		return b.build();
	}
	
	public Feed(FeedService svc, Handler h, Account acct, Uri uri) {
		Log.i(TAG, "Bound feed " + uri);
		m_svc = svc;
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(svc);
		m_feedUri      = uri;
		m_pollInterval = Integer.parseInt(prefs.getString("sync_frequency", "15")) * 60; 
		m_unreadCount  = 0;
		m_h	   		   = h;
		m_mH		   = new Handler();
		
		m_listeners = new ArrayList<Listener>();
		m_items = new ArrayList<JSONObject>();
		
		m_oauth = OAuth.getConsumerForAccount(m_svc, acct);
		
		Intent cacheIntent = new Intent(m_svc, ObjectService.class);
		cacheIntent.putExtra("account", acct);
	
		m_cacheConn = new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder svc) {
				m_cache = (ObjectCache) svc;
				m_h.post(m_pollFeedR);
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				m_h.removeCallbacks(m_pollFeedR);
				m_cache = null;
			}
			
		};
		
		m_svc.bindService(cacheIntent, m_cacheConn, Context.BIND_AUTO_CREATE);
	}
	
	protected void onUnbind() {
		m_svc.unbindService(m_cacheConn);
		m_h.removeCallbacks(m_pollFeedR);
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
	
	public void pollNow() {
		m_h.removeCallbacks(m_pollFeedR);
		m_h.post(m_pollFeedR);
	}
	
	private void fireUpdateStarted()
	{
		m_mH.post(new Runnable() {
			public void run() {
				synchronized(this) {
					for(Listener l : m_listeners) {
						l.updateStarted(Feed.this);
					}
				}
			}
		});		
	}
	
	private void fireUpdated()
	{
		m_mH.post(new Runnable() {
			public void run() {
				synchronized(this) {
					for(Listener l : m_listeners) {
						l.feedUpdated(Feed.this, m_unreadCount);
					}
				}
			}
		});
	}
	
	private void pollFeed()
	{
		Log.v(TAG, "Fetching feed " + m_feedUri);
		fireUpdateStarted();
		
		Uri uri = m_feedUri;
		Uri.Builder b = uri.buildUpon();
		synchronized(this) {
			if(m_items.size() != 0) {
				b.appendQueryParameter("since", m_items.get(0).optString("id"));
			}
		}
		b.appendQueryParameter("count", "50");
		uri = b.build();
		
		HttpURLConnection conn;
		try { 
			URL url = new URL(uri.toString());
			conn = OAuth.fetchAuthenticated(m_oauth, url);
		
			JSONObject coll = new JSONObject(Utils.readAll(conn.getInputStream()));
			JSONArray items = coll.getJSONArray("items");
		
			synchronized(this) {
				m_unreadCount += items.length();
				for(int i = items.length() - 1; i >= 0; i--) {
					JSONObject activity = items.getJSONObject(i);
					
					for(String name : OBJECT_NAMES) {
						JSONObject obj = activity.optJSONObject(name);
						if(obj != null) {
							m_cache.insertObject(obj);
						}
					}
					
					
					m_items.add(0, activity);
				}
			}
			fireUpdated();
		} catch(Exception ex) {
			Log.w(TAG, "Error fetching feed", ex);
		}
		
		enqueuePoll();
	}
	
	private void enqueuePoll()
	{
		if(m_pollInterval > 0) m_h.postDelayed(m_pollFeedR, m_pollInterval * 1000);
	}
}

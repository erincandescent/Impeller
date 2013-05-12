package eu.e43.impeller;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import oauth.signpost.OAuthConsumer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import eu.e43.impeller.account.OAuth;

public class Feed extends Binder {
	static final String TAG = "Feed";
	private FeedService				m_svc;
	private Handler					m_mH;
	private Handler					m_h;
	private Uri			  			m_feedUri;
	private int						m_pollInterval;	///< Poll interval in seconds
	private int						m_unreadCount;
	private List<JSONObject> 		m_items;		///< List of items
	private OAuthConsumer 			m_oauth;
	private List<Listener>			m_listeners;
	
	public interface Listener {
		public void updateStarted(Feed feed);
		public void feedUpdated(Feed feed, int items);
	}
	
	private Runnable m_pollFeedR = new Runnable() {
		@Override public void run() { pollFeed(); }
	};
	
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
		
		m_h.post(m_pollFeedR);
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
		b.appendQueryParameter("count", "200");
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

package eu.e43.impeller;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.GregorianCalendar;

import oauth.signpost.OAuthConsumer;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.jakewharton.disklrucache.DiskLruCache;
import com.jakewharton.disklrucache.DiskLruCache.Editor;
import com.jakewharton.disklrucache.DiskLruCache.Snapshot;

import eu.e43.impeller.account.OAuth;

public class ObjectService extends Service {
	private static final String TAG = "ObjectService";
	
	private static final int MAX_CACHE_SIZE = 1 * 1024 * 1024; // 1mb
	private DiskLruCache m_cache;
	
	class ObjectCache extends Binder {
		private Account m_acct;
		
		private ObjectCache(Intent i) {
			Account a = i.getParcelableExtra("account");
			if(a == null)
				throw new IllegalArgumentException("Need an account");
			
			Log.i(TAG, "Bound for account " + a.name);
			m_acct = a;
		}
		
		public JSONObject tryGetObject(String uri) throws IOException, JSONException {
			Log.i(TAG, "tryGetObject(" + uri + ")");
			String hash = Utils.sha1Hex(uri);
			return tryGetForHash(hash);
		}
		
		private JSONObject tryGetForHash(String hash) throws IOException, JSONException {
			Snapshot s = m_cache.get(hash);
			if(s != null)
				return new JSONObject(s.getString(0));
			else return null;
		}
		
		public JSONObject getObject(String uri, String proxyUrl) throws Exception {
			Log.i(TAG, "getObject(" + uri + ")");
			String hash = Utils.sha1Hex(uri);
			GregorianCalendar lastModified = null;
			
			Snapshot s = m_cache.get(hash);
			if(s != null) {
				lastModified              = new GregorianCalendar();
				GregorianCalendar expires = new GregorianCalendar();
				lastModified.setTimeInMillis(Long.valueOf(s.getString(1)));
				expires.setTimeInMillis(Long.valueOf(s.getString(2)));
				
				if(expires.after(new GregorianCalendar())) {
					Log.v(TAG, "In date");
					// Expires in future
					return new JSONObject(s.getString(0));
				} else {
					Log.v(TAG, "Expired; fetching");
					JSONObject obj = new JSONObject(s.getString(0));
					proxyUrl = Utils.getProxyUrl(obj);
				}
			} else {
				Log.v(TAG, "Unknown object; fetch");
			}
			
			if(proxyUrl == null) proxyUrl = uri;
			
			Long modificationTime = null;
			if(lastModified != null)
				modificationTime = lastModified.getTimeInMillis();
			
			HttpURLConnection conn = OAuth.fetchAuthenticated(ObjectService.this, m_acct, new URL(proxyUrl), modificationTime, true);
			Editor e = m_cache.edit(hash);
			String json;
			if(conn.getResponseCode() == 200) {
				json = Utils.readAll(conn.getInputStream());
				e.set(0,  json);
			} else { // cache was current
				json = s.getString(0);
			}
			e.set(1, String.valueOf(conn.getLastModified()));
			e.set(2, String.valueOf(conn.getExpiration()));
			e.commit();
			
			return new JSONObject(json);
		}
		
		public void insertObject(JSONObject obj) {
			insertObject(obj, null, null);
		}
		
		public void insertObject(JSONObject obj, GregorianCalendar lastModified, GregorianCalendar expires) {
			if(lastModified == null) {
				lastModified = new GregorianCalendar();
			}
			
			if(expires == null) {
				expires = new GregorianCalendar();
				expires.add(GregorianCalendar.MINUTE, 1);
			}
			
			String uri = obj.optString("id", null);
			if(uri != null) {
				Log.i(TAG, "insertObject(" + uri + ")");
				String hash = Utils.sha1Hex(uri);
				Editor ed = null;
				try {
					ed = m_cache.edit(hash);
					ed.set(0, obj.toString());
					ed.set(1, String.valueOf(lastModified.getTimeInMillis()));
					ed.set(2, String.valueOf(expires.getTimeInMillis()));
					ed.commit();
				} catch(IOException e) {
					if(ed != null)
						ed.abortUnlessCommitted();
				}
			}
		}
		
		public void invalidateObject(String uri) {
			try {
				m_cache.remove(Utils.sha1Hex(uri));
			} catch (IOException e) {
				Log.w(TAG, "Error invalidating", e);
			}
		}
	}
	
	@Override
	public void onCreate() {
		try {
			m_cache = DiskLruCache.open(getDir("objectCache", MODE_PRIVATE), 2, 3, MAX_CACHE_SIZE);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return new ObjectCache(intent);
	}
}

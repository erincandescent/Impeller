package eu.e43.impeller;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

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
	
	private static final int MAX_CACHE_SIZE = 10 * 1024 * 1024; // 10mb
	private DiskLruCache m_cache;
	
	class ObjectCache extends Binder {
		private OAuthConsumer m_oauth;
		
		private ObjectCache(Intent i) {
			Account a = i.getParcelableExtra("account");
			if(a == null)
				throw new IllegalArgumentException("Need an account");
			
			Log.i(TAG, "Bound for account " + a.name);
			m_oauth = OAuth.getConsumerForAccount(ObjectService.this, a);
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
		
		public JSONObject getObject(String uri) throws Exception {
			Log.i(TAG, "getObject(" + uri + ")");
			String hash = Utils.sha1Hex(uri);
			JSONObject o = tryGetForHash(hash);
			if(o != null)
				return o;
			
			Log.v(TAG, "fetch object " + uri);
			HttpURLConnection conn = OAuth.fetchAuthenticated(m_oauth, new URL(uri));
			String json = Utils.readAll(conn.getInputStream());
			
			Editor e = m_cache.edit(hash);
			e.set(0,  json);
			e.commit();
			
			return new JSONObject(json);
		}
		
		public void insertObject(JSONObject obj) {
			String uri = obj.optString("id", null);
			if(uri != null) {
				Log.i(TAG, "insertObject(" + uri + ")");
				String hash = Utils.sha1Hex(uri);
				Editor ed = null;
				try {
					ed = m_cache.edit(hash);
					ed.set(0, obj.toString());
					ed.commit();
				} catch(IOException e) {
					if(ed != null)
						ed.abortUnlessCommitted();
				}
			}
		}
	}
	
	@Override
	public void onCreate() {
		try {
			m_cache = DiskLruCache.open(getDir("objectCache", MODE_PRIVATE), 1, 1, MAX_CACHE_SIZE);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return new ObjectCache(intent);
	}
}

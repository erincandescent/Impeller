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
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class OAuthService extends Service {
	class LocalBinder extends Binder {
		OAuthService getService() {
			return OAuthService.this;
		}
	}
	
	private final LocalBinder m_binder = new LocalBinder();
	private SharedPreferences m_prefs;
	
	@Override
	public void onCreate() {
		m_prefs   = PreferenceManager.getDefaultSharedPreferences(this);
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return m_binder;
	}
	
	public boolean isAuthorized() {
		return m_prefs.contains("token_secret");
	}
	
	private static OAuthConsumer registerConsumer(String host) throws Exception {
			URL endpoint = new URL("https", host, "api/client/register");
			HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
					
			HashMap<String, String> params = new HashMap<String, String>();
			params.put("type", "client_associate");
			params.put("application_type", "native");
			//params.put("redirect_uris", "impeller:authorized");
			params.put("client_name", "Impeller");
			params.put("application_name", "Impeller");
			String requestBody = Utils.encode(params);
			
			conn.setDoOutput(true);
			conn.setDoInput(true);
			
			System.err.println("Connecting to " + endpoint.toString());
			System.err.println("Body: " + requestBody);
			
			Writer w = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
			w.write(requestBody);
			w.close();
			
			if(conn.getResponseCode() / 100 != 2) {
				String msg = Utils.readAll(new InputStreamReader(conn.getErrorStream()));
				System.err.println("Err body: " + msg);
				throw new IOException("Server returned error response " + conn.getResponseMessage());
			}
			
			String response = Utils.readAll(new InputStreamReader(conn.getInputStream(), "UTF-8"));
			System.err.println("Server response: " + response);
			JSONObject json = new JSONObject(response);
			
			return new DefaultOAuthConsumer(
					json.getString("client_id"),
					json.getString("client_secret"));
	}
	
	public OAuthConsumer getUnauthenticatedConsumer(String host) throws Exception {
		if(m_prefs.getString("host", null) == host) {
			return new DefaultOAuthConsumer(
					m_prefs.getString("client_id", null),
					m_prefs.getString("client_secret", null));	
		} else {
			OAuthConsumer c = registerConsumer(host);
			Editor e = m_prefs.edit();
			e.putString("host", host);
			e.putString("client_id", c.getConsumerKey());
			e.putString("client_secret", c.getConsumerSecret());
			e.remove("token");
			e.remove("token_secret");
			e.remove("username");
			e.remove("id");
			e.remove("whoami");
			e.commit();
			return c;
		}
	}
	
	public OAuthConsumer getAuthenticatedConsumer() {
		OAuthConsumer c = new DefaultOAuthConsumer(
			m_prefs.getString("client_id", null),
			m_prefs.getString("client_secret", null));
		
		c.setTokenWithSecret(
			m_prefs.getString("token", null), 
			m_prefs.getString("token_secret", null));
		
		return c;
	}
	
	public void setUser(OAuthConsumer c) throws Exception
	{
		Editor e = m_prefs.edit();
		e.putString("token", c.getToken());
		e.putString("token_secret", c.getTokenSecret());
		
		URL endpoint = new URL("https", getHost(), "api/whoami");
		String json = null;
		for(int i = 0; i < 5; i++) {
			HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
			conn.setInstanceFollowRedirects(false);
			c.sign(conn);
			conn.connect();
		
			if(conn.getResponseCode() == 200) {
				json = Utils.readAll(new InputStreamReader(conn.getInputStream(), "UTF-8"));
				break;
			} else if(conn.getResponseCode() / 100 == 3) {
				// Redirect
				endpoint = new URL(conn.getHeaderField("Location"));
				System.err.println("Redirect to" + endpoint);
			} else {
				String err = Utils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
				throw new Exception(err);
			}
		}
		if(json == null)
			throw new Exception("Redirect loop");
		
		JSONObject info = new JSONObject(json);
		
		e.putString("whoami", json);
		e.putString("username", info.getString("preferredUsername"));
		e.putString("id", info.getString("id"));
		
		e.commit();
	}
	
	public void signOut()
	{
		Editor e = m_prefs.edit();
		e.remove("token");
		e.remove("token_secret");
		e.remove("whoami");
		e.remove("username");
		e.remove("id");
		e.commit();
	}
	
	public String getUsername() {
		return m_prefs.getString("username", null);
	}
	
	public String getHost() {
		return m_prefs.getString("host", null);
	}
	
	public String getId() {
		return m_prefs.getString("id", null);
	}
	
	public JSONObject whoAmI() throws JSONException {
		return new JSONObject(m_prefs.getString("whoami", null));
	}
}

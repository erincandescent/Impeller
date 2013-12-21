package eu.e43.impeller.account;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import eu.e43.impeller.ImpellerApplication;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;

import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import eu.e43.impeller.Utils;

public class OAuth {
	static final String TAG = "OAuth";
	
	public static OAuthConsumer getConsumerForHost(Context ctx, String host) throws Exception {
		SharedPreferences prefs = ctx.getSharedPreferences("eu.e43.impeller.OAuthTokens", Context.MODE_PRIVATE);
		
		String clientId = prefs.getString(host, null);
		if(clientId != null) {
			String clientSecret = prefs.getString(host + ":secret", null);
			return new DefaultOAuthConsumer(clientId, clientSecret);
		}
		
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
		
		Log.v(TAG, "Registering client for host " + host);	
		Writer w = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
		w.write(requestBody);
		w.close();
		
		if(conn.getResponseCode() != 200) {
			String msg = Utils.readAll(new InputStreamReader(conn.getErrorStream()));
			Log.e(TAG, "Server returned an error response: " + msg);
			throw new IOException("Server returned error response " + conn.getResponseMessage());
		}
		
		String response = Utils.readAll(new InputStreamReader(conn.getInputStream(), "UTF-8"));
		JSONObject json = new JSONObject(response);
		
		Editor e = prefs.edit();
		e.putString(host,             json.getString("client_id"));
		e.putString(host + ":secret", json.getString("client_secret"));
		e.commit();
		
		return new DefaultOAuthConsumer(
				json.getString("client_id"),
				json.getString("client_secret"));
	}
	
	public static OAuthConsumer getConsumerForAccount(Context ctx, Account acct) {
		Log.i(TAG, "Get consumer for account " + acct.name);
		AccountManager mgr = AccountManager.get(ctx);

		String clientId     = mgr.getUserData(acct, "clientId");
		String clientSecret = mgr.getUserData(acct, "clientSecret");
		String token        = mgr.getUserData(acct, "token");
		String tokenSecret  = mgr.getUserData(acct, "tokenSecret");
			
		DefaultOAuthConsumer c = new DefaultOAuthConsumer(clientId, clientSecret);
		c.setTokenWithSecret(token,  tokenSecret);
		return c;
		
	}
	
	public static HttpURLConnection fetchAuthenticated(Context ctx, Account acct, URL url) throws Exception {
		return fetchAuthenticated(ctx, acct, url, true);
	}
	
	public static HttpURLConnection fetchAuthenticated(Context ctx, Account acct, URL url, boolean throwOnError) throws Exception {
		Log.i(TAG, "Authenticated fetch of " + url);
		for(int i = 0; i < 5; i++) {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "E43 Impeller/" + ImpellerApplication.ms_versionCode);
			conn.setInstanceFollowRedirects(false);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
			
			if(url.getHost().equals(((AccountManager)ctx.getSystemService(Context.ACCOUNT_SERVICE)).getUserData(acct, "host"))) {
				OAuthConsumer cons = getConsumerForAccount(ctx, acct);
				cons.sign(conn);
			}
			
			conn.connect();
		
			switch(conn.getResponseCode()) {
			case 200: // Success
			case 304: // Not modified
				Log.v(TAG, "Fetch complete (" + conn.getResponseCode() + ")");
				return conn;
				
			case 301: // Moved permanently
			case 302: // Found
			case 303: // See other
			case 307: // Moved temporarily
				// Redirect
				url = new URL(conn.getHeaderField("Location"));
				Log.v(TAG, "Following redirect to" + url);
				break;
				
			default:
				if(throwOnError) {
					String err = Utils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
					throw new Exception(err);
				} else {
					return conn;
				}
			}
		}
		
		throw new Exception("Redirection limit exceeded");
	}
}

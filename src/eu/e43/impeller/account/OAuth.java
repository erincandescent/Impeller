package eu.e43.impeller.account;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.basic.DefaultOAuthConsumer;

import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
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
	
	public static HttpURLConnection fetchAuthenticated(OAuthConsumer c, URL url) throws Exception {
		Log.i(TAG, "Authenticated fetch of " + url);
		for(int i = 0; i < 5; i++) {
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setInstanceFollowRedirects(false);
			c.sign(conn);
			conn.connect();
		
			if(conn.getResponseCode() == 200) {
				return conn;
			} else if(conn.getResponseCode() / 100 == 3) {
				// Redirect
				url = new URL(conn.getHeaderField("Location"));
				Log.v(TAG, "Following redirect to" + url);
			} else {
				String err = Utils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
				throw new Exception(err);
			}
		}
		
		throw new Exception("Redirection limit exceeded");
	}
}

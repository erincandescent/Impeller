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

package eu.e43.impeller.account;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.exception.OAuthException;

import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;

public class LoginActivity extends AccountAuthenticatorActivity implements OnClickListener {
	private static final String TAG = "LoginActivity";
	
	// TODO: Look these up via WebFinger
	private static final String REQUEST_TOKEN_URL = "https://%s/oauth/request_token";
	private static final String AUTHORIZE_URL     = "https://%s/oauth/authorize";
	private static final String ACCESS_TOKEN_URL = "https://%s/oauth/access_token";
	
	private AccountManager 		m_accountManager;
	private boolean				m_loginInProgress = false;
	private RequestTokenTask	m_requestTokenTask;
	private AuthTokenTask       m_authTokenTask;

	private String				m_host;
	private OAuthConsumer		m_consumer;
	private OAuthProvider 		m_provider;
	
	private TextView	   		m_idView;
	private Button	   	   		m_loginButton;
	private View 				m_loginFormView;
	private View 				m_loginStatusView;
	private WebView  			m_webView;
	
	@Override
	public void onCreate(Bundle icicle) {
		Log.v(TAG, "onCreate(" + icicle + ")");
		super.onCreate(icicle);
		setContentView(R.layout.activity_login);
		
		m_accountManager = AccountManager.get(this);
		final Intent intent = getIntent();
		
		String id = intent.getStringExtra("id"); 
		
		m_idView 		= (TextView) findViewById(R.id.id);	
		m_loginButton	= (Button)   findViewById(R.id.sign_in_button);
		m_loginFormView = 			 findViewById(R.id.login_form);
		m_loginStatusView = 	     findViewById(R.id.login_status);
		m_webView = (WebView)        findViewById(R.id.web_view);
		m_idView.setText(id);		
		
		m_loginButton.setOnClickListener(this);
		m_webView.setWebViewClient(new WebViewListener());
	}

	@Override
	public void onClick(View v) {
		if(v == m_loginButton) {
			attemptLogin();
		}
	}

	private void showView(final View which) {
		m_loginStatusView.setVisibility(which == m_loginStatusView ? View.VISIBLE : View.GONE);
		m_loginFormView.setVisibility(which == m_loginFormView ? View.VISIBLE : View.GONE);
		m_webView.setVisibility(which == m_webView ? View.VISIBLE : View.GONE);
	}
	
	private void attemptLogin() {
		if(m_loginInProgress)
			return;
		
		String id = m_idView.getText().toString();
		String[] parts = id.split("@");
		if(parts.length != 2) {
			m_idView.setError("Not a valid ID");
		}
		
		showView(m_loginStatusView);
		m_host = parts[1];
		m_requestTokenTask = new RequestTokenTask();
		m_requestTokenTask.execute();
	}
	
	private class RequestTokenTask extends AsyncTask<Void, Void, String> {

		@Override
		protected String doInBackground(Void... params) {
			// TODO Auto-generated method stub		
			try {
				m_consumer = OAuth.getConsumerForHost(LoginActivity.this, m_host);
			} catch(Exception e) {
				Log.e(TAG, "Error getting consumer", e);
				return null;
			}
			
			m_provider = new DefaultOAuthProvider(
					String.format(REQUEST_TOKEN_URL, m_host), 
					String.format(ACCESS_TOKEN_URL, m_host),
					String.format(AUTHORIZE_URL, m_host));
			
			try {
				return m_provider.retrieveRequestToken(m_consumer, "https://impeller.e43.eu/DUMMY_OAUTH_CALLBACK");
			} catch(OAuthException e) {
				Log.e(TAG, "Erorr getting request token", e);
				return null;
			}
		}
		
		protected void onPostExecute(final String tokenUrl) {
			if(tokenUrl != null) {
				m_webView.loadUrl(tokenUrl);
				showView(m_webView);
			} else {
				m_idView.setError("Error communicating with server");
				showView(m_loginFormView);
			}
		}		
	}
	
	private class WebViewListener extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url_) {
			try {
				URL url = new URL(url_);
				
				if(url.getHost().equals("impeller.e43.eu")) {
					m_authTokenTask = new AuthTokenTask();
					m_authTokenTask.execute(url.getQuery());
					return true;
				} else return false;
			} catch (MalformedURLException e) {
				return false;
			}
		 }	
	}
	
	private class AuthTokenTask extends AsyncTask<String, Void, Bundle> {
		@Override
		protected void onPreExecute() {
			showView(m_loginStatusView);
		}		
		
		@Override
		protected Bundle doInBackground(String... query_) {
			String query = query_[0];
			Map<String, String> params = Utils.getQueryMap(query);
			
			try {
				m_provider.retrieveAccessToken(m_consumer, params.get("oauth_verifier"));		
			} catch(OAuthException e) {
				Log.e(TAG, "Error getting access token", e);
				return null;
			}
			
			JSONObject whoAmI;
			try {
				URL url = new URL("https", m_host, "/api/whoami");
				HttpURLConnection conn;
				loop: while(true) {
					conn = (HttpURLConnection) url.openConnection();
					conn.setInstanceFollowRedirects(false);
					m_consumer.sign(conn);
					conn.connect();
					switch(conn.getResponseCode()) {
						case 200:
							break loop;  
							
						case 301:
						case 302:
						case 303:
						case 307:
							url = new URL(conn.getHeaderField("Location"));
							Log.v(TAG, "Following redirect to" + url);
							continue;
							
						default:
							String err = Utils.readAll(new InputStreamReader(conn.getErrorStream(), "UTF-8"));
							throw new Exception(err);
					}
				}
				
				whoAmI = new JSONObject(Utils.readAll(conn.getInputStream()));
			} catch(Exception e) {
				Log.e(TAG, "Error getting whoami", e);
				return null;
			}
			
			Bundle properties = new Bundle();
			properties.putString("username", whoAmI.optString("preferredUsername"));
			properties.putString("host", m_host);
			properties.putString("id", "acct:" + whoAmI.optString("preferredUsername") + "@" + m_host);
			properties.putString("clientId",     m_consumer.getConsumerKey());
			properties.putString("clientSecret", m_consumer.getConsumerSecret());
			properties.putString("token",        m_consumer.getToken());
			properties.putString("tokenSecret",  m_consumer.getTokenSecret());
			return properties;
		}
		
		protected void onPostExecute(final Bundle tokenInfo) {
			if(tokenInfo != null) {
				Account account = new Account(tokenInfo.getString("username") + "@" + tokenInfo.getString("host"), Authenticator.ACCOUNT_TYPE);
				m_accountManager.addAccountExplicitly(account, "(Ignored)", tokenInfo);
			
				final Intent i = new Intent();
				i.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Authenticator.ACCOUNT_TYPE);
				i.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
				setAccountAuthenticatorResult(i.getExtras());
				setResult(RESULT_OK, i);
				finish();
			} else {
				showView(m_loginFormView);
				m_idView.setError("Error getting access token");
			}
		}
		
	}
}

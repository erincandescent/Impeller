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

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.OAuthProvider;
import oauth.signpost.OAuthProviderListener;
import oauth.signpost.basic.DefaultOAuthProvider;
import oauth.signpost.http.HttpRequest;
import oauth.signpost.http.HttpResponse;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Activity which displays a login screen to the user, offering registration as
 * well.
 */
public class LoginActivity extends Activity {
	/**
	 * The default email to populate the email field with.
	 */
	public static final String EXTRA_ID = "eu.e43.impeller.ID";
	
	private static final String REQUEST_TOKEN_URL = "https://%s/oauth/request_token";
	private static final String AUTHORIZE_URL     = "https://%s/oauth/authorize";
	private static final String ACCESS_TOKEN_URL = "https://%s/oauth/access_token";

	/**
	 * Keep track of the login task to ensure we can cancel it if requested.
	 */
	private UserLoginTask m_authTask     = null;
	private GetTokenTask  m_getTokenTask = null;

	// Values for email and password at the time of the login attempt.
	private String m_id;

	// UI references.
	private EditText m_idView;
	private View m_loginFormView;
	private View m_loginStatusView;
	private TextView m_loginStatusMessageView;
	private WebView  m_webView;
	private OAuthService m_oauthService;
	private OAuthConnection m_oauthConn;
	private OAuthProvider m_prov;
	private OAuthConsumer m_cons;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		m_oauthConn = new OAuthConnection();
		this.bindService(new Intent(this, OAuthService.class), m_oauthConn, BIND_AUTO_CREATE);

		setContentView(R.layout.activity_login);

		// Set up the login form.
		m_id = getIntent().getStringExtra(EXTRA_ID);
		m_idView = (EditText) findViewById(R.id.id);
		m_idView.setText(m_id);
		m_idView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
					@Override
					public boolean onEditorAction(TextView textView, int id,
							KeyEvent keyEvent) {
						if (id == R.id.sign_in_button || id == EditorInfo.IME_NULL) {
							attemptLogin();
							return true;
						}
						return false;
					}
				});

		m_loginFormView = findViewById(R.id.login_form);
		m_loginStatusView = findViewById(R.id.login_status);
		m_loginStatusMessageView = (TextView) findViewById(R.id.login_status_message);
		
		m_webView = (WebView) findViewById(R.id.web_view);
		m_webView.setWebViewClient(new WebViewClient() {
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url_) {
				System.err.println("shouldOverride: " + url_);
				try {
					URL url = new URL(url_);
					
					if(url.getHost().equals("impeller.e43.eu")) {
						m_getTokenTask = new GetTokenTask();
						m_getTokenTask.execute(url.getQuery());
						System.err.println("YES!");
						return true;
					} else return false;
				} catch (MalformedURLException e) {
					return false;
				}
			 }
		});

		findViewById(R.id.sign_in_button).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						attemptLogin();
					}
				});
	}
	
	@Override
	protected void onDestroy() {
		unbindService(m_oauthConn);
		super.onDestroy();
	}

	/**
	 * Attempts to sign in or the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	public void attemptLogin() {
		if (m_authTask != null) {
			return;
		}

		// Reset errors.
		m_idView.setError(null);

		// Store values at the time of the login attempt.
		m_id = m_idView.getText().toString();

		boolean cancel = false;
		View focusView = null;

		// Check for a valid webfinger address.
		if (TextUtils.isEmpty(m_id)) {
			m_idView.setError(getString(R.string.error_field_required));
			focusView = m_idView;
			cancel = true;
		} else if (!m_id.contains("@")) {
			m_idView.setError(getString(R.string.error_invalid_id));
			focusView = m_idView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login and focus the first
			// form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// perform the user login attempt.
			m_loginStatusMessageView.setText(R.string.login_progress_signing_in);
			showView(m_loginStatusView);
			m_authTask = new UserLoginTask();
			m_authTask.execute((Void) null);
		}
	}

	private void showView(final View which) {
		m_loginStatusView.setVisibility(which == m_loginStatusView ? View.VISIBLE : View.GONE);
		m_loginFormView.setVisibility(which == m_loginFormView ? View.VISIBLE : View.GONE);
		m_webView.setVisibility(which == m_webView ? View.VISIBLE : View.GONE);
	}

	private final class OAuthConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			System.err.println("OAuth bound");
			m_oauthService = ((OAuthService.LocalBinder) service).getService();			
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			System.err.println("OAuth unbound");
			m_oauthService = null;
		}
	}

	/**
	 * Represents an asynchronous login/registration task used to authenticate
	 * the user.
	 */
	public class UserLoginTask extends AsyncTask<Void, Void, String> {
		@Override
		protected void onPreExecute() {
			showView(m_loginStatusView);
		}
		
		@Override
		protected String doInBackground(Void... params) {
			try {
				String[] parts = m_id.split("@");
				m_cons = m_oauthService.getUnauthenticatedConsumer(parts[1]);
				
				m_prov = new DefaultOAuthProvider(
						String.format(REQUEST_TOKEN_URL, parts[1]), 
						String.format(ACCESS_TOKEN_URL, parts[1]),
						String.format(AUTHORIZE_URL, parts[1]));
				
				m_prov.setListener(new OAuthProviderListener() {

					@Override
					public boolean onResponseReceived(HttpRequest arg0,
							HttpResponse arg1) throws Exception {
						//String resp = OAuthService.readAll(new InputStreamReader(
						//		((HttpURLConnection)arg0.unwrap()).getErrorStream()));
						//System.err.println("Response: " + resp);
						return false;
					}

					@Override
					public void prepareRequest(HttpRequest arg0)
							throws Exception {
						System.err.println("OAuth Provider URL: " + ((HttpURLConnection)arg0.unwrap()).getURL());						
					}

					@Override
					public void prepareSubmission(HttpRequest arg0)
							throws Exception {					
					}
					
				});
				
				return m_prov.retrieveRequestToken(m_cons, "http://impeller.e43.eu/oauth_callback");
			} catch(Exception ex) {
				ex.printStackTrace();
				return null;
			}
		}

		@Override
		protected void onPostExecute(final String tokenUrl) {
			if(tokenUrl != null) {
				showView(m_webView);
				m_webView.loadUrl(tokenUrl);
			} else {
				showView(m_loginFormView);
				m_idView.setError(getString(R.string.error_invalid_id));
			}
			m_authTask = null;
		}

		@Override
		protected void onCancelled() {
			m_authTask = null;
			showView(m_loginFormView);
		}
	}
	
	public class GetTokenTask extends AsyncTask<String, Void, Boolean> {
		@Override
		protected void onPreExecute() {
			showView(m_loginStatusView);
		}
		
		@Override
		protected Boolean doInBackground(String... query_) {
			try {
				String query = query_[0];
				Map<String, String> params = Utils.getQueryMap(query);
			
				m_prov.retrieveAccessToken(m_cons, params.get("oauth_verifier"));		
				m_oauthService.setUser(m_cons);
				return true;
			} catch(Exception ex) {
				ex.printStackTrace();
				return false;
			}
		}
		
		@Override
		protected void onPostExecute(Boolean success) {
			if(success.booleanValue()) {
				try {
					setResult(0, Intent.parseUri(m_oauthService.getId(), 0));
					finish();
				} catch (URISyntaxException e) {
					throw new RuntimeException(e);
				}
			} else {
				setResult(1);
				finish();
			}
			m_getTokenTask = null;
		}
	}  
}

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

import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import eu.e43.impeller.account.Authenticator;

public class FeedActivity extends Activity implements Feed.Listener, OnItemClickListener {
	static final String TAG = "FeedActivity";
	FeedConnection		m_feedConn  		= new FeedConnection();
	AccountManager  	m_accountManager	= null;
	Feed        		m_feed      		= null;
	ActivityAdapter		m_adapter   		= null;
	SharedPreferences 	m_prefs				= null;
	
	private final class FeedConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder bind) {
			System.out.println("Bound feed");
			m_feed    = (Feed) bind;
			m_adapter = new ActivityAdapter(FeedActivity.this, m_feed);
			
			m_feed.addListener(FeedActivity.this);
			
	        ListView lv = (ListView) findViewById(R.id.activity_list);
	        lv.setAdapter(m_adapter);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			m_feed.removeListener(FeedActivity.this);
			m_adapter.close();
			m_adapter = null;
			m_feed = null;
		}
	}
	
	public void updateStarted(Feed feed)
	{
		Toast.makeText(this, "Update started", Toast.LENGTH_SHORT).show();
	}
	
	public void feedUpdated(Feed feed, int items)
	{
		Toast.makeText(this, "Updated, " + items + " new notifications", Toast.LENGTH_SHORT).show();
	}

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
    	m_prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	m_accountManager = AccountManager.get(this);
    	setContentView(R.layout.activity_feed);
        ListView lv = (ListView) findViewById(R.id.activity_list);
        lv.setOnItemClickListener(this);
        
        String accountName = m_prefs.getString("accountName", null);
        if(accountName != null) {
        	Account[] accts = m_accountManager.getAccountsByType(Authenticator.ACCOUNT_TYPE);
        	for(Account a : accts) {
        		if(a.name == accountName) {
        			gotAccount(a);
        			return;
        		}
        	}
        }
        
        // No account saved or account is invalid
        // Request a new account from the user
        String[] accountTypes = new String[] { Authenticator.ACCOUNT_TYPE };
        String[] features = new String[0];
        Bundle extras = new Bundle();
        Intent chooseIntent = AccountManager.newChooseAccountIntent(null, null, accountTypes, false, null, "", features, extras);
        this.startActivityForResult(chooseIntent, 0);
    }
    
    @Override
    protected void onDestroy() {
    	if(m_feedConn != null)
    		unbindService(m_feedConn);
    	
    	super.onDestroy();
    }

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == RESULT_OK) {
			String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
			String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
			Log.i(TAG, "Logged in " + accountName);
			
			Editor e = m_prefs.edit();
			e.putString("accountName", accountName);
			e.apply();
			
			gotAccount(new Account(accountName, accountType));
		} else {
			finish();
		}
	}
	
	private void gotAccount(Account acct) {
		String host     = m_accountManager.getUserData(acct, "host");
		String username = m_accountManager.getUserData(acct, "username");
		
		Uri.Builder b = new Uri.Builder();
		b.scheme("https");
		b.authority(host);
		b.appendPath("api");
		b.appendPath("user");
		b.appendPath(username);
		b.appendPath("inbox");
		b.appendPath("major");
	
		Intent feedIntent = new Intent(Intent.ACTION_VIEW, b.build(), this, FeedService.class);
		feedIntent.putExtra("account", acct);
		Log.i(TAG, "Loading feed " + feedIntent);
		bindService(feedIntent, m_feedConn, BIND_AUTO_CREATE);
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.feed, menu);
        return true;
    }
    
    public void refresh(MenuItem itm) {
    	if(m_feed != null)
    		m_feed.pollNow();
    }
    
    public void openSettings(MenuItem itm) {
    	startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                openSettings(item);
                return true;
                
            case R.id.action_refresh:
                refresh(item);
                return true;
                
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
	@Override
	public void onItemClick(AdapterView<?> list, View view, int pos, long id) {
		JSONObject act = (JSONObject) m_adapter.getItem(pos);
		String url = null;
		//String url = act.optString("url");
		// Pump.io gives out 404s in this field!
		
		if(url == null) {
			JSONObject obj = act.optJSONObject("object");
			if(obj != null) {
				url = obj.optString("url");
			}
		}
		
		if(url != null) {
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
		}
	}
    
}

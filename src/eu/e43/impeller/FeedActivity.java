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

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

public class FeedActivity extends Activity implements FeedService.Listener, OnItemClickListener {
	private final class FeedConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder bind) {
			System.out.println("Bound feed");
			m_feed    = ((FeedService.LocalBinder) bind).getService();
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

	private final class OAuthConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName comp, IBinder bind) {
			m_oauth = ((OAuthService.LocalBinder)bind).getService();
			tryAuthorize();
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			m_oauth = null;
		}
	}
	
	public void updateStarted(FeedService feed)
	{
		Toast.makeText(this, "Update started", Toast.LENGTH_SHORT).show();
	}
	
	public void feedUpdated(FeedService feed, int items)
	{
		Toast.makeText(this, "Updated, " + items + " new notifications", Toast.LENGTH_SHORT).show();
	}

	OAuthConnection	m_oauthConn 	= null;
	FeedConnection	m_feedConn  	= null;

	OAuthService 	m_oauth	    	= null;
	FeedService 	m_feed      	= null;
	ActivityAdapter	m_adapter   	= null;
	private Intent 	m_feedIntent	= null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
    	
    	m_feedIntent = new Intent(this, FeedService.class);
    	
        setContentView(R.layout.activity_feed);
        
        ListView lv = (ListView) findViewById(R.id.activity_list);
        lv.setOnItemClickListener(this);
        
        beginAuthorize();
    }
    
    @Override
    protected void onDestroy() {
    	if(m_feedConn != null)
    		unbindService(m_feedConn);
    	if(m_oauthConn != null)
    		unbindService(m_oauthConn);
    	
    	super.onDestroy();
    }

    private void beginAuthorize() {
    	m_oauthConn = new OAuthConnection();
        if(!bindService(new Intent(this, OAuthService.class), m_oauthConn, BIND_AUTO_CREATE)) {
        	throw new RuntimeException("Unable to bind OAuth service");
        }
    }
    
    private void tryAuthorize() {
		if(!m_oauth.isAuthorized()) {
			startActivityForResult(new Intent(FeedActivity.this, LoginActivity.class), 0);
		} else {
			onAuthorized();
		}
    }
    
	private void onAuthorized() {
		try {
			this.setTitle(m_oauth.whoAmI().optString("displayName") + "'s feed");
		} catch(JSONException ex) {}
		
		
		m_feedConn = new FeedConnection();
		startService(m_feedIntent);
		if(!bindService(m_feedIntent, m_feedConn, BIND_AUTO_CREATE)) {
			throw new RuntimeException("Unable to bind Feed service");
		}
	}
    
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(resultCode == 0)
			tryAuthorize();
		else
			finish();
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
    
    public void signOut(MenuItem itm) {
    	unbindService(m_feedConn);
    	m_feedConn = null;
    	stopService(m_feedIntent);
    	
    	m_oauth.signOut();
    	tryAuthorize();
    }
    
    public void openSettings(MenuItem itm) {
    	startActivity(new Intent(this, SettingsActivity.class));
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

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
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

public class FeedActivity extends ActivityWithAccount implements Feed.Listener, OnItemClickListener {
	static final String TAG = "FeedActivity";
	FeedConnection		m_feedConn  		= new FeedConnection();
	Feed        		m_feed      		= null;
	ActivityAdapter		m_adapter   		= null;
	ListView			m_list				= null;
	
	@Override
	protected void onCreateEx() {
		startService(new Intent(this, FeedService.class));
		
		m_list = new ListView(this);
		setContentView(m_list);
	    m_list.setOnItemClickListener(this);
	}
	
	@Override
    protected void onResume() {
		super.onResume();
		if(m_feed != null)
			m_feed.clearUnread();
	}
	
	private final class FeedConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder bind) {
			System.out.println("Bound feed");
			m_feed    = (Feed) bind;
			m_adapter = new ActivityAdapter(FeedActivity.this, m_feed);
			
			m_feed.addListener(FeedActivity.this);
			
	        m_list.setAdapter(m_adapter);
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			m_feed.removeListener(FeedActivity.this);
			m_list.setAdapter(null);
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
    protected void onDestroy() {
    	if(m_feedConn != null)
    		unbindService(m_feedConn);
    	
    	super.onDestroy();
    }

	protected void gotAccount(Account acct) {
		Uri uri = Feed.getMainFeedUri(this, acct);
		Intent feedIntent = new Intent(Intent.ACTION_VIEW, uri, this, FeedService.class);
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
                
            case R.id.action_post:
            	Intent postIntent = new Intent(this, PostActivity.class);
            	postIntent.putExtra("account", m_account);
            	startActivity(postIntent);
            	return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
	@Override
	public void onItemClick(AdapterView<?> list, View view, int pos, long id) {
		JSONObject act = (JSONObject) m_adapter.getItem(pos);
		String url = null;
		String proxyUrl = null;
		//String url = act.optString("url");
		// Pump.io gives out 404s in this field!
		
		if(url == null) {
			JSONObject obj = act.optJSONObject("object");
			if(obj != null) {
				url = obj.optString("id");
				
				if(obj.has("pump_io")) {
					JSONObject pump_io = obj.optJSONObject("pump_io");
					proxyUrl = pump_io.optString("proxyURL");
				}
			}
		}
		
		if(url != null) {
			Intent objectIntent = new Intent(ObjectActivity.ACTION, Uri.parse(url), this, ObjectActivity.class);
			objectIntent.putExtra("account", m_account);
			objectIntent.putExtra("proxyURL", proxyUrl);
			startActivity(objectIntent);
		}
	}
    
}

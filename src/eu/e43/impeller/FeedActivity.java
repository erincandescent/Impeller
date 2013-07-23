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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.json.JSONObject;

import android.accounts.Account;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
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

import eu.e43.impeller.content.PumpContentProvider;

public class FeedActivity extends ActivityWithAccount implements OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {
	static final String TAG = "FeedActivity";
	ActivityAdapter		m_adapter;
	ListView			m_list				= null;
	Calendar            m_nextFetch         = null;

    // Activity IDs
    private static final int ACTIVITY_SELECT_PHOTO = 1;

	@Override
	protected void onCreateEx() {
		m_list = new ListView(this);
		setContentView(m_list);
	    m_list.setOnItemClickListener(this);

        m_adapter = new ActivityAdapter(this);
        m_list.setAdapter(m_adapter);
	}
	
	@Override
    protected void onStart() {
		super.onStart();
        Log.v(TAG, "onStart() - requesting sync");

        Calendar now = GregorianCalendar.getInstance();
        if(m_nextFetch == null || m_nextFetch.before(now)) {
            getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());
            now.add(Calendar.MINUTE, 5);
            m_nextFetch = now;
        }
	}

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    }

	protected void gotAccount(Account acct) {
        getLoaderManager().initLoader(0, null, this);
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.feed, menu);
        return true;
    }
    
    public void refresh(MenuItem itm) {
        getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_refresh:
                refresh(item);
                return true;
                
            case R.id.action_post:
            	Intent postIntent = new Intent(this, PostActivity.class);
            	postIntent.putExtra("account", m_account);
            	startActivity(postIntent);
            	return true;

            case R.id.action_post_image:
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, ACTIVITY_SELECT_PHOTO);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case ACTIVITY_SELECT_PHOTO:
                if(resultCode == RESULT_OK){
                    Uri selectedImage = data.getData();
                    Intent postIntent = new Intent(this, PostActivity.class);
                    postIntent.setType("image/*");
                    postIntent.putExtra(Intent.EXTRA_STREAM, selectedImage);
                    postIntent.putExtra("account", m_account);
                    startActivity(postIntent);
                }
                return;

            default:
                super.onActivityResult(requestCode, resultCode, data);
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
				proxyUrl = Utils.getProxyUrl(obj);
			}
		}
		
		if(url != null) {
			Intent objectIntent = new Intent(ObjectActivity.ACTION, Uri.parse(url), this, ObjectActivity.class);
			objectIntent.putExtra("account", m_account);
			objectIntent.putExtra("proxyURL", proxyUrl);
			startActivity(objectIntent);
		}
	}

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Uri uri =
                Uri.parse(PumpContentProvider.FEED_URL).buildUpon()
                        .appendPath(m_account.name)
                        .build();

        return new CursorLoader(this, uri,
                new String[] { "_json" },
                "verb='share' OR (verb='post' AND object.objectType<>'comment')", null,
                "object.updated DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> objectLoader, Cursor o) {
        if(objectLoader != null && o != null) {
            o.setNotificationUri(getContentResolver(), ((CursorLoader) objectLoader).getUri());
        }
        m_adapter.updateCursor(o);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> objectLoader) {
        m_adapter.updateCursor(null);
    }
}

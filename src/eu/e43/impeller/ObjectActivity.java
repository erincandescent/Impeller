package eu.e43.impeller;

import org.json.JSONObject;

import android.accounts.Account;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

import eu.e43.impeller.ObjectService.ObjectCache;

public class ObjectActivity extends ActivityWithAccount {
	private static final String TAG = "ObjectActivity";
	public static final String ACTION = "eu.e43.impeller.SHOW_OBJECT";
	private CacheConnection 	m_cacheConn;
	private ObjectCache 		m_cache;
	private GetObjectTask 		m_getObjectTask;

	private class CacheConnection implements ServiceConnection {
		@Override
		public void onServiceConnected(ComponentName name, IBinder cache) {
			m_cache = (ObjectCache) cache;
			m_getObjectTask.execute(null, null);
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			m_cache = null;			
		}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		m_cacheConn = new CacheConnection();
		
		setContentView(new Spinner(this));
		// Show the Up button in the action bar.
		setupActionBar();
		m_getObjectTask = new GetObjectTask();
	}
	
	@Override
	protected void gotAccount(Account a) {
		Intent cacheIntent = new Intent(this, ObjectService.class);
		cacheIntent.putExtra("account", a);
		bindService(cacheIntent, m_cacheConn, BIND_AUTO_CREATE);
	}

	/**
	 * Set up the {@link android.app.ActionBar}.
	 */
	private void setupActionBar() {

		getActionBar().setDisplayHomeAsUpEnabled(true);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.object, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	private void onGotObject(JSONObject obj) {
		ListView comments = new ListView(this);
		
        LayoutInflater vi = LayoutInflater.from(this);
        LinearLayout container = (LinearLayout) vi.inflate(R.layout.activity_object, null);
        comments.addHeaderView(container);
		
		setContentView(comments);
		ImageView authorIcon   = (ImageView)    findViewById(R.id.actorImage);
		TextView titleView     = (TextView)     findViewById(R.id.actorName);
		TextView dateView      = (TextView)     findViewById(R.id.objectDate);
		
		setTitle(obj.optString("displayName", "Object"));
		
		JSONObject author = obj.optJSONObject("author");
		if(author != null) {
			titleView.setText(author.optString("displayName"));
			JSONObject img = author.optJSONObject("image");
			if(img != null) {
				UrlImageViewHelper.setUrlDrawable(authorIcon, img.optString("url"));
			}
		} else {
			titleView.setText("No author. How bizzare.");
		}
		dateView.setText(obj.optString("published"));
		
		WebView wv = new WebView(this);
		wv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		String url  = obj.optString("url");
		String data = obj.optString("content", "No content");
		wv.loadDataWithBaseURL(url, data, "text/html", "utf-8", null);
		wv.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
		container.addView(wv);
		
		JSONObject replies = obj.optJSONObject("replies");
		if(replies != null) {
			comments.setAdapter(new CommentAdapter(this, replies));
		}
	}
	
	private class GetObjectTask extends AsyncTask<Void, Void, JSONObject> {
		@Override
		protected JSONObject doInBackground(Void... arg0) {
			Uri uri = getIntent().getData();
			try {
				return m_cache.getObject(uri.toString());
			} catch(Exception e) {
				Log.e(TAG, "Error getting object", e);
				Toast.makeText(ObjectActivity.this, "Error getting object: " + e.getMessage(), Toast.LENGTH_LONG).show();
				return null;
			}
		}
		
		protected void onPostExecute(final JSONObject obj) {
			onGotObject(obj);
		}
		
		
		
		
		
	}
}

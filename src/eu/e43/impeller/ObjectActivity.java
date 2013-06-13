package eu.e43.impeller;

import java.net.MalformedURLException;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.AdapterView.AdapterContextMenuInfo;
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
	private JSONObject			m_object;
	private CommentAdapter		m_commentAdapter;
	private Menu				m_menu;
	private ListView			m_commentsView;

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
	protected void onCreateEx() {
		setContentView(new Spinner(this));
		// Show the Up button in the action bar.
		setupActionBar();
		m_getObjectTask = new GetObjectTask();
	}
	
	@Override
	protected void gotAccount(Account a) {
		m_cacheConn = new CacheConnection();
		Intent cacheIntent = new Intent(this, ObjectService.class);
		cacheIntent.putExtra("account", a);
		bindService(cacheIntent, m_cacheConn, BIND_AUTO_CREATE);
	}
	
	@Override
	protected void onDestroy() {
		if(m_cacheConn != null) {
			unbindService(m_cacheConn);
		}
		super.onDestroy();
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
		m_menu = menu;
		updateMenu();
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
				
			case R.id.action_reply:
				Intent replyIntent = new Intent(PostActivity.ACTION_REPLY, null, this, PostActivity.class);
				replyIntent.putExtra("inReplyTo", this.m_object.toString());
				startActivityForResult(replyIntent, 0);
				return true;
				
			case R.id.action_like:
				new DoLike(m_object);
				return true;
				
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
	// At present context menus are only shown for comments
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo_) {
		super.onCreateContextMenu(menu, v, menuInfo_);
		
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) menuInfo_;
		
		JSONObject comment = (JSONObject) m_commentsView.getItemAtPosition(menuInfo.position);
		if(comment == null) return;
		
		JSONObject author = comment.optJSONObject("author");
		String title = "Comment";
		if(author != null && author.has("displayName")) {
			title = "Comment by " + author.optString("displayName");
		}
		
		menu.setHeaderTitle(title);
		getMenuInflater().inflate(R.menu.comment, menu);
		if(comment.optBoolean("liked", false)) {
			menu.findItem(R.id.action_like).setTitle(R.string.action_unlike);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		JSONObject comment = (JSONObject) m_commentsView.getItemAtPosition(menuInfo.position);
		
		
		switch(item.getItemId()) {
		case R.id.action_like:
			new DoLike(comment);
			return true;
			
		case R.id.action_showAuthor:
			JSONObject author = comment.optJSONObject("author");
			if(author == null)
				return true;
			
			Intent authorIntent = new Intent(ObjectActivity.ACTION, Uri.parse(author.optString("id")), this, ObjectActivity.class);
			authorIntent.putExtra("account", m_account);
			authorIntent.putExtra("proxyURL", Utils.getProxyUrl(author));
			startActivity(authorIntent);
			return true;
		
		default:
			return super.onContextItemSelected(item);
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == 0) {
			// Post comment
			if(resultCode == RESULT_OK) {
				if(m_commentAdapter != null)
					m_commentAdapter.updateComments();
			}
		} else {
			super.onActivityResult(requestCode, resultCode, data);
		}
	}
	
	private void onGotObject(JSONObject obj) {
		Log.v(TAG, "onGotObject(" + obj.toString() + ")");
		m_object = obj;
		ListView comments = new ListView(this);
		
        LayoutInflater vi = LayoutInflater.from(this);
        LinearLayout container = (LinearLayout) vi.inflate(R.layout.activity_object, null);
        comments.addHeaderView(container);
        setContentView(comments);
        m_commentsView = comments;
		
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
			m_commentAdapter = new CommentAdapter(this, replies, false);
			comments.setAdapter(m_commentAdapter);
		}
		
		updateMenu();
		
		registerForContextMenu(comments);
	}
	
	private class GetObjectTask extends AsyncTask<Void, Void, JSONObject> {
		@Override
		protected JSONObject doInBackground(Void... arg0) {
			Uri uri      = getIntent().getData();
			String proxyUrl = getIntent().getStringExtra("proxyURL");
			try {
				return m_cache.getObject(uri.toString(), proxyUrl);
			} catch(Exception e) {
				Log.e(TAG, "Error getting object", e);
				return null;
			}
		}
		
		protected void onPostExecute(final JSONObject obj) {
			if(obj != null) {
				onGotObject(obj);
			} else {
				Toast.makeText(ObjectActivity.this, "Error getting object", Toast.LENGTH_SHORT);
				finish();
			}
		}
	}
	
	private void updateMenu() {
		if(m_menu == null)
			return;
		
		MenuItem itm = m_menu.findItem(R.id.action_like);
		if(m_object != null && m_object.optBoolean("liked", false))
			itm.setTitle(R.string.action_unlike);
		else
			itm.setTitle(R.string.action_like);
	}
	
	private class DoLike implements PostTask.Callback {
		private JSONObject m_object;
		
		public DoLike(JSONObject object) {
			String action;
			m_object = object;
			
			if(object.optBoolean("liked", false))
				action = "unfavorite";
			else
				action = "favorite";
			
			JSONObject obj = new JSONObject();
			try {
				obj.put("verb", action);
				obj.put("object", object);
			} catch(JSONException e) {
				throw new RuntimeException(e);
			}
			
			PostTask task = new PostTask(ObjectActivity.this, this);
			task.execute(obj.toString());
		}

		@Override
		public void call(JSONObject obj) {
			// TODO Auto-generated method stub
			try {
				m_object.put("liked", !m_object.optBoolean("liked", false));
			} catch (JSONException e) {
				Log.v(TAG, "Swallowing exception", e);
			}
			updateMenu();
			
			m_cache.invalidateObject(m_object.optString("id"));
		}
	}
}

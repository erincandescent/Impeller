package eu.e43.impeller;

import java.net.MalformedURLException;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import eu.e43.impeller.content.PumpContentProvider;

public class ObjectActivity extends ActivityWithAccount implements View.OnClickListener {
	private static final String TAG = "ObjectActivity";
	public static final String ACTION = "eu.e43.impeller.SHOW_OBJECT";
	private JSONObject			m_object;
	private CommentAdapter		m_commentAdapter;
	private Menu				m_menu;
	private ListView			m_commentsView;

    private int toDIP(int dip) {
        final float density = getResources().getDisplayMetrics().density;
        return (int) (density * dip + 0.5f);
    }

	@Override
	protected void onCreateEx() {
        // Show the progress bar
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setProgressBarIndeterminate(true);
        setProgressBarIndeterminateVisibility(true);

        m_commentsView = new ListView(this);
        setContentView(m_commentsView);
        LayoutInflater li = LayoutInflater.from(this);
        RelativeLayout header = (RelativeLayout) li.inflate(R.layout.activity_object, null);
        RelativeLayout footer = (RelativeLayout) li.inflate(R.layout.activity_object_reply, null);
        int height = toDIP(80);

        header.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, height));

        m_commentsView.addHeaderView(header);
        m_commentsView.addFooterView(footer);

        setupActionBar();
	}
	
	@Override
	protected void gotAccount(Account a) {
        Log.i(TAG, "Got account, " + a.name + "; fetching " + getIntent().getData());
        Uri uri      = getIntent().getData();

        ContentResolver res = getContentResolver();
        Cursor c = res.query(Uri.parse(PumpContentProvider.OBJECT_URL),
                new String[] { "_json" },
                "id=?", new String[] { uri.toString() },
                null);
        try {
            if(c.getCount() != 0) {
                c.moveToFirst();
                try {
                    m_object = new JSONObject(c.getString(0));
                } catch(JSONException ex) {
                    Toast.makeText(this, "Bad object in database", Toast.LENGTH_SHORT).show();
                    this.finish();
                    return;
                }
            }
        } finally {
            c.close();
        }

        if(m_object == null) {
            Toast.makeText(this, "Error getting object", Toast.LENGTH_SHORT).show();
            this.finish();
            return;
        }

        try {
            Log.v(TAG, "Object is " + m_object.toString(4));
        } catch(JSONException e) {
            return;
        }

        setProgressBarIndeterminate(false);
        setProgressBarIndeterminateVisibility(false);

        ImageView authorIcon   = (ImageView)    findViewById(R.id.actorImage);
        TextView titleView     = (TextView)     findViewById(R.id.actorName);
        TextView dateView      = (TextView)     findViewById(R.id.objectDate);
        Button   replyButton   = (Button)       findViewById(R.id.replyButton);
        replyButton.setOnClickListener(this);

        setTitle(m_object.optString("displayName", "Object"));

        JSONObject author = m_object.optJSONObject("author");
        if(author != null) {
            titleView.setText(author.optString("displayName"));
            JSONObject img = author.optJSONObject("image");
            if(img != null) {
                getImageLoader().setImage(authorIcon, Utils.getImageUrl(img));
            }
        } else {
            titleView.setText("No author. How bizzare.");
        }
        dateView.setText(m_object.optString("published"));

        JSONObject image = m_object.optJSONObject("image");
        if(image != null) {
            ImageView iv = new ImageView(this);
            getImageLoader().setImage(iv, Utils.getImageUrl(image));
            //iv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            m_commentsView.addHeaderView(iv);
        }

        WebView wv = new WebView(this);
        //wv.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        String url  = m_object.optString("url");
        String data = m_object.optString("content", "No content");
        wv.loadDataWithBaseURL(url, data, "text/html", "utf-8", null);
        wv.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
        m_commentsView.addHeaderView(wv);

        JSONObject replies = m_object.optJSONObject("replies");
        m_commentAdapter = new CommentAdapter(this, replies, false);
        m_commentsView.setAdapter(m_commentAdapter);

        updateMenu();

        registerForContextMenu(m_commentsView);
        Log.i(TAG, "Finished showing object");
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
        /* Will come back later - "rich comments" */
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
	
	private void updateMenu() {
		if(m_menu == null)
			return;
		
		MenuItem itm = m_menu.findItem(R.id.action_like);
        if(itm == null)
            return;

		if(m_object != null && m_object.optBoolean("liked", false))
			itm.setTitle(R.string.action_unlike);
		else
			itm.setTitle(R.string.action_like);
	}

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.replyButton:
                EditText editor = (EditText) findViewById(R.id.replyText);
                editor.clearComposingText();

                view.setEnabled(false);
                editor.setEnabled(false);
                JSONObject comment = new JSONObject();
                try {
                    comment.put("objectType", "comment");
                    comment.put("inReplyTo", m_object);
                    comment.put("content", Html.toHtml(editor.getText()));
                } catch(JSONException e) {
                    throw new RuntimeException(e);
                }

                new PostReply(comment);
        }
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
            if(obj != null) {
			    try {
				    m_object.put("liked", !m_object.optBoolean("liked", false));
			    } catch (JSONException e) {
				    Log.v(TAG, "Swallowing exception", e);
			    }
			    updateMenu();
            }
			
			getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());
		}
	}

    private class PostReply implements PostTask.Callback {
        private JSONObject m_object;

        public PostReply(JSONObject object) {
            String action;
            m_object = object;

            JSONObject obj = new JSONObject();
            try {
                obj.put("verb", "post");
                obj.put("object", object);
            } catch(JSONException e) {
                throw new RuntimeException(e);
            }

            PostTask task = new PostTask(ObjectActivity.this, this);
            task.execute(obj.toString());
        }

        @Override
        public void call(JSONObject obj) {
            EditText editor      = (EditText) findViewById(R.id.replyText);
            Button   replyButton = (Button)   findViewById(R.id.replyButton);

            if(obj != null) {
                if(m_commentAdapter != null)
                    m_commentAdapter.updateComments();

                editor.setText("");

                getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());
            } else {
                Toast.makeText(ObjectActivity.this, "Error posting reply", Toast.LENGTH_SHORT).show();
            }

            editor.setEnabled(true);
            replyButton.setEnabled(true);
        }
    }
}

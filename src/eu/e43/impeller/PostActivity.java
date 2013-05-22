package eu.e43.impeller;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import oauth.signpost.OAuthConsumer;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import eu.e43.impeller.account.OAuth;

public class PostActivity extends ActivityWithAccount implements OnClickListener {
	public static final String ACTION_REPLY = "eu.e43.impeller.action.REPLY";
	
	private static final String TAG = "PostActivity";
	// EXTRA_HTML_TEXT is a 4.2 feature
	private static final String EXTRA_HTML_TEXT = "android.intent.extra.HTML_TEXT";
	
	Button   	m_postBtn;
	TextView 	m_content;
	Account  	m_account;
	JSONObject	m_inReplyTo = null;
	
	@Override
	protected void onCreateEx() {
		setContentView(R.layout.activity_post);
		
		m_content = (TextView) findViewById(R.id.content);
		m_postBtn = (Button) findViewById(R.id.post);
		m_postBtn.setOnClickListener(this);
		
		Intent intent = getIntent();
		if(Intent.ACTION_SEND.equals(intent.getAction())) {
			if(intent.hasExtra(EXTRA_HTML_TEXT)) {
				PumpHtml.setFromHtml(m_content, intent.getStringExtra(EXTRA_HTML_TEXT));
			} else {
				m_content.setText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT));
			}
		} else if(ACTION_REPLY.equals(intent.getAction())) {
			this.setTitle(R.string.title_activity_post_reply);
			try {
				m_inReplyTo = new JSONObject(intent.getStringExtra("inReplyTo"));
			} catch (JSONException e) {
				Log.e(TAG, "Error parsing inReplyTo", e);
				setResult(RESULT_CANCELED);
				finish();
			}
		}
	}

	public boolean onCreateOptionsMenu(Menu menu) { return true; }

	@Override
	protected void gotAccount(Account a) {
		m_account = a;
	}

	private void onPost() {
		try {
			JSONObject obj = new JSONObject();
			String generator = Utils.readAll(getResources().openRawResource(R.raw.generator));
			
			if(m_inReplyTo == null) {
				obj.put("objectType", "note");
			} else {
				obj.put("objectType", "comment");
				obj.put("inReplyTo", m_inReplyTo);
			}
			obj.put("content", Html.toHtml(m_content.getEditableText()));
			
			JSONObject act = new JSONObject();
			act.put("generator", new JSONObject(generator));
			act.put("verb", "post");
			act.put("object", obj);
			
			PostTask t = new PostTask();
			t.execute(act.toString());
		} catch(Exception ex) {
			Toast.makeText(this, "Error creating post: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
		}
	}
	
	@Override
	public void onClick(View v) {
		if(v == m_postBtn) { onPost(); }
	}

	private class PostTask extends AsyncTask<String, Void, Boolean> {
		ProgressDialog m_progress; 
		String m_url = null;
		
		@Override
		protected void onPreExecute() {
			m_progress = ProgressDialog.show(PostActivity.this, "Posting...", "Submitting post");
			m_progress.setIndeterminate(true);
		}
		
		@Override
		protected Boolean doInBackground(String... activity_) {
			try {
				String activity = activity_[0];
				Log.i(TAG, "Posting " + activity);
				OAuthConsumer cons = OAuth.getConsumerForAccount(PostActivity.this, m_account);
			
				Uri outboxUri = Feed.getFeedUri(PostActivity.this, m_account, "feed");
			
				URL url = new URL(outboxUri.toString());
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setDoOutput(true);
				conn.setDoInput(true);
				conn.setRequestMethod("POST");
				conn.setRequestProperty("Content-Type", "application/json");
				cons.sign(conn);
				
				OutputStream os = conn.getOutputStream();
				OutputStreamWriter wr = new OutputStreamWriter(os);
				wr.write(activity);
				wr.close();
							
				if(conn.getResponseCode() != 200) {
					Log.e(TAG, "Error creating post: " + Utils.readAll(conn.getErrorStream()));
					return false;
				}
				
				JSONObject result = new JSONObject(Utils.readAll(conn.getInputStream()));
				m_url = result.optString("id");
				
				return true;
			} catch (Exception e) {
				Log.e(TAG, "Error posting", e);
				return false;
			}
		}
		
		protected void onPostExecute(Boolean res) {
			m_progress.dismiss();
			if(res.booleanValue() == true) {
				if(ACTION_REPLY.equals(getIntent().getAction())) {
					Toast.makeText(PostActivity.this, "Posted", Toast.LENGTH_SHORT);
				} else {
					startActivity(new Intent(ObjectActivity.ACTION, Uri.parse(m_url), PostActivity.this, ObjectActivity.class));
				}
				setResult(RESULT_OK);
				finish();
			} else {
				Toast.makeText(PostActivity.this, "Error creating post", Toast.LENGTH_SHORT).show();
			}
		}
		
	}
}

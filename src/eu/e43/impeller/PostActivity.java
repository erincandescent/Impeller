package eu.e43.impeller;



import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

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
			obj.put("content", Html.toHtml((Spanned) m_content.getText()));
			
			JSONObject act = new JSONObject();
			act.put("generator", new JSONObject(generator));
			act.put("verb", "post");
			act.put("object", obj);
			
			PostTask t = new PostTask(this, new PostCallback());
			t.execute(act.toString());
		} catch(Exception ex) {
			Toast.makeText(this, "Error creating post: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
		}
	}
	
	@Override
	public void onClick(View v) {
		if(v == m_postBtn) { onPost(); }
	}
	
	private class PostCallback implements PostTask.Callback {
		ProgressDialog m_progress;
		
		public PostCallback() {
			m_progress = ProgressDialog.show(PostActivity.this, "Posting...", "Submitting post");
			m_progress.setIndeterminate(true);			
		}
		
		@Override
		public void call(JSONObject obj) {
			m_progress.dismiss();
			if(obj != null) {
				if(ACTION_REPLY.equals(getIntent().getAction())) {
					Toast.makeText(PostActivity.this, "Posted", Toast.LENGTH_SHORT);
				} else {
					String url = obj.optString("id");
					startActivity(new Intent(ObjectActivity.ACTION, Uri.parse(url), PostActivity.this, ObjectActivity.class));
				}
				setResult(RESULT_OK);
				finish();
			} else {
				Toast.makeText(PostActivity.this, "Error creating post", Toast.LENGTH_SHORT).show();
			} 
		}
		
	}
}

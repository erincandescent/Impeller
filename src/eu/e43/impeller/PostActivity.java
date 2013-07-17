package eu.e43.impeller;



import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import eu.e43.impeller.account.OAuth;
import oauth.signpost.OAuthConsumer;

public class PostActivity extends ActivityWithAccount {
	public static final String ACTION_REPLY = "eu.e43.impeller.action.REPLY";
	
	private static final String TAG = "PostActivity";
	// EXTRA_HTML_TEXT is a 4.2 feature
	private static final String EXTRA_HTML_TEXT = "android.intent.extra.HTML_TEXT";

    private static final int TYPE_NOTE    = 0;
    private static final int TYPE_COMMENT = 1;
    private static final int TYPE_IMAGE   = 2;

    // UI widgets

    TextView        m_titleLabel;
    EditText        m_title;
    ImageView       m_imageView;
    TextView        m_contentLabel;
	EditText 	    m_content;
    CheckBox        m_isPublic;
    ProgressDialog  m_progress;

    Uri         m_extraUri;
	Account  	m_account;
	JSONObject	m_inReplyTo = null;
    int         m_type;
	
	@Override
	protected void onCreateEx() {
		setContentView(R.layout.activity_post);

        m_titleLabel    = (TextView)  findViewById(R.id.titleLabel);
        m_title         = (EditText)  findViewById(R.id.title);
        m_imageView     = (ImageView) findViewById(R.id.image);
        m_contentLabel  = (TextView)  findViewById(R.id.contentLabel);
		m_content       = (EditText)  findViewById(R.id.content);
        m_isPublic      = (CheckBox)  findViewById(R.id.isPublic);

		Intent intent = getIntent();
        String type = intent.getType();
        Log.v(TAG, "MIME Type is " + type);
        if(type == null || type.startsWith("text/")) {
            if(ACTION_REPLY.equals(intent.getAction())) {
                m_type = TYPE_COMMENT;
            } else {
                m_type = TYPE_NOTE;
            }

            m_titleLabel.setVisibility(View.INVISIBLE);
            m_title.setVisibility(View.INVISIBLE);
            m_imageView.setVisibility(View.INVISIBLE);
            m_contentLabel.setVisibility(View.INVISIBLE);
        } else if(type.startsWith("image/")) {
            m_type = TYPE_IMAGE;
            m_extraUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(m_extraUri == null) {
                Log.e(TAG, "No image URI?");
                setResult(RESULT_CANCELED);
                finish();
            }

            m_imageView.setImageURI(m_extraUri);
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }

		if(Intent.ACTION_SEND.equals(intent.getAction())) {
			if(intent.hasExtra(EXTRA_HTML_TEXT)) {
				PumpHtml.setFromHtml(this, m_content, intent.getStringExtra(EXTRA_HTML_TEXT));
			} else if(intent.hasExtra(Intent.EXTRA_TEXT)) {
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

    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.post, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;

            case R.id.action_post:
                onPost();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

	@Override
	protected void gotAccount(Account a) {
		m_account = a;
	}

    private void onPost() {
        JSONObject obj = new JSONObject();

        try {
            m_progress = ProgressDialog.show(this, "Posting...", "Submitting post");
            m_progress.setIndeterminate(true);

            switch(m_type) {
                case TYPE_IMAGE:
                    PostImageTask t = new PostImageTask();
                    t.execute(m_extraUri);
                    return;

                case TYPE_NOTE:
                    obj.put("objectType", "note");
                    break;
                case TYPE_COMMENT:
                    obj.put("objectType", "comment");
                    break;

                default:
                    throw new RuntimeException("Bad type");

            }
            postPhase2(obj);
        } catch(Exception ex) {
            Log.e(TAG, "Error creating object", ex);
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    class PostImageTask extends AsyncTask<Uri, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(Uri... uris) {
            Log.v(TAG, "Posting image");
            Uri imageUri = uris[0];
            try {
                String type = getContentResolver().getType(imageUri);
                AssetFileDescriptor imgFile = getContentResolver().openAssetFileDescriptor(imageUri, "r");
                InputStream is = imgFile.createInputStream();
                OAuthConsumer cons = OAuth.getConsumerForAccount(PostActivity.this, m_account);

                URL uploadUrl = new URL(Utils.getUserUri(PostActivity.this, m_account, "uploads").toString());
                HttpURLConnection conn = (HttpURLConnection) uploadUrl.openConnection();

                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", type);
                cons.sign(conn);
                OutputStream os = conn.getOutputStream();

                byte[] buf = new byte[4096];
                int read = is.read(buf);
                while(read > 0) {
                    os.write(buf, 0, read);
                    read = is.read(buf);
                }
                os.close();

                if(conn.getResponseCode() != 200) {
                    String err = Utils.readAll(conn.getErrorStream());
                    Log.e(TAG, "Server returned error: " + err);
                    return null;
                }

                String json = Utils.readAll(conn.getInputStream());
                JSONObject orig = new JSONObject(json);
                JSONObject obj = new JSONObject();

                // Hack around lacking features
                obj.put("image",     orig.optJSONObject("image"));
                obj.put("fullImage", orig.optJSONObject("fullImage"));
                obj.put("objectType", "image");
                JSONArray dupes = new JSONArray();
                dupes.put(orig.optString("id"));
                obj.put("upstreamDuplicates", dupes);
                return obj;
            } catch (Exception e) {
                Log.e(TAG, "Error posting image", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(JSONObject obj) {
            postPhase2(obj);
        }
    }

	private void postPhase2(JSONObject obj) {
        if(obj == null) {
            Toast.makeText(this, "Error creating object", Toast.LENGTH_SHORT).show();
            m_progress.dismiss();
        }

		try {
            Log.v(TAG, "Begin phase 2");
            m_title.clearComposingText();
			m_content.clearComposingText();
			
			switch(m_type) {
                case TYPE_IMAGE:
                    obj.put("displayName", Html.escapeHtml(m_title.getText()));
                    break;
            }

            if(m_inReplyTo != null) {
				obj.put("inReplyTo", m_inReplyTo);
			}
			obj.put("content", Html.toHtml((Spanned) m_content.getText()));

            // if(obj.has("id")) {
            //    // Update object
            //    UpdateObjectTask t = new UpdateObjectTask();
            //    t.execute(obj);
            //} else {
                // Continue
                //Log.v(TAG, "No update - to phase 3");
                postPhase3(obj);
            //}
        } catch(Exception ex) {
            Toast.makeText(this, "Error creating post: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            m_progress.dismiss();
        }
    }

    /*
    class UpdateObjectTask extends AsyncTask<JSONObject, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(JSONObject... jsonObjects) {
            JSONObject obj = jsonObjects[0];
            try {
                Log.v(TAG, "PUT object for update: " + obj.toString(4));
                URL url = new URL(obj.getString("id"));

                OAuthConsumer cons = OAuth.getConsumerForAccount(PostActivity.this, m_account);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("PUT");
                conn.setRequestProperty("Content-Type", "application/json");
                cons.sign(conn);

                OutputStream os = conn.getOutputStream();
                OutputStreamWriter wr = new OutputStreamWriter(os, "UTF-8");
                wr.write(obj.toString());
                wr.close();

                int code = conn.getResponseCode();
                if(code != 200) {
                    Log.e(TAG, "Response " + code + "; error " + Utils.readAll(conn.getErrorStream()));
                }

                conn.getInputStream().close();
            } catch(Exception ex) {
                Log.w(TAG, "Error updating object", ex);
            }
            return obj;
        }

        @Override
        protected void onPostExecute(JSONObject obj) {
            postPhase3(obj);
        }
    }
    */

    private void postPhase3(JSONObject obj) {
        try {
            Log.v(TAG, "Begin phase 3");
            JSONObject act = new JSONObject();
            if(m_isPublic.isChecked()) {
                JSONObject thePublic = new JSONObject();
                thePublic.put("id",         "http://activityschema.org/collection/public");
                thePublic.put("objectType", "collection");

                JSONArray to = new JSONArray();
                to.put(thePublic);
                act.put("to", to);
            }

            String generator = Utils.readAll(getResources().openRawResource(R.raw.generator));
            act.put("generator", new JSONObject(generator));
			act.put("verb", "post");
			act.put("object", obj);
			
			PostTask t = new PostTask(this, new PostCallback());
			t.execute(act.toString());
		} catch(Exception ex) {
			Toast.makeText(this, "Error creating post: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            m_progress.dismiss();
		}
	}
	
	private class PostCallback implements PostTask.Callback {
		@Override
		public void call(JSONObject obj) {
			m_progress.dismiss();
			if(obj != null) {
				if(ACTION_REPLY.equals(getIntent().getAction())) {
					Toast.makeText(PostActivity.this, "Posted", Toast.LENGTH_SHORT);
				} else {
                    JSONObject targetObj = obj.optJSONObject("object");
                    if(targetObj != null) {
					    String url = targetObj.optString("id");
					    startActivity(new Intent(ObjectActivity.ACTION, Uri.parse(url), PostActivity.this, ObjectActivity.class));
                    }
				}
				setResult(RESULT_OK);
				finish();
			} else {
				Toast.makeText(PostActivity.this, "Error creating post", Toast.LENGTH_SHORT).show();
			} 
		}
		
	}
}

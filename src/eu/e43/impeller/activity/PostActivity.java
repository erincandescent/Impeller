package eu.e43.impeller.activity;



import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.location.Address;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

import eu.e43.impeller.Constants;
import eu.e43.impeller.LocationServices;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.uikit.LocationAdapter;
import eu.e43.impeller.uikit.PumpHtml;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
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

    EditText        m_title;
    ImageView       m_imageView;
	EditText 	    m_content;
    Spinner         m_location;
    CheckBox        m_isPublic;
    ProgressDialog  m_progress;

    Uri             m_extraUri;
	Account  	    m_account;
	JSONObject	    m_inReplyTo = null;
    int             m_type;
    LocationAdapter m_locations;
	
	@Override
	protected void onCreateEx(Bundle _) {
        Intent intent = getIntent();
        String type = intent.getType();
        setContentView(R.layout.activity_post);

        Log.v(TAG, "MIME Type is " + type);
        if(type == null || type.startsWith("text/")) {
            if(ACTION_REPLY.equals(intent.getAction())) {
                m_type = TYPE_COMMENT;
            } else {
                m_type = TYPE_NOTE;
            }
        } else if(type.startsWith("image/")) {
            m_type = TYPE_IMAGE;
            m_extraUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(m_extraUri == null) {
                Log.e(TAG, "No image URI?");
                setResult(RESULT_CANCELED);
                finish();
            }
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }

        m_title         = (EditText)  findViewById(R.id.title);
        m_imageView     = (ImageView) findViewById(R.id.image);
        m_location      = (Spinner)   findViewById(R.id.location);
        m_content       = (EditText)  findViewById(R.id.content);
        m_isPublic      = (CheckBox)  findViewById(R.id.isPublic);

        if(m_type == TYPE_IMAGE) {
            m_imageView.setVisibility(View.VISIBLE);
            SetupImageTask t = new SetupImageTask();
            t.execute();
        }

        if(intent.hasExtra(Intent.EXTRA_SUBJECT)) {
            m_title.setText(intent.getStringExtra(Intent.EXTRA_SUBJECT));
        }

        if(intent.hasExtra(EXTRA_HTML_TEXT)) {
            PumpHtml.setFromHtml(this, m_content, intent.getStringExtra(EXTRA_HTML_TEXT));
        } else if(intent.hasExtra(Intent.EXTRA_TEXT)) {
            m_content.setText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT));
        }

        if(ACTION_REPLY.equals(intent.getAction())) {
            setTitle(R.string.title_activity_post_reply);
            m_title.setVisibility(View.GONE);
            m_title.setText("");
            try {
                m_inReplyTo = new JSONObject(intent.getStringExtra("inReplyTo"));
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing inReplyTo", e);
                setResult(RESULT_CANCELED);
                finish();
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(Integer.parseInt(prefs.getString(Constants.PREF_MY_LOCATION, "0"))
                >= Constants.MY_LOCATION_FETCH) {
            m_locations = new LocationAdapter(this, m_location);
            m_location.setAdapter(m_locations);
        } else {
            findViewById(R.id.location_container).setVisibility(View.GONE);
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

    private void dismissProgress() {
        if(m_progress != null) {
            m_progress.dismiss();
            m_progress = null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissProgress();
        super.onDestroy();
    }

    private class SetupImageTask extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Void... voids) {
            try {
                InputStream is = getContentResolver().openInputStream(m_extraUri);
                Bitmap bmp = BitmapFactory.decodeStream(is);
                is.close();
                Display disp = getWindowManager().getDefaultDisplay();

                Point size = new Point();
                disp.getSize(size);

                int dispLargest = size.x > size.y ? size.x : size.y;
                int imgLargest = bmp.getWidth() > bmp.getHeight() ? bmp.getWidth() : bmp.getHeight();
                if(imgLargest > dispLargest) {
                    int newWidth  = bmp.getWidth()  * dispLargest / imgLargest;
                    int newHeight = bmp.getHeight() * dispLargest / imgLargest;

                    Log.i(TAG, "Scaling image from " + bmp.getWidth() + "x" + bmp.getHeight()
                            + " to " + newWidth + "x" + newHeight);

                    bmp = Bitmap.createScaledBitmap(bmp, newWidth, newHeight, true);
                }
                return bmp;
            } catch(IOException e) {
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if(bitmap != null) {
                m_imageView.setImageBitmap(bitmap);
            } else {
                Toast.makeText(PostActivity.this, "File not found", Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
            }
        }
    }

    private void onPost() {
        JSONObject obj = new JSONObject();
        if(m_title != null) m_title.clearComposingText();
        if(m_content != null) m_content.clearComposingText();

        m_progress = ProgressDialog.show(this, "Posting...", "Submitting post");
        m_progress.setIndeterminate(true);


        try {
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

    class PostImageTask extends AsyncTask<Object, Void, JSONObject> {
        @Override
        protected JSONObject doInBackground(Object... uris) {
            Log.v(TAG, "Posting image");
            Uri    imageUri    = (Uri) uris[0];

            try {
                String type = getContentResolver().getType(imageUri);
                AssetFileDescriptor imgFile = getContentResolver().openAssetFileDescriptor(imageUri, "r");
                InputStream is = imgFile.createInputStream();
                OAuthConsumer cons = OAuth.getConsumerForAccount(PostActivity.this, m_account);

                Uri uploadUri = Utils.getUserUri(PostActivity.this, m_account, "uploads");

                Log.v(TAG, "Uploading to " + uploadUri);

                URL uploadUrl = new URL(uploadUri.toString());
                HttpURLConnection conn = (HttpURLConnection) uploadUrl.openConnection();

                conn.setRequestMethod("POST");

                long length = imgFile.getLength();
                if(length == AssetFileDescriptor.UNKNOWN_LENGTH || length >= Integer.MAX_VALUE) {
                    conn.setChunkedStreamingMode(4096);
                } else {
                    conn.setFixedLengthStreamingMode((int) length);
                }
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
                return new JSONObject(json);
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
            dismissProgress();
            return;
        }

		try {
            Log.v(TAG, "Begin phase 2");
            obj.put("content", Html.toHtml((Spanned) m_content.getText()));

            if(m_inReplyTo != null) {
				obj.put("inReplyTo", m_inReplyTo);
			}

            if(m_title.getText().length() > 0) {
                obj.put("displayName", m_title.getText().toString());
            }


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

            Address addr = (Address) m_location.getSelectedItem();
            if(addr != null) {
                JSONObject place = LocationServices.buildPlace(addr);
                act.put("location", place);
                if(!obj.has("location")) {
                    obj.put("location", place);
                }
            }

            act.put("object", obj);

            if(m_type == TYPE_IMAGE) {
                JSONObject updateAct = new JSONObject();
                for(Iterator<String> i = act.keys(); i.hasNext();) {
                    String k = i.next();
                    updateAct.put(k, act.get(k));
                }

                updateAct.put("verb", "update");

                UpdateCallback uc = new UpdateCallback();
                uc.m_originalActivity = act;
                PostTask t = new PostTask(this, uc);
                t.execute(updateAct.toString());
            } else {
                PostTask t = new PostTask(this, new PostCallback());
                t.execute(act.toString());
            }
        } catch(Exception ex) {
            Toast.makeText(this, "Error creating post: " + ex.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            dismissProgress();
        }
    }

    private class UpdateCallback implements PostTask.Callback {
        public JSONObject m_originalActivity;

        @Override
        public void call(JSONObject activity) {
            if(activity != null) {
                PostTask t = new PostTask(PostActivity.this, new PostCallback());
                t.execute(m_originalActivity.toString());
            } else {
                dismissProgress();
                Toast.makeText(PostActivity.this, "Error updating object", Toast.LENGTH_SHORT).show();
            }
        }
    }

	private class PostCallback implements PostTask.Callback {
		@Override
		public void call(JSONObject obj) {
            dismissProgress();

			if(obj != null) {
                Toast.makeText(PostActivity.this, "Posted", Toast.LENGTH_SHORT);

                ContentValues cv = new ContentValues();
                cv.put("_json", obj.toString());
                getContentResolver().insert(Uri.parse(PumpContentProvider.OBJECT_URL), cv);
                getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());

				setResult(RESULT_OK);
				finish();
			} else {
				Toast.makeText(PostActivity.this, "Error creating post", Toast.LENGTH_SHORT).show();
			} 
		}
		
	}
}

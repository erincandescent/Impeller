package eu.e43.impeller.activity;

import org.htmlcleaner.CompactHtmlSerializer;
import org.htmlcleaner.ContentNode;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;

import eu.e43.impeller.Constants;
import eu.e43.impeller.LocationServices;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.ogp.MetaElement;
import eu.e43.impeller.ogp.OpenGraph;
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
    private static final int TYPE_VIDEO   = 3;

    // UI widgets
    EditText        m_title;
    ImageView       m_postType;
    ImageView       m_imageView;
	EditText 	    m_content;
    Spinner         m_location;
    CheckBox        m_isPublic;
    ProgressDialog  m_progress;
    LocationAdapter m_locations;

    // Object properties
    int             m_type;             // objectType
    Uri             m_imageUri;         // Image URI
	JSONObject	    m_inReplyTo = null; // inReplyTo
    String          m_embedCode;        // embedCode
    JSONObject      m_videoLink = null; // Video medialink
    // Object properties which the UI may change:
    String          m_proposedTitle;
    String          m_proposedText;
    String          m_proposedHTML;
    String          m_sourceLink;


    protected void onCreateEx(Bundle _) {
        setContentView(R.layout.activity_post);
    }

	@Override
	protected void gotAccount(Account a_, Bundle _) {
        Intent intent = getIntent();
        String type = intent.getType();

        Log.v(TAG, "MIME Type is " + type);
        if(type == null || type.startsWith("text/")) {
            if(ACTION_REPLY.equals(intent.getAction())) {
                m_type = TYPE_COMMENT;
            } else {
                m_type = TYPE_NOTE;
            }
        } else if(type.startsWith("image/")) {
            m_type = TYPE_IMAGE;
            m_imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if(m_imageUri == null) {
                Log.e(TAG, "No image URI?");
                setResult(RESULT_CANCELED);
                finish();
            }
        } else {
            setResult(RESULT_CANCELED);
            finish();
        }

        if(intent.hasExtra(Intent.EXTRA_SUBJECT)) {
            m_proposedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        }

        if(intent.hasExtra(EXTRA_HTML_TEXT)) {
            m_proposedHTML = intent.getStringExtra(EXTRA_HTML_TEXT);
        }

        if(intent.hasExtra(Intent.EXTRA_TEXT)) {
            m_proposedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        }

        if(m_proposedText != null) {
            Log.v(TAG, "Has proposed text... " + m_proposedText);
            try {
                URI uri = new URI(m_proposedText.trim());

                Log.v(TAG, "Proposed text is an URL " + uri);
                if("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                    Log.v(TAG, "Performing discovery on URL " + uri);
                    new DiscoveryTask().execute(uri);
                    return;
                }

            } catch(URISyntaxException ex) {
                // Pass
            }
        }
        // else
        prepareUi();
    }

    private void prepareUi() {
        // Prepare after doing full discovery
        m_title         = (EditText)  findViewById(R.id.title);
        m_imageView     = (ImageView) findViewById(R.id.image);
        m_location      = (Spinner)   findViewById(R.id.location);
        m_content       = (EditText)  findViewById(R.id.content);
        m_isPublic      = (CheckBox)  findViewById(R.id.isPublic);
        m_postType      = (ImageView) findViewById(R.id.postType);

        switch(m_type) {
            case TYPE_NOTE:
            case TYPE_COMMENT:
                m_postType.setImageResource(R.drawable.ic_note_dark);
                break;

            case TYPE_IMAGE:
                m_postType.setImageResource(R.drawable.ic_picture_dark);
                break;

            case TYPE_VIDEO:
                m_postType.setImageResource(R.drawable.ic_video_dark);
                break;
        }

        if(m_imageUri != null) {
            m_imageView.setVisibility(View.VISIBLE);
            if(isImageLink()) {
                getImageLoader().setImage(m_imageView, m_imageUri.toString());
            } else {
                SetupImageTask t = new SetupImageTask();
                t.execute();
            }
        }

        if(m_proposedTitle != null) {
            m_title.setText(m_proposedTitle);
        }

        if(m_proposedHTML != null) {
            PumpHtml.setFromHtml(this, m_content, m_proposedHTML);
        } else if(m_proposedText != null) {
            m_content.setText(m_proposedText);
        }

        if(ACTION_REPLY.equals(getIntent().getAction())) {
            setTitle(R.string.title_activity_post_reply);
            m_title.setVisibility(View.GONE);
            m_title.setText("");

            try {
                m_inReplyTo = new JSONObject(getIntent().getStringExtra("inReplyTo"));
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

    private boolean isImageLink() {
        return m_imageUri.getScheme().equals("http") || m_imageUri.getScheme().equals("https");
    }

    private class DiscoveryTask extends AsyncTask<URI, Void, Void> {
        ProgressDialog m_discoveryProgress;

        @Override
        protected void onPreExecute() {
            m_discoveryProgress = ProgressDialog.show(PostActivity.this,
                    getString(R.string.just_a_moment),
                    getString(R.string.getting_information), true);
        }

        @Override
        protected Void doInBackground(URI... params) {
            try {
                OpenGraph og = new OpenGraph(params[0].toString(), true);

                for(MetaElement elem : og.getProperties()) {
                    Log.d(TAG, elem.getNamespace().getPrefix() + ":" + elem.getProperty() + "=" + elem.getContent());
                }

                String image = og.getContent("image");
                if(m_imageUri == null && image != null) {
                    if(og.getContent("image:secure_url") != null) {
                        m_imageUri = Uri.parse(og.getContent("image:secure_url"));
                        Log.i(TAG, "Has og:image:secure_url " + m_imageUri);
                    } else {
                        m_imageUri = Uri.parse(image);
                        Log.i(TAG, "Has og:image " + m_imageUri);
                    }
                } else Log.i(TAG, "No og:image");

                if(og.getContent("url") != null) {
                    m_sourceLink = og.getContent("url");
                    Log.i(TAG, "og:url " + m_sourceLink);
                } else {
                    m_sourceLink = params[0].toString();
                }

                if(og.getContent("title") != null)
                    m_proposedTitle = og.getContent("title");

                if(m_proposedText == null && og.getContent("description") != null)
                    m_proposedText = og.getContent("description");

                String ogType = og.getContent("type");

                if(ogType != null && ogType.equals("video")) {
                    String videoUrl = og.getContent("video");
                    if(videoUrl != null) {
                        if(og.getContent("video:secure_url") != null)
                            videoUrl = og.getContent("video:secure_url");

                        String videoType = og.getContent("video:type");

                        if(videoUrl == null) {
                            // pass
                        } else {
                            m_type = TYPE_VIDEO;

                            HtmlCleaner cleaner = new HtmlCleaner();
                            TagNode embedCode;

                            Integer width  = null;
                            Integer height = null;

                            if(og.getContent("video:width") != null)
                                width = Integer.parseInt(og.getContent("video:width"));

                            if(og.getContent("video:height") != null)
                                height = Integer.parseInt(og.getContent("video:height"));

                            if(videoType != null && !videoType.startsWith("video/")) {
                                embedCode = new TagNode("object");
                                embedCode.addAttribute("data", videoUrl);
                                embedCode.addAttribute("type", videoType);

                                // For Flash on downlevel browsers
                                TagNode movieParam = new TagNode("param");
                                movieParam.addAttribute("movie", videoUrl);
                                embedCode.addChild(movieParam);
                            } else { // video/*
                                embedCode = new TagNode("video");
                                embedCode.addAttribute("src", videoUrl);
                                if(videoType != null)
                                    embedCode.addAttribute("type", videoType);
                            }

                            if(width != null)
                                embedCode.addAttribute("width", width.toString());

                            if(height != null)
                                embedCode.addAttribute("height", height.toString());

                            // Image and link fallback!
                            TagNode linkNode = new TagNode("a");
                            linkNode.addAttribute("href", m_sourceLink);
                            if(isImageLink()) {
                                TagNode imgNode = new TagNode("img");
                                imgNode.addAttribute("src", m_imageUri.toString());
                                imgNode.addAttribute("alt", "Watch video at source");
                                linkNode.addChild(imgNode);
                            } else {
                                linkNode.addChild(new ContentNode("Watch video at source"));
                            }

                            if(og.getContent(OpenGraph.TWITTER_NS, "player") != null) {
                                // iframe fallback
                                TagNode iframeNode = new TagNode("iframe");
                                iframeNode.addAttribute("src",    og.getContent(OpenGraph.TWITTER_NS, "player"));
                                if(width != null)
                                    iframeNode.addAttribute("width", width.toString());
                                if(height != null)
                                    iframeNode.addAttribute("height", height.toString());
                                iframeNode.addChild(linkNode);
                                embedCode.addChild(iframeNode);
                            } else {
                                embedCode.addChild(linkNode);
                            }

                            TagNode dummyNode = new TagNode("div");
                            dummyNode.addChild(embedCode);

                            m_embedCode = cleaner.getInnerHtml(dummyNode);
                            Log.v(TAG, "Video embed code: " + m_embedCode);

                            m_videoLink = new JSONObject();
                            m_videoLink.put("url", videoUrl);
                            if(videoType != null)
                                m_videoLink.put("type",   videoType);
                            if(width != null)
                                m_videoLink.put("width", width);
                            if(height != null)
                                m_videoLink.put("height", height);
                        }
                    }
                }

            } catch (Exception e) {
                Log.w(TAG, "Error doing discovery", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            m_discoveryProgress.dismiss();
            prepareUi();
        }
    }

    private class SetupImageTask extends AsyncTask<Void, Void, Bitmap> {

        @Override
        protected Bitmap doInBackground(Void... voids) {
            try {
                InputStream is = getContentResolver().openInputStream(m_imageUri);
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

        m_progress = ProgressDialog.show(this,
                getString(R.string.posting_title),
                getString(R.string.status_submitting_post));
        m_progress.setIndeterminate(true);

        try {
            switch(m_type) {
                case TYPE_IMAGE:
                    if(!isImageLink()) {
                        PostImageTask t = new PostImageTask();
                        t.execute(m_imageUri);
                        return;
                    }

                    obj.put("objectType", "image");

                case TYPE_NOTE:
                    obj.put("objectType", "note");
                    break;
                case TYPE_COMMENT:
                    obj.put("objectType", "comment");
                    break;

                case TYPE_VIDEO:
                    obj.put("objectType", "video");
                    if(m_videoLink != null)
                        obj.put("stream", m_videoLink);
                    if(m_embedCode != null)
                        obj.put("embedCode", m_embedCode);
                    break;

                default:
                    throw new RuntimeException("Bad type");

            }

            if(m_imageUri != null) {
                JSONObject image = new JSONObject();
                image.put("url", m_imageUri);
                obj.put("image", image);
            }

            postPhase2(obj);
        } catch(Exception ex) {
            Log.e(TAG, "Error creating object", ex);
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        }
    }

    class PostImageTask extends AsyncTask<Object, Long, JSONObject> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            m_progress.setMessage(getString(R.string.status_uploading_image));
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            if(values[0] != AssetFileDescriptor.UNKNOWN_LENGTH) {
                m_progress.setIndeterminate(false);
                m_progress.setMax(values[0].intValue());
                m_progress.setProgress(values[1].intValue());
            } else {
                m_progress.setIndeterminate(true);
            }
        }

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
                long progress = 0;
                int read = is.read(buf);
                while(read > 0) {
                    progress += read;
                    publishProgress(length, progress);
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
            m_progress.setIndeterminate(true);
            postPhase2(obj);
        }
    }

	private void postPhase2(JSONObject obj) {
        m_progress.setMessage(getString(R.string.status_submitting_post));
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
            } else if(m_inReplyTo == null) {
                // To work around Pump.io bug 885, explicitly send to followers if not a reply
                JSONObject followers = new JSONObject();
                String followersUri = Utils.getUserUri(this, m_account, "followers").toString();
                followers.put("id", followersUri);
                followers.put("url", followersUri);
                followers.put("objectType", "collection");
                JSONArray to = new JSONArray();
                to.put(followers);
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

package eu.e43.impeller.fragment;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URI;
import java.util.ArrayList;

import eu.e43.impeller.ImpellerApplication;
import eu.e43.impeller.api.Constants;
import eu.e43.impeller.api.Content;
import eu.e43.impeller.activity.PostActivity;
import eu.e43.impeller.uikit.AvatarView;
import eu.e43.impeller.uikit.BrowserChrome;
import eu.e43.impeller.uikit.CommentAdapter;
import eu.e43.impeller.uikit.CustomTypefaceSpan;
import eu.e43.impeller.uikit.ImageLoader;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.MainActivity;
import eu.e43.impeller.content.ContentUpdateReceiver;
import eu.e43.impeller.activity.ActivityWithAccount;
import eu.e43.impeller.uikit.InReplyToView;
import eu.e43.impeller.uikit.LocationView;
import eu.e43.impeller.uikit.OverlayController;
import eu.e43.impeller.uikit.TouchImageView;

public class StandardObjectFragment
        extends ObjectFragment
        implements View.OnClickListener,
        ListView.OnItemClickListener,
        Toolbar.OnMenuItemClickListener {
	private static final String TAG = "StandardObjectFragment";
    private static final int ACTIVITY_SELECT_REPLY_PHOTO = 100;
    private static final int ACTIVITY_REPLY_POSTED       = 101;
    private Context             m_appContext;
    private Account             m_account;
	private CommentAdapter      m_commentAdapter;

    // Contains all WebViews, so they may be appropriately paused/resumed
    private ArrayList<WebView>  m_webViews    = new ArrayList<WebView>();
    private Toolbar m_toolbar = null;

    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    private ImageLoader getImageLoader() {
        return getMainActivity().getImageLoader();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

	@Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        m_appContext = getActivity().getApplicationContext();

        if(savedInstanceState != null) {
            m_account = savedInstanceState.getParcelable("account");
            String replyText = savedInstanceState.getString("replyText");
            if(replyText!=null)
            {
                View root = getView();
                ListView lv = (ListView) root.findViewById(android.R.id.list);
                EditText editor = (EditText) lv.findViewById(R.id.replyText);
                editor.setText(replyText);
            }
        } else {
            m_account = getMainActivity().getAccount();
        }
	}

    @Override
    public void onPause() {
        super.onPause();
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return;

        for(WebView wv : m_webViews)
            wv.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return;

        for(WebView wv : m_webViews)
            wv.onResume();
    }

    private View createImmersiveHeader(LayoutInflater inflater,
                                       JSONObject obj,
                                       JSONObject image,
                                       boolean video,
                                       ListView lv) {
        View header = inflater.inflate(R.layout.view_image_header, null);

        ImageView iv = (ImageView) header.findViewById(R.id.image);
        TextView  tv = (TextView)  header.findViewById(R.id.title);
        ImageView vp = (ImageView) header.findViewById(R.id.video_prompt);
        Toolbar   tc = (Toolbar)   header.findViewById(R.id.title_container);
        tc.setNavigationIcon(R.drawable.ic_empty_24dp);

        getImageLoader().setImage(iv, Utils.getImageUrl(getMainActivity(), image));

        String title = obj.optString("displayName", null);
        if(title != null && title.length() > 0) {
            tv.setText(title);
            tv.setTypeface(ImpellerApplication.serif);
        } else {
            tv.setVisibility(View.GONE);
        }

        if(video)
            vp.setVisibility(View.VISIBLE);

        iv.setOnClickListener(this);

        lv.setOnScrollListener(new ImmersiveScrollListener(header, tv));

        return header;
    }

    class ImmersiveScrollListener implements ListView.OnScrollListener {
        private final ViewGroup  m_header;
        private final TextView   m_title;
        private final Toolbar    m_titleContainer;
        private boolean          m_scrolled = false;

        ImmersiveScrollListener(View header, TextView title) {
            m_header = (ViewGroup) header;
            m_title  = title;
            m_titleContainer = (Toolbar) m_header.findViewById(R.id.title_container);
        }

        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {}

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            int bottom = 0;
            if(firstVisibleItem == 0) {
                // Header on screen
                bottom = m_header.getBottom();
            }

            if(bottom <= m_toolbar.getHeight()) {
                if(m_title.getParent() == m_titleContainer) {
                    m_titleContainer.removeView(m_title);
                    m_toolbar.addView(m_title);
                    //m_title.setVisibility(View.INVISIBLE);
                }

                float mix = ((float) bottom) / m_toolbar.getHeight();
                int primary = getResources().getColor(R.color.im_primary);
                int col = Utils.mixColours(0x60000000, primary, mix);

                m_toolbar.setBackgroundColor(col);
                m_scrolled = true;
            } else if(m_scrolled) {
                m_toolbar.setBackgroundResource(R.drawable.scrim_top);
                m_scrolled = false;

                if(m_title.getParent() == m_toolbar) {
                    m_toolbar.removeView(m_title);
                    m_titleContainer.addView(m_title);
                }
            }
        }
    }

    /* Creates the video player for HTML videos */
    private View createHTMLVideoPlayer(String url, JSONObject obj, JSONObject stream) {
        WebView wv = new WebView(getActivity());
        wv.setHorizontalScrollBarEnabled(false);
        wv.setWebChromeClient(new BrowserChrome(getMainActivity()));
        wv.getSettings().setLoadWithOverviewMode(true);
        wv.getSettings().setUseWideViewPort(true);
        wv.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
        wv.getSettings().setJavaScriptEnabled(true);

        Integer width = null;
        if(stream != null && stream.has("width"))
            width = stream.optInt("width");

        String html = Utils.formatHtmlFragment(obj.optString("embedCode", ""), width);
        Log.d(TAG, "HTML is " + html);
        wv.loadDataWithBaseURL(url, html, "text/html", "utf-8", null);
        m_webViews.add(wv);
        return wv;
    }

    /* We have two heading modes:
     * - Standard  - for normal content. Title goes in toolbar; a spacer goes behind the toolbar,
     *               everything is normal and bog standard
     * - Immersive - for content with a video or image. The image or video preview goes behind the
     *               action bar, which is made transparent
     * This function sets up those headings.
     */
    private void createHeader(LayoutInflater inflater, ListView lv, JSONObject obj) {
        boolean immersive = false;
        boolean video     = false;
        String url        = obj.optString("url", "about:blank");
        JSONObject image  = obj.optJSONObject("image");
        JSONObject stream = null;

        if(obj.has("fullImage"))
            image = obj.optJSONObject("fullImage");

        if(image != null)
            immersive = true;

        if(obj.optString("objectType", "post").equals("video")) {
            stream = obj.optJSONObject("stream");

            // Only try VideoView where we have a video/ mediatype
            if(stream != null && stream.optString("type").startsWith("video/")) {
                immersive = true;
                video = true;
            } else if(obj.has("embedCode")) {
                video = true;
            }
        }

        if(immersive) {
            m_toolbar.setBackgroundResource(R.drawable.scrim_top);
            lv.addHeaderView(createImmersiveHeader(inflater, obj, image, video, lv));
        } else {
            m_toolbar.setBackgroundResource(R.color.im_primary);
            m_toolbar.setTitle(obj.optString("displayName", null));
            lv.addHeaderView(inflater.inflate(R.layout.view_object_title, null));
        }

        JSONObject inReplyTo = obj.optJSONObject("inReplyTo");
        if(inReplyTo != null) {
            InReplyToView inReplyToView = new InReplyToView(getMainActivity(), inReplyTo);
            lv.addHeaderView(inReplyToView);
        }

        ViewGroup header = (ViewGroup) inflater.inflate(R.layout.view_object_header, null);
        lv.addHeaderView(header);

        if(video && !immersive) {
            lv.addHeaderView(createHTMLVideoPlayer(url, obj, stream));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root    = inflater.inflate(R.layout.fragment_object_standard, null);
        ListView lv  = (ListView) root.findViewById(android.R.id.list);
        m_toolbar    = (Toolbar)  root.findViewById(R.id.objectToolbar);
        m_toolbar.inflateMenu(R.menu.object);
        m_toolbar.setOnMenuItemClickListener(this);
        m_toolbar.setNavigationIcon(R.drawable.ic_arrow_back_white_24dp);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            for(WebView wv : m_webViews) {
                wv.onPause();
            }
        }

        JSONObject obj = getObject();
        createHeader(inflater, lv, obj);

        JSONObject pump_io = obj.optJSONObject("pump_io");
        JSONObject image = obj.optJSONObject("fullImage");
        if(image == null && pump_io != null) {
            image = pump_io.optJSONObject("fullImage");
        }

        if(image == null) {
            image = obj.optJSONObject("image");
        }

        String url  = obj.optString("url", "about:blank");

        if(obj.has("content")) {
            ViewGroup contentViews = (ViewGroup) inflater.inflate(R.layout.view_object_content, null);
            WebView wv = (WebView) contentViews.findViewById(R.id.webView);

            String data = Utils.formatHtmlFragment(obj.optString("content", "No content"), null);
            wv.loadDataWithBaseURL(url, data, "text/html", "utf-8", null);
            wv.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
            lv.addHeaderView(contentViews);
            m_webViews.add(wv);
        }

        if(obj.has("location")) {
            JSONObject location = obj.optJSONObject("location");
            if(location != null) {
                LocationView locView = new LocationView(getMainActivity(), location);
                lv.addHeaderView(locView);
            }
        }

        m_commentAdapter = new CommentAdapter(this, 0, m_intId);
        lv.setAdapter(m_commentAdapter);
        lv.setOnItemClickListener(this);
        registerForContextMenu(lv);

        ViewGroup footer = (ViewGroup) inflater.inflate(R.layout.view_object_reply, null);
        lv.addFooterView(footer);

        objectUpdated(obj, root);

        getActivity().sendOrderedBroadcast(new Intent(
                ContentUpdateReceiver.UPDATE_REPLIES, Uri.parse(m_id),
                getActivity(), ContentUpdateReceiver.class
        ).putExtra(Constants.EXTRA_ACCOUNT, getMainActivity().getAccount()), null);

        return root;
    }

    @Override
    public void onSaveInstanceState (Bundle outState)
    {
        View root = getView();
        if(root == null) return;
        ListView lv = (ListView) root.findViewById(android.R.id.list);
        EditText editor = (EditText) lv.findViewById(R.id.replyText);
        outState.putString("replyText", editor.getText().toString());
    }

    @Override
    public void objectUpdated(JSONObject obj) {
        View root = getView();
        if(root == null) return;
        objectUpdated(obj, root);
    }

    private void objectUpdated(JSONObject obj, View root) {
        AvatarView      authorIcon      = (AvatarView)   root.findViewById(R.id.authorAvatar);
        TextView        authorName      = (TextView)     root.findViewById(R.id.authorName);
        TextView        objectDetails   = (TextView)     root.findViewById(R.id.objectDetails);
        ImageButton     postReplyButton = (ImageButton)  root.findViewById(R.id.postReplyButton);
        Toolbar         toolbar         = (Toolbar)      root.findViewById(R.id.objectToolbar);

        Menu toolMenu = toolbar.getMenu();
        toolMenu.findItem(R.id.action_like).setChecked(obj.optBoolean("liked", false));

        postReplyButton.setOnClickListener(this);
        authorIcon.setOnClickListener(this);


        JSONObject author = obj.optJSONObject("author");
        if(author != null) {
            authorName.setText(author.optString("displayName"));
            authorName.setVisibility(View.VISIBLE);
            getImageLoader().setImage(authorIcon, Utils.getImageUrl(getMainActivity(),
                    author.optJSONObject("image")));
        } else authorName.setVisibility(View.GONE);

        String date = Utils.humanDate(obj.optString("published"));
        objectDetails.setText(date);

        updateLikeState();

        Log.i(TAG, "Finished showing object");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case ACTIVITY_SELECT_REPLY_PHOTO:
                if(resultCode == Activity.RESULT_OK){
                    Uri selectedImage = data.getData();
                    Intent postIntent = new Intent(getActivity(), PostActivity.class);
                    postIntent.setAction(PostActivity.ACTION_REPLY);
                    postIntent.setType("image/*");
                    postIntent.putExtra(Intent.EXTRA_STREAM, selectedImage);
                    postIntent.putExtra(Constants.EXTRA_ACCOUNT, m_account);
                    postIntent.putExtra(Constants.EXTRA_IN_REPLY_TO, getObject().toString());
                    startActivity(postIntent);
                }
                return;

            case ACTIVITY_REPLY_POSTED:
                break;

            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }
	// At present context menus are only shown for comments
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo_) {
		super.onCreateContextMenu(menu, v, menuInfo_);
		
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) menuInfo_;

        View root = getView();
        ListView lv = (ListView) root.findViewById(android.R.id.list);

		JSONObject comment = (JSONObject) lv.getItemAtPosition(menuInfo.position);
		if(comment == null) return;
		
		JSONObject author = comment.optJSONObject("author");
		String title = "Comment";
		if(author != null && author.has("displayName")) {
			title = "Comment by " + author.optString("displayName");
		}
		
		menu.setHeaderTitle(title);
		getActivity().getMenuInflater().inflate(R.menu.comment, menu);
		if(comment.optBoolean("liked", false)) {
			menu.findItem(R.id.action_like).setTitle(R.string.action_unlike);
		}
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
        View root = getView();
        ListView lv = (ListView) root.findViewById(android.R.id.list);

		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		JSONObject comment = (JSONObject) lv.getItemAtPosition(menuInfo.position);
		
		
		switch(item.getItemId()) {
		case R.id.action_like:
			new DoLike(comment, !comment.optBoolean("liked", false));
			return true;
			
		case R.id.action_showAuthor:
			JSONObject author = comment.optJSONObject("author");
			if(author == null)
				return true;
			
			//Intent authorIntent = new Intent(
            //        StandardObjectFragment.ACTION,  Uri.parse(author.optString("id")),
            //        getActivity(), StandardObjectFragment.class);
			//authorIntent.putExtra(Constants.EXTRA_ACCOUNT, m_account);
			//startActivity(authorIntent);
			return true;
		
		default:
			return super.onContextItemSelected(item);
		}
	}

	private void updateLikeState() {
        View rootView = getView();
        if(rootView == null)
            return;

        Toolbar tbr = (Toolbar) rootView.findViewById(R.id.objectToolbar);
        Menu theMenu = tbr.getMenu();

        MenuItem likeItem = theMenu.findItem(R.id.action_like);

        if(getObject() == null) return;

        boolean liked = getObject().optBoolean("liked", false);
        likeItem.setChecked(liked);

        if(getObject().optString("url") == null) {
            theMenu.findItem(R.id.action_viewInBrowser).setVisible(false);
        }
	}

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.postReplyButton: {
                View root = getView();
                ListView lv = (ListView) root.findViewById(android.R.id.list);

                EditText editor = (EditText) lv.findViewById(R.id.replyText);
                editor.clearComposingText();

                if (editor.getText().length() == 0) {
                    break;
                }

                view.setEnabled(false);
                editor.setEnabled(false);
                JSONObject comment = new JSONObject();
                try {
                    comment.put("objectType", "comment");
                    comment.put("inReplyTo", getObject());
                    comment.put("content", Html.toHtml(editor.getText()));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                new PostReply(comment);
                break;
            }

            case R.id.image: {
                if(getObject().has("stream")) {
                    // Video
                    JSONObject stream = getObject().optJSONObject("stream");
                    if (stream == null) return;
                    String url = stream.optString("url");
                    if (url == null) return;

                    Intent i = new Intent(Intent.ACTION_VIEW);
                    i.setDataAndType(Uri.parse(url), stream.optString("type", "video/*"));
                    try {
                        startActivity(i);
                    } catch (ActivityNotFoundException ex) {
                        Toast.makeText(getActivity(), "Unable to launch video player", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // Image
                    TouchImageView img = new TouchImageView(getActivity());
                    img.setImageDrawable(((ImageView) view).getDrawable());
                    getMainActivity().showOverlay(new OverlayController() {
                        @Override
                        public void onHidden() {
                        }

                        @Override
                        public void onShown() {

                        }

                        @Override
                        public boolean isImmersive() {
                            return false;
                        }
                    }, img);
                }
                break;
            }

            case R.id.authorAvatar: {
                JSONObject author = getObject().optJSONObject("author");
                if(author != null) {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(author.optString("id")),
                            getActivity(), MainActivity.class);
                    Log.v(TAG, "Showing author" + intent);
                    startActivity(intent);
                }
            }
        }
    }

    // Toolbar actions
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_like: {
                new DoLike(getObject(), !item.isChecked());
                break;
            }

            case R.id.action_replyImage: {
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, ACTIVITY_SELECT_REPLY_PHOTO);
                return true;
            }

            case R.id.action_replyNote: {
                Intent noteIntent = new Intent(getActivity(), PostActivity.class);
                noteIntent.setAction(PostActivity.ACTION_REPLY);
                noteIntent.putExtra(Constants.EXTRA_ACCOUNT, m_account);
                noteIntent.putExtra(Constants.EXTRA_IN_REPLY_TO, getObject().toString());
                startActivity(noteIntent);
                return true;
            }

            case R.id.action_viewInBrowser: {
                String url = getObject().optString("url");
                Uri uri = Uri.parse(url);
                Intent showIntent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(showIntent);
                return true;
            }

            case R.id.action_share: {
                Intent intent = new Intent(Intent.ACTION_SEND);
                JSONObject object = getObject();
                String title = Html.fromHtml(object.optString("displayName")).toString();
                String body  = getObject().optString("content");

                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, title);
                intent.putExtra(Intent.EXTRA_TEXT, Html.fromHtml(body));
                intent.putExtra(PostActivity.EXTRA_HTML_TEXT, body);
                intent.putExtra(Constants.EXTRA_AS_OBJECT_ID, object.optString("id"));
                intent.putExtra(Constants.EXTRA_AS_OBJECT, object.toString());

                FragmentManager fm = getChildFragmentManager();
                FragmentTransaction tx = fm.beginTransaction();
                Fragment old = fm.findFragmentByTag("dialog");
                if(old != null)
                    tx.remove(old);
                tx.addToBackStack(null);
                ShareFragment.newInstance(intent).show(tx, "dialog");

                return true;
            }
        }
        return false;
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        showItemByPosition(position);
    }

    public void showItemByPosition(int position) {
        View root = getView();
        ListView lv = (ListView) root.findViewById(android.R.id.list);

        JSONObject obj = (JSONObject) lv.getItemAtPosition(position);
        if(obj == null) return;

        String url = obj.optString("id");

        if(url != null) {
            getMainActivity().showObjectInMode(MainActivity.Mode.OBJECT, Uri.parse(url));
        }
    }

    public Uri getUri() {
        return (Uri) getArguments().getParcelable("id");
    }

    public String getDisplayName() {
        if(getObject() != null) {
            return getObject().optString("displayName", "Object");
        } else return "Object";
    }

    private class DoLike implements PostTask.Callback {
		private JSONObject m_object;
		
		public DoLike(JSONObject object, boolean state) {
			String action;
            try {
			    m_object = Utils.buildStubObject(object);
			
			    if(state)
				    action = "favorite";
			    else
				    action = "unfavorite";
			
			    JSONObject obj = new JSONObject();

				obj.put("verb", action);
				obj.put("object", object);

                PostTask task = new PostTask((ActivityWithAccount) getActivity(), this);
                task.execute(obj.toString());
			} catch(JSONException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void call(JSONObject act) {
            if(act == null) return;

            try {
                JSONObject theObj = act.getJSONObject("object");
                Log.v(TAG, "Returned verb '" + act.optString("verb") + "'");
                theObj.put("liked", "favorite".equals(act.optString("verb")));
                act.put("object", theObj);

                Log.v(TAG, "Inserting " + act.toString(0));
            } catch(JSONException e) {
                Log.e(TAG, "Swallowing exception", e);
            }

            ContentValues cv = new ContentValues();
            cv.put("_json", act.toString());

            m_appContext.getContentResolver().insert(getMainActivity().getContentUris().activitiesUri, cv);
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

            PostTask task = new PostTask((ActivityWithAccount) getActivity(), this);
            task.execute(obj.toString());
        }

        @Override
        public void call(JSONObject obj) {
            try {
                View root = getView();
                ListView lv = (ListView) root.findViewById(android.R.id.list);

                EditText editor      = (EditText) lv.findViewById(R.id.replyText);
                Button   replyButton = (Button)   lv.findViewById(R.id.postReplyButton);
                if(obj != null)
                    editor.setText("");
                editor.setEnabled(true);
                replyButton.setEnabled(true);
            } catch(IllegalStateException ex) {
                // Content View not yet created
                // Pass
            }

            if(obj != null) {
                ContentValues cv = new ContentValues();
                cv.put("_json", obj.toString());
                m_appContext.getContentResolver().insert(getMainActivity().getContentUris().objectsUri, cv);

                m_appContext.getContentResolver().requestSync(
                    m_account, Content.AUTHORITY, new Bundle());
            } else {
                Toast.makeText(m_appContext, "Error posting reply", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

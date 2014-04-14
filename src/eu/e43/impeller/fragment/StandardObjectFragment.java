package eu.e43.impeller.fragment;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.support.v7.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;

import eu.e43.impeller.activity.PostActivity;
import eu.e43.impeller.uikit.AvatarView;
import eu.e43.impeller.uikit.BrowserChrome;
import eu.e43.impeller.uikit.CommentAdapter;
import eu.e43.impeller.uikit.ImageLoader;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.MainActivity;
import eu.e43.impeller.content.ContentUpdateReceiver;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.activity.ActivityWithAccount;
import eu.e43.impeller.uikit.InReplyToView;
import eu.e43.impeller.uikit.LocationView;
import eu.e43.impeller.uikit.OverlayController;
import eu.e43.impeller.uikit.TouchImageView;

public class StandardObjectFragment extends ObjectFragment implements View.OnClickListener, ListView.OnItemClickListener, PopupMenu.OnMenuItemClickListener {
	private static final String TAG = "StandardObjectFragment";
    private static final int ACTIVITY_SELECT_REPLY_PHOTO = 100;
    private static final int ACTIVITY_REPLY_POSTED       = 101;
    private Context             m_appContext;
    private Account             m_account;
	private CommentAdapter      m_commentAdapter;

    // Contains all WebViews, so they may be appropriately paused/resumed
    private ArrayList<WebView>  m_webViews    = new ArrayList<WebView>();

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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ListView lv = (ListView) inflater.inflate(R.layout.fragment_object_standard, null);

        for(WebView wv : m_webViews) {
            wv.onPause();
        }

        JSONObject obj = getObject();

        ViewGroup header = (ViewGroup) inflater.inflate(R.layout.view_object_header, null);
        ViewGroup footer = (ViewGroup) inflater.inflate(R.layout.view_object_reply, null);

        JSONObject inReplyTo = obj.optJSONObject("inReplyTo");
        if(inReplyTo != null) {
            InReplyToView inReplyToView = new InReplyToView(getMainActivity(), inReplyTo);
            lv.addHeaderView(inReplyToView);
            header.setBackgroundResource(R.drawable.card_middle_bg);
        }
        lv.addHeaderView(header);
        lv.addFooterView(footer);

        JSONObject pump_io = obj.optJSONObject("pump_io");
        JSONObject image = obj.optJSONObject("fullImage");
        if(image == null && pump_io != null) {
            image = pump_io.optJSONObject("fullImage");
        }

        if(image == null) {
            image = obj.optJSONObject("image");
        }

        String url  = obj.optString("url", "about:blank");

        if(obj.optString("objectType", "post").equals("video")) {
            JSONObject stream = obj.optJSONObject("stream");

            // Only try VideoView where we have a video/ mediatype
            if(stream != null && (stream.optString("type").startsWith("video/") || !obj.has("embedCode"))) {
                FrameLayout ly = (FrameLayout) inflater.inflate(R.layout.view_object_video_preview, null);
                ImageView thumb = (ImageView) ly.findViewById(R.id.video_thumb);
                if(image != null) {
                    getImageLoader().setImage(thumb, Utils.getImageUrl(image));
                }
                ly.setOnClickListener(this);
                lv.addHeaderView(ly);
            } else if(obj.has("embedCode")) {
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
                lv.addHeaderView(wv);
                m_webViews.add(wv);
            }
        } else if(image != null) {
            ImageView iv = new ImageView(getActivity());
            iv.setId(R.id.image);
            iv.setBackgroundResource(R.drawable.card_middle_bg);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Utils.dip(getActivity(), 256));
            getImageLoader().setImage(iv, Utils.getImageUrl(image));
            iv.setOnClickListener(this);
            lv.addHeaderView(iv);
        }

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

        m_commentAdapter = new CommentAdapter(this, 0, m_id);
        lv.setAdapter(m_commentAdapter);
        lv.setOnItemClickListener(this);
        registerForContextMenu(lv);

        objectUpdated(obj, lv);

        getActivity().sendOrderedBroadcast(new Intent(
                ContentUpdateReceiver.UPDATE_REPLIES, Uri.parse(m_id),
                getActivity(), ContentUpdateReceiver.class
        ).putExtra("account", getMainActivity().getAccount()), null);

        return lv;
    }

    @Override
    public void objectUpdated(JSONObject obj) {
        View root = getView();
        if(root == null) return;
        objectUpdated(obj, root);
    }

    private void objectUpdated(JSONObject obj, View root) {
        AvatarView      authorIcon      = (AvatarView)   root.findViewById(R.id.actorImage);
        TextView        authorName      = (TextView)     root.findViewById(R.id.actorName);
        TextView        dateView        = (TextView)     root.findViewById(R.id.objectDate);
        TextView        titleView       = (TextView)     root.findViewById(R.id.objectTitle);
        ToggleButton    likeButton      = (ToggleButton) root.findViewById(R.id.likeButton);
        ImageButton     replyButton     = (ImageButton)  root.findViewById(R.id.replyButton);
        Button          postReplyButton = (Button)       root.findViewById(R.id.postReplyButton);

        likeButton.setChecked(obj.optBoolean("liked", false));

        likeButton.setOnClickListener(this);
        replyButton.setOnClickListener(this);
        postReplyButton.setOnClickListener(this);

        String title = obj.optString("displayName", null);
        if(title != null) {
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(title);
        } else titleView.setVisibility(View.GONE);

        JSONObject author = obj.optJSONObject("author");
        if(author != null) {
            authorName.setText(author.optString("displayName"));
            JSONObject img = author.optJSONObject("image");
            if(img != null) {
                getImageLoader().setImage(authorIcon, Utils.getImageUrl(img));
            }
        } else {
            authorName.setText("No author.");
        }
        dateView.setText(Utils.humanDate(obj.optString("published")));

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
                    postIntent.putExtra("account", m_account);
                    postIntent.putExtra("inReplyTo", getObject().toString());
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
			//authorIntent.putExtra("account", m_account);
			//authorIntent.putExtra("proxyURL", Utils.getProxyUrl(author));
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

        ToggleButton likeButton = (ToggleButton) rootView.findViewById(R.id.likeButton);
        if(likeButton == null)
            return;

        if(getObject() == null) return;

        boolean liked = getObject().optBoolean("liked", false);
        likeButton.setChecked(liked);
	}

    @Override
    public void onClick(View view) {
        switch(view.getId()) {
            case R.id.likeButton: {
                new DoLike(getObject(), ((ToggleButton)view).isChecked());
                break;
            }

            case R.id.replyButton: {
                PopupMenu mnu = new PopupMenu(getActivity(), view);
                mnu.inflate(R.menu.reply);
                mnu.setOnMenuItemClickListener(this);
                mnu.show();
                break;
            }

            case R.id.postReplyButton:
                View root = getView();
                ListView lv = (ListView) root.findViewById(android.R.id.list);

                EditText editor = (EditText) lv.findViewById(R.id.replyText);
                editor.clearComposingText();

                if(editor.getText().length() == 0) {
                    break;
                }

                view.setEnabled(false);
                editor.setEnabled(false);
                JSONObject comment = new JSONObject();
                try {
                    comment.put("objectType", "comment");
                    comment.put("inReplyTo", getObject());
                    comment.put("content", Html.toHtml(editor.getText()));
                } catch(JSONException e) {
                    throw new RuntimeException(e);
                }

                new PostReply(comment);
                break;

            case R.id.image:
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
                break;

            case R.id.video_preview:
                JSONObject stream = getObject().optJSONObject("stream");
                if(stream == null) return;
                String url = stream.optString("url");
                if(url == null) return;

                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(Uri.parse(url), stream.optString("type", "video/*"));
                try {
                    startActivity(i);
                } catch(ActivityNotFoundException ex) {
                    Toast.makeText(getActivity(), "Unable to launch video player", Toast.LENGTH_SHORT).show();
                }
        }
    }

    // For reply menu
    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.action_replyImage:
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, ACTIVITY_SELECT_REPLY_PHOTO);
                return true;

            case R.id.action_replyNote:
                Intent noteIntent = new Intent(getActivity(), PostActivity.class);
                noteIntent.setAction(PostActivity.ACTION_REPLY);
                noteIntent.putExtra("account", m_account);
                noteIntent.putExtra("inReplyTo", getObject().toString());
                startActivity(noteIntent);
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

            m_appContext.getContentResolver().insert(Uri.parse(PumpContentProvider.ACTIVITY_URL), cv);
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
                m_appContext.getContentResolver().insert(Uri.parse(PumpContentProvider.OBJECT_URL), cv);

                m_appContext.getContentResolver().requestSync(
                    m_account, PumpContentProvider.AUTHORITY, new Bundle());
            } else {
                Toast.makeText(m_appContext, "Error posting reply", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

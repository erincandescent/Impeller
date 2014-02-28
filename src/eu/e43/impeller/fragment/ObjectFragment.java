package eu.e43.impeller.fragment;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ListFragment;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
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
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

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

public class ObjectFragment extends ListFragment implements View.OnClickListener {
	private static final String TAG = "ObjectFragment";
	public static final String ACTION = "eu.e43.impeller.SHOW_OBJECT";
    private static final int ACTIVITY_SELECT_REPLY_PHOTO = 100;
    private static final int ACTIVITY_REPLY_POSTED       = 101;
    private Context             m_appContext;
    private Account             m_account;
	private JSONObject			m_object;
	private CommentAdapter      m_commentAdapter;
	private Menu				m_menu;
    private MainActivity.Mode   m_mode;

    // Contains all WebViews, so they may be appropriately paused/resumed
    private ArrayList<WebView>  m_webViews    = new ArrayList<WebView>();

    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    private ImageLoader getImageLoader() {
        return getMainActivity().getImageLoader();
    }

    public MainActivity.Mode getMode() {
        return m_mode;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        m_mode = MainActivity.Mode.valueOf(getArguments().getString("mode"));
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
        for(WebView wv : m_webViews)
            wv.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        for(WebView wv : m_webViews)
            wv.onResume();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getMainActivity().onShowObjectFragment(this);
        return inflater.inflate(R.layout.fragment_object, null);
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        for(WebView wv : m_webViews) {
            wv.onPause();
        }

        ListView lv = getListView();
        LayoutInflater inflater = LayoutInflater.from(getActivity());

        ViewGroup header = (ViewGroup) inflater.inflate(R.layout.view_object_header, null);
        ViewGroup footer = (ViewGroup) inflater.inflate(R.layout.view_object_reply, null);

        lv.addFooterView(footer);

        Uri uri = this.getArguments().getParcelable("id");

        ContentResolver res = getActivity().getContentResolver();
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
                    Toast.makeText(getActivity(), "Bad object in database", Toast.LENGTH_SHORT).show();
                    getFragmentManager().popBackStack();
                }
            }
        } finally {
            c.close();
        }

        if(m_object == null) {
            Toast.makeText(getActivity(), "Error getting object", Toast.LENGTH_SHORT).show();
            getFragmentManager().popBackStack();
        }

        AvatarView authorIcon    = (AvatarView)    header.findViewById(R.id.actorImage);
        TextView   titleView     = (TextView)     header.findViewById(R.id.actorName);
        TextView   dateView      = (TextView)     header.findViewById(R.id.objectDate);
        Button     replyButton   = (Button)       footer.findViewById(R.id.replyButton);
        replyButton.setOnClickListener(this);

        //setTitle(m_object.optString("displayName", "Object"));

        JSONObject inReplyTo = m_object.optJSONObject("inReplyTo");
        if(inReplyTo != null) {
            InReplyToView inReplyToView = new InReplyToView(getMainActivity(), inReplyTo);
            lv.addHeaderView(inReplyToView);
            header.setBackgroundResource(R.drawable.card_middle_bg);
        }
        lv.addHeaderView(header);

        JSONObject author = m_object.optJSONObject("author");
        if(author != null) {
            titleView.setText(author.optString("displayName"));
            JSONObject img = author.optJSONObject("image");
            if(img != null) {
                getImageLoader().setImage(authorIcon, Utils.getImageUrl(img));
            }
        } else {
            titleView.setText("No author.");
        }
        dateView.setText(Utils.humanDate(m_object.optString("published")));

        JSONObject pump_io = m_object.optJSONObject("pump_io");
        JSONObject image = m_object.optJSONObject("fullImage");
        if(image == null && pump_io != null) {
            image = pump_io.optJSONObject("fullImage");
        }

        if(image == null) {
            image = m_object.optJSONObject("image");
        }

        String url  = m_object.optString("url", "about:blank");

        if(m_object.optString("objectType", "post").equals("video")) {
            JSONObject stream = m_object.optJSONObject("stream");

            // Only try VideoView where we have a video/ mediatype
            if(stream != null && (stream.optString("type").startsWith("video/") || !m_object.has("embedCode"))) {
                FrameLayout ly = (FrameLayout) inflater.inflate(R.layout.view_object_video_preview, null);
                ImageView thumb = (ImageView) ly.findViewById(R.id.video_thumb);
                if(image != null) {
                    getImageLoader().setImage(thumb, Utils.getImageUrl(image));
                }
                ly.setOnClickListener(this);
                lv.addHeaderView(ly);
            } else if(m_object.has("embedCode")) {
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

                String html = Utils.formatHtmlFragment(m_object.optString("embedCode", ""), width);
                Log.d(TAG, "HTML is " + html);
                wv.loadDataWithBaseURL(url, html, "text/html", "utf-8", null);
                lv.addHeaderView(wv);
                m_webViews.add(wv);
            }
        } else if(image != null) {
            ImageView iv = new ImageView(getActivity());
            iv.setBackgroundResource(R.drawable.card_middle_bg);
            iv.setAdjustViewBounds(true);
            iv.setMaxHeight(Utils.dip(getActivity(), 256));
            getImageLoader().setImage(iv, Utils.getImageUrl(image));
            lv.addHeaderView(iv);
        }

        if(m_object.has("content")) {
            ViewGroup contentViews = (ViewGroup) inflater.inflate(R.layout.view_object_content, null);
            WebView wv = (WebView) contentViews.findViewById(R.id.webView);

            String data = Utils.formatHtmlFragment(m_object.optString("content", "No content"), null);
            wv.loadDataWithBaseURL(url, data, "text/html", "utf-8", null);
            wv.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
            lv.addHeaderView(contentViews);
            m_webViews.add(wv);
        }

        if(m_object.has("location")) {
            JSONObject location = m_object.optJSONObject("location");
            if(location != null) {
                LocationView locView = new LocationView(getMainActivity(), location);
                lv.addHeaderView(locView);
            }
        }

        getActivity().sendOrderedBroadcast(new Intent(
                ContentUpdateReceiver.UPDATE_REPLIES, Uri.parse(m_object.optString("id")),
                getActivity(), ContentUpdateReceiver.class
        ).putExtra("account", getMainActivity().getAccount()), null);
        updateMenu();

        m_commentAdapter = new CommentAdapter(this, 0, getArguments().getParcelable("id").toString());
        setListAdapter(m_commentAdapter);

        registerForContextMenu(lv);

        Log.i(TAG, "Finished showing object");
    }
    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim)
    {
        Animator anim = AnimatorInflater.loadAnimator(getActivity(),
                enter ? android.R.animator.fade_in : android.R.animator.fade_out);

        if(!enter) anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation)
            {
                getMainActivity().onHideObjectFragment(ObjectFragment.this);
            }
        });

        return anim;
    }
        @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		// Inflate the menu; this adds items to the action bar if it is present.
		inflater.inflate(R.menu.object, menu);
		m_menu = menu;
		updateMenu();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_like:
				new DoLike(m_object);
				return true;

            case R.id.action_replyImage:
                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, ACTIVITY_SELECT_REPLY_PHOTO);

			default:
				return super.onOptionsItemSelected(item);
		}
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
                    postIntent.putExtra("inReplyTo", m_object.toString());
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
		
		JSONObject comment = (JSONObject) getListView().getItemAtPosition(menuInfo.position);
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
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		JSONObject comment = (JSONObject) getListView().getItemAtPosition(menuInfo.position);
		
		
		switch(item.getItemId()) {
		case R.id.action_like:
			new DoLike(comment);
			return true;
			
		case R.id.action_showAuthor:
			JSONObject author = comment.optJSONObject("author");
			if(author == null)
				return true;
			
			//Intent authorIntent = new Intent(
            //        ObjectFragment.ACTION,  Uri.parse(author.optString("id")),
            //        getActivity(), ObjectFragment.class);
			//authorIntent.putExtra("account", m_account);
			//authorIntent.putExtra("proxyURL", Utils.getProxyUrl(author));
			//startActivity(authorIntent);
			return true;
		
		default:
			return super.onContextItemSelected(item);
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
                EditText editor = (EditText) getListView().findViewById(R.id.replyText);
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
            case R.id.video_preview:
                JSONObject stream = m_object.optJSONObject("stream");
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

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        showItemByPosition(position);
    }

    public void showItemByPosition(int position) {
        JSONObject obj = (JSONObject) getListView().getItemAtPosition(position);
        if(obj == null) return;

        String url = obj.optString("id");

        if(url != null) {
            getMainActivity().showObjectInMode(MainActivity.Mode.OBJECT, Uri.parse(url));
        }
    }

    public Uri getUri() {
        return (Uri) getArguments().getParcelable("id");
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
			
			PostTask task = new PostTask((ActivityWithAccount) getActivity(), this);
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

            m_appContext.getContentResolver().requestSync(
                    m_account, PumpContentProvider.AUTHORITY, new Bundle());
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
                EditText editor      = (EditText) getListView().findViewById(R.id.replyText);
                Button   replyButton = (Button)   getListView().findViewById(R.id.replyButton);
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

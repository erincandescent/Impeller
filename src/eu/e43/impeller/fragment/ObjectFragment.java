package eu.e43.impeller.fragment;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.Activity;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
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
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import eu.e43.impeller.activity.PostActivity;
import eu.e43.impeller.uikit.AvatarView;
import eu.e43.impeller.uikit.CommentAdapter;
import eu.e43.impeller.uikit.ImageLoader;
import eu.e43.impeller.PostTask;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.MainActivity;
import eu.e43.impeller.content.ContentUpdateReceiver;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.activity.ActivityWithAccount;
import eu.e43.impeller.uikit.LocationView;

public class ObjectFragment extends ListFragment implements View.OnClickListener {
	private static final String TAG = "ObjectFragment";
	public static final String ACTION = "eu.e43.impeller.SHOW_OBJECT";
    private static final int ACTIVITY_SELECT_REPLY_PHOTO = 100;
    private static final int ACTIVITY_REPLY_POSTED       = 101;
    private Context             m_appContext;
    private Account             m_account;
	private JSONObject			m_object;
	private CommentAdapter m_commentAdapter;
	private Menu				m_menu;

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

        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);

        if(savedInstanceState != null) {
            m_account = savedInstanceState.getParcelable("account");
        } else {
            m_account = getMainActivity().getAccount();
        }

        getMainActivity().onShowObjectFragment(this);
	}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_object, null);
    }

    @Override
    public void onViewCreated (View view, Bundle savedInstanceState) {
        ListView lv = getListView();
        LayoutInflater inflater = LayoutInflater.from(getActivity());

        ViewGroup header = (ViewGroup) inflater.inflate(R.layout.view_object_header, null);
        ViewGroup footer = (ViewGroup) inflater.inflate(R.layout.view_object_reply, null);

        lv.addHeaderView(header);
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

        if(image != null) {
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

            String url  = m_object.optString("url", "about:blank");
            String data = m_object.optString("content", "No content");
            wv.loadDataWithBaseURL(url, data, "text/html", "utf-8", null);
            wv.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
            lv.addHeaderView(contentViews);
        }

        if(m_object.has("location")) {
            JSONObject location = m_object.optJSONObject("location");
            if(location != null) {
                LocationView locView = new LocationView(getMainActivity(), location);
                lv.addHeaderView(locView);
            }
        }

        m_commentAdapter = new CommentAdapter(this, 0, uri.toString());
        setListAdapter(m_commentAdapter);

        registerForContextMenu(lv);

        getActivity().sendOrderedBroadcast(new Intent(
                ContentUpdateReceiver.UPDATE_REPLIES, Uri.parse(m_object.optString("id")),
                getActivity(), ContentUpdateReceiver.class
        ).putExtra("account", getMainActivity().getAccount()), null);
        updateMenu();
        Log.i(TAG, "Finished showing object");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getActivity().getActionBar().setDisplayHomeAsUpEnabled(false);
        getMainActivity().onHideObjectFragment(this);
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

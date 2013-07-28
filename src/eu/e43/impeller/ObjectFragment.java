package eu.e43.impeller;

import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.ListFragment;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NavUtils;
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
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import eu.e43.impeller.content.PumpContentProvider;

public class ObjectFragment extends ListFragment implements View.OnClickListener {
	private static final String TAG = "ObjectFragment";
	public static final String ACTION = "eu.e43.impeller.SHOW_OBJECT";
    private Account             m_account;
	private JSONObject			m_object;
	private CommentAdapter		m_commentAdapter;
	private Menu				m_menu;

    public MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    private int toDIP(int dip) {
        final float density = getResources().getDisplayMetrics().density;
        return (int) (density * dip + 0.5f);
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

        getActivity().getActionBar().setDisplayHomeAsUpEnabled(true);

        if(savedInstanceState != null) {
            m_account = savedInstanceState.getParcelable("account");
        } else {
            m_account = getMainActivity().getAccount();
        }

        getMainActivity().onShowObjectFragment(this);
	}

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ListView lv = new ListView(getActivity());
        lv.setBackgroundColor(0xFFFFFFFF);

        RelativeLayout header = (RelativeLayout) inflater.inflate(R.layout.object_header, null);
        RelativeLayout footer = (RelativeLayout) inflater.inflate(R.layout.object_reply, null);
        int height = toDIP(80);

        header.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT, height));

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
                    return lv;
                }
            }
        } finally {
            c.close();
        }

        if(m_object == null) {
            Toast.makeText(getActivity(), "Error getting object", Toast.LENGTH_SHORT).show();
            getFragmentManager().popBackStack();
            return lv;
        }

        ImageView authorIcon   = (ImageView)    header.findViewById(R.id.actorImage);
        TextView titleView     = (TextView)     header.findViewById(R.id.actorName);
        TextView dateView      = (TextView)     header.findViewById(R.id.objectDate);
        Button   replyButton   = (Button)       footer.findViewById(R.id.replyButton);
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
        dateView.setText(m_object.optString("published"));

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
            iv.setAdjustViewBounds(true);
            getImageLoader().setImage(iv, Utils.getImageUrl(image));
            lv.addHeaderView(iv);
        }

        WebView wv = new WebView(getActivity());
        String url  = m_object.optString("url", "about:blank");
        String data = m_object.optString("content", "No content");
        wv.loadDataWithBaseURL(url, data, "text/html", "utf-8", null);
        wv.getSettings().setLayoutAlgorithm(LayoutAlgorithm.SINGLE_COLUMN);
        lv.addHeaderView(wv);

        JSONObject replies = m_object.optJSONObject("replies");
        m_commentAdapter = new CommentAdapter((ActivityWithAccount) getActivity(), replies, false);
        setListAdapter(m_commentAdapter);

        registerForContextMenu(lv);

        updateMenu();
        Log.i(TAG, "Finished showing object");

        return lv;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
				
			default:
				return super.onOptionsItemSelected(item);
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

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* Will come back later - "rich comments" */
		if(requestCode == 0) {
			// Post comment
			if(resultCode == Activity.RESULT_OK) {
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
			
			getActivity().getContentResolver().requestSync(
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
            EditText editor      = (EditText) getListView().findViewById(R.id.replyText);
            Button   replyButton = (Button)   getListView().findViewById(R.id.replyButton);

            if(obj != null) {
                if(m_commentAdapter != null)
                    m_commentAdapter.updateComments();

                editor.setText("");

                getActivity().getContentResolver().requestSync(
                        m_account, PumpContentProvider.AUTHORITY, new Bundle());
            } else {
                Toast.makeText(getActivity(), "Error posting reply", Toast.LENGTH_SHORT).show();
            }

            editor.setEnabled(true);
            replyButton.setEnabled(true);
        }
    }
}

package eu.e43.impeller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.urlimageviewhelper.UrlImageViewHelper;

public class CommentAdapter extends BaseAdapter {
	private static final String TAG = "CommentAdapter";
	private JSONArray m_comments;
	private Activity  m_ctx;
	
	public CommentAdapter(Activity act, JSONObject collection) {
		m_ctx = act;
		m_comments = collection.optJSONArray("items");
		if(m_comments == null)
			m_comments = new JSONArray();
	}
	
	@Override
	public int getCount() {
		return m_comments.length();
	}

	@Override
	public JSONObject getItem(int idx) {
		Log.v(TAG, "getItem(" + idx + ")");
		try {
			return m_comments.getJSONObject(m_comments.length() - idx - 1);
		} catch(JSONException ex) {
			Log.e(TAG, "Error getting comment", ex);
			return new JSONObject();
		}
	}

	@Override
	public long getItemId(int idx) {
		return idx;
	}

	@Override
	public View getView(int position, View v, ViewGroup parent) {
		Log.v(TAG, "getView(" + position + ")");
		if(v == null) {
			LayoutInflater vi = LayoutInflater.from(m_ctx);
			v = vi.inflate(R.layout.comment_view, null);
		}
		
		JSONObject comment = getItem(position);
		
		ImageView authorImage = (ImageView) v.findViewById(R.id.commentAuthorImage);
		TextView  commentBody = (TextView)  v.findViewById(R.id.commentBody);
		TextView  commentMeta = (TextView)  v.findViewById(R.id.commentMeta);
		
		JSONObject author = comment.optJSONObject("author");
		if(author != null) {
			JSONObject image = author.optJSONObject("image");
			if(image != null)
				UrlImageViewHelper.setUrlDrawable(authorImage, image.optString("url"));
			commentMeta.setText("By " + author.optString("displayName") + " at " + comment.optString("published"));
		}
		PumpHtml.setFromHtml(commentBody, comment.optString("content"));
		
		return v;
	}

}

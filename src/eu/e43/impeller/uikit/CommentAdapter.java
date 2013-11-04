package eu.e43.impeller.uikit;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Fragment;
import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.activity.ActivityWithAccount;

public class CommentAdapter extends BaseAdapter implements LoaderManager.LoaderCallbacks<Cursor> {
	private static final String TAG = "CommentAdapter";
	private Fragment            m_ctx;
    private String              m_objectId;
    private Cursor              m_cursor;
	
	public CommentAdapter(Fragment ctx, int loaderId, String objectId) {
		m_ctx       = ctx;
        m_objectId  = objectId;
        m_cursor    = null;

        LoaderManager lm = m_ctx.getLoaderManager();
        lm.initLoader(loaderId, null, this);
	}

	
	@Override
	public int getCount() {
		return m_cursor != null ? m_cursor.getCount() : 0;
	}

	@Override
	public JSONObject getItem(int idx) {
		Log.v(TAG, "getItem(" + idx + ")");
        try {
            m_cursor.moveToPosition(idx);
            return new JSONObject(m_cursor.getString(0));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

	@Override
	public long getItemId(int idx) {
		return idx;
	}

	@Override
	public View getView(int position, View v, ViewGroup parent) {
		Log.v(TAG, "getView(" + position + ")");

        ActivityWithAccount activity = (ActivityWithAccount) m_ctx.getActivity();
		if(v == null) {
			LayoutInflater vi = LayoutInflater.from(m_ctx.getActivity());
			v = vi.inflate(R.layout.view_comment, null);
		}

        if(m_cursor == null) {
            return v;
        }

        m_cursor.moveToPosition(position);
        JSONObject comment = null;
        try {
            comment = new JSONObject(m_cursor.getString(0));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        AvatarView authorAvatar = (AvatarView) v.findViewById(R.id.commentAuthorAvatar);
		TextView  commentBody   = (TextView)   v.findViewById(R.id.commentBody);
		TextView  commentMeta   = (TextView)   v.findViewById(R.id.commentMeta);
        ImageView       image   = (ImageView)  v.findViewById(R.id.image);
		
		JSONObject author = comment.optJSONObject("author");
		if(author != null) {
			JSONObject imageObj = author.optJSONObject("image");
			if(imageObj != null)
				activity.getImageLoader().setImage(authorAvatar, Utils.getImageUrl(imageObj));

            commentMeta.setText("By " + author.optString("displayName") + " at " +
                    Utils.humanDate(comment.optString("published")));
		}

        JSONObject imageObj = comment.optJSONObject("image");
        if(imageObj != null) {
            image.setVisibility(View.VISIBLE);
            activity.getImageLoader().setImage(image, Utils.getImageUrl(imageObj));
        } else {
            image.setVisibility(View.GONE);
        }

        Utils.updateStatebar(v, m_cursor.getInt(1), m_cursor.getInt(2), m_cursor.getInt(3));
		PumpHtml.setFromHtml(activity, commentBody, comment.optString("content"));
		
		return v;
	}

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return new CursorLoader(m_ctx.getActivity(),
                Uri.parse(PumpContentProvider.OBJECT_URL),
                new String[] { "_json", "replies", "likes", "shares" },
                "inReplyTo=?",
                new String[] { m_objectId },
                "published ASC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        data.setNotificationUri(m_ctx.getActivity().getContentResolver(), Uri.parse(PumpContentProvider.OBJECT_URL));
        notifyDataSetChanged();
        m_cursor = data;
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        notifyDataSetInvalidated();
        m_cursor = null;
    }
}

/* Copyright 2013 Owen Shepherd. A part of Impeller.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.e43.impeller.uikit;

import org.json.JSONException;
import org.json.JSONObject;

import android.database.Cursor;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.HashMap;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;

public class ActivityAdapter extends RecyclerView.Adapter<ActivityAdapter.Holder> {
	private static final String TAG = "ActivityAdapter";

    private static final int FIELD_ROWID   = 0;
    private static final int FIELD_ID      = 1;
    private static final int FIELD_JSON    = 2;
    private static final int FIELD_REPLIES = 3;
    private static final int FIELD_LIKES   = 4;
    private static final int FIELD_SHARES  = 5;

    public static class Holder extends RecyclerView.ViewHolder {
        // Activity header bits
        public RelativeLayout   activityHeader;
        public AvatarView       actorAvatar;
        public TextView         actorName;
        public TextView         activityDetails;

        // Object header bits
        public RelativeLayout   objectHeader;
        public AvatarView       authorAvatar;
        public TextView         title;
        public TextView         authorName;
        public TextView         objectDetails;

        // Image & Content
        public ImageView        objectImage;
        public TextView         content;

        public Holder(View v) {
            super(v);
            activityHeader  = (RelativeLayout) v.findViewById(R.id.activityHeader);
            actorAvatar     = (AvatarView) v.findViewById(R.id.actorAvatar);
            actorName       = (TextView) v.findViewById(R.id.actorName);
            activityDetails = (TextView) v.findViewById(R.id.activityDetails);

            objectHeader    = (RelativeLayout) v.findViewById(R.id.objectHeader);
            authorAvatar    = (AvatarView) v.findViewById(R.id.authorAvatar);
            title           = (TextView) v.findViewById(R.id.objectTitle);
            authorName      = (TextView) v.findViewById(R.id.authorName);
            objectDetails   = (TextView) v.findViewById(R.id.objectDetails);

            objectImage     = (ImageView) v.findViewById(R.id.image);
            content         = (TextView) v.findViewById(R.id.content);
        }

        public Holder(LayoutInflater inf) {
            this(inf.inflate(R.layout.view_activity, null));
        }

        public void bindToActivity(ActivityWithAccount ctx, JSONObject act) {
            ImageLoader ldr = ctx.getImageLoader();

            JSONObject obj    = act.optJSONObject("object");
            JSONObject actor  = act.optJSONObject("actor");
            JSONObject author = obj.optJSONObject("author");

            String verb = act.optString("verb", "post");

            if(verb.equals("post") || verb.equals("share")) {
                // Major item
                if(actor.optString("id").equals(author.optString("id"))) {
                    // Default case - hide activity header
                    activityHeader.setVisibility(View.GONE);
                } else {
                    // Show activity header
                    activityHeader.setVisibility(View.VISIBLE);
                    actorName.setText(actor.optString("displayName",
                            actor.optString("preferredUsername",
                                    actor.optString("id"))));
                    ldr.setImage(actorAvatar, getImage(ctx, actor));

                    String published = act.optString("published");

                    activityDetails.setText(String.format("%s • %s",
                            ActivityUtils.getVerb(ctx, verb),
                            Utils.humanDate(published)));
                }

                // Object header
                if(obj.has("displayName")) {
                    title.setText(obj.optString("displayName"));
                } else {
                    title.setVisibility(View.GONE);
                }

                authorName.setText(author.optString("displayName",
                        author.optString("preferredUsername",
                                author.optString("id"))));
                ldr.setImage(authorAvatar, getImage(ctx, author));

                String published = obj.optString("published");
                objectDetails.setText(Utils.humanDate(published));

                // Content
                if(obj.has("image")) {
                    ldr.setImage(objectImage, getImage(ctx, obj));
                    objectImage.setVisibility(View.VISIBLE);
                } else {
                    objectImage.setVisibility(View.GONE);
                }

                if(obj.has("content")) {
                    PumpHtml.setFromHtml(ctx, content, obj.optString("content"));
                    content.setVisibility(View.VISIBLE);
                } else {
                    content.setVisibility(View.GONE);
                }
            } else {
                // Minor item
                activityHeader.setVisibility(View.GONE);
                objectImage.setVisibility(View.GONE);
                content.setVisibility(View.GONE);
                title.setVisibility(View.GONE);

                authorName.setText(actor.optString("displayName",
                        actor.optString("preferredUsername",
                                actor.optString("id"))));
                ldr.setImage(authorAvatar, getImage(ctx, actor));
                objectDetails.setText(ActivityUtils.localizedDescription(ctx, act));
            }
        }
    }

    Cursor                      m_cursor;
	ActivityWithAccount m_ctx;
    int m_lastScannedObjectPosition;

    HashMap<String, Integer>    m_objectPositions = new HashMap<String, Integer>();
    LruCache<Integer, JSONObject> m_objects = new LruCache<Integer, JSONObject>(32);

	public ActivityAdapter(ActivityWithAccount ctx) {
		m_cursor = null;
		m_ctx  = ctx;
        setHasStableIds(true);
	}

    public int findItemById(String id) {
        Integer pos = m_objectPositions.get(id);
        if(pos == null) {
            if(m_cursor == null) return -1;

            if(!m_cursor.moveToPosition(m_lastScannedObjectPosition))
                return -1;

            do {
                String objId = m_cursor.getString(FIELD_ID);
                pos = m_cursor.getPosition();
                m_objectPositions.put(id, pos);
                m_lastScannedObjectPosition = pos;

                if(id.equals(objId)) {
                    return pos;
                }
            } while(m_cursor.moveToNext());
            return -1;
        } else return pos;
    }

    public void updateCursor(Cursor c) {
        if(m_cursor != null && m_cursor != c) m_cursor.close();
        m_cursor = c;
        m_lastScannedObjectPosition = 0;
        m_objectPositions.clear();

        if(m_cursor != null)
            Log.v(TAG, "Updated with " + c.getCount() + " activities");

        notifyDataSetChanged();
    }
	
	public void close() {
        if(m_cursor != null)
            m_cursor.close();
		m_cursor = null;
	}

	public Object getItem(int position) {
        m_cursor.moveToPosition(position);
        int id = m_cursor.getInt(FIELD_ROWID);

        JSONObject act = m_objects.get(id);
        if(act != null) {
            return act;
        } else {
            try {
                m_objectPositions.put(m_cursor.getString(FIELD_ID), position);

                act = new JSONObject(m_cursor.getString(FIELD_JSON));
                act.put("_replies", m_cursor.getInt(FIELD_REPLIES));
                act.put("_likes",   m_cursor.getInt(FIELD_LIKES));
                act.put("_shares",  m_cursor.getInt(FIELD_SHARES));

                m_objects.put(id, act);
                return act;
            } catch(JSONException e) {
                throw new RuntimeException(e);
            }
        }
	}

    private static String getImage(ActivityWithAccount awa, JSONObject obj) {
		JSONObject mediaLink = obj.optJSONObject("image");
		if(mediaLink == null) return null;
		
		return Utils.getImageUrl(awa, mediaLink);
	}

    @Override
    public Holder onCreateViewHolder(ViewGroup viewGroup, int type) {
        LayoutInflater inf = LayoutInflater.from(m_ctx);
        return new Holder(inf);
    }

    @Override
    public void onBindViewHolder(Holder holder, int pos) {
        holder.bindToActivity(m_ctx, (JSONObject) getItem(pos));
    }

    @Override
    public int getItemCount() {
        int c = 0;
        if(m_cursor != null)
            c = m_cursor.getCount();

        Log.v(TAG, "getItemCount " + c);
        return c;
    }

	@Override
	public long getItemId(int id) {
	    m_cursor.moveToPosition(id);
        return m_cursor.getLong(FIELD_ROWID);
    }
}

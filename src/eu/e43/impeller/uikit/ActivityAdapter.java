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
import android.text.Html;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Checkable;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.HashMap;
import com.google.common.collect.ImmutableMap;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;

public class ActivityAdapter extends BaseAdapter {
	static final String TAG = "ActivityAdapter";
	
    Cursor                      m_cursor;
	ActivityWithAccount m_ctx;

    HashMap<String, Integer>    m_objectPositions;
    ImmutableMap<String, String> mapVerbs;
    ImmutableMap<String, String> mapObjectTypes;
    int m_lastScannedObjectPosition;

    LruCache<Integer, JSONObject> m_objects = new LruCache<Integer, JSONObject>(20);

	public ActivityAdapter(ActivityWithAccount ctx) {
		m_cursor = null;
		m_ctx  = ctx;
		m_objectPositions = new HashMap<String, Integer>();
		mapVerbs = ImmutableMap.<String, String> builder()
		.put("post", m_ctx.getResources().getString(R.string.verb_posted))
		.put("follow", m_ctx.getResources().getString(R.string.verb_followed))
		.put("stop-following", m_ctx.getResources().getString(R.string.verb_stopped_following))
		.put("favorite", m_ctx.getResources().getString(R.string.verb_favorited))
		.put("unfavorite", m_ctx.getResources().getString(R.string.verb_unfavorited))
		.put("share", m_ctx.getResources().getString(R.string.verb_shared))
		.put("unshare", m_ctx.getResources().getString(R.string.verb_unshared))
		.put("like", m_ctx.getResources().getString(R.string.verb_liked))
		.put("unlike", m_ctx.getResources().getString(R.string.verb_unliked))
		.put("create", m_ctx.getResources().getString(R.string.verb_created))
		.put("add", m_ctx.getResources().getString(R.string.verb_added))
		.put("delete", m_ctx.getResources().getString(R.string.verb_deleted))
		.put("join", m_ctx.getResources().getString(R.string.verb_joined))
		.put("remove", m_ctx.getResources().getString(R.string.verb_removed))
		.put("leave", m_ctx.getResources().getString(R.string.verb_left))
		.put("play", m_ctx.getResources().getString(R.string.verb_played))
		.put("listen", m_ctx.getResources().getString(R.string.verb_listened_to))
		.put("checkin", m_ctx.getResources().getString(R.string.verb_checked_in_at))
		.put("update", m_ctx.getResources().getString(R.string.verb_updated))
		.build();
		mapObjectTypes = ImmutableMap.<String, String> builder()
		.put("note", m_ctx.getResources().getString(R.string.object_type_a_note))
		.put("image", m_ctx.getResources().getString(R.string.object_type_an_image))
		.put("comment", m_ctx.getResources().getString(R.string.object_type_a_comment))
		.put("person", m_ctx.getResources().getString(R.string.object_type_a_person))
		.put("group", m_ctx.getResources().getString(R.string.object_type_a_group))
		.put("activity", m_ctx.getResources().getString(R.string.object_type_an_activity))
		.put("place", m_ctx.getResources().getString(R.string.object_type_a_place))
		.put("collection", m_ctx.getResources().getString(R.string.object_type_a_collection))
		.put("review", m_ctx.getResources().getString(R.string.object_type_a_review))
		.put("article", m_ctx.getResources().getString(R.string.object_type_an_article))
		.put("video", m_ctx.getResources().getString(R.string.object_type_a_video))
		.put("audio", m_ctx.getResources().getString(R.string.object_type_an_audio))
		.put("service", m_ctx.getResources().getString(R.string.object_type_a_service))
		.put("application", m_ctx.getResources().getString(R.string.object_type_an_application))
		.put("game", m_ctx.getResources().getString(R.string.object_type_a_game))
		.put("event", m_ctx.getResources().getString(R.string.object_type_an_event))
		.put("file", m_ctx.getResources().getString(R.string.object_type_a_file))
		.build();
	}

    public int findItemById(String id) {
        Integer pos = m_objectPositions.get(id);
        if(pos == null) {
            if(m_cursor == null) return -1;

            if(!m_cursor.moveToPosition(m_lastScannedObjectPosition)) return -1;
            do {
                String objId = m_cursor.getString(1);
                pos = m_cursor.getPosition();
                m_objectPositions.put(id, pos);
                if(id.equals(objId)) {
                    m_lastScannedObjectPosition = pos;
                    return pos;
                }
            } while(m_cursor.moveToNext());
            return -1;
        } else return pos;
    }

    private static class Wrapper extends FrameLayout implements Checkable {
        private boolean m_checked = false;

        public Wrapper(View child) {
            super(child.getContext());
            addView(child);
        }

        @Override
        public void setChecked(boolean b) {
            m_checked = b;

            if(b) {
                setBackgroundResource(android.R.color.holo_blue_bright);
            } else {
                setBackgroundResource(android.R.color.transparent);
            }
        }

        @Override
        public boolean isChecked() {
            return m_checked;
        }

        @Override
        public void toggle() {
            setChecked(!m_checked);
        }
    }

    public void updateCursor(Cursor c) {
        if(m_cursor != null && m_cursor != c) m_cursor.close();
        m_cursor = c;
        m_objects.evictAll();
        m_lastScannedObjectPosition = 0;
        m_objectPositions.clear();
        notifyDataSetChanged();
    }
	
	public void close() {
        if(m_cursor != null)
            m_cursor.close();
		m_cursor = null;
	}

	@Override
	public int getCount() {
        if(m_cursor != null)
		    return m_cursor.getCount();
        else
            return 0;
	}

	@Override
	public Object getItem(int position) {
        JSONObject act = m_objects.get(position);
        if(act != null) {
            return act;
        } else {
            try {
                m_cursor.moveToPosition(position);
                act = new JSONObject(m_cursor.getString(0));
                JSONObject obj = act.optJSONObject("object");
                if(obj != null) {
                    String id = obj.optString("id");
                    if(id != null) {
                        m_objectPositions.put(id, position);
                    }
                }

                act.put("_replies", m_cursor.getInt(1));
                act.put("_likes",   m_cursor.getInt(2));
                act.put("_shares",  m_cursor.getInt(3));

                m_objects.put(position, act);
                m_objectPositions.put(m_cursor.getString(1), position);
                return act;
            } catch(JSONException e) {
                throw new RuntimeException(e);
            }
        }
	}

	private static String getImage(JSONObject obj) {
		JSONObject mediaLink = obj.optJSONObject("image");
		if(mediaLink == null) return null;
		
		return Utils.getImageUrl(mediaLink);
	}
	
	@Override
	public int getViewTypeCount()
	{
		return 2;
	}
	
	@Override
	public int getItemViewType(int pos)
	{
		JSONObject json = (JSONObject) getItem(pos);
		return getItemViewType(json);
	}
	
	public int getItemViewType(JSONObject act) {
		JSONObject obj = act.optJSONObject("object");
		if(obj == null) {
			return 1;
		} else if("image".equals(obj.optString("objectType"))) {
			return 1;
		} else { //if("note".equals(obj.optString("objectType"))) {
			return 0;
		}
	}
	
	public String getLocalizedDescription(JSONObject json)
	{
	    try {
		String verb = json.optString("verb").toLowerCase();
		String objectType = json.getJSONObject("object").optString("objectType").toLowerCase();
		if(mapVerbs.containsKey(verb)&&mapObjectTypes.containsKey(objectType))
		{
		    String strActor,strVerb,strObject,strReply;
		
		    String actorUrl = json.getJSONObject("actor").optString("url");
		    
		    if(actorUrl!="")
			strActor = String.format("<a href='%s'>%s</a>",actorUrl,
			    json.getJSONObject("actor").optString("displayName",m_ctx.getResources().getString(R.string.actor_unknown)));
		    else
			strActor = json.getJSONObject("actor").optString("displayName",m_ctx.getResources().getString(R.string.actor_unknown));
		    
		    strVerb = mapVerbs.get(verb);
		    
		    String objectUrl = json.getJSONObject("object").optString("url");
		    String objectDisplayName = json.getJSONObject("object").optString("displayName");
		    
		    if(objectDisplayName!="")
		    {
			if(objectUrl!="")
			    strObject = String.format("<a href='%s'>%s</a>",objectUrl,objectDisplayName);
			else
			    strObject = objectDisplayName;
		    }
		    else if(objectUrl!="")
			strObject = String.format("<a href='%s'>%s</a>",objectUrl,mapObjectTypes.get(objectType));
		    else
			strObject = mapObjectTypes.get(objectType);
		
		    if(json.getJSONObject("object").has("inReplyTo"))
		    {
			String replyToObjectType = json.getJSONObject("object").getJSONObject("inReplyTo").optString("objectType").toLowerCase();
			String replyToObjectUrl = json.getJSONObject("object").getJSONObject("inReplyTo").optString("url");
			String replyToObjectDisplayName = json.getJSONObject("object").getJSONObject("inReplyTo").optString("displayName");
			
			if(replyToObjectDisplayName!="")
			{
			    if(replyToObjectUrl!="")
				strReply = String.format("<a href='%s'>%s</a>",replyToObjectUrl,replyToObjectDisplayName);
			    else
				strReply = replyToObjectDisplayName;
			}
			else if(mapObjectTypes.containsKey(replyToObjectType))
			{
			    if(replyToObjectUrl!="")
				strReply = String.format("<a href='%s'>%s</a>",replyToObjectUrl,mapObjectTypes.get(replyToObjectType));
			    else
				strReply = mapObjectTypes.get(replyToObjectType);
			}
			else
			{
			    if(replyToObjectUrl!="")
				strReply = String.format("<a href='%s'>%s</a>",replyToObjectUrl,m_ctx.getResources().getString(R.string.object_type_an_object));
			    else
				strReply = mapObjectTypes.get(m_ctx.getResources().getString(R.string.object_type_an_object));
			}
			    
			return String.format(m_ctx.getResources().getString(R.string.format_string_reply),strActor,strVerb,strObject,strReply);
		    }
		    else return String.format(m_ctx.getResources().getString(R.string.format_string_activity),strActor,strVerb,strObject);
		    
		}
		
		return json.optString("content", "(Action string missing)");
	    }
	    catch(JSONException e)
	    {
	    	return e.getLocalizedMessage();
	    }
		      
	}
	
	@Override
	public View getView(int position, View v, ViewGroup parent) {
	    JSONObject json = (JSONObject) getItem(position);
	    int type = getItemViewType(json);

	    switch(type) {
	    case 0:
	    	// General case
		    if (v == null) {
		        LayoutInflater vi = LayoutInflater.from(m_ctx);
		        v = new Wrapper(vi.inflate(R.layout.post_view, null));
		    }

		    TextView   caption      = (TextView)   v.findViewById(R.id.caption);
		    TextView   description  = (TextView)   v.findViewById(R.id.description);
		    AvatarView actorAvatar  = (AvatarView) v.findViewById(R.id.actorAvatar);
            AvatarView authorAvatar = (AvatarView) v.findViewById(R.id.authorAvatar);
            
            try {
            
		    if(m_ctx.getResources().getString(R.string.is_this_a_localization).equals("Y"))
		    {
			description.setText(Html.fromHtml(getLocalizedDescription(json)));
		    }
		    else
		    {
			description.setText(Html.fromHtml(json.optString("content", "(Action string missing)")));
		    }
		    	JSONObject obj = json.getJSONObject("object");
		    	String content = obj.optString("content");
                if(content == null) {
                    caption.setVisibility(View.GONE);
                } else {
                    caption.setVisibility(View.VISIBLE);
                    PumpHtml.setFromHtml(m_ctx, caption, content);
                }
				
				m_ctx.getImageLoader().setImage(actorAvatar, getImage(json.getJSONObject("actor")));

                String actorId  = json.getJSONObject("actor").getString("id");
                String authorId = json.getJSONObject("object").getJSONObject("author").getString("id");
                if(actorId.equals(authorId)) {
                    authorAvatar.setVisibility(View.GONE);
                } else {
                    authorAvatar.setVisibility(View.VISIBLE);
                    m_ctx.getImageLoader().setImage(authorAvatar,
                            getImage(json.getJSONObject("object").getJSONObject("author")));
                }
			} catch (JSONException e) {
                caption.setVisibility(View.VISIBLE);
				caption.setText(Html.fromHtml(e.getLocalizedMessage()));
				//caption.loadData(e.getLocalizedMessage(), "text/plain", "utf-8");
			}
		    break;
		    
	    case 1:
	    	// Image
	    	if(v == null) {
	    		LayoutInflater vi = LayoutInflater.from(m_ctx);
	    		v = new Wrapper(vi.inflate(R.layout.view_image, null));
	    	}
	    	
	    	TextView imgDescription = (TextView)  v.findViewById(R.id.description);
	    	ImageView imgImg        = (ImageView) v.findViewById(R.id.imageImage);
	    	
	    	try {
	    		if(m_ctx.getResources().getString(R.string.is_this_a_localization).equals("Y"))
			{
			    imgDescription.setText(Html.fromHtml(getLocalizedDescription(json)));
			}
			else
			{
			    imgDescription.setText(Html.fromHtml(json.optString("content", "(Action string missing)")));
			}
	    		m_ctx.getImageLoader().setImage(imgImg, getImage(json.getJSONObject("object")));
	    	} catch(JSONException e) {
	    		imgDescription.setText(e.getLocalizedMessage());
	    	}
	    	break;
	    }

        int replies = json.optInt("_replies");
        int likes   = json.optInt("_likes");
        int shares  = json.optInt("_shares");
        Utils.updateStatebar(v, replies, likes, shares);
		
		return v;
	}

	@Override
	public long getItemId(int id) {
		return id;
	}
}

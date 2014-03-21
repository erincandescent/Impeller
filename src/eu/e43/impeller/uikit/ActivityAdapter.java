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
import java.util.HashSet;
import java.util.Arrays;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;

public class ActivityAdapter extends BaseAdapter {
	static final String TAG = "ActivityAdapter";
	
    Cursor                      m_cursor;
	ActivityWithAccount m_ctx;

    HashMap<String, Integer>    m_objectPositions;
    HashSet knownVerbs;
    HashSet knownObjectTypes;
    int m_lastScannedObjectPosition;

    LruCache<Integer, JSONObject> m_objects = new LruCache<Integer, JSONObject>(20);

	public ActivityAdapter(ActivityWithAccount ctx) {
		m_cursor = null;
		m_ctx  = ctx;
		m_objectPositions = new HashMap<String, Integer>();
		knownVerbs = new HashSet(Arrays.asList("post","follow","stop-following","favorite","unfavorite","share","unshare",
		"like","unlike","create","add","delete","join","remove","leave","play","listen","checkin"));
		knownObjectTypes = new HashSet(Arrays.asList("note","image","comment","person","group","activity","place","collection",
		"review","article","video","audio","service","application","game","event","file"));
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
		if(knownVerbs.contains(verb)&&knownObjectTypes.contains(objectType))
		{
		    StringBuilder sbDescription = new StringBuilder("");			
		    
		    String actorUrl = json.getJSONObject("actor").optString("url");
		    
		    if(actorUrl!="")
		    {
			sbDescription.append("<a href='");
			sbDescription.append(actorUrl);
			sbDescription.append("'>");
		    }
		      
		    sbDescription.append(json.getJSONObject("actor").optString("displayName",m_ctx.getResources().getString(R.string.actor_unknown)));
		    
		    if(actorUrl!="")
		    {
			sbDescription.append("</a>");
		    }
		    
		    sbDescription.append(" ");
				    
		    if(verb.equals("post"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_posted));
		    else if(verb.equals("follow"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_followed));
		    else if(verb.equals("stop-following"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_stopped_following));
		    else if(verb.equals("favorite"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_favorited));
		    else if(verb.equals("unfavorite"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_unfavorited));
		    else if(verb.equals("share"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_shared));
		    else if(verb.equals("unshare"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_unshared));
		    else if(verb.equals("like"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_liked));
		    else if(verb.equals("unlike"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_unliked));
		    else if(verb.equals("create"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_created));
		    else if(verb.equals("add"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_added));
		    else if(verb.equals("delete"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_deleted));
		    else if(verb.equals("join"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_joined));
		    else if(verb.equals("remove"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_removed));
		    else if(verb.equals("leave"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_left));
		    else if(verb.equals("play"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_played));
		    else if(verb.equals("listen"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_listened_to));
		    else if(verb.equals("checkin"))sbDescription.append(m_ctx.getResources().getString(R.string.verb_checked_in_at));
		    	    
		    sbDescription.append(" ");
		    
		    String objectUrl = json.getJSONObject("object").optString("url");
		    
		    if(objectUrl!="")
		    {
			sbDescription.append("<a href='");
			sbDescription.append(objectUrl);
			sbDescription.append("'>");
		    }
		    
		    if(objectType.equals("note"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_note));
		    else if(objectType.equals("image"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_image));
		    else if(objectType.equals("comment"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_comment));
		    else if(objectType.equals("person"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_person));
		    else if(objectType.equals("group"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_group));
		    else if(objectType.equals("activity"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_activity));
		    else if(objectType.equals("place"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_place));
		    else if(objectType.equals("collection"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_collection));
		    else if(objectType.equals("review"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_review));
		    else if(objectType.equals("article"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_article));
		    else if(objectType.equals("video"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_video));
		    else if(objectType.equals("audio"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_audio));
		    else if(objectType.equals("service"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_service));
		    else if(objectType.equals("application"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_application));
		    else if(objectType.equals("game"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_game));
		    else if(objectType.equals("event"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_event));
		    else if(objectType.equals("file"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_file));
		    
		    if(objectUrl!="")
		    {
			sbDescription.append("</a>");
		    }
		    
		    if(json.getJSONObject("object").has("inReplyTo"))
		    {
			sbDescription.append(" ");
			sbDescription.append(m_ctx.getResources().getString(R.string.description_in_reply_to));
			sbDescription.append(" ");
			
			String replyToObjectType = json.getJSONObject("object").getJSONObject("inReplyTo").optString("objectType").toLowerCase();
			String replyToObjectUrl = json.getJSONObject("object").getJSONObject("inReplyTo").optString("url");
			
			if(replyToObjectUrl!="")
			{
			    sbDescription.append("<a href='");
			    sbDescription.append(replyToObjectUrl);
			    sbDescription.append("'>");
			}
			
			if(replyToObjectType.equals("note"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_note));
			else if(replyToObjectType.equals("image"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_image));
			else if(replyToObjectType.equals("comment"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_comment));
			else if(replyToObjectType.equals("person"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_person));
			else if(replyToObjectType.equals("group"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_group));
			else if(replyToObjectType.equals("activity"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_activity));
			else if(replyToObjectType.equals("place"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_place));
			else if(replyToObjectType.equals("collection"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_collection));
			else if(replyToObjectType.equals("review"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_review));
			else if(replyToObjectType.equals("article"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_article));
			else if(replyToObjectType.equals("video"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_video));
			else if(replyToObjectType.equals("audio"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_audio));
			else if(replyToObjectType.equals("service"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_service));
			else if(replyToObjectType.equals("application"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_application));
			else if(replyToObjectType.equals("game"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_game));
			else if(replyToObjectType.equals("event"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_event));
			else if(replyToObjectType.equals("file"))sbDescription.append(m_ctx.getResources().getString(R.string.object_type_a_file));
			else sbDescription.append(m_ctx.getResources().getString(R.string.object_type_an_object));
			
			if(replyToObjectUrl!="")
			{
			    sbDescription.append("</a>");
			}
			
		    }
		    
		    return sbDescription.toString();
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

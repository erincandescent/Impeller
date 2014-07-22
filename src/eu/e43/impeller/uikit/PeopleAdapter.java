package eu.e43.impeller.uikit;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.support.v4.widget.CursorAdapter;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;

/**
 * Created by oshepherd on 15/04/2014.
 */
public class PeopleAdapter extends BaseAdapter implements Filterable {
    private static final String TAG = "PeopleAdapter";
    LruCache<Integer, JSONObject> m_objCache;
    Cursor                        m_cursor;
    JSONObject[]                  m_people;
    Integer[] m_filterResult;
    ActivityWithAccount           m_context;
    Filter                        m_filter;
    CharSequence                  m_lastFilter;
    JSONObject                    m_followersObject;
    JSONObject                    m_publicObject;

    public PeopleAdapter(ActivityWithAccount context) {
        m_objCache = new LruCache<Integer, JSONObject>(32);
        m_context = context;
    }

    public void buildSpecialObjects() {
        try {
            m_publicObject = new JSONObject();
            m_publicObject.put("id",          "http://activityschema.org/collection/public");
            m_publicObject.put("displayName", m_context.getString(R.string.public_collection));
            m_publicObject.put("objectType", "collection");
            m_followersObject = new JSONObject();
            m_followersObject.put("id",          Utils.getUserUri(m_context, m_context.getAccount(), "followers"));
            m_followersObject.put("displayName", m_context.getString(R.string.followers_collection));
            m_followersObject.put("objectType", "collection");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public JSONObject getPublicObject()    { return m_publicObject; }
    public JSONObject getFollowersObject() { return m_followersObject; }

    public void swapCursor(Cursor crs) {
        if(crs == null) {
            m_cursor = null;
            m_people = new JSONObject[0];
            return;
        }

        m_cursor = crs;
        m_people = new JSONObject[crs.getCount() + 2];

        m_people[0] = m_publicObject;
        m_people[1] = m_followersObject;

        crs.moveToFirst();
        while(!crs.isAfterLast()) {
            try {
                m_people[crs.getPosition() + 2] = new JSONObject(crs.getString(1));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            crs.moveToNext();
        }

        if(m_filterResult != null) {
            m_filter.filter(m_lastFilter);
            m_filterResult = null;
        }
        notifyDataSetChanged();
    }

    private View newView() {
        return LayoutInflater.from(m_context).inflate(R.layout.view_person_list_item, null);
    }

    private void bindView(View v, JSONObject obj) {
        try {
            AvatarView ava = (AvatarView) v.findViewById(R.id.personAvatar);
            TextView  name = (TextView)   v.findViewById(R.id.personName);

            JSONObject img = obj.optJSONObject("image");
            if(img != null) {
                m_context.getImageLoader().setImage(ava, Utils.getImageUrl(m_context, img));
            } else {
                m_context.getImageLoader().setImage(ava, (URI) null);
            }
            name.setText(obj.optString("displayName", obj.getString("id")));
        } catch (JSONException e) {
            throw new RuntimeException("Bad object in database", e);
        }
    }

    @Override
    public int getCount() {
        if(m_filterResult != null) {
            return m_filterResult.length;
        } else if(m_people != null) {
            return m_people.length;
        } else {
            return 0;
        }
    }

    @Override
    public JSONObject getItem(int position) {
        if(m_filterResult != null) {
            position = m_filterResult[position];
        }

        return m_people[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }



    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if(convertView == null)
            convertView = newView();
        bindView(convertView, getItem(position));
        return convertView;
    }

    @Override
    public Filter getFilter() {
        if(m_filter == null) {
            m_filter = new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint_) {
                    Log.v(TAG, "Filter for " + constraint_);
                    if(constraint_ == null || constraint_.equals(""))
                        return new FilterResults();

                    String constraint = constraint_.toString().toLowerCase();
                    String[] constraintWords = constraint.split(" ");

                    JSONObject[] peeps = m_people;
                    ArrayList<Integer> results = new ArrayList<Integer>();
                    for(int i = 0; i < peeps.length; i++) {
                        JSONObject peep = peeps[i];

                        String dispName = peep.optString("displayName");
                        String[] words;
                        if(dispName != null) {
                            words = dispName.split(" ");
                        } else {
                            // Cut off acct:
                            words = new String[] {
                                    peep.optString("id").substring(5)
                            };
                        }

                        boolean add = false;
                        for(String constraintWord : constraintWords) {
                            add = false;

                            for(String word : words) {
                                if (word.toLowerCase().startsWith(constraintWord)) {
                                    add = true;
                                    break;
                                }
                            }

                            if(add == false)
                                break;
                        }

                        if(add) {
                            results.add(i);
                        }
                    }

                    FilterResults res = new FilterResults();
                    res.count = results.size();
                    res.values = results.toArray(new Integer[0]);
                    return res;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    m_filterResult = (Integer[]) results.values;
                    m_lastFilter   = constraint;
                    notifyDataSetChanged();
                }
            };
        }
        return m_filter;
    }
}

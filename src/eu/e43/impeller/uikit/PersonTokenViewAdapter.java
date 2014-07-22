package eu.e43.impeller.uikit;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.tokenautocomplete.TokenCompleteTextView;

import org.json.JSONObject;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;

/**
 * Created by oshepherd on 15/04/2014.
 */
public class PersonTokenViewAdapter implements TokenCompleteTextView.ViewAdapter {
    private ActivityWithAccount m_ctx;
    private LayoutInflater      m_inflater;

    public PersonTokenViewAdapter(ActivityWithAccount ctx) {
        m_ctx       = ctx;
        m_inflater  = LayoutInflater.from(ctx);
    }

    @Override
    public View getViewForObject(Object object) {
        JSONObject person = (JSONObject) object;

        View tokView = m_inflater.inflate(R.layout.view_token, null);

        AvatarView pic  = (AvatarView) tokView.findViewById(R.id.personAvatar);
        TextView   name = (TextView)   tokView.findViewById(R.id.personName);

        JSONObject img = person.optJSONObject("image");
        if(img != null) m_ctx.getImageLoader().setImage(pic, Utils.getImageUrl(m_ctx, img));

        name.setText(person.optString("displayName", person.optString("id")));

        return tokView;
    }

    @Override
    public Object defaultObject(String completionText) {
        return null;
    }
}

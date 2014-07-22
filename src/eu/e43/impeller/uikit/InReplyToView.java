package eu.e43.impeller.uikit;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.json.JSONObject;

import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.MainActivity;

/**
 * Created by OShepherd on 07/12/13.
 */
public class InReplyToView extends RelativeLayout implements View.OnClickListener {
    MainActivity m_activity;
    JSONObject   m_inReplyTo;

    public InReplyToView(MainActivity context, JSONObject inReplyTo) {
        super(context);
        m_activity  = context;
        m_inReplyTo = inReplyTo;

        LayoutInflater.from(context).inflate(R.layout.view_object_in_reply_to, this);

        setBackgroundResource(R.drawable.card_top_accent_bg);

        JSONObject author = inReplyTo.optJSONObject("author");
        if(author != null) {
            AvatarView parentAvatar = (AvatarView) findViewById(R.id.authorAvatar);
            TextView parentAuthor = (TextView)     findViewById(R.id.authorName);

            parentAuthor.setText(author.optString("displayName"));
            JSONObject img = author.optJSONObject("image");
            if(img != null) {
                m_activity.getImageLoader().setImage(parentAvatar, Utils.getImageUrl(m_activity, img));
            }
        }

        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        String url = m_inReplyTo.optString("id");

        if(url != null) {
            m_activity.showObjectInMode(MainActivity.Mode.OBJECT, Uri.parse(url));
        } else {
            url = m_inReplyTo.optString("url");
            if(url != null) {
                m_activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        }
    }
}

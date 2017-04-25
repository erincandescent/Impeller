package eu.e43.impeller.uikit;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

import eu.e43.impeller.AppConstants;
import eu.e43.impeller.ImpellerApplication;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;

/**
 * Created by OShepherd on 03/11/13.
 */
public class LocationView extends FrameLayout implements View.OnClickListener {
    private static final String TAG = "LocationView";
    private static final String IMAGE_URL_FORMAT = "http://staticmap.openstreetmap.de/staticmap.php?center=%f,%f&zoom=14&size=%dx%d";
    private static final String GEO_LAT_LONG_FORMAT = "geo:%f,%f";
    private static final Uri GEO_BASE = Uri.parse("geo:");
    private ActivityWithAccount m_activity;

    private SharedPreferences m_prefs;
    private JSONObject   m_addr;
    private FrameLayout  m_mapDisplay;
    private LinearLayout m_locationOverlay;
    private TextView     m_globe;
    private TextView     m_placeName;

    public LocationView(ActivityWithAccount context, JSONObject addr) {
        super(context);
        m_activity = context;
        m_addr = addr;
        m_prefs = PreferenceManager.getDefaultSharedPreferences(context);

        LayoutInflater.from(context).inflate(R.layout.view_location, this);
        setLayoutParams(new ListView.LayoutParams(LayoutParams.MATCH_PARENT, Utils.pxFromDip(m_activity, 64)));
        setBackgroundResource(R.drawable.card_middle_bg);
        setPadding(0, 0, Utils.pxFromDip(m_activity, 1), 0);

        m_mapDisplay        = (FrameLayout)     findViewById(R.id.map_display);
        m_globe             = (TextView)        findViewById(R.id.location_globe_icon);
        m_placeName         = (TextView)        findViewById(R.id.location_name);
        m_locationOverlay   = (LinearLayout)    findViewById(R.id.location_overlay);

        m_globe.setTypeface(ImpellerApplication.fontAwesome);
        if(addr.has("displayName")) {
            m_placeName.setText(addr.optString("displayName"));
        } else if(addr.has("address")) {
            JSONObject _addr = addr.optJSONObject("address");
            if(_addr != null) {
                String address = _addr.optString("formatted", _addr.optString("streetAddress"));
                address = address.trim().replace("\n", ", ");
                m_placeName.setText(address);
            } else {
                m_locationOverlay.setVisibility(View.GONE);
            }
        } else {
            m_locationOverlay.setVisibility(View.GONE);
        }

        setOnClickListener(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        if(oldh != h && w != oldw) {
            try {
                if(m_prefs.getBoolean(AppConstants.PREF_LOCATION_MAPS, true)) {
                    JSONObject pos = m_addr.getJSONObject("position");
                    double longitude = pos.getDouble("longitude");
                    double latitude  = pos.getDouble("latitude");

                    String url = String.format(Locale.ENGLISH, IMAGE_URL_FORMAT,
                            latitude, longitude, w, h);

                    Log.i(TAG, "Image URL is " + url);

                    m_activity.getImageLoader().setBackground(m_mapDisplay, url);
                }
            } catch (JSONException e) {
                Log.w(TAG, "Error getting location information");
            }
        }
    }

    @Override
    public void onClick(View v) {
        Uri url = null;

        if(m_addr.has("position")) {
            JSONObject pos = m_addr.optJSONObject("position");
            if(pos != null) {
                double longitude = pos.optDouble("longitude");
                double latitude  = pos.optDouble("latitude");

                url = Uri.parse(String.format(Locale.ENGLISH, GEO_LAT_LONG_FORMAT,
                        latitude, longitude));
            }
        }

        if(url == null && m_addr.has("address")) {
            JSONObject addr = m_addr.optJSONObject("address");
            if(addr != null) {
                url = GEO_BASE.buildUpon().appendQueryParameter("q",
                        addr.optString("formatted", addr.optString("streetAddress"))).build();
            }
        }

        if(url == null && m_addr.has("url")) {
            url = Uri.parse(m_addr.optString("url"));
        }

        if(url != null) {
            m_activity.startActivity(new Intent(Intent.ACTION_VIEW, url));
        }
    }
}

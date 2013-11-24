package eu.e43.impeller;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by OShepherd on 03/11/13.
 */
public class LocationServices implements LocationListener {
    private static LocationServices ms;
    private static final String TAG = "LocationServices";
    private Context                 m_context;
    private LocationManager         m_locationManager;
    private Criteria                m_criteria;
    private List<Address>           m_lastAddress = new ArrayList<Address>();
    private List<AddressListener>   m_listeners   = new ArrayList<AddressListener>();
    private Looper                  m_looper;
    private Handler                 m_mainHandler;
    private Geocoder                m_geocoder;
    private Runnable                m_dispatchLocationUpdates = new Runnable() {
        @Override
        public void run() {
            synchronized(this) {
                for(AddressListener l : m_listeners) {
                    l.locationUpdate(m_lastAddress);
                }
                m_listeners.clear();
            }
        }
    };

    public interface AddressListener {
        public void locationUpdate(List<Address> addresses);
    }

    public static LocationServices get(Context ctx) {
        if(ms == null) {
            ms = new LocationServices(ctx);
        }
        return ms;
    }

    private LocationServices(Context ctx) {
        m_context = ctx.getApplicationContext();
        m_locationManager = (LocationManager) m_context.getSystemService(Context.LOCATION_SERVICE);

        m_criteria = new Criteria();
        m_criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        m_criteria.setCostAllowed(false);
        m_criteria.setPowerRequirement(Criteria.POWER_MEDIUM);

        m_geocoder = new Geocoder(m_context);
        m_mainHandler = new Handler(Looper.getMainLooper());

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                synchronized(LocationServices.this) {
                    m_looper = Looper.myLooper();
                    LocationServices.this.notifyAll();
                }
                Looper.loop();
            }
        }).start();
    }

    public void getNearbyPlaces(AddressListener l) {
        synchronized(this) {
            while(m_looper == null) {
                try {
                    this.wait();
                } catch(InterruptedException ex) {
                    // Pass
                }
            }
        }

        if(m_listeners.size() == 0) {
            try {
                m_locationManager.requestSingleUpdate(m_criteria, this, m_looper);
            } catch(RuntimeException e) {
                Toast.makeText(m_context, "Your device firmware is non-conforming. Location services disabled.", 15);
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(m_context);
                prefs.edit().putString(Constants.PREF_MY_LOCATION, "0").apply();
                m_mainHandler.post(m_dispatchLocationUpdates);
                return;
            }
        }

        m_listeners.add(l);
    }

    // LocationListener
    @Override
    public void onLocationChanged(Location location) {
        try {
            synchronized(this) {
                m_lastAddress = m_geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 10);
            }
            m_mainHandler.post(m_dispatchLocationUpdates);
        } catch(IOException ex) {
            Log.w(TAG, "Geocoder failed", ex);
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    public static JSONObject buildPlace(Address addr) throws JSONException {
        JSONObject place = new JSONObject();
        place.put("objectType", "place");

        if(addr.hasLongitude() || addr.hasLatitude()) {
            JSONObject position = new JSONObject();
            position.put("latitude", addr.getLatitude());
            position.put("longitude", addr.getLongitude());
            place.put("position", position);
        }

        JSONObject address = new JSONObject();
        String premesis        = addr.getPremises();
        String subThoroughfare = addr.getSubThoroughfare();
        String thoroughfare    = addr.getThoroughfare();

        String locality = addr.getLocality();
        String region   = addr.getAdminArea();
        String country  = addr.getCountryCode();
        String postCode = addr.getPostalCode();

        StringBuilder streetAddress = new StringBuilder();
        if(premesis != null) {
            streetAddress.append(premesis);
            streetAddress.append('\n');
        }

        if(subThoroughfare != null) {
            streetAddress.append(subThoroughfare);
            streetAddress.append('\n');
        }

        if(thoroughfare != null) {
            streetAddress.append(thoroughfare);
        }

        StringBuilder fullAddress = new StringBuilder();
        for(int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
            fullAddress.append(addr.getAddressLine(i));
            fullAddress.append('\n');
        }

        if(fullAddress != null)   address.put("formatted",        fullAddress);
        if(streetAddress != null) address.put("streetAddress",    streetAddress);
        if(locality != null)      address.put("locality",         locality);
        if(region != null)        address.put("region",           region);
        if(postCode != null)      address.put("postalCode",       postCode);
        if(country != null)       address.put("country",          country);
        if(address.length() > 0) place.put("address", address);

        String url = addr.getUrl();
        if(url != null)
            place.put("url", url);

        String featureName = addr.getFeatureName();
        if(featureName != null) {
            place.put("displayName", featureName);
        } else if(addr.getMaxAddressLineIndex() > 0) {
            place.put("displayName", addr.getAddressLine(0));
        }

        return place;
    }
}

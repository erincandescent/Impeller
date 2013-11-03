package eu.e43.impeller.uikit;

import android.content.Context;
import android.location.Address;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import eu.e43.impeller.LocationServices;
import eu.e43.impeller.R;

/**
 * Created by OShepherd on 03/11/13.
 */
public class LocationAdapter extends ArrayAdapter<Address> implements LocationServices.AddressListener {

    public LocationAdapter(Context context) {
        super(context, 0);
        add(null);

        LocationServices.get(context).getNearbyPlaces(this);
    }

    @Override
    public View getView(int position, View v, ViewGroup parent) {
        if(v == null) {
            LayoutInflater vi = LayoutInflater.from(getContext());
            v = vi.inflate(android.R.layout.simple_list_item_1, null);
        }

        TextView text = (TextView) v.findViewById(android.R.id.text1);

        Address addr = getItem(position);
        if(addr == null) {
            text.setText(R.string.no_location);
        } else {
            StringBuilder bld = new StringBuilder();
            String featureName = addr.getFeatureName();
            boolean needDelim = false;
            if(featureName != null) {
                bld.append(featureName);
                needDelim = true;
            }

            for(int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                if(needDelim) {
                    bld.append(", ");
                } else needDelim = true;
                bld.append(addr.getAddressLine(i));
            }
            text.setText(bld.toString());
        }

        return v;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent);
    }

    @Override
    public void locationUpdate(List<Address> addresses) {
        addAll(addresses);
    }
}

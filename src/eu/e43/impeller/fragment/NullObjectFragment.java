package eu.e43.impeller.fragment;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONObject;

import eu.e43.impeller.R;

import static eu.e43.impeller.R.layout.fragment_object_null;


/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 *
 */
public class NullObjectFragment extends ObjectFragment {


    public NullObjectFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(fragment_object_null, container);
    }


    @Override
    public void objectUpdated(JSONObject obj) {

    }
}

package eu.e43.impeller.fragment;

import android.support.v4.app.Fragment;
import android.os.Bundle;

import org.json.JSONObject;

/**
 * Created by oshepherd on 04/04/14.
 */
public abstract class ObjectFragment extends Fragment {
    public static final String PARAM_ID     = "eu.e43.impeller.ObjectFragment.ID";
    public static final String PARAM_INT_ID = "eu.e43.impeller.ObjectFragment.INT_ID";

    protected String m_id;
    protected int    m_intId;

    public static ObjectFragment prepare(ObjectFragment frag, String id, int intId) {
        Bundle args = new Bundle();
        args.putString(PARAM_ID, id);
        args.putInt(PARAM_INT_ID, intId);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if(args == null)
            throw new RuntimeException("Missing parameters");

        m_id    = args.getString(PARAM_ID);
        m_intId = args.getInt(PARAM_INT_ID);
    }

    public JSONObject getObject()
    {
        Fragment parent = getParentFragment();
        if(parent == null) return null;
        return ((ObjectContainerFragment)parent).getObject();
    }

    public abstract void objectUpdated(JSONObject obj);
}

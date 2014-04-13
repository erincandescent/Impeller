package eu.e43.impeller.fragment;

import android.app.Fragment;
import android.os.Bundle;

import org.json.JSONObject;

/**
 * Created by oshepherd on 04/04/14.
 */
public abstract class ObjectFragment extends Fragment {
    public static final String PARAM_ID     = "eu.e43.impeller.ObjectFragment.ID";

    protected String m_id;

    public static ObjectFragment create(Class<? extends ObjectFragment> cls, String id) {
        ObjectFragment frag = null;
        try {
            frag = cls.newInstance();
        } catch (java.lang.InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Bundle args = new Bundle();
        args.putString(PARAM_ID, id);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle args = getArguments();
        if(args == null)
            throw new RuntimeException("Missing parameters");

        m_id = args.getString(PARAM_ID);
    }

    public JSONObject getObject()
    {
        Fragment parent = getParentFragment();
        if(parent == null) return null;
        return ((ObjectContainerFragment)parent).getObject();
    }

    public abstract void objectUpdated(JSONObject obj);
}

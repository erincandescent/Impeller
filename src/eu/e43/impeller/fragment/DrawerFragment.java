package eu.e43.impeller.fragment;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import eu.e43.impeller.Constants;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.ActivityWithAccount;
import eu.e43.impeller.uikit.AvatarView;
import eu.e43.impeller.uikit.NavigationDrawerAdapter;

public class DrawerFragment extends Fragment implements AdapterView.OnItemClickListener {
    private static final String TAG = "DrawerFragment";
    private DrawerActionListener mListener;

    public DrawerFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View v = inflater.inflate(R.layout.fragment_drawer, container, false);

        ListView modes = (ListView) v.findViewById(R.id.viewList);
        modes.setAdapter(new NavigationDrawerAdapter(getActivity()));
        modes.setOnItemClickListener(this);

        v.findViewById(R.id.changeAccountButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mListener != null) mListener.doChangeAccount();
            }
        });

        return v;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (DrawerActionListener) activity;
            onAccountChanged(((ActivityWithAccount) getActivity()).getAccount());
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnFragmentInteractionListener");
        }
    }

    public void onAccountChanged(Account acct) {
        ActivityWithAccount awa = (ActivityWithAccount) getActivity();

        if(acct == null)
            return;

        AvatarView avatar = (AvatarView) getView().findViewById(R.id.avatar);
        TextView acctId   = (TextView)   getView().findViewById(R.id.accountId);
        TextView acctName = (TextView)   getView().findViewById(R.id.accountName);

        avatar.resetAvatar();
        acctId.setText(acct.name);

        // Temp / in case there isn't a displayName
        acctName.setText(acct.name);

        String id = AccountManager.get(awa).getUserData(acct, "id");
        Cursor c = getActivity().getContentResolver().query(
                awa.getContentUris().objectsUri, new String[] { "_json" }, 
                "id=?", new String[] { id }, null);
        try {
            if(c.moveToFirst()) {
                JSONObject obj = new JSONObject(c.getString(0));
                String name = obj.optString("displayName");
                if(name != null) {
                    acctName.setText(name);
                }

                String img = Utils.getImageUrl(awa, obj.optJSONObject("image"));
                Log.v(TAG, "The user's avatar is at " + img);
                awa.getImageLoader().setImage(avatar, img);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Bad data in database", e);
        } finally {
            c.close();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        Constants.FeedID feed = (Constants.FeedID) parent.getItemAtPosition(position);
        if(mListener != null)
            mListener.onSelectFeed(feed);
    }

    public interface DrawerActionListener {
        public void onSelectFeed(Constants.FeedID feed);
        public void doChangeAccount();
    }

}

package eu.e43.impeller.fragment;
import java.util.List;

import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import eu.e43.impeller.R;
import eu.e43.impeller.activity.ActivityWithAccount;
import eu.e43.impeller.activity.ShareActivity;

class ShareFragment extends DialogFragment implements AdapterView.OnItemClickListener {
    private PackageManager    m_packageManager = null;
    private Intent            m_intent         = null;
    private List<ResolveInfo> m_resolved       = null;

    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        ResolveInfo info = m_resolved.get(position);
        ActivityInfo activity = info.activityInfo;

        Intent intent = (Intent) m_intent.clone();
        intent.setComponent(new ComponentName(activity.applicationInfo.packageName, activity.name));
        getActivity().startActivity(intent);

        dismiss();
    }

    private class ShareAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return m_resolved.size();
        }

        @Override
        public Object getItem(int position) {
            return m_resolved.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            ResolveInfo info = m_resolved.get(position);
            if(view == null) {
                view = LayoutInflater.from(getActivity()).inflate(R.layout.view_activity_icon, null);
            }

            ImageView icon = (ImageView) view.findViewById(R.id.icon);
            TextView label = (TextView)  view.findViewById(R.id.label);

            icon.setImageDrawable(info.loadIcon(m_packageManager));
            label.setText(info.loadLabel(m_packageManager));

            return view;
        }
    }

    static ShareFragment newInstance(Intent i) {
        ShareFragment f = new ShareFragment();
        Bundle args = new Bundle();
        args.putParcelable("intent", i);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        m_intent = (Intent) getArguments().getParcelable("intent");

        m_packageManager = getActivity().getPackageManager();

        Intent shareIntent = (Intent) m_intent.clone();
        shareIntent.setClass(getActivity(), ShareActivity.class);

        ActivityWithAccount awa = (ActivityWithAccount) getActivity();
        shareIntent.putExtra("account", awa.getAccount());

        Intent[] specifics = new Intent[] {
                shareIntent
        };

        m_resolved = m_packageManager.queryIntentActivityOptions(null, specifics, m_intent, 0);
    }

    @Override
    public Dialog onCreateDialog(Bundle icicle) {
        Dialog d = super.onCreateDialog(icicle);
        d.setTitle("Share");
        return d;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle icicle) {
        GridView gv = (GridView) inflater.inflate(R.layout.fragment_share, container, false);
        gv.setAdapter(new ShareAdapter());
        gv.setOnItemClickListener(this);
        gv.setNumColumns(3);
        return gv;
    }
}
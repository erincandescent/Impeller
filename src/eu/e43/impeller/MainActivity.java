/* Copyright 2013 Owen Shepherd. A part of Impeller.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.e43.impeller;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import org.json.JSONObject;

import android.accounts.Account;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;

import eu.e43.impeller.content.PumpContentProvider;

public class MainActivity extends ActivityWithAccount {
	static final String TAG = "MainActivity";
	private Calendar        m_nextFetch      = null;
    private boolean         m_twoPane        = false;
    private FeedFragment    m_feedFragment   = null;
    private ObjectFragment  m_objectFragment = null;

	@Override
	protected void onCreateEx(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        m_twoPane = "two_pane".equals(findViewById(R.id.main_activity).getTag());

        if(savedInstanceState == null) {
            getActionBar().hide();
            getFragmentManager().beginTransaction()
                .add(R.id.feed_fragment, new SplashFragment())
                .setTransition(FragmentTransaction.TRANSIT_NONE)
                .commit();
        }
	}
	
	@Override
    protected void onStart() {
		super.onStart();

        Calendar now = GregorianCalendar.getInstance();
        if(m_nextFetch == null || m_nextFetch.before(now) && m_account != null) {
            Log.v(TAG, "onStart() - requesting sync");

            getContentResolver().requestSync(m_account, PumpContentProvider.AUTHORITY, new Bundle());
            now.add(Calendar.MINUTE, 5);
            m_nextFetch = now;
        }
	}

    @Override
    protected void onDestroy() {
    	super.onDestroy();
    }

	protected void gotAccount(Account acct, Bundle icicle) {
        if(icicle == null) {
            FragmentManager fm = getFragmentManager();
            FragmentTransaction txn = fm.beginTransaction();
            txn.replace(R.id.feed_fragment, new FeedFragment());
            txn.setTransition(FragmentTransaction.TRANSIT_NONE);
            txn.commit();
            getActionBar().show();
        }
	}

    public boolean isTwoPane() {
        return m_twoPane;
    }

    public void showObject(Uri id) {
        Bundle args = new Bundle();
        args.putParcelable("id", id);

        ObjectFragment objFrag = new ObjectFragment();
        objFrag.setArguments(args);

        FragmentManager fm = getFragmentManager();
        if(m_objectFragment != null) {
            fm.popBackStack();
        }
        FragmentTransaction txn = fm.beginTransaction();
        txn.replace(R.id.content_fragment, objFrag);
        txn.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        txn.addToBackStack(null);
        txn.commit();
    }

    public void onAddFeedFragment(FeedFragment fFrag) {
        m_feedFragment = fFrag;

        if(m_objectFragment != null) {
            m_feedFragment.setSelectedItem((Uri) m_objectFragment.getArguments().getParcelable("id"));
        }
    }

    public void onRemoveFeedFragment(FeedFragment fFrag) {
        if(m_feedFragment == fFrag)
            m_feedFragment = null;
    }

    public void onShowObjectFragment(ObjectFragment oFrag) {
        m_objectFragment = oFrag;

        if(m_feedFragment != null)
            m_feedFragment.setSelectedItem((Uri) oFrag.getArguments().getParcelable("id"));

        if(m_twoPane) {
            View ctFrag = findViewById(R.id.content_container);
            if(ctFrag != null) {
                ctFrag.setVisibility(View.VISIBLE);
            }
        } else {
            View ctFrag = findViewById(R.id.content_fragment);
            View fdFrag = findViewById(R.id.feed_fragment);
            fdFrag.setVisibility(View.GONE);
            ctFrag.setVisibility(View.VISIBLE);
        }
    }

    public void onHideObjectFragment(ObjectFragment oFrag) {
        if(m_objectFragment == oFrag) m_objectFragment = null;

        if(m_feedFragment != null)
            m_feedFragment.setSelection(-1);

        if(m_twoPane) {
            View ctFrag = findViewById(R.id.content_container);
            ctFrag.setVisibility(View.GONE);
        } else {
            View ctFrag = findViewById(R.id.content_fragment);
            View fdFrag = findViewById(R.id.feed_fragment);
            ctFrag.setVisibility(View.GONE);
            fdFrag.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }
}

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

package eu.e43.impeller.activity;

import java.util.Calendar;
import java.util.GregorianCalendar;

import android.accounts.Account;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import eu.e43.impeller.fragment.FeedFragment;
import eu.e43.impeller.fragment.ObjectFragment;
import eu.e43.impeller.R;
import eu.e43.impeller.fragment.SplashFragment;
import eu.e43.impeller.content.PumpContentProvider;

public class MainActivity extends ActivityWithAccount {
	static final String TAG = "MainActivity";

    /** Time to do next feed fetch */
	private Calendar        m_nextFetch     = null;

    /** Tablet UI mode? */
    private boolean         m_isTablet      = false;

    /** Pointer to the active feed fragment (if any) */
    private FeedFragment m_feedFragment     = null;

    /** Pointer to the active object fragment (if any) */
    private ObjectFragment m_objectFragment = null;

    /** Display mode */
    public enum Mode {
        /** Showing feed */
        FEED,

        /** Showing an object from the feed */
        FEED_OBJECT,

        /** Showing an object */
        OBJECT
    };

    Mode m_displayMode = Mode.FEED;

	@Override
	protected void onCreateEx(Bundle savedInstanceState) {
        PreferenceManager.setDefaultValues(this, R.xml.pref_general,   false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
        setContentView(R.layout.activity_main);

        m_isTablet = "two_pane".equals(findViewById(R.id.main_activity).getTag());

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;

            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;

            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setDisplayMode(Mode m) {

        View fdFrag = findViewById(R.id.feed_fragment);
        View ctFrag = m_isTablet ? findViewById(R.id.content_container) : findViewById(R.id.content_fragment);

        switch(m) {
            case FEED:
                fdFrag.setVisibility(View.VISIBLE);
                ctFrag.setVisibility(View.GONE);
                break;

            case FEED_OBJECT:
                fdFrag.setVisibility(m_isTablet ? View.VISIBLE : View.GONE);
                ctFrag.setVisibility(View.VISIBLE);
                break;

            case OBJECT:
                fdFrag.setVisibility(View.GONE);
                ctFrag.setVisibility(View.VISIBLE);
        }
    }

    public boolean isTwoPane() {
        return m_isTablet && m_displayMode == Mode.FEED_OBJECT;
    }

    public void showObjectInMode(Mode mode, Uri id) {
        Bundle args = new Bundle();
        args.putParcelable("id", id);
        args.putString("mode", mode.toString());

        ObjectFragment objFrag = new ObjectFragment();
        objFrag.setArguments(args);

        FragmentManager fm = getFragmentManager();
        if(m_objectFragment != null && mode == Mode.FEED_OBJECT) {
            fm.popBackStack();
        }

        FragmentTransaction txn = fm.beginTransaction();
        txn.replace(R.id.content_fragment, objFrag);
        txn.setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        txn.addToBackStack(null);
        txn.commit();

        setDisplayMode(mode);
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

        if(m_feedFragment != null && m_displayMode == Mode.FEED_OBJECT)
            m_feedFragment.setSelectedItem((Uri) oFrag.getArguments().getParcelable("id"));

        setDisplayMode(oFrag.getMode());
    }

    public void onHideObjectFragment(ObjectFragment oFrag) {
        if(m_objectFragment == oFrag) {
            m_objectFragment = null;
        } else {
            return;
        }

        setDisplayMode(Mode.FEED);

        if(m_feedFragment != null)
            m_feedFragment.setSelection(-1);
    }
}

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
import android.annotation.TargetApi;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ViewFlipper;

import eu.e43.impeller.Constants;
import eu.e43.impeller.account.Authenticator;
import eu.e43.impeller.fragment.DrawerFragment;
import eu.e43.impeller.fragment.FeedFragment;
import eu.e43.impeller.fragment.ObjectContainerFragment;
import eu.e43.impeller.R;
import eu.e43.impeller.fragment.SplashFragment;
import eu.e43.impeller.content.PumpContentProvider;
import eu.e43.impeller.uikit.NavigationDrawerAdapter;
import eu.e43.impeller.uikit.OverlayController;

public class MainActivity extends ActivityWithAccount implements DrawerFragment.DrawerActionListener {
	static final String TAG = "MainActivity";

    /** Time to do next feed fetch */
	private Calendar        m_nextFetch     = null;

    /** Tablet UI mode? */
    private boolean         m_isTablet      = false;

    /** Drawer toggle controller */
    private ActionBarDrawerToggle m_drawerToggle = null;

    /** Drawer layout */
    private DrawerLayout m_drawerLayout = null;

    /** Drawer fragment */
    private DrawerFragment m_drawerFragment = null;

    /** Pointer to the active feed fragment (if any) */
    private FeedFragment m_feedFragment     = null;

    /** Pointer to the active object fragment (if any) */
    private ObjectContainerFragment m_objectFragment = null;

    Mode m_displayMode = Mode.FEED;

	@Override
	protected void onCreateEx(Bundle savedInstanceState) {
        PreferenceManager.setDefaultValues(this, R.xml.pref_general,   false);
        PreferenceManager.setDefaultValues(this, R.xml.pref_data_sync, false);
        setContentView(R.layout.activity_main);

        m_drawerLayout     = (DrawerLayout) findViewById(R.id.drawer_layout);
        m_drawerFragment   = (DrawerFragment) getSupportFragmentManager().findFragmentById(R.id.navigation_drawer);
        m_drawerToggle = new ActionBarDrawerToggle(
                this,
                m_drawerLayout,
                R.drawable.ic_navigation_drawer,
                R.string.drawer_open,
                R.string.drawer_close
        );
        m_drawerLayout.setDrawerListener(m_drawerToggle);

        m_isTablet = "two_pane".equals(findViewById(R.id.main_activity).getTag());

        if(savedInstanceState == null) {
            getSupportActionBar().hide();
            getSupportFragmentManager().beginTransaction()
                .add(R.id.feed_fragment, new SplashFragment())
                .setTransition(FragmentTransaction.TRANSIT_NONE)
                .commit();
        } else {
            m_feedFragment      = (FeedFragment)            getSupportFragmentManager().getFragment(savedInstanceState, "feedFragment");
            m_objectFragment    = (ObjectContainerFragment) getSupportFragmentManager().getFragment(savedInstanceState, "objectFragment");

            setDisplayMode((Mode) savedInstanceState.getSerializable("displayMode"));
            Log.i(TAG, "Restoring in display mode " + m_displayMode.toString());
        }
	}

    @Override
    protected void queryForAccount(QueryReason reason) {
        if(reason == QueryReason.Startup) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Account[] accts = m_accountManager.getAccountsByType(Authenticator.ACCOUNT_TYPE);

            Account theAccount = null;
            String lastAccount = prefs.getString("lastAccount", null);
            if (lastAccount != null) {
                for (Account act : accts) {
                    if (lastAccount.equals(act.name)) {
                        theAccount = act;
                        break;
                    }
                }
            }

            if (theAccount == null) {
                if (accts.length > 0) {
                    theAccount = accts[0];
                } else {
                    super.queryForAccount(reason);
                    return;
                }
            }

            haveGotAccount(theAccount);
        } else {
            super.queryForAccount(reason);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        m_drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        m_drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putSerializable("displayMode", m_displayMode);

        if(m_feedFragment != null)
            getSupportFragmentManager().putFragment(outState, "feedFragment", m_feedFragment);
        if(m_objectFragment != null)
            getSupportFragmentManager().putFragment(outState, "objectFragment", m_objectFragment);
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

    @Override
    protected void gotAccount(Account acct) {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putString("lastAccount", acct.name)
            .commit();

        getSupportActionBar().show();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        if(m_feedFragment == null || !acct.equals(m_feedFragment.getAccount())) {
            FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
            if(m_objectFragment != null) {
                tx.remove(m_objectFragment);
            }
            tx.replace(R.id.feed_fragment, new FeedFragment());
            tx.commit();
            setDisplayMode(Mode.FEED);
        } else setDisplayMode(m_displayMode);

        if(m_drawerFragment != null)
            m_drawerFragment.onAccountChanged(acct);
	}

    @Override
    protected void onStartIntent(Intent startIntent) {
        onNewIntent(startIntent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "New intent " + intent);

        if(intent.hasExtra(Constants.EXTRA_ACCOUNT)) {
            haveGotAccount((Account) intent.getParcelableExtra(Constants.EXTRA_ACCOUNT));
        }

        String action = intent.getAction();
        if(Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null)
                return;

            Uri id = null;
            if (uri.getScheme().equals("content") && uri.getHost().equals("eu.e43.impeller.content")) {
                id = Uri.parse(uri.getLastPathSegment());
            } else {
                id = uri;
            }

            setIntent(intent);
            showObjectInMode(Mode.OBJECT, id);
        } else if(Constants.ACTION_SHOW_FEED.equals(action)) {
            showFeed((Constants.FeedID) intent.getSerializableExtra(Constants.EXTRA_FEED_ID));
        } else {
            Log.d(TAG, "Unknown new intent " + intent);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (m_drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getSupportFragmentManager();
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

        Log.d(TAG, "Mode " + m_displayMode.toString() + " -> " + m.toString());
        if(m != m_displayMode)
            evictOverlay();

        switch(m) {
            case FEED:
                fdFrag.setVisibility(View.VISIBLE);
                ctFrag.setVisibility(View.GONE);
                m_drawerToggle.setDrawerIndicatorEnabled(true);
                break;

            case FEED_OBJECT:
                fdFrag.setVisibility(m_isTablet ? View.VISIBLE : View.GONE);
                ctFrag.setVisibility(View.VISIBLE);
                m_drawerToggle.setDrawerIndicatorEnabled(false);
                break;

            case OBJECT:
                fdFrag.setVisibility(View.GONE);
                ctFrag.setVisibility(View.VISIBLE);
                m_drawerToggle.setDrawerIndicatorEnabled(false);
                break;
        }

        m_displayMode = m;
    }

    public boolean isTwoPane() {
        return m_isTablet && m_displayMode == Mode.FEED_OBJECT;
    }

    public void showObjectInMode(Mode mode, Uri id) {
        ObjectContainerFragment objFrag = ObjectContainerFragment.newInstance(id.toString(), mode);
        FragmentManager fm = getSupportFragmentManager();
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
        Log.i(TAG, "Add feed fragment");
        m_feedFragment = fFrag;

        if(m_displayMode == Mode.FEED) {
            setTitle(m_feedFragment.getFeedId().getNameString());
        }

        if(m_objectFragment != null) {
            m_feedFragment.setSelectedItem((Uri) m_objectFragment.getArguments().getParcelable("id"));
        }
    }

    public void onRemoveFeedFragment(FeedFragment fFrag) {
        Log.i(TAG, "Remove feed fragment");
        if(m_feedFragment == fFrag)
            m_feedFragment = null;
    }

    public void onShowObjectFragment(ObjectContainerFragment oFrag) {
        Log.i(TAG, "Show object fragment in mode "+ oFrag.getMode() + " " + oFrag);
        m_objectFragment = oFrag;

        if(m_feedFragment != null && m_displayMode == Mode.FEED_OBJECT)
            m_feedFragment.setSelectedItem(Uri.parse(oFrag.getArguments().getString(ObjectContainerFragment.PARAM_ID)));

        setDisplayMode(oFrag.getMode());
    }

    public void onHideObjectFragment(ObjectContainerFragment oFrag) {
        Log.i(TAG, "Hide object fragment " + oFrag);
        if(m_objectFragment == oFrag) {
            m_objectFragment = null;
        } else {
            return;
        }

        setDisplayMode(Mode.FEED);

        if(m_feedFragment != null)
            m_feedFragment.setSelection(-1);
    }

    /* Navigation listener */

    @Override
    public void onSelectFeed(Constants.FeedID feed) {
        m_drawerLayout.closeDrawers();
        showFeed(feed);
    }

    @Override
    public void doChangeAccount() {
        m_drawerLayout.closeDrawers();
        super.queryForAccount(QueryReason.User);
    }

    private void showFeed(Constants.FeedID id) {
        FragmentTransaction trans = getSupportFragmentManager().beginTransaction();

        if(m_feedFragment.getFeedId() != id) {
            FeedFragment ff = new FeedFragment();
            Bundle args = new Bundle();
            args.putSerializable("feed", id);
            ff.setArguments(args);
            trans.replace(R.id.feed_fragment, ff);
        }

        if(m_objectFragment != null) {
            trans.remove(m_objectFragment);
        }

        setDisplayMode(Mode.FEED);
        trans.commit();
        m_drawerLayout.closeDrawers();
    }

    // Overlays
    OverlayController m_overlayController;

    public void showOverlay(OverlayController controller, View overlay) {
        if(m_overlayController != null) {
            evictOverlay();
        }
        ViewFlipper flipper = (ViewFlipper) findViewById(R.id.overlay_flipper);
        flipper.addView(overlay);
        flipper.setDisplayedChild(1);
        m_overlayController = controller;
        setUiFlags();
    }

    private void evictOverlay() {
        OverlayController controller = m_overlayController;
        if(controller != null) {
            hideOverlay(controller);
            controller.onHidden();
        }
    }

    @Override
    public void onBackPressed() {
        if(m_overlayController != null) {
            evictOverlay();
        } else super.onBackPressed();
    }

    public void hideOverlay(OverlayController controller) {
        if(m_overlayController == controller) {
            ViewFlipper flipper = (ViewFlipper) findViewById(R.id.overlay_flipper);
            flipper.setDisplayedChild(0);
            flipper.removeViewAt(1);
            m_overlayController = null;
            setUiFlags();
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    void setUiFlags() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return;

        ViewFlipper flipper = (ViewFlipper) findViewById(R.id.overlay_flipper);
        if(m_overlayController != null) {
            // Fullscreen
            int flags =
                      View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;

            if(m_overlayController.isImmersive()) {
                flags |=
                      View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
                }
            }

            flipper.setSystemUiVisibility(flags);
        } else {
            // Standard
            flipper.setSystemUiVisibility(0);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if(hasFocus) setUiFlags();
    }

    /** Display mode */
    public enum Mode {
        /** Showing feed */
        FEED,

        /** Showing an object from the feed */
        FEED_OBJECT,

        /** Showing an object */
        OBJECT
    }
}

package eu.e43.impeller.activity;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;

import eu.e43.impeller.AccountUtils;
import eu.e43.impeller.R;
import eu.e43.impeller.account.Authenticator;
import eu.e43.impeller.content.PumpContentProvider;

/**
 * A {@link PreferenceActivity} that presents a set of application settings. On
 * handset devices, settings are presented as a single list. On tablets,
 * settings are split by category, with category headers shown to the left of
 * the list of settings.
 * <p>
 * See <a href="http://developer.android.com/design/patterns/settings.html">
 * Android Design: Settings</a> for design guidelines and the <a
 * href="http://developer.android.com/guide/topics/ui/settings.html">Settings
 * API Guide</a> for more information on developing a Settings UI.
 */
public class SettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // In the simplified UI, fragments are not used at all and we instead
        // use the older PreferenceActivity APIs.

        // Add 'general' preferences.
        addPreferencesFromResource(R.xml.pref_general);

        // Add 'notifications' preferences, and a corresponding header.
        //PreferenceCategory fakeHeader = new PreferenceCategory(this);
        //fakeHeader.setTitle(R.string.pref_header_notifications);
        //getPreferenceScreen().addPreference(fakeHeader);
        //addPreferencesFromResource(R.xml.pref_notification);

        // Add 'data and sync' preferences, and a corresponding header.
        PreferenceCategory fakeHeader = new PreferenceCategory(this);
        fakeHeader.setTitle(R.string.pref_header_data_sync);
        getPreferenceScreen().addPreference(fakeHeader);
        addPreferencesFromResource(R.xml.pref_data_sync);

        // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
        // their values. When their values change, their summaries are updated
        // to reflect the new value, per the Android Design guidelines.
        bindPreferenceSummaryToValue(findPreference("my_location"));
        bindPreferenceSummaryToValue(findPreference("sync_frequency"));
    }

    /**
     * A preference value change listener that updates the preference's summary
     * to reflect its new value.
     */
    private static Preference.OnPreferenceChangeListener sBindPreferenceSummaryToValueListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object value) {
            String stringValue = value.toString();

            if (preference instanceof ListPreference) {
                // For list preferences, look up the correct display value in
                // the preference's 'entries' list.
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue(stringValue);

                // Set the summary to reflect the new value.
                preference.setSummary(
                        index >= 0
                                ? listPreference.getEntries()[index]
                                : null);
            } else {
                // For all other preferences, set the summary to the value's
                // simple string representation.
                preference.setSummary(stringValue);
            }
            return true;
        }
    };

    /** Syncs the synchronization preference with the ContentResolver */
    private static Preference.OnPreferenceChangeListener sSyncFrequencyListener = new Preference.OnPreferenceChangeListener() {
        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            ContentResolver res = preference.getContext().getContentResolver();
            Integer newValInt = (Integer) newValue;

            AccountManager mgr = AccountManager.get(preference.getContext());
            for(Account acct : mgr.getAccountsByType(Authenticator.ACCOUNT_TYPE)) {
                if(newValInt > 0) {
                    res.addPeriodicSync(acct, PumpContentProvider.AUTHORITY, null, 60 * newValInt);
                    res.addPeriodicSync(acct, ContactsContract.AUTHORITY, null, 60 * newValInt);
                } else {
                    res.removePeriodicSync(acct, PumpContentProvider.AUTHORITY, null);
                    res.removePeriodicSync(acct, ContactsContract.AUTHORITY, null);
                }
            }

            return sBindPreferenceSummaryToValueListener.onPreferenceChange(preference, newValue);
        }
    };

    /**
     * Binds a preference's summary to its value. More specifically, when the
     * preference's value is changed, its summary (line of text below the
     * preference title) is updated to reflect the value. The summary is also
     * immediately updated upon calling this method. The exact display format is
     * dependent on the type of preference.
     *
     * @see #sBindPreferenceSummaryToValueListener
     */
    private static void bindPreferenceSummaryToValue(Preference preference) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(sBindPreferenceSummaryToValueListener);

        // Trigger the listener immediately with the preference's
        // current value.
        sBindPreferenceSummaryToValueListener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }
}

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
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.support.v4.app.NavUtils;

import com.github.machinarius.preferencefragment.PreferenceFragment;

import eu.e43.impeller.api.Content;
import eu.e43.impeller.R;
import eu.e43.impeller.account.Authenticator;

public class SettingsActivity extends ActionBarActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);


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
            PreferenceCategory fakeHeader = new PreferenceCategory(getActivity());
            fakeHeader.setTitle(R.string.pref_header_data_sync);
            getPreferenceScreen().addPreference(fakeHeader);
            addPreferencesFromResource(R.xml.pref_data_sync);

            // Bind the summaries of EditText/List/Dialog/Ringtone preferences to
            // their values. When their values change, their summaries are updated
            // to reflect the new value, per the Android Design guidelines.
            bindPreferenceSummaryToValue(findPreference("my_location"));
            bindPreferenceToListener(findPreference("sync_frequency"), sSyncFrequencyListener);
        }
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
            Integer newValInt = Integer.parseInt(newValue.toString());

            AccountManager mgr = AccountManager.get(preference.getContext());
            for(Account acct : mgr.getAccountsByType(Authenticator.ACCOUNT_TYPE)) {
                Bundle empty = new Bundle();
                if(newValInt > 0) {
                    res.addPeriodicSync(acct, Content.AUTHORITY, empty, 60 * newValInt);
                    res.addPeriodicSync(acct, ContactsContract.AUTHORITY, empty, 60 * newValInt);
                } else {
                    res.removePeriodicSync(acct, Content.AUTHORITY, empty);
                    res.removePeriodicSync(acct, ContactsContract.AUTHORITY, empty);
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
        bindPreferenceToListener(preference, sBindPreferenceSummaryToValueListener);
    }

    private static void bindPreferenceToListener(Preference preference, Preference.OnPreferenceChangeListener listener) {
        // Set the listener to watch for value changes.
        preference.setOnPreferenceChangeListener(listener);

        // Trigger the listener immediately with the preference's
        // current value.
        listener.onPreferenceChange(preference,
                PreferenceManager
                        .getDefaultSharedPreferences(preference.getContext())
                        .getString(preference.getKey(), ""));
    }
}

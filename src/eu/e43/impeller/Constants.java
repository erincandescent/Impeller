package eu.e43.impeller;

import android.accounts.Account;
import android.content.Intent;

/** Random useful constants */
public class Constants {
    /// Notify that the direct inbox has been displayed (Broadcast)
    public static final String ACTION_DIRECT_INBOX_OPENED = "eu.e43.impeller.action.DIRECT_INBOX_DISPLAYED";

    /// Show a specific feed (Activity)
    public static final String ACTION_SHOW_FEED = "eu.e43.impeller.action.SHOW_FEED";

    /// Notification that a new feed entry has been received
    public static final String ACTION_NEW_FEED_ENTRY = "eu.e43.impeller.action.NEW_FEED_ENTRY";

    public static Intent makeShowFeedIntent(Account acct, FeedID id) {
        Intent i = new Intent(ACTION_SHOW_FEED);
        i.putExtra(EXTRA_ACCOUNT, acct);
        i.putExtra(EXTRA_FEED_ID, id);
        return i;
    }

    // Impeller extras
    /// Account object to be passed
    public static final String EXTRA_ACCOUNT       = "eu.e43.impeller.extra.ACCOUNT";
    /// feed_entry._ID database value (int)
    public static final String EXTRA_FEED_ENTRY_ID = "eu.e43.impeller.extra.FEED_ENTRY_ID";
    /// JSON object to be replied to
    public static final String EXTRA_IN_REPLY_TO   = "eu.e43.impeller.extra.IN_REPLY_TO";
    /// An Impeller database content ID
    public static final String EXTRA_CONTENT_URI   = "eu.e43.impeller.extra.CONTENT_URI";
    /// The feed to be displayed [FeedID]
    public static final String EXTRA_FEED_ID       = "eu.e43.impeller.extra.FEED_ID";


    // ActivityStreams extras
    public static final String EXTRA_ACTIVITYSTREAMS_ID     = "ms.activitystrea.OBJECT_ID";
    public static final String EXTRA_ACTIVITYSTREAMS_OBJECT = "ms.activitystrea.OBJECT";

    // Preferences
    public static final String PREF_SYNC_FREQUENCY = "sync_frequency";
    public static final String PREF_LOCATION_MAPS  = "location_maps";
    public static final String PREF_MY_LOCATION    = "my_location";

    public static final int MY_LOCATION_NEVER = 0;
    public static final int MY_LOCATION_FETCH = 1;
    public static final int MY_LOCATION_SET   = 2;

    /** List of user's feeds */
    public enum FeedID {
        MAJOR_FEED(R.string.feed_major),
        MINOR_FEED(R.string.feed_minor),
        DIRECT_FEED(R.string.feed_direct);

        private int nameString = 0;

        private FeedID(int ns) {
            nameString = ns;
        }

        public int getNameString() {
            return nameString;
        }
    }
}

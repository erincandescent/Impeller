package eu.e43.impeller.content;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import eu.e43.impeller.Constants;
import eu.e43.impeller.R;
import eu.e43.impeller.Utils;
import eu.e43.impeller.activity.MainActivity;
import eu.e43.impeller.activity.PostActivity;
import eu.e43.impeller.uikit.ActivityUtils;
import eu.e43.impeller.uikit.ImageLoader;

/**
 * Created by oshepherd on 17/04/2014.
 */
public class FeedNotificationService extends Service {
    private static final String TAG = "FeedNotificationService";
    public static final String ACTION_NOTIFY_DIRECT = "eu.e43.impeller.content.FeedNotificationService.DirectMessage";

    private final List<PendingNotification> m_pendingNotifications = new ArrayList<PendingNotification>();
    int m_latestStartId;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        m_latestStartId = startId;

        Log.v(TAG, "Got " + intent);
        if(intent.getAction().equals(ACTION_NOTIFY_DIRECT)) {
            try {
                return processDirectNotification(intent);
            } catch (JSONException e) {
                throw new RuntimeException("Database contains invalid object", e);
            }
        } else if(intent.getAction().equals(Constants.ACTION_DIRECT_INBOX_OPENED)) {
            SharedPreferences prefs = getSharedPreferences("notifications", MODE_PRIVATE);
            Account acct = intent.getParcelableExtra(Constants.EXTRA_ACCOUNT);
            int shown = intent.getIntExtra(Constants.EXTRA_FEED_ENTRY_ID, 0);
            String tag = "eu.e43.impeller.direct_notice:" + acct.name;

            for(PendingNotification not : m_pendingNotifications) {
                not.maybeCancel(acct, shown);
            }

            NotificationManagerCompat.from(this).cancel(tag, 0);

            prefs.edit().putInt("viewed:"   + acct.name, shown).commit();
        }
        maybeStopService();
        return START_NOT_STICKY;
    }

    private void maybeStopService() {
        if(m_pendingNotifications.isEmpty())
            stopSelfResult(m_latestStartId);
    }

    private int processDirectNotification(Intent intent) throws JSONException {
        Account acct = intent.getParcelableExtra(Constants.EXTRA_ACCOUNT);
        String acctId = AccountManager.get(this).getUserData(acct, "id");

        PumpContentProvider.Uris uris = PumpContentProvider.Uris.get(acct);
        SharedPreferences prefs = getSharedPreferences("notifications", MODE_PRIVATE);

        String notifiedKey = "notified:" + acct.name;
        int viewed    = prefs.getInt("viewed:"   + acct.name, 0);
        int notified  = prefs.getInt(notifiedKey, 0);

        // Grab the entries in the direct feed since the user last looked at it
        ContentResolver res = getContentResolver();
        Cursor c = res.query(uris.feedUri, new String[]{"_ID", "_json"},
                "(SELECT COUNT(*) FROM recipients WHERE recipients.activity=activity._ID " +
                        "AND recipients.recipient=(SELECT _ID FROM objects WHERE id=?)) AND " +
                        "(verb='share' OR (verb='post' AND object.objectType<>'comment')) AND " +
                        "(feed_entries._ID > ?)",
                new String[]{ acctId, Integer.toString(viewed) },
                "feed_entries._ID ASC");

        NotificationCompat.Builder groupBuilder = null;
        NotificationCompat.InboxStyle groupInbox = null;

        if(c.getCount() > 1) {
            String tag = "eu.e43.impeller.direct_notice:" + acct.name;
            groupBuilder = new NotificationCompat.Builder(this);
            String title = String.format(getString(R.string.message_group_notification_header),
                    c.getCount());

            groupInbox = new NotificationCompat.InboxStyle();
            groupInbox.setBigContentTitle(title);
            groupInbox.setSummaryText(acct.name);

            groupBuilder.setContentTitle(title);
            groupBuilder.setContentText(acct.name);
            groupBuilder.setSmallIcon(R.drawable.ic_impeller_wb);

            PendingIntent showIntent =
                    PendingIntent.getActivity(this, 0,
                        new Intent(Constants.ACTION_SHOW_FEED)//, null, this, MainActivity.class)
                            .putExtra(Constants.EXTRA_ACCOUNT, acct)
                            .putExtra(Constants.EXTRA_FEED_ID, Constants.FeedID.DIRECT_FEED), 0);

            groupBuilder.setContentIntent(showIntent);

            groupBuilder.setNumber(c.getCount());
            groupBuilder.setGroup(tag);
            groupBuilder.setGroupSummary(true);
            groupBuilder.setStyle(groupInbox);
        }

        List<CharSequence> lineBuffer = new ArrayList<CharSequence>();

        int mostRecent = 0;
        while(c.moveToNext()) {
            mostRecent = c.getInt(0);
            String activityJSON = c.getString(1);
            JSONObject activity = new JSONObject(activityJSON);
            lineBuffer.add(Html.fromHtml(ActivityUtils.localizedDescription(this, activity)));

            if(mostRecent > notified) {
                Notification groupNotice = null;
                if(c.isLast() && groupBuilder != null) {
                    for(int i = lineBuffer.size() - 1; i >= 0; i--) {
                        groupInbox.addLine(lineBuffer.get(i));
                    }
                    groupNotice = groupBuilder.build();
                }

                PendingNotification notice = new PendingNotification(acct, mostRecent, activity, groupNotice);
                m_pendingNotifications.add(notice);
            }
        }

        prefs.edit().putInt(notifiedKey, mostRecent).commit();

        maybeStopService();
        return START_REDELIVER_INTENT;
    }

    private class PendingNotification {
        private final JSONObject m_activity;
        private final Account m_acct;
        private final NotificationCompat.Builder m_builder;
        private final ImageLoader m_imageLoader;
        private final int m_entryId;
        private final Notification m_groupNotice;
        private boolean m_canceled = false;

        /// Cancel the activity if acct matches and shown >= m_entryId
        public void maybeCancel(Account acct, int shown) {
            if(acct.equals(m_acct) && shown >= m_entryId) {
                m_canceled = true;
            }
        }

        public PendingNotification(Account acct, int entryId, JSONObject activity,
                                   Notification groupNotice) throws JSONException {
            m_acct = acct;
            m_entryId = entryId;
            m_activity = activity;
            m_imageLoader = new ImageLoader(FeedNotificationService.this, acct);

            m_groupNotice = groupNotice;
            m_builder = new NotificationCompat.Builder(FeedNotificationService.this);

            JSONObject actor = m_activity.getJSONObject("actor");
            String actorName = actor.optString("displayName", actor.getString("id"));

            m_builder.setSmallIcon(R.drawable.ic_impeller_wb);
            m_builder.setAutoCancel(true);
            m_builder.setContentTitle(String.format(getString(R.string.direct_notification_title), actorName));
            m_builder.setContentText(Html.fromHtml(m_activity.optString("content", "(No activity string)")));

            m_imageLoader.load(new AvatarListener(),
                    Utils.getImageUrl(FeedNotificationService.this, acct, actor.optJSONObject("image")));
        }

        private class AvatarListener implements ImageLoader.Listener {
            @Override
            public void loaded(BitmapDrawable dr, URI uri) {
                setIcon(dr.getBitmap());
            }

            @Override
            public void error(URI uri) {
                setIcon(BitmapFactory.decodeResource(getResources(), R.drawable.noavatar));
            }
        }

        private void setIcon(Bitmap icon) {
            if(m_canceled) { doneNotification(this); return; }

            // Set density of the icon for appropriate scaling across all displays
            // We don't rescale the image so that maximum quality can be preserved if e.g. it is
            // broadcast to an Android Wear device

            int largestDimension = Math.max(icon.getHeight(), icon.getWidth());
            float scaleFactor    = largestDimension / 64.f;
            int   targetDpi      = (int) (DisplayMetrics.DENSITY_DEFAULT * scaleFactor + .5f);

            icon = icon.copy(Bitmap.Config.ARGB_8888, true);
            icon.setDensity(targetDpi);

            Log.d(TAG, "Scaled to size " + icon.getWidth() + "x" + icon.getHeight());
            m_builder.setLargeIcon(icon);

            try {
                JSONObject obj = m_activity.getJSONObject("object");
                JSONObject img = obj.optJSONObject("image");
                if (img != null) {
                    m_imageLoader.load(new ImageListener(), Utils.getImageUrl(
                            FeedNotificationService.this, m_acct, img));
                } else {
                    NotificationCompat.BigTextStyle style = new NotificationCompat.BigTextStyle();
                    if (obj.has("displayName"))
                        style.setBigContentTitle(Html.fromHtml(obj.optString("displayName")));

                    style.bigText(Html.fromHtml(obj.optString("content")));
                    m_builder.setStyle(style);

                    finalizeNotification();
                }
            } catch (JSONException e) {
                Log.e(TAG, "Missing object", e);
                doneNotification(this);
            }
        }

        private class ImageListener implements ImageLoader.Listener {
            @Override
            public void loaded(BitmapDrawable dr, URI uri) {
                NotificationCompat.BigPictureStyle style = new NotificationCompat.BigPictureStyle();
                style.bigPicture(dr.getBitmap());

                JSONObject object = m_activity.optJSONObject("object");
                if (object.has("displayName"))
                    style.setBigContentTitle(object.optString("displayName"));
                m_builder.setStyle(style);

                finalizeNotification();
            }

            @Override
            public void error(URI uri) {
                finalizeNotification();
            }
        }

        private void finalizeNotification() {
            if(m_canceled) { doneNotification(this); return; }

            String tag = "eu.e43.impeller.direct_notice:" + m_acct.name;

            Intent showIntent = new Intent(
                    Intent.ACTION_VIEW, Uri.parse(m_activity.optJSONObject("object").optString("id")),
                    FeedNotificationService.this, MainActivity.class);
            showIntent.putExtra(Constants.EXTRA_ACCOUNT, m_acct);

            Intent replyIntent = new Intent(PostActivity.ACTION_REPLY, null, FeedNotificationService.this, PostActivity.class);
            replyIntent.putExtra(Constants.EXTRA_ACCOUNT, m_acct);
            replyIntent.putExtra(Constants.EXTRA_IN_REPLY_TO, m_activity.optJSONObject("object").toString());

            m_builder.setContentIntent(PendingIntent.getActivity(FeedNotificationService.this, 0, showIntent, 0));
            m_builder.addAction(R.drawable.ic_action_reply, "Reply",
                    PendingIntent.getActivity(FeedNotificationService.this, 0, replyIntent, 0));
            m_builder.setGroup(tag);
            m_builder.setAutoCancel(true);
            Notification notice = m_builder.build();

            NotificationManagerCompat mgr = NotificationManagerCompat.from(FeedNotificationService.this);


            mgr.notify(tag, m_entryId, notice);

            if(m_groupNotice != null) {
                mgr.notify(tag, 0, m_groupNotice);
            }

            doneNotification(this);
        }
    }

    void doneNotification(PendingNotification not) {
        m_pendingNotifications.remove(not);
        maybeStopService();
    }
}

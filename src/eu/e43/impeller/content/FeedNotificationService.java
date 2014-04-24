package eu.e43.impeller.content;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.text.Html;
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
                PendingNotification not = new PendingNotification(
                        (Account) intent.getParcelableExtra("account"),
                        intent.getData(),
                        intent.getStringExtra("activity"));
                m_pendingNotifications.add(not);

                return START_REDELIVER_INTENT;
            } catch (JSONException e) {
                Log.e(TAG, "Bad activity", e);

                if(m_pendingNotifications.isEmpty()) {
                    stopSelfResult(startId);
                }
                return START_NOT_STICKY;
            }
        } else {
            if(m_pendingNotifications.isEmpty())
                stopSelfResult(startId);
            return START_NOT_STICKY;
        }
    }

    private class PendingNotification {
        private final JSONObject m_activity;
        private final Uri m_contentUri;
        private final Account m_acct;
        private final NotificationCompat.Builder m_builder;
        private final ImageLoader m_imageLoader;

        public PendingNotification(Account acct, Uri contentUri, String activity) throws JSONException {
            m_acct = acct;
            m_contentUri = contentUri;
            m_activity = new JSONObject(activity);
            m_imageLoader = new ImageLoader(FeedNotificationService.this, acct);

            m_builder = new NotificationCompat.Builder(FeedNotificationService.this);

            JSONObject actor = m_activity.getJSONObject("actor");
            String actorName = actor.optString("displayName", actor.getString("id"));

            m_builder.setSmallIcon(R.drawable.ic_impeller_wb);
            m_builder.setAutoCancel(true);
            m_builder.setContentTitle(String.format(getString(R.string.direct_notification_title), actorName));
            m_builder.setContentText(Html.fromHtml(m_activity.optString("content", "(No activity string)")));

            m_imageLoader.load(new AvatarListener(), Utils.getImageUrl(actor.optJSONObject("image")));
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
                    m_imageLoader.load(new ImageListener(), Utils.getImageUrl(img));
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
            Intent showIntent = new Intent(
                    Intent.ACTION_VIEW, Uri.parse(m_activity.optJSONObject("object").optString("id")),
                    FeedNotificationService.this, MainActivity.class);
            showIntent.putExtra("account", m_acct);

            Intent replyIntent = new Intent(PostActivity.ACTION_REPLY, null, FeedNotificationService.this, PostActivity.class);
            replyIntent.putExtra("account", m_acct);
            replyIntent.putExtra("inReplyTo", m_activity.optJSONObject("object").toString());

            m_builder.setContentIntent(PendingIntent.getActivity(FeedNotificationService.this, 0, showIntent, 0));
            m_builder.addAction(R.drawable.ic_action_reply, "Reply",
                    PendingIntent.getActivity(FeedNotificationService.this, 0, replyIntent, 0));
            m_builder.setAutoCancel(true);
            Notification notice = m_builder.build();

            NotificationManager mgr = (NotificationManager) FeedNotificationService.this.getSystemService(Context.NOTIFICATION_SERVICE);
            mgr.notify(m_contentUri.toString(), Constants.NOTIFICATION_DIRECT, notice);

            doneNotification(this);
        }
    }

    void doneNotification(PendingNotification not) {
        m_pendingNotifications.remove(not);
        if(m_pendingNotifications.isEmpty()) {
            stopSelfResult(m_latestStartId);
        }
    }
}

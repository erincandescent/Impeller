package eu.e43.impeller;
import android.app.Application;

import com.atlassian.jconnect.droid.Api;

import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

@ReportsCrashes(
        formKey = "",
        mode = ReportingInteractionMode.DIALOG,
        resDialogEmailPrompt = R.string.crash_notification_dialog_request_user_email,
        resNotifTickerText = R.string.crash_notification_title,
        resNotifTitle = R.string.crash_notification_title,
        resNotifText = R.string.crash_notification_text,
        resDialogText = R.string.crash_notification_dialog_text)
public class ImpellerApplication extends Application {
    @Override
    public void onCreate() {
        Api.init(this);
        super.onCreate();
    }
}

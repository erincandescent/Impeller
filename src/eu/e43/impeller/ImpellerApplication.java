package eu.e43.impeller;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Debug;

import com.atlassian.jconnect.droid.Api;

import org.acra.ReportingInteractionMode;
import org.acra.annotation.ReportsCrashes;

import eu.e43.impeller.account.Authenticator;
import eu.e43.impeller.content.PumpContentProvider;

@ReportsCrashes(
        formKey = "",
        mode = ReportingInteractionMode.DIALOG,
        resDialogEmailPrompt = R.string.crash_notification_dialog_request_user_email,
        resNotifTickerText = R.string.crash_notification_title,
        resNotifTitle = R.string.crash_notification_title,
        resNotifText = R.string.crash_notification_text,
        resDialogText = R.string.crash_notification_dialog_text)
public class ImpellerApplication extends Application {
    public static Typeface fontAwesome;
    public static int ms_versionCode;

    @Override
    public void onCreate() {
        if(!Debug.isDebuggerConnected()) {
            Api.init(this);
        }

        super.onCreate();

        try {
            PackageInfo packageInfo = null;
            packageInfo = getPackageManager()
                    .getPackageInfo(getPackageName(), 0);
            ms_versionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // That'll be the day...
            throw new RuntimeException(e);
        }

        SharedPreferences prefs = getSharedPreferences("Impeller", MODE_PRIVATE);
        int oldVersion = prefs.getInt("version", 0);

        if(oldVersion < 16) {
            AccountManager mgr = (AccountManager) getSystemService(ACCOUNT_SERVICE);
            Account[] accts = mgr.getAccountsByType(Authenticator.ACCOUNT_TYPE);
            for(Account a : accts) {
                getContentResolver().setSyncAutomatically(a, PumpContentProvider.AUTHORITY, true);
            }
        }

        prefs.edit().putInt("version", ms_versionCode).apply();

        fontAwesome = Typeface.createFromAsset(getAssets(), "FontAwesome.otf");
    }
}

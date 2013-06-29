package com.atlassian.jconnect.droid.ui;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class UiUtil {

    private UiUtil() {
        throw new AssertionError("Don't instantiate me");
    }

    public static void alert(Context context, String msg) {
        Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
    }

    public static void alert(Context context, int msgResId, Object... params) {
        alert(context, context.getString(msgResId, params));
    }

    public static String getTextFromView(Activity activity, int viewId) {
        return findView(activity, viewId, TextView.class).getText().toString();
    }

    public static <T extends View> T findView(Activity context, int viewId, Class<T> viewType) {
        return viewType.cast(context.findViewById(viewId));
    }

    public static <T extends View> T findView(View parentView, int viewId, Class<T> viewType) {
        return viewType.cast(parentView.findViewById(viewId));
    }

    public static TextView findTextView(View parentView, int viewId) {
        return findView(parentView, viewId, TextView.class);
    }
}

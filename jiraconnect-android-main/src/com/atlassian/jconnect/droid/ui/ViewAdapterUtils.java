package com.atlassian.jconnect.droid.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public final class ViewAdapterUtils {

    private ViewAdapterUtils() {
        throw new AssertionError("Don't instantiate me");
    }

    public static View getOrInflate(Context context, int viewId, View convertView, ViewGroup parent) {
        if (convertView != null) {
            return convertView;
        } else {
            return getInflater(context).inflate(viewId, parent, false);
        }
    }

    public static LayoutInflater getInflater(Context context) {
        return (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

}

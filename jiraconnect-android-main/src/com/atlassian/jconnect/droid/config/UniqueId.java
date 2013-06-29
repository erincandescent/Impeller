package com.atlassian.jconnect.droid.config;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

public final class UniqueId {

    private static final String UUID_KEY = "com.atlassian.jconnect.droid.config.UUID";

    private final Context owner;

    private final String udid;
    private final String uuid;

    public UniqueId(Context owner) {
        this.owner = checkNotNull(owner);
        this.udid = initUdid();
        this.uuid = initUuid();
    }

    private String initUdid() {
        String androidId = Settings.Secure.getString(owner.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (androidId == null) {
            throw new AssertionError("ANDROID_ID setting was null");
        }
        return androidId;
    }

    private String initUuid() {
        final SharedPreferences prefs = owner.getSharedPreferences(BaseConfig.PREFERENCES_NAME, Context.MODE_PRIVATE);
        String uuid = prefs.getString(UUID_KEY, null);
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            prefs.edit().putString(UUID_KEY, uuid).commit();
        }
        return uuid;
    }

    public String getUdid() {
        return udid;
    }

    public String getUuid() {
        return uuid;
    }

}

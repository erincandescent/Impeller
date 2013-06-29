package com.atlassian.jconnect.droid.config;

import android.content.Context;
import android.content.SharedPreferences;
import com.atlassian.jconnect.droid.R;
import com.google.common.collect.ImmutableMap;

import java.util.Map;

import static com.google.common.base.Preconditions.*;
import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * <p/>
 * Reads the main configuration of the JMC: JIRA URL, project and API key.
 * 
 * <p/>
 * Those configuration values are assumed to be present in the app's static
 * resources.
 * 
 * @since 1.0
 * @author Dariusz Kordonski &lt;dkordonski@atlassian.com&gt;
 */
public final class BaseConfig {

    public static final String PREFERENCES_NAME = "com.atlassian.jconnect.droid.config";

    // TODO 1) api key may be null?
    public static final String SERVER_URL_KEY = "jconnect.droid.config.server_url";
    public static final String PROJECT_KEY = "jconnect.droid.config.project";
    public static final String API_KEY = "jconnect.droid.config.api_key";

    private static final Map<String, Integer> ALL_PROPERTIES_KEYS = ImmutableMap.of(
            SERVER_URL_KEY,
            R.string.jconnect_droid_config_server_url,
            PROJECT_KEY,
            R.string.jconnect_droid_config_project,
            API_KEY,
            R.string.jconnect_droid_config_api_key);

    private final Context context;
    private final SharedPreferences preferences;
    private final UniqueId uniqueId;

    public BaseConfig(Context context) {
        this.context = checkNotNull(context);
        this.preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        this.uniqueId = new UniqueId(context);
    }

    public boolean hasError() {
        for (String value : allProperties().values()) {
            if (isNullOrEmpty(value)) {
                return true;
            }
        }
        return false;
    }

    public String getError() {
        for (Map.Entry<String, String> property : allProperties().entrySet()) {
            if (isNullOrEmpty(property.getValue())) {
                return context.getString(R.string.jconnect_droid_no_config_property, property.getKey());
            }
        }
        throw new IllegalStateException("No error");
    }

    private Map<String, String> allProperties() {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        for (String propKey : ALL_PROPERTIES_KEYS.keySet()) {
            builder.put(propKey, getNoCheck(propKey));
        }
        return builder.build();
    }

    public String getServerUrl() {
        return getProperty(SERVER_URL_KEY);
    }

    public BaseConfig setServerUrl(String serverUrl) {
        return setProperty(SERVER_URL_KEY, serverUrl);
    }

    public String getProjectKey() {
        return getProperty(PROJECT_KEY);
    }

    public BaseConfig setProjectKey(String projectKey) {
        return setProperty(PROJECT_KEY, projectKey);
    }

    public String getApiKey() {
        return getProperty(API_KEY);
    }

    public BaseConfig setApiKey(String apiKey) {
        return setProperty(API_KEY, apiKey);
    }

    private BaseConfig setProperty(String propKey, String propValue) {
        checkArgument(!isNullOrEmpty(propValue), "'" + propKey + "' value cannot be empty");
        preferences.edit().putString(propKey, propValue).commit();
        return this;
    }

    public UniqueId uniqueId() {
        return uniqueId;
    }

    private String getProperty(String propertyKey) {
        if (hasError()) {
            throw new IllegalStateException("There is an error: " + getError());
        }
        return getNoCheck(propertyKey);
    }

    private String getNoCheck(String propertyKey) {
        return preferences.getString(propertyKey, getDefaultValue(propertyKey));
    }

    private String getDefaultValue(String propertyKey) {
        final Integer resId = ALL_PROPERTIES_KEYS.get(propertyKey);
        checkState(resId != null, "No resource ID for property key '" + propertyKey + "'");
        return context.getString(resId);
    }

}

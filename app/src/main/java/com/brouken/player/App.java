package com.brouken.player;

import android.app.Application;
import android.preference.PreferenceManager;

import io.sentry.android.core.SentryAndroid;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        final String dsn = BuildConfig.SENTRY_DSN;
        if (dsn == null || dsn.isEmpty())
            return;
        SentryAndroid.init(this, options -> {
            options.setDsn(dsn);
            options.setRelease(BuildConfig.APPLICATION_ID + "@" + BuildConfig.VERSION_NAME);
            options.setDist(String.valueOf(BuildConfig.VERSION_CODE));
            options.setEnvironment(BuildConfig.DEBUG ? "debug" : "release");
            // Honor the user's consent toggle. Checked per event (and for events cached offline or
            // from a crash and sent on the next launch), so switching it off takes effect immediately.
            options.setBeforeSend((event, hint) ->
                    PreferenceManager.getDefaultSharedPreferences(this)
                            .getBoolean("crashReporting", true) ? event : null);
        });
    }
}

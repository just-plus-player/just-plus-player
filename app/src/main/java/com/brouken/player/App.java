package com.brouken.player;

import android.app.Application;
import android.preference.PreferenceManager;

import io.sentry.SentryEvent;
import io.sentry.android.core.SentryAndroid;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;

import java.util.List;

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
            options.setBeforeSend((event, hint) -> {
                // Honor the user's consent toggle. Checked per event (and for events cached offline or
                // from a crash and sent on the next launch), so switching it off takes effect immediately.
                if (!PreferenceManager.getDefaultSharedPreferences(this)
                        .getBoolean("crashReporting", true)) {
                    return null;
                }
                // Drop URL query strings that ExoPlayer bakes into error messages (e.g. "Response code:
                // 403 for https://host/path?token=...") so tokens/session ids never leave the device.
                stripUrlQueries(event);
                return event;
            });
        });
    }

    private static void stripUrlQueries(final SentryEvent event) {
        final Message message = event.getMessage();
        if (message != null) {
            message.setFormatted(Utils.stripUrlQuery(message.getFormatted()));
            message.setMessage(Utils.stripUrlQuery(message.getMessage()));
        }
        final List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException exception : exceptions) {
                exception.setValue(Utils.stripUrlQuery(exception.getValue()));
            }
        }
    }
}

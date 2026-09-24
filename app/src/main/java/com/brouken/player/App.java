package com.brouken.player;

import android.app.Application;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatDelegate;

import com.brouken.player.skip.SegmentFinder;

import io.sentry.SentryEvent;
import io.sentry.android.core.SentryAndroid;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;

import java.util.List;
import java.util.Map;

public class App extends Application {

    /**
     * Process start, captured when this class is loaded — the first app code to run. Used by the error
     * report's uptime line; a static beats Process.getStartElapsedRealtime() only in that it needs no
     * API-level branch (that call arrived in API 24, this app supports 23).
     */
    static final long START_ELAPSED = SystemClock.elapsedRealtime();

    @Override
    public void onCreate() {
        super.onCreate();
        // Before any screen reads them: the settings screen is an entry point of its own, and a value
        // left by a withdrawn option would otherwise sit there unnamed until something played.
        Prefs.migrateWithdrawnValues(
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));
        // Here rather than in PlayerActivity, because "System" appearance resolves to whatever default
        // is in force and this is the only place guaranteed to have run. Prefs.isLight answers false on
        // a television whatever the system says, and Prefs.getNightMode leaves "System" UNSPECIFIED so
        // it defers to this default - but SettingsActivity is an APPLICATION_PREFERENCES entry point,
        // so it can be the first activity in the process. Set in PlayerActivity, the default was not yet
        // there: a television reporting a light or undefined night mode got the light Material base with
        // the dark accent overlay on top - dark grounds lettered in near-black.
        if (Utils.isTvBox(this)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        // The defaults the settings screen would otherwise write the first time somebody opens it -
        // androidx.preference persists every default as it inflates. Written here instead, before any
        // playback, because the player compares the whole preference store across a trip to that screen
        // (Prefs.snapshot): three dozen keys appearing at once while a film is paused behind it read as
        // "a setting changed" and rebuilt the player - over a torrent server, a stream re-opened from
        // zero for a screen that was opened and closed. false: existing choices are never overwritten.
        androidx.preference.PreferenceManager.setDefaultValues(this, R.xml.root_preferences, false);
        initSentry();
        // Lets the skip-segment lookups honour the caching their sources ask for (see the method).
        SegmentFinder.setCacheDir(getCacheDir());
        // Installed after Sentry so it wraps (and chains to) Sentry's crash handler rather than
        // replacing it: any uncaught crash lands on ErrorActivity, then Sentry still reports.
        ErrorActivity.installCrashHandler(this);
    }

    private void initSentry() {
        // Turned off for everyone, not just as a default: the consent switch is hidden, so a stored
        // "on" from an earlier build would otherwise keep reporting with no way to stop it.
        if (!BuildConfig.ENABLE_CRASH_REPORTING)
            return;
        final String dsn = BuildConfig.SENTRY_DSN;
        if (dsn == null || dsn.isEmpty())
            return;
        SentryAndroid.init(this, options -> {
            options.setDsn(dsn);
            options.setRelease(BuildConfig.APPLICATION_ID + "@" + BuildConfig.VERSION_NAME);
            options.setDist(String.valueOf(BuildConfig.VERSION_CODE));
            options.setEnvironment(BuildConfig.DEBUG ? "debug" : "release");
            // The player's own trace (Utils.log) doubles as breadcrumbs. A playback session that is
            // recovering logs a line per load, state change and rung, so the default hundred would hold
            // only the last minute of it.
            options.setMaxBreadcrumbs(500);
            // Breadcrumbs bypass beforeSend, so they get the same sanitisation on their own way in.
            options.setBeforeBreadcrumb((breadcrumb, hint) -> {
                breadcrumb.setMessage(Utils.stripUrlQuery(breadcrumb.getMessage()));
                return breadcrumb;
            });
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
        // Tags and extras need the same pass: a Format's id/label can be a URL (SubtitleUtils stores the
        // subtitle's own uri there), and any future tag would otherwise bypass this sanitisation silently.
        final Map<String, String> tags = event.getTags();
        if (tags != null) {
            for (Map.Entry<String, String> tag : tags.entrySet()) {
                tag.setValue(Utils.stripUrlQuery(tag.getValue()));
            }
        }
        final Map<String, Object> extras = event.getExtras();
        if (extras != null) {
            for (Map.Entry<String, Object> extra : extras.entrySet()) {
                if (extra.getValue() instanceof String) {
                    extra.setValue(Utils.stripUrlQuery((String) extra.getValue()));
                }
            }
        }
    }
}

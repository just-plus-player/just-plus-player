package com.brouken.player.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.StyleSpan;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.brouken.player.BuildConfig;
import com.brouken.player.R;

/**
 * Shared, Activity-agnostic UI for the self-updater: the "update available" dialog and the
 * download-progress dialog that ends in the system installer. Kept free of {@code Prefs} so it can
 * live in this subpackage; the optional "skip this version" action is passed in as a {@link Runnable}.
 */
public final class UpdateUi {

    private UpdateUi() {}

    /**
     * Shows the "update available" dialog. {@code onSkip} — when non-null — adds a "Skip this
     * version" button that runs it (used by the silent auto-check; the manual check passes null).
     * {@code warnPlaybackStops} spells out that installing ends the film: the dialog is reachable
     * mid-playback from the button beside the gear, and the installer takes the process with it.
     */
    public static void showAvailableDialog(final Activity activity, final UpdateInfo info,
                                           final Runnable onSkip, final boolean warnPlaybackStops) {
        if (activity.isFinishing()) {
            return;
        }
        final int padH = dp(activity, 20);
        final TextView message = new TextView(activity);
        final String header = activity.getString(R.string.update_available, BuildConfig.VERSION_NAME, info.versionName);
        final String changelog = info.changelog != null ? info.changelog.trim() : "";

        final SpannableStringBuilder text = new SpannableStringBuilder(header);
        text.setSpan(new StyleSpan(Typeface.BOLD), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (warnPlaybackStops) {
            text.append("\n").append(activity.getString(R.string.update_stops_playback));
        }
        if (!changelog.isEmpty()) {
            text.append("\n\n").append(MarkdownRenderer.render(changelog));
        }
        message.setText(text);
        message.setMovementMethod(LinkMovementMethod.getInstance());

        final ScrollView scroll = new ScrollView(activity);
        scroll.setPadding(padH, dp(activity, 8), padH, 0);
        scroll.addView(message);

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.pref_update_header)
                .setView(scroll)
                .setPositiveButton(R.string.update_now, (dialog, which) -> startDownload(activity, info))
                .setNegativeButton(R.string.update_later, null);
        if (onSkip != null) {
            builder.setNeutralButton(R.string.update_skip, (dialog, which) -> onSkip.run());
        }
        builder.show();
    }

    /** Downloads the APK with a progress dialog, then launches the system installer. */
    public static void startDownload(final Activity activity, final UpdateInfo info) {
        if (activity.isFinishing()) {
            return;
        }
        final ProgressBar bar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(info.size <= 0);

        final int pad = dp(activity, 20);
        final LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(bar);

        // The worker does not exist yet — the dialog has to be built first so the callbacks below can
        // dismiss it — so the cancel action reaches it through this holder. Nothing can read it before it
        // is assigned: show() only posts, and the assignment happens before this returns to the looper.
        final Thread[] download = new Thread[1];
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_downloading)
                .setView(layout)
                // Without this the dialog had no way out at all: a download that stalls without failing
                // (a connection that trickles bytes never reaches OkHttp's read timeout) left the user
                // with a modal that BACK could not dismiss, which on a TV means Home is the only escape.
                .setNegativeButton(android.R.string.cancel, (d, which) -> cancelDownload(download[0]))
                // BACK does the same as the button rather than nothing: it is what people reach for, and
                // merely dismissing would leave the download running to fire the installer later, out of
                // any context. Cancelling is not dismissing, so a normal finish does not come through here.
                .setOnCancelListener(d -> cancelDownload(download[0]))
                .create();
        dialog.show();

        download[0] = Updater.downloadApkAsync(activity, info,
                percent -> activity.runOnUiThread(() -> {
                    // Only the first tick has anything to switch: setting this per percent swapped the
                    // drawable and invalidated a hundred times over a download.
                    if (bar.isIndeterminate()) {
                        bar.setIndeterminate(false);
                    }
                    bar.setProgress(percent);
                }),
                file -> activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()) {
                        dialog.dismiss();
                    }
                    if (file != null) {
                        Updater.installApk(activity, file);
                    } else {
                        Toast.makeText(activity, R.string.update_download_failed, Toast.LENGTH_LONG).show();
                    }
                }));
    }

    /**
     * Stops a download the user called off. Interrupting is the whole cancel: the read loop checks the flag
     * every chunk, and {@code downloadApkAsync} suppresses its own callback once interrupted, so no
     * installer is launched and no failure toast is shown for something nobody is waiting for any more.
     * A socket read already in flight is not interruptible, so the worker can sit in one until OkHttp's
     * read timeout — harmless: it is a daemon thread, and the partial file is deleted by the next download.
     */
    private static void cancelDownload(final Thread download) {
        if (download != null) {
            download.interrupt();
        }
    }

    private static int dp(final Activity activity, final int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

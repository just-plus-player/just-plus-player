package com.brouken.player.update;

import android.app.Activity;
import android.app.AlertDialog;
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
     */
    public static void showAvailableDialog(final Activity activity, final UpdateInfo info, final Runnable onSkip) {
        if (activity.isFinishing()) {
            return;
        }
        final int padH = dp(activity, 20);
        final TextView message = new TextView(activity);
        final String header = activity.getString(R.string.update_available, BuildConfig.VERSION_NAME, info.versionName);
        final String changelog = info.changelog != null ? info.changelog.trim() : "";
        message.setText(changelog.isEmpty() ? header : header + "\n\n" + changelog);

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

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_downloading)
                .setView(layout)
                .setCancelable(false)
                .create();
        dialog.show();

        Updater.downloadApkAsync(activity, info,
                percent -> activity.runOnUiThread(() -> {
                    bar.setIndeterminate(false);
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

    private static int dp(final Activity activity, final int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}

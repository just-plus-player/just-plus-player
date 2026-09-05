package com.brouken.player.update;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.text.format.Formatter;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import com.brouken.player.BuildConfig;
import com.brouken.player.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Shared, Activity-agnostic UI for the self-updater: the "update available" dialog and the
 * download-progress dialog that ends in the system installer. Kept free of {@code Prefs} so it can
 * live in this subpackage; the optional "skip this version" action is passed in as a {@link Runnable}.
 */
public final class UpdateUi {

    private UpdateUi() {}

    /**
     * Shows the "update available" dialog: a header that answers what is being offered — the new version,
     * against the one installed, with its date and weight — and the release notes under it.
     * {@code onSkip} — when non-null — adds a "Skip this version" action that runs it (used by the silent
     * auto-check; the manual check passes null). {@code warnPlaybackStops} spells out that installing ends
     * the film: the dialog is reachable mid-playback from the button beside the gear, and the installer
     * takes the process with it.
     */
    public static void showAvailableDialog(final Activity activity, final Context ctx,
                                           final UpdateInfo info,
                                           final Runnable onSkip, final boolean warnPlaybackStops) {
        if (activity.isFinishing()) {
            return;
        }
        final int pad = dp(ctx, 24);
        final int ink = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, 0);
        final int quiet = MaterialColors.getColor(ctx,
                com.google.android.material.R.attr.colorOnSurfaceVariant, ink);
        final int accent = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, ink);

        final LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(pad, dp(ctx, 20), pad, 0);

        // The mark of what this is, then what it is: an icon, the offer, and the version it brings — the
        // one place in the dialog the accent is spent, because the version number is what the whole sheet
        // is about.
        final ImageView icon = new ImageView(ctx);
        icon.setImageDrawable(ContextCompat.getDrawable(ctx, R.drawable.ic_update_24dp));
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(quiet));
        final LinearLayout.LayoutParams iconLp =
                new LinearLayout.LayoutParams(dp(ctx, 32), dp(ctx, 32));
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        column.addView(icon, iconLp);

        column.addView(text(ctx, activity.getString(R.string.update_title), 22, ink, Typeface.DEFAULT_BOLD,
                Gravity.CENTER_HORIZONTAL, dp(ctx, 12)));
        column.addView(text(ctx, info.versionName, 16, accent, null,
                Gravity.CENTER_HORIZONTAL, dp(ctx, 2)));

        // What the release is, in the four numbers anybody weighs it by. A row is left out rather than
        // filled with a dash: an unknown size or date says nothing worth a line of its own.
        final LinearLayout table = new LinearLayout(ctx);
        table.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams tableLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tableLp.topMargin = dp(ctx, 20);
        column.addView(table, tableLp);
        addRow(table, activity.getString(R.string.update_current_version), BuildConfig.VERSION_NAME, quiet, ink);
        addRow(table, activity.getString(R.string.update_latest_version), info.versionName, quiet, ink);
        addRow(table, activity.getString(R.string.update_release_date), releaseDate(ctx, info.publishedAt),
                quiet, ink);
        addRow(table, activity.getString(R.string.update_size),
                info.size > 0 ? Formatter.formatFileSize(ctx, info.size) : null, quiet, ink);

        if (warnPlaybackStops) {
            column.addView(text(ctx, activity.getString(R.string.update_stops_playback), 14, quiet, null,
                    Gravity.START, dp(ctx, 16)));
        }

        final String changelog = info.changelog != null ? info.changelog.trim() : "";
        if (!changelog.isEmpty()) {
            column.addView(text(ctx, activity.getString(R.string.update_release_notes), 14, accent, null,
                    Gravity.START, dp(ctx, 20)));
            final TextView notes = text(ctx, null, 14, quiet, null, Gravity.START, dp(ctx, 6));
            notes.setText(MarkdownRenderer.render(changelog));
            notes.setMovementMethod(LinkMovementMethod.getInstance());
            column.addView(notes);
        }

        final ScrollView scroll = new ScrollView(ctx);
        scroll.addView(column);

        final LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // The actions are the dialog's own row rather than the button bar's: three of them want more
        // lettering than a 360dp phone has, and a bar that cannot fit them clips instead of wrapping.
        // This one wraps — whatever does not fit goes to a line below, still ending at the same edge.
        final FlowRow actions = new FlowRow(ctx);
        actions.setPadding(pad, dp(ctx, 20), pad, dp(ctx, 12));
        final LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        content.addView(actions, actionsLp);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setView(content)
                .create();

        if (onSkip != null) {
            actions.addView(action(ctx, R.string.update_skip, false, v -> {
                dialog.dismiss();
                onSkip.run();
            }));
        }
        actions.addView(action(ctx, R.string.update_later, false, v -> dialog.dismiss()));
        final View forward = action(ctx, R.string.update_now, true, v -> {
            dialog.dismiss();
            startDownload(activity, ctx, info);
        });
        actions.addView(forward);
        // A remote needs somewhere to start, and the notes need to be reachable from there: focus opens on
        // the action this dialog exists to offer, and Up from the row enters the scroll rather than dying.
        scroll.setFocusable(true);
        dialog.show();
        forward.requestFocus();
    }

    /** One line of the release table: what it is on the left, what it says on the right. */
    private static void addRow(final LinearLayout table, final String label, final String value,
                               final int labelInk, final int valueInk) {
        if (value == null || value.isEmpty()) {
            return;
        }
        final Context ctx = table.getContext();
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(ctx, 6), 0, dp(ctx, 6));
        // The label takes what is left after the value, so a long version string wraps the label rather
        // than being pushed off the row; both widths are set here, since the shared builder hands back a
        // full-width view for the column this table sits in.
        final TextView name = text(ctx, label, 14, labelInk, null, Gravity.START, 0);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);
        final TextView read = text(ctx, value, 14, valueInk, null, Gravity.END, 0);
        final LinearLayout.LayoutParams readLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        readLp.setMarginStart(dp(ctx, 16));
        read.setLayoutParams(readLp);
        row.addView(read);
        table.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** A dialog action: filled for the one that moves things forward, outlined for the rest. */
    private static View action(final Context ctx, final int label, final boolean forward,
                               final View.OnClickListener onClick) {
        final MaterialButton button = forward
                ? new MaterialButton(ctx)
                : new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        if (!forward) {
            button.setTextColor(MaterialColors.getColor(ctx,
                    com.google.android.material.R.attr.colorOnSurfaceVariant, button.getCurrentTextColor()));
        }
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMinHeight(dp(ctx, 48));
        button.setOnClickListener(onClick);
        return button;
    }

    private static TextView text(final Context ctx, final String value, final int sizeSp, final int ink,
                                 final Typeface face, final int gravity, final int topMargin) {
        final TextView view = new TextView(ctx);
        if (value != null) {
            view.setText(value);
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(ink);
        if (face != null) {
            view.setTypeface(face);
        }
        view.setGravity(gravity);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        view.setLayoutParams(lp);
        return view;
    }

    /** GitHub's timestamp in the reader's own date format, or null when the release carried none. */
    private static String releaseDate(final Context ctx, final String publishedAt) {
        if (publishedAt == null || publishedAt.isEmpty()) {
            return null;
        }
        try {
            final SimpleDateFormat github = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            github.setTimeZone(TimeZone.getTimeZone("UTC"));
            final Date date = github.parse(publishedAt);
            return date == null ? null : DateFormat.getMediumDateFormat(ctx).format(date);
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * A row of controls that wraps. Android has no such container of its own, and the dialog button bar
     * does not wrap: it lays three actions out in a line and clips whatever runs past the edge. Children
     * keep their natural width, end at the same edge as the text above them, and drop to a new line when
     * the one they are on is full.
     */
    private static final class FlowRow extends ViewGroup {

        private final int gap;

        FlowRow(final Context context) {
            super(context);
            gap = dp(context, 8);
        }

        @Override
        protected void onMeasure(final int widthSpec, final int heightSpec) {
            final int width = MeasureSpec.getSize(widthSpec);
            final int usable = width - getPaddingLeft() - getPaddingRight();
            int x = 0;
            int rowHeight = 0;
            int height = 0;
            for (int i = 0; i < getChildCount(); i++) {
                final View child = getChildAt(i);
                measureChild(child, MeasureSpec.makeMeasureSpec(usable, MeasureSpec.AT_MOST), heightSpec);
                if (x > 0 && x + child.getMeasuredWidth() > usable) {
                    height += rowHeight + gap;
                    x = 0;
                    rowHeight = 0;
                }
                x += child.getMeasuredWidth() + gap;
                rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
            }
            setMeasuredDimension(width, height + rowHeight + getPaddingTop() + getPaddingBottom());
        }

        @Override
        protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
            final int usable = getWidth() - getPaddingLeft() - getPaddingRight();
            int y = getPaddingTop();
            int first = 0;
            while (first < getChildCount()) {
                int width = 0;
                int rowHeight = 0;
                int past = first;
                while (past < getChildCount()) {
                    final View child = getChildAt(past);
                    if (width > 0 && width + child.getMeasuredWidth() > usable) {
                        break;
                    }
                    width += child.getMeasuredWidth() + gap;
                    rowHeight = Math.max(rowHeight, child.getMeasuredHeight());
                    past++;
                }
                // Ending where the content above ends, so the row reads as the foot of the sheet rather
                // than a band of its own.
                int x = getPaddingLeft() + usable - (width - gap);
                for (int i = first; i < past; i++) {
                    final View child = getChildAt(i);
                    child.layout(x, y, x + child.getMeasuredWidth(), y + child.getMeasuredHeight());
                    x += child.getMeasuredWidth() + gap;
                }
                y += rowHeight + gap;
                first = past;
            }
        }
    }

    /** Downloads the APK with a progress dialog, then launches the system installer. */
    public static void startDownload(final Activity activity, final Context dialogContext,
                                     final UpdateInfo info) {
        if (activity.isFinishing()) {
            return;
        }
        final ProgressBar bar = new ProgressBar(dialogContext, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setIndeterminate(info.size <= 0);

        final int pad = dp(dialogContext, 20);
        final LinearLayout layout = new LinearLayout(dialogContext);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);
        layout.addView(bar);

        // The worker does not exist yet — the dialog has to be built first so the callbacks below can
        // dismiss it — so the cancel action reaches it through this holder. Nothing can read it before it
        // is assigned: show() only posts, and the assignment happens before this returns to the looper.
        final Thread[] download = new Thread[1];
        final AlertDialog dialog = new MaterialAlertDialogBuilder(dialogContext)
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

    private static int dp(final Context context, final int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}

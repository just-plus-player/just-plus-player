package com.brouken.player;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The report screen: a friendly, full-screen surface for anything the app wants to hand over as text -
 * a playback error, a crash, a playback dump asked for on purpose. The panel shows only the caller's
 * short summary and what else the report carries; the full diagnostic report (device + media URI +
 * stack/cause chain - the same detail sent to Sentry) is what Copy / Share / Upload carry.
 *
 * <p>Opened three ways today: {@link #installCrashHandler} for an uncaught exception, the player's
 * {@code showErrorScreen} for a fatal playback error, and {@link #showReport} for a dump nobody had to
 * fail for. The extras are the whole contract, so any other part of the app can open it the same way.
 */
public class ErrorActivity extends AppCompatActivity {

    /**
     * The screen's name in the top bar. The card carries no headline, so this is the only name it has,
     * and what it says is what opened the screen: a crash, a playback failure, a playback report, or
     * whatever asks next. Left out, the bar reads the neutral "Report" the layout carries - this screen
     * is not an error screen, it is the one place the app hands text over.
     */
    public static final String EXTRA_TITLE = "title";
    /** Optional cause in words, shown above the summary; absent unless the caller can name it. */
    public static final String EXTRA_MESSAGE = "message";
    /** Short, human-facing text shown in the panel: error code + message. */
    public static final String EXTRA_SUMMARY = "summary";
    /** Full diagnostic report body; a device/app header is prepended for Copy/Share/Upload. */
    public static final String EXTRA_REPORT = "report";

    private String report;
    private String uploadedUrl;
    private boolean uploading;

    private MaterialButton btnUpload;
    private View uploadResult;
    private TextView uploadUrl;
    private ImageView qrImage;

    @Override
    protected void onApplyThemeResource(final Resources.Theme theme, final int resid, final boolean first) {
        super.onApplyThemeResource(theme, resid, first);
        // Here and not in onCreate, for the reason SettingsActivity records: when the appearance differs
        // from the system's, AppCompat sets the window theme again on the way to the recreated activity,
        // and an overlay put on in onCreate is gone by the time anything is drawn.
        theme.applyStyle(Prefs.accentOverlay(this, Prefs.isLight(this)), true);
    }

    /**
     * And again after a night mode change that did not rebuild the activity, which takes the overlay
     * off - the reason SettingsActivity records.
     */
    @Override
    public void onNightModeChanged(final int mode) {
        getTheme().applyStyle(Prefs.accentOverlay(this, Prefs.isLight(this)), true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Before super, or AppCompat applies the old mode first and then recreates.
        getDelegate().setLocalNightMode(Prefs.getNightMode(this));
        super.onCreate(savedInstanceState);

        // The appearance in force, never the configuration - see the note in SettingsActivity.onCreate.
        // AMOLED goes on before anything asks for the decor view, which takes its background from the
        // theme as it stands at that moment.
        final boolean night = !Prefs.isLight(this);
        if (night && Prefs.isAmoledBlack(this)) {
            getTheme().applyStyle(R.style.ThemeOverlay_JustPlus_Amoled, true);
        }
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!night);
        // Uniform behaviour across versions, for the reason BrowserActivity records: from API 35 edge to
        // edge is enforced and the decor insets nothing, so an activity must inset itself - which is how
        // the screen's name came to sit under the status bar on a recent phone. Up to API 34 the decor
        // still fits the window and would apply the same insets again, doubling the gap; turning the
        // fitting off makes both versions behave the same way.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_error);
        applyInsets();

        // Strip URL query strings (tokens/session ids) from everything shown, copied, shared or uploaded
        // — matching Sentry's beforeSend sanitisation. Critical because the report can be pasted publicly
        // (termbin) and ExoPlayer bakes full URLs into exception messages/stack traces. Skipped only when
        // the person turned masking off, to hand over the very link that failed.
        final boolean mask = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Prefs.PREF_KEY_MASK_REPORTS, true);
        final String summary = mask ? Utils.stripUrlQuery(getIntent().getStringExtra(EXTRA_SUMMARY))
                : getIntent().getStringExtra(EXTRA_SUMMARY);
        final String body = mask ? Utils.stripUrlQuery(getIntent().getStringExtra(EXTRA_REPORT))
                : getIntent().getStringExtra(EXTRA_REPORT);
        report = buildReport(body);

        final MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.errorDetails)).setText(summary != null ? summary : "");
        // No headline, glyph or explanation of its own: "Something went wrong" and "the app is fine, go
        // back and try again" said nothing the card does not, and the owner asked for the block gone. A
        // caller's name for the screen goes where Material keeps one; a named cause goes into the card.
        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null) {
            toolbar.setTitle(title);
        }
        final String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (message != null) {
            final TextView cause = findViewById(R.id.errorMessage);
            cause.setText(message);
            cause.setVisibility(View.VISIBLE);
        }
        fillRows(body);

        btnUpload = findViewById(R.id.btnUpload);
        uploadResult = findViewById(R.id.uploadResult);
        uploadUrl = findViewById(R.id.uploadUrl);
        qrImage = findViewById(R.id.qrImage);

        // Glyphs only, so each one says its name on a long press as well as to a screen reader; the
        // upload button's name follows its state (upload, uploading, copy link).
        final MaterialButton btnCopy = findViewById(R.id.btnCopy);
        final MaterialButton btnShare = findViewById(R.id.btnShare);
        label(btnCopy, R.string.error_copy);
        label(btnShare, R.string.error_share);
        label(btnUpload, R.string.error_upload);
        // The contour the rest of the app's buttons take on a remote: the filled one's edge appears, the
        // outlined ones' widens 1dp -> 2dp.
        for (final MaterialButton button : new MaterialButton[]{btnCopy, btnShare, btnUpload}) {
            Utils.focusRing(button);
        }
        btnCopy.setOnClickListener(v -> copy(report));
        // Nothing on a TV box can empty the clipboard - no keyboard, no text app - and nothing there can
        // take a share either: a text/plain intent on Android TV is answered by Bluetooth, an e-mail stub
        // and this player itself, none of which sends a report anywhere. Both are dead ends that only
        // take D-pad stops away from Upload, which is how a report actually leaves a box.
        if (Utils.isTvBox(this)) {
            btnCopy.setVisibility(View.GONE);
            btnShare.setVisibility(View.GONE);
        }
        btnShare.setOnClickListener(v -> share(report));
        btnUpload.setOnClickListener(v -> upload());
        // The link and its code are one big button once they are there: the glyph that copies the link
        // says so only in a tooltip, and the URL itself is deliberately not selectable (it trapped the
        // D-pad on a television).
        uploadResult.setOnClickListener(v -> {
            if (uploadedUrl != null) {
                copy(uploadedUrl);
            }
        });

        if (Utils.isTvBox(this)) {
            // A set is read from three metres: the TV column of the type scale (DESIGN.md 4) rather than
            // the phone's, and the overscan strip a set may not show kept clear at the sides and foot.
            textSize(R.id.errorMessage, 17f);
            textSize(R.id.reportHeading, 22f);
            textSize(R.id.errorDetails, 15f);
            textSize(R.id.uploadUrl, 17f);
            textSize(R.id.uploadHint, 16f);
            for (final int id : new int[]{R.id.rowDevice, R.id.rowApp, R.id.rowPlayer, R.id.rowLog}) {
                textSize(id, 17f);
            }
        }

        // Nothing wears a focus mark where there is no remote. A window that opens without a touch -
        // which is every crash - is not in touch mode, so the first focusable view takes focus and draws
        // Material's state layer: a grey disc behind the arrow that nobody asked for and that the rest of
        // the app never shows. Letting the column itself hold that focus puts it where nothing is drawn.
        if (!Utils.isTvBox(this)) {
            final View column = findViewById(R.id.content);
            column.setFocusableInTouchMode(true);
            column.requestFocus();
        }

        // The one thing a remote came here to do. It was the back arrow until 2026-09-17 - the thinking
        // being that a screen nobody asked for should not publish on the first press - and the owner's
        // call is the button: on a box the report leaves by upload or not at all, and Back is a key of
        // its own that needs no focus. The arrow keeps Material's own focus wash for when it is reached,
        // the one every other bar in the app shows.
        if (Utils.isTvBox(this)) {
            btnUpload.requestFocus();
        }
    }

    /**
     * The system bars, the cutout and - on a television, where every system inset is zero - the 48/27dp
     * band a set may not show. The bar and the card both sit inside it, so the arrow's box and the
     * card's edge stand on one line; the foot of the column keeps clear of the navigation bar.
     */
    private void applyInsets() {
        final View root = findViewById(R.id.error_root);
        final View content = findViewById(R.id.content);
        final int pad = Utils.dpToPx(16);
        final UiMetrics metrics = UiMetrics.of(this, Utils.isTvBox(this));
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left + metrics.overscanH(), bars.top + metrics.overscanV(),
                    bars.right + metrics.overscanH(), 0);
            // Fixed base, never the view's own padding: this fires again on every rotation and a
            // read-then-add would grow the gap each time.
            content.setPadding(pad, pad, pad, pad + bars.bottom + metrics.overscanV());
            return windowInsets;
        });
    }

    /**
     * What else the report carries, read off the text that is about to leave rather than declared: the
     * device and build lines are the header {@link #buildReport} writes, the player line is present
     * exactly when the caller's dump has one, and the log count is counted.
     */
    private void fillRows(final String body) {
        ((TextView) findViewById(R.id.rowDevice)).setText(
                Build.MANUFACTURER + " " + Build.MODEL + " · Android " + Build.VERSION.RELEASE);
        // The name and version only: the build code and the flavour are in the header the report carries.
        ((TextView) findViewById(R.id.rowApp)).setText(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
        // appendPlayerState opens with the video format; a crash arrives with no player to ask.
        findViewById(R.id.rowPlayer).setVisibility(
                body != null && body.contains("\nVideo: ") ? View.VISIBLE : View.GONE);
        final int lines = logLines(body);
        final TextView rowLog = findViewById(R.id.rowLog);
        rowLog.setVisibility(lines > 0 ? View.VISIBLE : View.GONE);
        rowLog.setText(getResources().getQuantityString(R.plurals.report_log_lines, lines, lines));
    }

    /** Lines of the trace when the dump carries one (see appendPlayerState), else of the whole body. */
    static int logLines(final String body) {
        if (body == null || body.isEmpty()) {
            return 0;
        }
        final int at = body.indexOf("Trace:\n");
        final String log = at >= 0 ? body.substring(at + "Trace:\n".length()) : body.trim();
        if (log.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < log.length(); i++) {
            if (log.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    private void label(final MaterialButton button, final int textRes) {
        button.setContentDescription(getString(textRes));
        TooltipCompat.setTooltipText(button, getString(textRes));
        // A button that carries a label keeps it in step with its state - the television's upload button
        // is lettered, the phone's is a glyph with the same name in its tooltip and to a screen reader.
        if (button.getText().length() > 0) {
            button.setText(textRes);
        }
    }

    private void textSize(final int id, final float sp) {
        ((TextView) findViewById(id)).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /** What a toast used to say here. {@link Notice} is the one notice surface the app has (DESIGN.md 8). */
    private void notice(final int textRes, final int iconRes) {
        Notice.show(this, textRes, false, iconRes);
    }

    /**
     * Install a process-wide handler so any uncaught crash (not just playback errors) lands on this
     * screen too, then chains to the previously-registered handler — Sentry's when enabled — so crash
     * reporting and process termination still happen. Call once, after Sentry is initialised.
     */
    public static void installCrashHandler(final Context context) {
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                // This activity's empty taskAffinity puts the crash screen in a task of its own, so the
                // app's task keeps its history and is restored as the player. CLEAR_TASK then clears that
                // crash task rather than the app's — needed because a second crash would otherwise reuse
                // the existing task (same affinity, intents equal apart from extras) and, with no
                // onNewIntent handling, relaunch the FIRST crash's report.
                app.startActivity(new Intent(app, ErrorActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(EXTRA_TITLE, app.getString(R.string.crash_report_title))
                        .putExtra(EXTRA_SUMMARY, summaryOf(throwable))
                        .putExtra(EXTRA_REPORT, stackTrace(throwable)));
            } catch (Throwable ignored) {
                // Never let the error screen's own failure mask the original crash.
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                Process.killProcess(Process.myPid());
                System.exit(10);
            }
        });
    }

    /**
     * The same screen, opened on purpose rather than by a failure — so the playback dump gets the device
     * header, the QR and the three actions instead of a clipboard a TV box has no way to empty.
     */
    public static void showReport(final Context context, final String title, final String summary,
                                  final String report) {
        context.startActivity(new Intent(context, ErrorActivity.class)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_SUMMARY, summary)
                .putExtra(EXTRA_REPORT, report));
    }

    // The exception type as the "code" line, plus the deepest cause message — mirrors the playback path.
    static String summaryOf(final Throwable t) {
        final String message = rootMessage(t);
        final String name = t.getClass().getSimpleName();
        return message != null ? name + "\n" + message : name;
    }

    static String stackTrace(final Throwable t) {
        final StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    // Deepest non-empty message in the cause chain — the most specific description of what failed.
    static String rootMessage(final Throwable t) {
        String message = null;
        for (Throwable c = t; c != null; c = c.getCause()) {
            final String m = c.getLocalizedMessage();
            if (m != null && !m.isEmpty()) {
                message = m;
            }
        }
        return message;
    }

    private String buildReport(final String body) {
        // Header mirrors the metadata Sentry attaches (release, dist, environment, timestamp) so a
        // pasted/shared report carries at least as much context as a Sentry event. The device and
        // runtime blocks below carry what Sentry's device/app contexts did — they are what makes a
        // manually pasted report a viable replacement for automatic reporting.
        final String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date());
        final StringBuilder sb = new StringBuilder();
        sb.append(BuildConfig.APPLICATION_ID).append('@').append(BuildConfig.VERSION_NAME)
                .append(" (build ").append(BuildConfig.VERSION_CODE)
                .append(", ").append(BuildConfig.FLAVOR)
                .append(' ').append(BuildConfig.DEBUG ? "debug" : "release").append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (Android ").append(Build.VERSION.RELEASE)
                .append(", API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Time: ").append(time).append('\n');
        appendDevice(sb);
        appendRuntime(sb);
        sb.append('\n').append(body != null ? body : "");
        return sb.toString();
    }

    /**
     * Firmware, hardware and display detail — all of it from Build/Resources, so no permission is
     * involved. The fingerprint pins the exact OEM build (crashes cluster per firmware, not per model);
     * refresh rate and form factor matter because of frame-rate matching and the TV layout.
     */
    private void appendDevice(final StringBuilder sb) {
        sb.append("Build: ").append(Build.FINGERPRINT).append('\n');
        sb.append("Hardware: ").append(Build.DEVICE).append('/').append(Build.HARDWARE);
        if (Build.VERSION.SDK_INT >= 31) {
            sb.append(' ').append(Build.SOC_MODEL);
        }
        sb.append(", ").append(Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?")
                .append(", patch ").append(Build.VERSION.SECURITY_PATCH).append('\n');
        final DisplayMetrics metrics = getResources().getDisplayMetrics();
        sb.append("Display: ").append(metrics.widthPixels).append('x').append(metrics.heightPixels)
                .append(" @").append(metrics.densityDpi).append("dpi ")
                .append(String.format(Locale.US, "%.2fHz", refreshRate())).append(", ")
                .append(Utils.isTvBox(this) ? "tv" : Utils.isTablet(this) ? "tablet" : "phone")
                .append(getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                        ? ", landscape" : ", portrait").append('\n');
        sb.append("Locale: ").append(Locale.getDefault()).append(", ")
                .append(TimeZone.getDefault().getID()).append('\n');
    }

    /**
     * How the app was installed, how long it had been running and how much headroom it had — the
     * difference between "playback failed" and "playback failed 4 seconds in with the heap nearly full".
     */
    private void appendRuntime(final StringBuilder sb) {
        sb.append("Runtime: up ").append((SystemClock.elapsedRealtime() - App.START_ELAPSED) / 1000)
                .append("s, installer ").append(installer()).append('\n');
        final Runtime runtime = Runtime.getRuntime();
        sb.append("Memory: heap ").append((runtime.totalMemory() - runtime.freeMemory()) >> 20)
                .append('/').append(runtime.maxMemory() >> 20).append(" MB");
        final ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            final ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(memory);
            sb.append(", device ").append(memory.availMem >> 20).append(" MB free")
                    .append(memory.lowMemory ? " (low)" : "");
        }
        sb.append(", storage ").append(new StatFs(getFilesDir().getAbsolutePath()).getAvailableBytes() >> 20)
                .append(" MB free\n");
        // Same SharedPreferences read App.initSentry() uses — cheaper than building a Prefs, which
        // would also load unrelated playback state.
        final android.content.SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        sb.append("Prefs:")
                .append(BuildConfig.ENABLE_CRASH_REPORTING && prefs.getBoolean("crashReporting", false)
                        ? " crash reporting on" : " crash reporting off")
                .append(prefs.getBoolean(Prefs.PREF_KEY_MASK_REPORTS, true) ? "" : ", masking off")
                .append('\n');
    }

    private float refreshRate() {
        final DisplayManager dm = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        final Display display = dm != null ? dm.getDisplay(Display.DEFAULT_DISPLAY) : null;
        return display != null ? display.getRefreshRate() : 0f;
    }

    // Deprecated since API 30 in favour of getInstallSourceInformation(), itself replaced by
    // getInstallSourceInfo() in API 36 — this one call still answers "store install or sideload?" on
    // every supported level, which is all the report needs.
    @SuppressWarnings("deprecation")
    private String installer() {
        try {
            final String name = getPackageManager().getInstallerPackageName(getPackageName());
            // No installer at all means adb / a raw APK, which is worth telling apart from a store install.
            return name != null ? name : "none (sideloaded)";
        } catch (Exception e) {
            return "?";
        }
    }

    private void copy(final String text) {
        final ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Just+ Player error", text));
            notice(R.string.error_copied, R.drawable.ic_content_copy_24dp);
        }
    }

    /**
     * As a .txt file: a messenger cuts a message of five hundred trace lines short or splits it, and a
     * file arrives whole. Text only when the file cannot be written.
     */
    private void share(final String text) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.error_share_subject));
        try {
            // Stamped, so two reports in one chat can be told apart; the earlier ones are cleared, so the
            // cache keeps only the last and never a pile of them.
            final File dir = new File(getCacheDir(), "report");
            final File[] old = dir.listFiles();
            if (old != null) {
                for (File stale : old) {
                    stale.delete();
                }
            }
            dir.mkdirs();
            final File file = new File(dir, "justplus-report-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
            try (OutputStream out = new FileOutputStream(file)) {
                out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            intent.putExtra(Intent.EXTRA_STREAM,
                    FileProvider.getUriForFile(this, getPackageName() + ".provider", file));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (IOException | IllegalArgumentException e) {
            intent.putExtra(Intent.EXTRA_TEXT, text);
        }
        startActivity(Intent.createChooser(intent, getString(R.string.error_share)));
    }

    /**
     * The screen's one forward action, in its three states: send, sending, sent. Once the report is up
     * the button's job changes to handing over the link it earned — which is also how a television
     * reaches that link at all, the URL itself being unfocusable text. While the report is in flight the
     * glyph gives way to the ring, and a second press does nothing rather than a second upload.
     */
    private void upload() {
        if (uploading) {
            return;
        }
        if (uploadedUrl != null) {
            copy(uploadedUrl);
            return;
        }
        uploading = true;
        btnUpload.setIcon(spinner());
        label(btnUpload, R.string.error_uploading);
        new Thread(() -> {
            final String url = uploadToTermbin(report);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                uploading = false;
                if (url != null) {
                    uploadedUrl = url;
                    uploadState(R.drawable.ic_link_24dp, R.string.error_copy_link);
                    showUploaded(url);
                } else {
                    uploadState(R.drawable.ic_cloud_upload_24dp, R.string.error_upload);
                    notice(R.string.error_upload_failed, R.drawable.ic_cloud_upload_24dp);
                }
            });
        }).start();
    }

    private void uploadState(final int iconRes, final int nameRes) {
        btnUpload.setIconResource(iconRes);
        label(btnUpload, nameRes);
    }

    /**
     * What the button wears while the report is in flight: Material's own indeterminate ring, as the
     * button's icon rather than as a second view over it. One answer for both arrangements - beside the
     * television's label it turns where the cloud was, and on a phone it fills the 48dp disc alone. It
     * takes the button's own icon tint, so it is white on the accent without being told.
     */
    private Drawable spinner() {
        final CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(this, null, 0,
                com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall);
        spec.indicatorSize = Utils.dpToPx(24);
        spec.trackThickness = Utils.dpToPx(2);
        spec.indicatorInset = 0;
        // Its own paint, not the button's icon tint - an IndeterminateDrawable ignores the tint list, and
        // Material's own style colours the ring colorPrimary, which on this button is the coral it would
        // be turning on: measured invisible on the television.
        spec.indicatorColors = new int[]{MaterialColors.getColor(btnUpload,
                com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)};
        final Drawable ring = IndeterminateDrawable.createCircularDrawable(this, spec);
        ring.setVisible(true, true);
        if (ring instanceof Animatable) {
            ((Animatable) ring).start();
        }
        return ring;
    }

    private void showUploaded(final String url) {
        // A television is done here: the link cannot be pasted anywhere on a box and the QR is the
        // hand-off, so the row goes rather than standing there as a button that leads nowhere.
        if (Utils.isTvBox(this)) {
            findViewById(R.id.reportActions).setVisibility(View.GONE);
        }
        // The summary gives way to the QR and the heading goes with it: "what the report contains" is
        // answered by the lines below, and a code with a link under it needs no title to say what it is.
        findViewById(R.id.reportHeading).setVisibility(View.GONE);
        findViewById(R.id.errorDetails).setVisibility(View.GONE);
        uploadUrl.setText(url);
        uploadResult.setVisibility(View.VISIBLE);
        // Drawn here rather than fetched from api.qrserver.com, which is what this screen used to do: the
        // app already encodes its invite codes with zxing (Utils.qrBitmap), and a report's URL is not
        // something to hand to a third service on the way to showing it.
        // Encoded at the view's own pixel size, so nothing is scaled: an upscaled code is a blurred one.
        final Bitmap qr = Utils.qrBitmap(url, qrImage.getLayoutParams().width);
        if (qr != null) {
            qrImage.setImageBitmap(qr);
        }
    }

    // Raw-socket paste to termbin.com:9999 — it echoes back the public URL of the pasted text.
    private static String uploadToTermbin(final String text) {
        try (Socket socket = new Socket("termbin.com", 9999)) {
            socket.setSoTimeout(10000);
            final OutputStream out = socket.getOutputStream();
            out.write(text.getBytes("UTF-8"));
            out.flush();
            final InputStream in = socket.getInputStream();
            final StringBuilder sb = new StringBuilder();
            final byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) != -1) {
                sb.append(new String(buf, 0, n, "UTF-8"));
            }
            final String url = sb.toString().trim().replace(" ", "");
            return url.isEmpty() ? null : url;
        } catch (Exception e) {
            return null;
        }
    }

}

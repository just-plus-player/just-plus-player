package com.brouken.player;

import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

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
 * Friendly, full-screen surface for a playback error. The on-screen panel shows only the error code
 * and message; the full diagnostic report (device + media URI + stack/cause chain — the same detail
 * sent to Sentry) is what Copy / Share / Upload carry. Reached from the player's error handling.
 */
public class ErrorActivity extends AppCompatActivity {

    /** Optional headline override; defaults to the playback-error title in the layout. */
    public static final String EXTRA_TITLE = "title";
    /** Optional headline body override; defaults to the playback-error copy in the layout. */
    public static final String EXTRA_MESSAGE = "message";
    /** Short, human-facing text shown in the panel: error code + message. */
    public static final String EXTRA_SUMMARY = "summary";
    /** Full diagnostic report body; a device/app header is prepended for Copy/Share/Upload. */
    public static final String EXTRA_REPORT = "report";

    private String report;
    private String uploadedUrl;

    private MaterialButton btnUpload;
    private LinearProgressIndicator uploadProgress;
    private View uploadResult;
    private TextView uploadUrl;
    private ImageView qrImage;

    @Override
    protected void onApplyThemeResource(final Resources.Theme theme, final int resid, final boolean first) {
        super.onApplyThemeResource(theme, resid, first);
        theme.applyStyle(Prefs.accentOverlay(this, false), true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_error);

        // Strip URL query strings (tokens/session ids) from everything shown, copied, shared or uploaded
        // — matching Sentry's beforeSend sanitisation. Critical because the report can be pasted publicly
        // (termbin) and ExoPlayer bakes full URLs into exception messages/stack traces.
        final String summary = Utils.stripUrlQuery(getIntent().getStringExtra(EXTRA_SUMMARY));
        report = buildReport(Utils.stripUrlQuery(getIntent().getStringExtra(EXTRA_REPORT)));

        ((TextView) findViewById(R.id.errorDetails)).setText(summary != null ? summary : "");
        final String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null) {
            ((TextView) findViewById(R.id.errorTitle)).setText(title);
            // A title override means the screen was opened on purpose, so the error glyph would announce a
            // failure that did not happen; the logo mark keeps the halo without the alarm.
            ((ImageView) findViewById(R.id.errorIcon)).setImageResource(R.drawable.ic_logo_mark);
        }
        final String message = getIntent().getStringExtra(EXTRA_MESSAGE);
        if (message != null) {
            ((TextView) findViewById(R.id.errorMessage)).setText(message);
        }

        btnUpload = findViewById(R.id.btnUpload);
        uploadProgress = findViewById(R.id.uploadProgress);
        uploadResult = findViewById(R.id.uploadResult);
        uploadUrl = findViewById(R.id.uploadUrl);
        qrImage = findViewById(R.id.qrImage);

        // Every action is a Material button now, so each one takes the contour the rest of the app's
        // buttons take on a remote: the filled one's edge appears, the outlined ones' widens 1dp -> 2dp.
        for (final int id : new int[]{R.id.btnCopy, R.id.btnShare, R.id.btnUpload, R.id.btnClose}) {
            Utils.focusRing(findViewById(id));
        }
        findViewById(R.id.btnCopy).setOnClickListener(v -> copy(report));
        // Nothing on a TV box can empty the clipboard — no keyboard, no text app — so Copy is a dead end
        // there and only takes a D-pad stop away from Upload, which is how a report actually leaves the box.
        if (Utils.isTvBox(this)) {
            findViewById(R.id.btnCopy).setVisibility(View.GONE);
        }
        findViewById(R.id.btnShare).setOnClickListener(v -> share(report));
        btnUpload.setOnClickListener(v -> upload());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        // A set is read from three metres: the TV column of the type scale (DESIGN.md 4) rather than the
        // phone's, which is what the fixed sizes in the layout are.
        if (Utils.isTvBox(this)) {
            textSize(R.id.errorTitle, 26f);
            textSize(R.id.errorMessage, 17f);
            textSize(R.id.errorDetails, 15f);
        }

        // Close, not the filled button: sending the report publishes it, and the first press of a remote
        // on a screen the viewer did not ask for should not be the one that does that.
        findViewById(R.id.btnClose).requestFocus();

        animateIn();
    }

    private void textSize(final int id, final float sp) {
        ((TextView) findViewById(id)).setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    /** What a toast used to say here. A snackbar is the one notice surface the app has (DESIGN.md 8). */
    private void notice(final int textRes) {
        Snackbar.make(findViewById(android.R.id.content), textRes, Snackbar.LENGTH_SHORT).show();
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
                        .putExtra(EXTRA_MESSAGE, app.getString(R.string.error_crash_message))
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
    public static void showReport(final Context context, final String title, final String message,
                                  final String summary, final String report) {
        context.startActivity(new Intent(context, ErrorActivity.class)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MESSAGE, message)
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
        sb.append("Prefs: file access ").append(prefs.getString("fileAccess", "auto"))
                .append(BuildConfig.ENABLE_CRASH_REPORTING && prefs.getBoolean("crashReporting", false)
                        ? ", crash reporting on" : ", crash reporting off")
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

    private void animateIn() {
        // A subtle rise+fade so the screen doesn't slam in; skipped when the user disables animations.
        if (Utils.isReducedMotion(this)) {
            return;
        }
        final View root = findViewById(android.R.id.content);
        root.setAlpha(0f);
        root.setTranslationY(getResources().getDisplayMetrics().density * 16f);
        root.animate().alpha(1f).translationY(0f).setDuration(220).start();
    }

    private void copy(final String text) {
        final ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Just+ Player error", text));
            notice(R.string.error_copied);
        }
    }

    private void share(final String text) {
        final Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.error_share_subject));
        intent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(intent, getString(R.string.error_share)));
    }

    /**
     * The screen's one forward action, in its three states: send, sending, sent. Once the report is up
     * the button's job changes to handing over the link it earned — which is also how a television
     * reaches that link at all, the URL itself being unfocusable text.
     */
    private void upload() {
        if (uploadedUrl != null) {
            copy(uploadedUrl);
            return;
        }
        btnUpload.setEnabled(false);
        btnUpload.setText(R.string.error_uploading);
        uploadProgress.show();
        new Thread(() -> {
            final String url = uploadToTermbin(report);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                uploadProgress.hide();
                btnUpload.setEnabled(true);
                if (url != null) {
                    uploadedUrl = url;
                    btnUpload.setText(R.string.error_copy_link);
                    btnUpload.setIconResource(R.drawable.ic_link_24dp);
                    showUploaded(url);
                } else {
                    btnUpload.setText(R.string.error_upload);
                    notice(R.string.error_upload_failed);
                }
            });
        }).start();
    }

    private void showUploaded(final String url) {
        // Replace the code panel's content with the QR.
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

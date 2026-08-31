package com.brouken.player;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import androidx.appcompat.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.TextView;

/**
 * The branded page shown while there is no clip to play: an animated brand-mark reveal, the "Open
 * video" call to action, an "Open link" pill and the way into settings — together the only entry
 * points to media when nothing is loaded. Owns the page's layout, its reveal and its attention
 * pulse; the activity only says when the page comes and goes.
 */
class EmptyState {

    private final PlayerActivity activity;
    private ObjectAnimator pulse;

    EmptyState(PlayerActivity activity) {
        this.activity = activity;
    }

    boolean isVisible() {
        final View overlay = activity.findViewById(R.id.empty_state);
        return overlay != null && overlay.getVisibility() == View.VISIBLE;
    }

    void show() {
        final View overlay = activity.findViewById(R.id.empty_state);
        if (overlay == null) {
            return;
        }
        final View mark = activity.findViewById(R.id.empty_state_mark);
        final TextView title = activity.findViewById(R.id.empty_state_title);
        final TextView subtitle = activity.findViewById(R.id.empty_state_subtitle);
        final View open = activity.findViewById(R.id.empty_state_open);
        final View link = activity.findViewById(R.id.empty_state_link);
        final View room = activity.findViewById(R.id.empty_state_room);
        final View settings = activity.findViewById(R.id.empty_state_settings);

        // No video to match, so neither the orientation nor the brightness preference has anything to say
        // here: hand the page back to the system, whichever way the device is held and however bright it
        // keeps its screen. Both are reapplied by hide() once media takes over.
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        activity.mBrightnessControl.setActive(false, !Utils.isReducedMotion(activity));

        open.setOnClickListener(v -> activity.openFile(activity.mPrefs.mediaUri));
        link.setOnClickListener(v -> askForLink());
        // Joining needs no media of its own — the room says what it is playing — so this belongs on
        // the page you land on with a code in hand, not only in the player's gear menu.
        room.setOnClickListener(v -> activity.showJoinMenu());
        settings.setOnClickListener(v -> activity.openSettings());
        stopPulse();

        // TV is viewed from across the room; scale the phone-tuned sizes up, matching the
        // isTvBox sizing used elsewhere (poster, clock, skip button).
        // The overlay runs edge to edge, so the corner lockup has to dodge the status bar and any
        // cutout itself. The listener keeps it right across rotations, which don't recreate the activity.
        overlay.setOnApplyWindowInsetsListener((v, insets) -> {
            pad(v, insets);
            return insets;
        });
        pad(overlay, activity.coordinatorLayout.getRootWindowInsets());

        if (PlayerActivity.isTvBox) {
            setViewSize(activity.findViewById(R.id.empty_state_mark_icon), 40);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            setViewSize(activity.findViewById(R.id.empty_state_open_icon), 28);
            ((TextView) activity.findViewById(R.id.empty_state_open_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            final int padV = Utils.dpToPx(18);
            open.setPadding(Utils.dpToPx(30), padV, Utils.dpToPx(32), padV);
            open.setMinimumHeight(Utils.dpToPx(64));
            setViewSize(activity.findViewById(R.id.empty_state_link_icon), 28);
            ((TextView) activity.findViewById(R.id.empty_state_link_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            link.setPadding(Utils.dpToPx(26), padV, Utils.dpToPx(28), padV);
            link.setMinimumHeight(Utils.dpToPx(64));
            setViewSize(activity.findViewById(R.id.empty_state_room_icon), 28);
            ((TextView) activity.findViewById(R.id.empty_state_room_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            room.setPadding(Utils.dpToPx(26), padV, Utils.dpToPx(28), padV);
            room.setMinimumHeight(Utils.dpToPx(64));
            setViewSize(activity.findViewById(R.id.empty_state_settings_icon), 28);
            ((TextView) activity.findViewById(R.id.empty_state_settings_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            settings.setPadding(Utils.dpToPx(26), padV, Utils.dpToPx(28), padV);
            settings.setMinimumHeight(Utils.dpToPx(64));
        } else if (activity.ui.deviceClass != UiMetrics.DeviceClass.PHONE) {
            // Tablet: scale the phone XML defaults by the device-class factor (phone keeps the XML sizes).
            final UiMetrics ui = activity.ui;
            setViewSize(activity.findViewById(R.id.empty_state_mark_icon), ui.dpS(28));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(18));
            subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(20));
            setViewSize(activity.findViewById(R.id.empty_state_open_icon), ui.dpS(20));
            ((TextView) activity.findViewById(R.id.empty_state_open_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(16));
            setViewSize(activity.findViewById(R.id.empty_state_link_icon), ui.dpS(20));
            ((TextView) activity.findViewById(R.id.empty_state_link_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(16));
            setViewSize(activity.findViewById(R.id.empty_state_room_icon), ui.dpS(20));
            ((TextView) activity.findViewById(R.id.empty_state_room_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(16));
            setViewSize(activity.findViewById(R.id.empty_state_settings_icon), ui.dpS(20));
            ((TextView) activity.findViewById(R.id.empty_state_settings_label))
                    .setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.sp(15));
        }

        // The controls are still up behind this page: showController() is what keeps the system bars
        // with them (see the controller visibility listener), and this page is laid out around those
        // bars. A covered view is still a focusable one, though, so a remote walking down this column
        // carried on into the control bar's icons and clicked on things nobody could see. Fenced off
        // rather than hidden, so the bars stay where they are. Before the early return below, because
        // a page that comes back already visible needs the fence just as much.
        activity.playerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

        final boolean wasVisible = overlay.getVisibility() == View.VISIBLE;
        overlay.setVisibility(View.VISIBLE);
        if (!wasVisible) {
            // Reorders the children, so it relayouts the page — pointless on a page already in front.
            overlay.bringToFront();
        }

        final View[] items = {mark, title, subtitle, open, link, room, settings};

        // Nothing to reveal when the page is already on screen: coming back from the background releases
        // the player (savePlayer) and rebuilds it, landing here again on the very page the user is looking
        // at, and replaying the reveal there reads as a glitch. The pulse does not come back either — it
        // has already pointed the way once, and restarting it on a page in front of the user is both the
        // flicker it caused and rendering nobody asked for. Same settled end state as reduced motion, so
        // both take this path, cancelling first: a trip to the background can interrupt the reveal midway
        // and leave its animators pending.
        if (Utils.isReducedMotion(activity) || wasVisible) {
            for (View v : items) {
                v.animate().cancel();
                v.setAlpha(1f);
                v.setScaleX(1f);
                v.setScaleY(1f);
                v.setTranslationY(0f);
            }
            open.requestFocus();
            return;
        }

        final float density = activity.getResources().getDisplayMetrics().density;
        final PathInterpolator easeOutExpo = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

        // The title is part of the logo lockup now, so it reveals with the mark rather than
        // sliding in behind it.
        mark.setAlpha(0f);
        mark.setScaleX(0.85f);
        mark.setScaleY(0.85f);
        subtitle.setAlpha(0f);
        subtitle.setTranslationY(12 * density);
        open.setAlpha(0f);
        open.setTranslationY(16 * density);
        link.setAlpha(0f);
        link.setTranslationY(16 * density);
        room.setAlpha(0f);
        room.setTranslationY(16 * density);
        settings.setAlpha(0f);
        settings.setTranslationY(16 * density);

        mark.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(0).setDuration(450).setInterpolator(easeOutExpo).start();
        subtitle.animate().alpha(1f).translationY(0f)
                .setStartDelay(160).setDuration(300).setInterpolator(easeOutExpo).start();
        open.animate().alpha(1f).translationY(0f)
                .setStartDelay(260).setDuration(350).setInterpolator(easeOutExpo)
                .withEndAction(() -> {
                    open.requestFocus();
                    startPulse(open);
                }).start();
        link.animate().alpha(1f).translationY(0f)
                .setStartDelay(310).setDuration(350).setInterpolator(easeOutExpo).start();
        room.animate().alpha(1f).translationY(0f)
                .setStartDelay(355).setDuration(350).setInterpolator(easeOutExpo).start();
        settings.animate().alpha(1f).translationY(0f)
                .setStartDelay(400).setDuration(350).setInterpolator(easeOutExpo).start();
    }

    void hide() {
        // The controls own the remote again. FOCUS_AFTER_DESCENDANTS is the value PlayerView sets on
        // itself, so this puts back what was there rather than a guess at it.
        activity.playerView.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        // Media is taking the screen, so the preferences apply again — including after an empty state
        // relaxed them. No video format yet, so VIDEO lands on landscape and STATE_READY corrects it.
        Utils.setOrientation(activity, activity.mPrefs.orientation);
        activity.mBrightnessControl.setActive(true, !Utils.isReducedMotion(activity));
        stopPulse();
        final View overlay = activity.findViewById(R.id.empty_state);
        if (overlay != null && overlay.getVisibility() != View.GONE) {
            overlay.setVisibility(View.GONE);
        }
    }

    // Typing a URL into a player is the exception, so it lives behind a plain input dialog rather than
    // a surface of its own. Prefilled from the clipboard when that already holds a playable link — the
    // usual way one arrives here, and the only bearable one with a TV remote. Also offered by the gear
    // menu, which is why this is not private.
    void askForLink() {
        final EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("https://");
        final Uri pasted = clipboardUri();
        if (pasted != null) {
            input.setText(pasted.toString());
            input.setSelection(input.getText().length());
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.empty_state_link)
                .setView(input)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> openLink(input.getText().toString()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openLink(final String text) {
        final Uri uri = Uri.parse(text.trim());
        if (!Utils.isSupportedNetworkUri(uri)) {
            activity.showSnack(activity.getString(R.string.error_link_invalid), null);
            return;
        }
        // Hand the link to the same VIEW path a shared link takes through onNewIntent, so API state
        // reset, subtitle discovery and focus behave identically — and getIntent() keeps pointing at
        // what is actually playing.
        final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        activity.setIntent(intent);
        activity.handleViewIntent(intent);
        activity.initializePlayer();
    }

    private Uri clipboardUri() {
        final ClipboardManager cm =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = cm != null ? cm.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        final CharSequence text = clip.getItemAt(0).coerceToText(activity);
        if (text == null) {
            return null;
        }
        final Uri uri = Uri.parse(text.toString().trim());
        return Utils.isSupportedNetworkUri(uri) ? uri : null;
    }

    // Inset the page by the system bars plus a margin — 48dp on TV, where panels still cut
    // the outer few percent, 24dp elsewhere.
    private void pad(View overlay, WindowInsets insets) {
        int top = 0, left = 0, right = 0, bottom = 0;
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                final android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = bars.top;
                left = bars.left;
                right = bars.right;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                left = insets.getSystemWindowInsetLeft();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
        }
        // Mirror each axis on its larger inset: the centred block then sits on the screen's real centre
        // (a one-sided cutout would otherwise push it off), while every edge still clears its bar.
        final int pad = Utils.dpToPx(PlayerActivity.isTvBox ? 48 : 24);
        final int padH = pad + Math.max(left, right);
        final int padV = pad + Math.max(top, bottom);
        overlay.setPadding(padH, padV, padH, padV);
    }

    private void setViewSize(View view, int dp) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.width = Utils.dpToPx(dp);
        lp.height = Utils.dpToPx(dp);
        view.setLayoutParams(lp);
    }

    private void startPulse(View view) {
        // Rasterise the pill once and scale that texture. Scaling the view itself re-measures the label
        // and re-hints its glyphs every frame, which reads as the text twitching rather than growing.
        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        pulse = ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f));
        pulse.setDuration(1400);
        // Three breaths (six halves, ~8s) and done, ending back at 1f. Running forever kept the whole
        // window redrawing at the display's refresh rate for as long as the page was up — invisible on a
        // fast phone, a tax on battery and on weaker devices — and the pill has made its point by then.
        pulse.setRepeatCount(5);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        // Hands the texture back and leaves the page static once the pulse is over. Also runs on cancel,
        // which wants exactly the same cleanup.
        pulse.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                stopPulse();
            }
        });
        pulse.start();
    }

    void stopPulse() {
        if (pulse != null) {
            // Cleared before the cancel, which calls back into here through the animator's end listener.
            final ObjectAnimator running = pulse;
            pulse = null;
            running.cancel();
        }
        // Put the scale back: cancel() stops the pulse wherever it stood, a few percent up, while both
        // the next pulse and the reveal start from 1f — so without this the pill snaps down on whatever
        // comes next. It is what made the button jump when the app was reopened from the background.
        final View open = activity.findViewById(R.id.empty_state_open);
        if (open != null) {
            open.setScaleX(1f);
            open.setScaleY(1f);
            // Drop the layer — it is only worth its texture while animating, and nothing puts the pulse
            // back on a page that is already up, so this is the last time the pill is rasterised.
            open.setLayerType(View.LAYER_TYPE_NONE, null);
        }
    }
}

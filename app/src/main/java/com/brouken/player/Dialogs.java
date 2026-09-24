package com.brouken.player;

import static android.content.Context.UI_MODE_SERVICE;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.graphics.Outline;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.AudioManager;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.LocaleList;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.util.StateSet;
import android.view.Display;
import android.view.LayoutInflater;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewOutlineProvider;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.text.HtmlCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.annotation.OptIn;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.android.material.textfield.TextInputLayout;
import androidx.core.graphics.ColorUtils;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.animation.AnimationUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.sigpwned.chardet4j.Chardet;
import com.sigpwned.chardet4j.io.DecodedInputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;

/**
 * Every window the app raises and every notice it shows, in one place.
 *
 * <p>Windows, that is — every dialog and every panel. Dialogs used to be built from a Material builder
 * wherever one was needed and panels through a handful of helpers in the middle of {@link Utils}, so
 * what shape a question took was decided by whichever file it happened to be written in.
 *
 * <p><b>Not the notices.</b> The third channel the app speaks through lives in {@link Notice} — one
 * plate dropped at the top of the window, on every screen the app has, so the same refusal no longer
 * arrives as a toast here and a snackbar there. Two things still stand outside it: {@code
 * PlayerActivity.showSnack}, which trades the plate for a dialog on a television because its Details
 * button cannot be reached with a D-pad, and {@link #showText}, the line drawn over the video and
 * taken away again.
 *
 * <p>The design rules those surfaces answer to are written down in
 * {@code feature/material-design/DESIGN.md} — R1 (two grounds), R6 (ask is a dialog, browse is a
 * panel), R7 (the window decides), R8 (a panel is a docked sheet). This class is where they are
 * enforced rather than remembered: nothing below builds a window without going through
 * {@link #dialogContext}, and nothing decides its own shape.
 */
final class Dialogs {

    private Dialogs() {}

    /**
     * The line drawn over the video and taken away again — the same plate, on the same line, with the
     * same kind of glyph as every other thing this app says (see {@link Notice}).
     */
    public static void showText(final CustomPlayerView playerView, final CharSequence text,
                                final int iconRes, final long timeout) {
        playerView.removeCallbacks(playerView.textClearRunnable);
        playerView.readout(text, iconRes);
        playerView.postDelayed(playerView.textClearRunnable, timeout);
    }

    public static void showText(final CustomPlayerView playerView, final CharSequence text,
                                final int iconRes) {
        showText(playerView, text, iconRes, 1200);
    }

    /**
     * The one window recipe every player panel uses: a Material 3 card over a dimmed picture,
     * dismissible by a tap outside. Docked to the end edge and centred vertically, or docked to the
     * bottom edge on a tall narrow window — see the anchor rule below.
     *
     * <p>Not {@code SideSheetDialog} or {@code BottomSheetDialog}, deliberately. Both are fullscreen
     * windows, and a fullscreen dialog window makes OxygenOS treat the panel as immersive and apply its
     * two-swipe back-gesture guard, where a plain window closes on one back. The scrim comes from the
     * window's own dim instead, and the window stays a hair narrower than the screen so it never becomes
     * a fullscreen one. That is also why a bottom-docked panel here has no drag handle and no
     * swipe-to-dismiss: it is a plain window, and a handle that promised a drag it cannot perform would
     * be worse than no handle. It closes on a tap outside and on back, like every other panel.
     *
     * <p>Every panel in the player is this one shape, this one size and this one place, whichever button
     * opened it: a panel that arrives from a different edge or at a different width depending on the
     * press reads as several different panels, and the viewer has to learn each of them. What varies is
     * the window, not the content. A compact-width window — a phone held upright — takes the bottom
     * edge, which is what Material's size classes ask for on that class of window and where the thumb
     * already is. Everything wider takes the end edge: a phone or tablet held sideways is a
     * compact-height window, where a sheet from the bottom has less room than this card has and the
     * strip of picture beside it is worth keeping, and a television has no bottom sheet in its own
     * component set, an unreserved overscan strip along that edge, and every piece of its chrome there
     * already.
     *
     * <p>The one thing that follows from the edge rather than being chosen is the width, and the shape:
     * a sheet docked to the bottom is that edge's width, capped at the 640dp Material states for a
     * sheet, with the two corners against the edge square and the navigation bar's inset carried inside
     * it; a sheet at the end edge is inset from every side with 16dp corners all round. See
     * {@link UiMetrics#panelWidthPx}.
     *
     * <p>The system bars are a margin here, not padding. A flat fill could run under the status bar
     * unnoticed; a card with a visible corner cannot, so what used to inset the content now insets the
     * card, and the content keeps only the padding its own design asks for. The heights are read
     * IGNORING VISIBILITY: a panel opened from another panel arrives with the bars already hidden, and
     * {@code getInsets()} would report zero and put the card's corner under the cutout.
     */
    /**
     * What the screen's end edge takes out of a side panel, and therefore what its content does not
     * get: the overscan band on a television, or a cutout or a navigation bar on anything else,
     * whichever is the wider.
     *
     * <p>Asked here so that {@link #pickerWindow}, which spends it, and a panel laying its own
     * controls out, which must not, are reading one number. They were not: the window is
     * {@link UiMetrics#panelWidthPx} wide and the panels measured themselves against that, while the
     * card inside had already given this much of it back to the edge. On a television that is 48dp,
     * and 48dp is exactly how far the keypad's last column and the speed panel's plus stood off the
     * screen.
     */
    public static int panelEdgePx(final Activity activity, final UiMetrics ui) {
        int insetEnd = 0;
        if (Build.VERSION.SDK_INT >= 30) {
            final WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
            if (insets != null) {
                final android.graphics.Insets blocked = android.graphics.Insets.max(
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()),
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()));
                insetEnd = activity.getResources().getConfiguration().getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL ? blocked.left : blocked.right;
            }
        }
        return Math.max(insetEnd, ui.overscanH());
    }

    /**
     * The width a side panel's content actually has to work with: the window, less what the edge takes
     * and less the panel's own side padding.
     */
    public static int panelContentPx(final Activity activity, final UiMetrics ui, final int hPad) {
        return ui.panelWidthPx(activity.getResources().getConfiguration())
                - panelEdgePx(activity, ui) - 2 * hPad;
    }

    /**
     * A screen whose own bars step back while a sheet is over it.
     *
     * <p>A detached sheet stands 16dp off every edge and the navigation surface sits under it, so what
     * shows in that standoff is the bottom of a bar and a label sliced by the sheet's corner - "\u0418\u0437" and
     * then a rounded edge. Dimmed content behind a dialog is ordinary; a word cut in half at the
     * screen's corner reads as a fault. The panels already ask for the status bar to be hidden while
     * they are up (see applyPickerBars); this is the same for the bar the app draws itself.
     */
    interface Chromed {
        /** Hidden, not gone: the rows behind must not reflow under the dim and move while nobody looks. */
        void chrome(boolean visible);
    }

    public static void pickerWindow(final Activity activity, final UiMetrics ui, final Dialog dialog,
                                    final View content) {
        pickerWindow(activity, ui, dialog, content, false);
    }

    /**
     * @param wideBottom a sheet that docks to the bottom whatever the window's width, and takes that
     *                   width rather than a panel's: the playlist's rail of frames, which is one frame
     *                   tall and as long as the screen. Everything else about it is the bottom sheet a
     *                   phone held upright already gets.
     */
    public static void pickerWindow(final Activity activity, final UiMetrics ui, final Dialog dialog,
                                    final View content, final boolean wideBottom) {
        if (activity instanceof Chromed) {
            final Chromed host = (Chromed) activity;
            host.chrome(false);
            // The dialog's own decor tells us when it is over, rather than a dismiss listener the
            // caller may want for itself.
            dialog.getWindow().getDecorView().addOnAttachStateChangeListener(
                    new View.OnAttachStateChangeListener() {
                        @Override
                        public void onViewAttachedToWindow(final View view) {
                        }

                        @Override
                        public void onViewDetachedFromWindow(final View view) {
                            host.chrome(true);
                        }
                    });
        }
        final Configuration cfg = activity.getResources().getConfiguration();
        // One edge for every panel, and the window is what picks it — never which button was pressed:
        // a panel that arrives from a different edge depending on what opened it reads as several
        // different panels. A compact-width window, which is a phone held upright, gets the bottom edge,
        // where the thumb is and where Material puts a sheet on that class of window. Everything wider
        // gets the end edge: there the strip of picture beside the panel is worth having, and a remote's
        // focus travels along one side of the screen instead of across the bottom of it.
        final boolean bottom = wideBottom || cfg.screenWidthDp < 600;
        // Sideways, the end edge belongs to the player and to nothing else. The edge dock exists so the
        // picture keeps playing beside the panel — that is its whole argument, and it is an argument
        // only the player can make. On a browser or a settings screen there is nothing behind the panel
        // worth keeping in view, and a full-height slab against one edge reads as a second screen that
        // arrived rather than as a question about this one. So everywhere else a wide window gets what
        // a wide window has always got: a card in the middle, rounded on all four corners.
        final boolean sideDock = !bottom && activity instanceof PlayerActivity;
        final boolean centred = !bottom && !sideDock;
        // And one width, which follows the edge rather than the content — see UiMetrics.panelWidthPx.
        final int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        // A panel's width is a column's; a rail's is the screen's. panelWidthPx caps a wide window at
        // 360dp, which is the right number for something docked to the end edge and the wrong one for
        // something lying along the bottom of it.
        final int panelWidth = wideBottom ? screenWidth - Utils.dpToPx(8) : ui.panelWidthPx(cfg);
        // The only margin left anywhere: the top one under the bottom sheet, which keeps a long list from
        // reaching the very top of the screen. A docked sheet has no others.
        final int vMargin = Math.max(Utils.dpToPx(8), ui.overscanV());
        // What actually blocks pixels while a panel is open, and nothing more. applyPickerBars hides the
        // status bar and shows the navigation bar, so reserving room for the status bar costs the card
        // 24dp of height for a bar that is not on screen — which is what clipped the last row of a long
        // menu while a fifth of the window stood empty. The cutout is there whether or not any bar is,
        // and the navigation inset is read ignoring visibility because a panel opened from another panel
        // arrives with the bars already turned off. Per edge, because a cutout on the left of a
        // sideways phone says nothing about the right edge this panel is docked to.
        int insetTop = 0;
        int insetBottom = 0;
        int insetEnd = 0;
        final WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                final android.graphics.Insets blocked = android.graphics.Insets.max(
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout()),
                        insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()));
                insetTop = blocked.top;
                insetBottom = blocked.bottom;
                insetEnd = cfg.getLayoutDirection()
                        == View.LAYOUT_DIRECTION_RTL ? blocked.left : blocked.right;
            } else {
                // No per-type insets before 30, and no way to ask about a hidden bar either.
                insetBottom = insets.getSystemWindowInsetBottom();
            }
        }

        // A panel is a DOCKED SHEET, not a floating card — the same component seen from two sides. Upright
        // it docks to the bottom, full width, and grows up; sideways and on a television it docks to the
        // end edge, a fixed width and the full height. Either way the two corners against the screen go
        // square and only the leading edge is rounded, at the radius Material gives that shape: 28dp for
        // the bottom sheet, 16dp for the side one.
        //
        // Why a fixed height sideways, when a card that wrapped its content seemed thriftier: the card had
        // no silhouette. A two-row picker floated 172dp tall, a five-file playlist filled the window, and
        // the playlist's own mode toggle moved the panel by 221dp on a television — one panel, four
        // pictures, and which one appeared depended on how many files a folder held. A sheet has none of
        // those variables. The price is an empty column under short content (a two-track list leaves 51 %
        // of the field), paid deliberately: in a 440dp column that reads as a short list, where the same
        // gap in the 575dp card it replaced read as a slab.
        // A DETACHED sheet: it clears every edge it is near and carries the radius all the way round.
        //
        // R8 said the opposite and said it for a reason — a docked sheet has one silhouette whatever it
        // holds, and squaring the corners against the screen is what stops it reading as a card glued to
        // the bottom. What that argument missed is that the corners were being squared against an edge
        // the sheet never actually reached: a phone's gesture bar and a television's overscan both stand
        // between the two, so the shape was drawn flush to a line the viewer does not see. Detached, it
        // is flush to nothing and the radius is the same on all four corners, which is one silhouette
        // just as much as the other was.
        // A card in the middle takes the dialog's own radius, which is the sheet's; only the edge
        // dock is the tighter 16, being the one shape that is still half against the screen.
        final int corner = Utils.dpToPx(sideDock ? 16 : 28);
        final boolean rtl = cfg.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final ShapeAppearanceModel.Builder shape = ShapeAppearanceModel.builder().setAllCornerSizes(corner);
        // How far the sheet stands off the screen. Material's own number for a detached sheet is 16dp;
        // on a television the outer band a set may cut is wider, and there it is that band instead.
        final int detachH = Math.max(Utils.dpToPx(16), ui.overscanH());
        final int detachV = Math.max(Utils.dpToPx(16), ui.overscanV());
        final MaterialShapeDrawable card = new MaterialShapeDrawable(shape.build());
        // From the content's own theme, not the player's: the panels follow the appearance choice, so
        // the card is light when the app is light and black under AMOLED.
        // Over the picture the ground role is right: what is behind is not a surface at all, and the
        // panel wants the app's own black. Over a page it is that page's colour, and a panel painted in
        // it disappears into it - on the owner's phone under Sapphire the sheet and the browser behind
        // it were both accentGround #101418, with nothing between them but the dim. The container role
        // is what a dialog is supposed to carry anyway (DESIGN.md: "Dialog - 28dp on
        // colorSurfaceContainerHigh"), and on that accent it is #272B2F: a step of 23 instead of none.
        final int surface = MaterialColors.getColor(content,
                activity instanceof PlayerActivity
                        ? R.attr.colorSurface : R.attr.colorSurfaceContainerHigh,
                ContextCompat.getColor(activity, R.color.sheet_surface));
        card.setFillColor(ColorStateList.valueOf(surface));
        content.setBackground(card);
        content.setClipToOutline(true); // so a full-bleed row's ripple stops at the rounded end

        // The blocked edges are the host's padding, not the card's margin: FrameLayout treats a margin
        // under CENTER_VERTICAL as a shift rather than a bound, so an uneven pair walks the card off the
        // top of the screen. Padding bounds it, and the margin left on the card is even.
        final FrameLayout host = new FrameLayout(activity);
        // A sheet must not reach the top edge; the panels hide the status bar, so without a floor here a
        // long playlist would grow into a full-screen dialog that arrived from the bottom.
        //
        // At the bottom the inset goes inside the sheet instead of under it, which is what Material's own
        // bottom sheet does (paddingBottomSystemWindowInsets): the surface reaches the screen's edge and
        // the rows stop above the navigation bar. Held as a margin it left a strip of video between the
        // sheet and the edge, and a sheet with a gap under it is a card again.
        // The status bar is the browser's and the settings screen's — the player hides it before a
        // panel opens, which is why only the bottom sheet used to reserve anything here. A card in
        // the middle is bounded by the same top inset, or it is laid out over the clock.
        host.setPadding(0, bottom ? Math.max(insetTop, Utils.dpToPx(56))
                : centred ? Math.max(insetTop, detachV) : 0, 0,
                centred ? Math.max(insetBottom, detachV) : 0);
        // The navigation bar's inset used to be taken inside the sheet, so that the surface could reach
        // the screen's edge while the rows stopped above the bar. A detached sheet does not reach that
        // edge at all, so the inset belongs under it — as margin, with the standoff — or the sheet would
        // float and still carry a bar's worth of empty surface along its bottom.
        if (sideDock) {
            // The surface reaches the screen's edges; its content does not. That is Android TV's own rule
            // — background art may cross the overscan band, anything interactive may not — and it is what
            // lets the sheet dock instead of float. The blocked edges and the overscan therefore live as
            // the sheet's padding rather than as its margin, added to whatever padding the panel already
            // carries on its leading side.
            // The same number panelContentPx hands the panels, so the two cannot disagree again.
            // Less whatever the standoff above already provides. That padding existed because the card
            // used to touch the screen: it was the only thing keeping rows out of the overscan band and
            // from under a navigation bar. The card now stands detachH/detachV clear of those edges, so
            // counting them again would spend the margin twice — and this is a 360dp column that the
            // notes already admit is tight.
            final int edge = Math.max(0, panelEdgePx(activity, ui) - detachH);
            content.setPadding(
                    content.getPaddingLeft() + (rtl ? edge : 0),
                    content.getPaddingTop(),
                    content.getPaddingRight() + (rtl ? 0 : edge),
                    content.getPaddingBottom());
        }
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                bottom ? panelWidth - 2 * detachH : panelWidth,
                sideDock ? ViewGroup.LayoutParams.MATCH_PARENT
                        : ViewGroup.LayoutParams.WRAP_CONTENT,
                bottom ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                        : sideDock ? Gravity.END | Gravity.TOP
                        : Gravity.CENTER);
        if (bottom) {
            lp.setMargins(detachH, vMargin, detachH, detachV + insetBottom);
        } else if (sideDock) {
            // The same standoff on the three edges this one is near. The window is widened by exactly
            // this margin below, so the card keeps its full width and the leading edge lands on the
            // window's own: a margin the window was not given would push the card's left edge to
            // -detachH, where it is clipped — the whole rounded corner and a strip of live content
            // with it, 48dp of it on a television.
            // The bar above is cleared by the card rather than by its content. The panel's window asks
            // for the status bar to be hidden, as the player's own window does, and a shell may simply
            // refuse a window this shape: then white glyphs land across a light sheet with the clock
            // beside them over the film, and no appearance flag serves a bar that is half over each. So
            // the room is taken whether or not the bar is up - the insets are read ignoring visibility
            // - and the sheet starts below wherever it would be. It costs the 8dp between the standoff
            // and a status bar's height on a screen that did hide it.
            lp.setMargins(0, Math.max(detachV, insetTop), detachH,
                    Math.max(detachV, insetBottom));
        } else {
            // Wrapping its content, so a two-row list is a small card and not a full-height slab with
            // a hole in it — the price the edge dock pays for one silhouette, which is a price only the
            // player has a reason to pay. The host's own padding holds it clear of the bars.
            lp.setMargins(detachH, detachV, detachH, detachV);
        }
        host.addView(content, lp);

        // The panels build a close button; this is where it learns what it closes. One place decides,
        // and a panel that forgot to carry one simply has none.
        final View close = content.findViewById(R.id.picker_close);
        if (close != null) {
            close.setOnClickListener(v -> dialog.cancel());
        }

        // A panel is a plain Dialog holding the Activity that raised it, and a plain Dialog is not taken
        // down with its host: rotate a screen that does not handle the change itself and the window
        // outlives the Activity it belongs to — "has leaked window", and a crash on some builds. The
        // player declares configChanges and never noticed; the settings screens do not, and the lists
        // and fields that now open as panels there are raised on every rotation a viewer makes.
        // Hung off the host's own decor rather than a lifecycle callback so that one line covers every
        // panel in the app, whichever screen opened it.
        final View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor != null) {
            decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    decor.removeOnAttachStateChangeListener(this);
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                }
            });
        }

        dialog.setContentView(host);
        // setCanceledOnTouchOutside measures "outside" against the window, and a sheet docked to the
        // bottom is given a window as wide as the screen and as tall as it — so on a phone held upright
        // there was no outside to tap, and only the sideways panel, whose window is the width of the
        // card, could be dismissed that way. The host is what fills the rest of the screen, so it is
        // what answers: a press landing beyond the card's own bounds closes the panel, whichever edge
        // the card is docked to. The card's children keep every press that reaches them.
        dialog.setCanceledOnTouchOutside(true);
        host.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN
                    || (event.getX() >= content.getLeft() && event.getX() <= content.getRight()
                        && event.getY() >= content.getTop() && event.getY() <= content.getBottom())) {
                return false;
            }
            dialog.cancel();
            return true;
        });
        final Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        // The window IS the sheet: same width, same edge. That is what makes every press beyond it an
        // outside press, which is how the panel closes without a scrim of its own.
        window.setLayout(
                bottom ? screenWidth - Utils.dpToPx(8)
                        : sideDock ? Math.min(screenWidth, panelWidth + detachH)
                        // A centred card needs the whole window under it, or there is no "outside" for
                        // a press to land in and the only way out is the button.
                        : screenWidth - Utils.dpToPx(8),
                ViewGroup.LayoutParams.MATCH_PARENT);
        window.setGravity(bottom ? Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL
                : sideDock ? Gravity.END : Gravity.CENTER);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // A sheet that carries a field has to stand above the keyboard, and a sheet docked to the
        // bottom is exactly where the keyboard comes up. The forms this raises used to be floating
        // dialogs, which Dialogs.keyboardResizes lifted by naming the keyboard among the insets their
        // window fits inside; a panel never needed that, because until these forms moved down here no
        // panel had a field in it. Without it the card sits under the keyboard and is not on screen at
        // all — the navigation bar rides up and the sheet does not.
        //
        // Below API 30 this line is the whole of it: the window is not edge to edge there, so
        // ADJUST_RESIZE shrinks its frame to clear the keyboard and the bottom-gravity card rises with
        // it, which is what a docked sheet has always done on Android 6 through 10.
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // From 30 the frame is not shrunk — this app targets 36, every window is edge to edge, and the
        // platform ignores both ADJUST_RESIZE and fitInsetsTypes for one that is, which is why naming
        // the keyboard the way keyboardResizes does for a floating dialog changed nothing here. So the
        // inset is read and applied by hand: the card is the host's bottom-gravity child, so bottom
        // padding on the host carries it up, and it comes back down on its own when the keyboard goes.
        if (bottom && Build.VERSION.SDK_INT >= 30) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            host.setOnApplyWindowInsetsListener((v, applied) -> {
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                        applied.getInsets(WindowInsets.Type.ime()).bottom);
                return applied;
            });
            host.requestApplyInsets();
        }
        // A sheet slides in from the edge it is docked to. Every panel is a Theme_Translucent_NoTitleBar
        // dialog, which fades in on the spot, and a shape that materialises in the middle of the screen
        // reads as a rectangle that appeared rather than as a surface that was pulled in. Done on the view
        // instead of through windowAnimationStyle so the scrim keeps its own fade underneath, and so the
        // direction can follow the layout direction — END is the left edge in right-to-left.
        content.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                content.getViewTreeObserver().removeOnPreDrawListener(this);
                if (bottom) {
                    content.setTranslationY(content.getHeight());
                    content.animate().translationY(0);
                } else if (sideDock) {
                    content.setTranslationX(rtl ? -content.getWidth() : content.getWidth());
                    content.animate().translationX(0);
                } else {
                    // Nothing to be pulled in from: a card in the middle is not docked to an edge, and
                    // sliding it in from one would be a claim about where it lives that is not true.
                    // It grows into place the way a dialog does.
                    content.setAlpha(0f);
                    content.setScaleX(0.9f);
                    content.setScaleY(0.9f);
                    content.animate().alpha(1f).scaleX(1f).scaleY(1f);
                }
                content.animate().setDuration(220)
                        .setInterpolator(AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR).start();
                return true;
            }
        });
        // A panel's window is one of the app's windows and is dressed like them: edge to edge, no strip
        // of its own at the top, and free to cross a cutout. Theme_Translucent_NoTitleBar predates all
        // three. A window that does not say it draws the system bars itself is given a BLACK one drawn
        // for it - DecorView.calculateBarColor, which returns Color.BLACK for exactly that case - and
        // that is the band that stood across the top of every panel on a screen which keeps its status
        // bar: the browser's, since the player hides its own before a panel opens.
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        // And the sheet reaches the screen's edges rather than the decor's, which is what the inset
        // arithmetic above is for; without this the card stopped below the status bar and left a strip
        // of dimmed screen over a sheet that is meant to be full height.
        WindowCompat.setDecorFitsSystemWindows(window, false);
        // Which means the status bar's icons are now read against the sheet, not against the screen it
        // covers, so they are set from the card's own colour: dark on a light panel, light on a dark one.
        //
        // And the bar's VISIBILITY is now the panel's business too, which is what dressing the window
        // this way cost and nobody noticed at the time: a window that draws the system bars can be the
        // one the platform takes the bars' state from, and this one asked for nothing, so on a shell
        // that picks it the player's own hide (applyPickerBars, a frame earlier, on the activity's
        // window) was simply overruled - the status bar stood over a light sheet in white glyphs, with
        // the clock beside it over the film, and no single appearance flag serves a bar that is half
        // over each. So the panel asks for what the screen behind it asked for, and only in the player:
        // the browser and the settings screen keep their bar, and the side dock pads its content clear.
        //
        // Asked when the window is on screen rather than here. A dialog's window has no insets
        // controller until it is attached; what stands in for it before that keeps the appearance and
        // replays it, but the visibility asked of it was being dropped - which is why the same call,
        // made here, changed nothing on the device that needed it.
        final boolean lightBars = ColorUtils.calculateLuminance(surface) > 0.5;
        content.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(final View v) {
                v.removeOnAttachStateChangeListener(this);
                final androidx.core.view.WindowInsetsControllerCompat bars =
                        WindowCompat.getInsetsController(window, v);
                bars.setAppearanceLightStatusBars(lightBars);
                bars.setAppearanceLightNavigationBars(lightBars);
                if (activity instanceof PlayerActivity) {
                    bars.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars());
                    // The navigation bar stays up while a panel is open - OxygenOS reads a hidden
                    // gesture bar as fullscreen and guards the back gesture behind a second swipe -
                    // so only the status bar is asked about, and it comes back on a swipe like any
                    // other hidden bar.
                    bars.setSystemBarsBehavior(androidx.core.view.WindowInsetsControllerCompat
                            .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            }

            @Override
            public void onViewDetachedFromWindow(final View v) {
            }
        });
        if (Build.VERSION.SDK_INT >= 28) {
            window.getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        // The picture behind a modal sheet steps back rather than competing with it.
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.4f);
    }

    /**
     * The background every row inside a player panel wears: a rounded fill, a ripple clipped to it, and
     * the D-pad focus ring.
     *
     * <p>The ring is the whole point. A remote used to be shown focus as a white wash, and over a row
     * lit with the accent the wash took the accent with it — measured 2.21:1 for the label on a focused
     * current row, against the 4.5 it wants. An edge says the same thing and destroys nothing.
     *
     * @param fill the row's own colour, or {@link Color#TRANSPARENT} for a row that is not the current one
     */
    public static Drawable pickerRow(final Context ctx, final int fill) {
        return pickerRow(ctx, fill, false);
    }

    /**
     * The same tile, optionally with an edge at rest.
     *
     * <p>A row inside a list does not want one — the card it sits in is its frame, and twenty rows each
     * boxed is a grid, not a list. A key on a keypad has no such frame: without an edge it is a digit
     * printed on the panel, and twelve of them read as a table of numbers rather than as twelve things
     * to press. So the key keeps a hairline in the surface's outline colour, and the focus ring is that
     * same edge widened — the signal is a change of shape, which the eye catches without being aimed
     * at it, rather than an edge appearing out of nothing.
     *
     * @param outlined true to draw the resting hairline
     */
    public static Drawable pickerRow(final Context ctx, final int fill, final boolean outlined) {
        return pickerRow(ctx, fill, outlined, Utils.dpToPx(8));
    }

    /**
     * The same tile at a stated corner, for a row tall enough that R3 calls it a card: 8dp up to
     * 56dp, 12dp from 72dp.
     */
    public static Drawable pickerRow(final Context ctx, final int fill, final boolean outlined,
                                     final int corner) {
        return pickerRow(ctx, fill, outlined, corner,
                ctx.getResources().getDimensionPixelSize(R.dimen.focus_ring_width));
    }

    /**
     * The same tile with the ring drawn at a stated width, for a card wide enough that the standard
     * 2dp reads as a hairline on it — see {@code focus_ring_width_card}.
     */
    public static Drawable pickerRow(final Context ctx, final int fill, final boolean outlined,
                                     final int corner, final int ringWidth) {
        final GradientDrawable content = new GradientDrawable();
        content.setCornerRadius(corner);
        content.setColor(fill);
        content.setStroke(ringWidth, ContextCompat.getColorStateList(ctx, R.color.focus_ring));
        Drawable layer = content;
        if (outlined) {
            // Two widths, so two drawables: a GradientDrawable's stroke width is not state-dependent,
            // and a hairline that only changes colour is the weakest focus event this app has —
            // measured 2.76:1 where a ring appearing reads 16.30:1.
            final GradientDrawable rest = new GradientDrawable();
            rest.setCornerRadius(corner);
            rest.setColor(fill);
            rest.setStroke(Utils.dpToPx(1), ContextCompat.getColorStateList(ctx, R.color.focus_ring_outlined));
            final StateListDrawable states = new StateListDrawable();
            states.addState(new int[]{android.R.attr.state_focused}, content);
            states.addState(StateSet.WILD_CARD, rest);
            layer = states;
        }
        final GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(corner);
        mask.setColor(Color.WHITE);
        return Utils.coveringRipple(Utils.pressOnly(MaterialColors.getColor(ctx, R.attr.colorControlHighlight,
                ContextCompat.getColor(ctx, R.color.ripple_chrome))), layer, mask);
    }

    /**
     * A panel's title row: whatever names the panel, then the close button at the end of the same line.
     *
     * @param title the panel's own header view, or null for a panel whose groups name themselves — the
     *              row is then the close button alone, which is still the line every panel starts with
     */
    public static LinearLayout pickerHeader(final Context ctx, final UiMetrics ui, final View title) {
        return pickerHeader(ctx, ui, title, null);
    }

    /**
     * The same, for a panel that is one step of several: a back arrow at the start of the line, where
     * every toolbar in Android puts one, and the close button still at the end. Back goes one step,
     * close leaves the whole thing — two different questions, so two different controls.
     *
     * @param back what the arrow does, or null for a panel nothing came before
     */
    public static LinearLayout pickerHeader(final Context ctx, final UiMetrics ui, final View title,
                                            final Runnable back) {
        final LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (back != null) {
            final com.google.android.material.button.MaterialButton arrow =
                    iconButton(ctx, ui, R.drawable.ic_arrow_back_24dp, R.string.back);
            arrow.setOnClickListener(v -> back.run());
            row.addView(arrow);
        }
        // Weight on whatever is to the left of it, so the close button sits at the end whether the row
        // holds a title, a title and a count, or nothing at all.
        row.addView(title == null ? new View(ctx) : title,
                new LinearLayout.LayoutParams(0, title == null
                        ? 1 : ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(pickerClose(ctx, ui));
        return row;
    }

    /**
     * A context for dialogs raised from the player, themed by the appearance choice instead of by the
     * window that raises them. The player's own theme is dark by design, so a dialog built against it
     * cannot follow that choice at all: this hands back one of two explicit dialog themes, carrying
     * the app's colour roles and the accent theme's own grounds.
     *
     * Every view a dialog builds for itself has to come from this context too, or dark text lands on a
     * light panel.
     */
    public static Context dialogContext(final Context base) {
        final boolean light = Prefs.isLight(base);
        // Wrapped straight around whatever it was handed — an Activity, in every real call — because a
        // ContextThemeWrapper keeps its base's window token and a dialog needs one to exist at all.
        final android.view.ContextThemeWrapper themed = new android.view.ContextThemeWrapper(base,
                light ? R.style.Theme_Dialogs_Light : R.style.Theme_Dialogs_Dark);
        // Theme.Dialogs.* carries no accent of its own, so this is what decides it — not left to the
        // wrapped context, whose accent may be older than the preference by the time a dialog opens.
        // Above AMOLED and not below it: the accent brings the theme's own grounds with it now, and
        // AMOLED's black has to land on top of them. Settings applies the two in the same order.
        themed.getTheme().applyStyle(Prefs.accentOverlay(base, light), true);
        // AMOLED is part of the same appearance choice, and a panel is the largest dark surface the app
        // ever puts on screen — the one place the option is worth the most.
        if (!light && Prefs.isAmoledBlack(base)) {
            themed.getTheme().applyStyle(R.style.ThemeOverlay_JustPlus_Amoled, true);
        }
        return themed;
    }

    /**
     * Keeps a dialog that carries a text field where it first appeared, whatever the keyboard does to
     * the bars around it.
     *
     * <p>The player hides the status bar, and a dialog over it brings the bar back — until the keyboard
     * comes up, when the system reconsiders and hides it again. A dialog's frame is laid out below the
     * bars that are visible, so that hide moves the frame up by a status bar, in the same breath as the
     * keyboard shrinks it. On the owner's phone the picture stayed where it was while the touch frame
     * moved: a press measured 795 on a row whose own {@code getLocationOnScreen} said 898, and the row
     * below took the press meant for the one above. The 110px gap was the height of the bar.
     *
     * <p>So the frame is inset by the bars whether they show or not, and pinned to the top: the keyboard
     * can only make the window shorter, results can only make it taller, and the top edge never has a
     * reason to move.
     */
    static void keyboardResizes(final android.app.Dialog dialog) {
        final Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        final WindowManager.LayoutParams lp = window.getAttributes();
        // A window already docked to an edge keeps the edge it was docked to; only a centred one has a
        // reason to move, and the top is the one place a keyboard cannot push it away from.
        if (lp.gravity == Gravity.NO_GRAVITY || lp.gravity == (Gravity.CENTER)) {
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            // Naming the types takes over from the system, which would otherwise add the keyboard for
            // ADJUST_RESIZE by itself — so it is named here too.
            lp.setFitInsetsTypes(lp.getFitInsetsTypes() | WindowInsets.Type.ime());
            lp.setFitInsetsIgnoringVisibility(true);
        }
        window.setAttributes(lp);
    }

    /**
     * The strip a dialog's fields stand in, inset to the dialog's own text margin so a field lines
     * up with the title above it.
     */
    static LinearLayout dialogFields(final Context context) {
        final LinearLayout fields = new LinearLayout(context);
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(fieldGutter(context), 0, fieldGutter(context), 0);
        return fields;
    }

    /**
     * The gutter a dialog's fields stand in, so a field lines up with the title above it and the answers
     * below it.
     *
     * <p>24dp is the gutter an alert dialog leaves around its own content, and it is right for the window
     * in the middle. A sheet has already padded itself by 10dp and sets its header and its answers 6dp
     * inside that, so the same strip there stands 34dp in while the title above it and the buttons below
     * it stand at 16dp — measured on the owner's phone, and visible as a field narrower than the two
     * buttons under it. Same 6dp, so one gutter runs down the sheet.
     *
     * <p>Said as a number rather than left in a layout because a field can arrive either way: built by
     * {@link #dialogFields}, or inflated from a preference's own {@code dialogLayout}, which carries
     * androidx's 24dp and cannot know which window it is about to stand in. The second kind is why this
     * is public at all — see {@code SettingsActivity.onDisplayPreferenceDialog}.
     */
    static int fieldGutter(final Context context) {
        return Utils.dpToPx(asksAtTheEdge(context) ? 6 : 24);
    }

    /**
     * A dialog that carries a strip of {@link #textField} rows, sized for a window a keyboard has taken
     * most of. Two things had to give on a 360x800dp phone whose keyboard leaves 385dp:
     *
     * <p>The strip goes in a scroller. An alert dialog gives its custom panel whatever room is left once
     * the title and the buttons have taken theirs and measures it at exactly that, and a bare strip
     * answers that by squeezing its last field to a 20dp sliver — a scroller answers it by scrolling,
     * and carries the focused field into view with it.
     *
     * <p>And the card is allowed to use the window: Material keeps 80dp clear above and below an alert,
     * which is 160dp of a short window spent on air. 24dp is what Material itself leaves around a picker
     * dialog, and it is the difference between both fields standing there and one of them below the fold.
     * The insets only cap the card, so nothing moves on a screen where it already fits.
     *
     * <p><b>The floating half of {@link #fields}, and not a way in of its own.</b> Eight callers used
     * to build their forms straight off this, which meant eight forms that never asked
     * {@link #asksAtTheEdge} and so went on floating in the middle of a phone while everything around
     * them had moved to the bottom edge — the kind of miss that is invisible from the code and obvious
     * on the screen. One caller is left, and it is the one this cannot serve: PlayerActivity.createRoom
     * needs the positive button itself, to grey it out while a listed room has no password, and a
     * sheet hands its buttons to nobody.
     */
    static MaterialAlertDialogBuilder fieldDialog(final Context context, final View fields) {
        final ScrollView scroll = new ScrollView(context);
        // The same hairline the dialog draws over its own message when that scrolls, for the same
        // reason: a field cut off at the edge of the panel has to look cut off rather than broken.
        scroll.setScrollIndicators(View.SCROLL_INDICATOR_TOP | View.SCROLL_INDICATOR_BOTTOM);
        scroll.addView(fields);
        return new MaterialAlertDialogBuilder(context)
                .setView(scroll)
                .setBackgroundInsetTop(Utils.dpToPx(24))
                .setBackgroundInsetBottom(Utils.dpToPx(24));
    }

    /**
     * Adds a Material text field to {@link #dialogFields} and hands back the editor, which is what
     * the dialog reads and writes; the box around it needs nothing further said to it.
     */
    static EditText textField(final ViewGroup fields, final CharSequence hint) {
        return textField(fields, hint, null);
    }

    /**
     * The same, with an example of what to type. A label has to be a word or two because it shrinks
     * onto the outline and stays there for good; an example is longer and is only worth reading with
     * the field empty and in front of you, which is exactly when a placeholder shows.
     */
    static EditText textField(final ViewGroup fields, final CharSequence hint,
                              final CharSequence placeholder) {
        final TextInputLayout field = (TextInputLayout) LayoutInflater.from(fields.getContext())
                .inflate(R.layout.dialog_text_field, fields, false);
        field.setHint(hint);
        field.setPlaceholderText(placeholder);
        // The accent the dark panel shares with the light one measures 3.3:1 on its card: enough for
        // the 2dp outline, not for the 12sp label riding it. The label alone takes the brighter ink —
        // colorPrimaryInverse is the accent's ink in every window theme.
        if (!MaterialColors.isColorLight(MaterialColors.getColor(field, R.attr.colorSurface))) {
            field.setHintTextColor(ColorStateList.valueOf(
                    MaterialColors.getColor(field, R.attr.colorPrimaryInverse)));
        }
        fields.addView(field);
        return field.getEditText();
    }


    /**
     * The one control every player panel carries: a close button for its header.
     *
     * <p>It exists for the panel that fills the screen. A playlist grows until 56dp of picture is all
     * that is left above it, and then the only ways out are the system Back and a press on that strip —
     * one of which is a gesture on most phones now, and the other of which looks like nothing. Material
     * gives a surface that has taken the screen an explicit close, and this is it.
     *
     * <p>No listener here: {@link #pickerWindow} finds it by id and wires it to the dialog, so what
     * closing means is decided in the one place that already knows.
     */
    public static com.google.android.material.button.MaterialButton pickerClose(
            final Context ctx, final UiMetrics ui) {
        final com.google.android.material.button.MaterialButton button =
                iconButton(ctx, ui, R.drawable.ic_close_24dp, R.string.error_close);
        button.setId(R.id.picker_close);
        return button;
    }

    /** A 48dp glyph on nothing, in the colour a panel gives its quieter text. */
    static com.google.android.material.button.MaterialButton iconButton(
            final Context ctx, final UiMetrics ui, final int icon, final int description) {
        return iconButton(ctx, ui, icon, ctx.getString(description), false);
    }

    static com.google.android.material.button.MaterialButton iconButton(
            final Context ctx, final UiMetrics ui, final int icon, final int description,
            final boolean outlined) {
        return iconButton(ctx, ui, icon, ctx.getString(description), outlined);
    }

    /**
     * The same glyph, with or without an edge of its own.
     *
     * <p>The box is padding plus glyph, never a minimum width around a smaller one. MaterialButton lays
     * its icon against {@code paddingStart} and does not move it when the view is stretched by
     * {@code minWidth}, so a 24dp glyph in a 40dp button widened to 48 sits 4dp left of the middle —
     * measured on the ± of the offset panel, and 2dp on the panels' close button, which had been that
     * way unseen for as long as those buttons drew no background. Padding is symmetric, so the glyph is
     * centred by construction whatever the box is.
     *
     * @param outlined true for a control that stands alone — a ± beside a number, a backspace — and so
     *                 has to say where it begins; false for one of a row inside a header or a bar,
     *                 which is already a frame and does not want a second one drawn inside it
     */
    static com.google.android.material.button.MaterialButton iconButton(
            final Context ctx, final UiMetrics ui, final int icon, final CharSequence description,
            final boolean outlined) {
        final com.google.android.material.button.MaterialButton button =
                new com.google.android.material.button.MaterialButton(ctx, null, outlined
                        ? com.google.android.material.R.attr.materialIconButtonOutlinedStyle
                        : com.google.android.material.R.attr.materialIconButtonStyle);
        final int glyph = ui.dpS(24);
        final int box = ui.dpS(48); // the platform's floor for anything a finger has to hit
        button.setIconResource(icon);
        button.setIconSize(glyph);
        button.setIconTint(ColorStateList.valueOf(MaterialColors.getColor(ctx,
                R.attr.colorOnSurfaceVariant, ContextCompat.getColor(ctx, R.color.ink_secondary))));
        button.setContentDescription(description);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        final int pad = (box - glyph) / 2;
        button.setPadding(pad, pad, pad, pad);
        // Kept as a floor, not as the thing that makes the box: see the note above.
        button.setMinWidth(box);
        button.setMinHeight(box);
        button.setMinimumWidth(box);
        button.setMinimumHeight(box);
        Utils.focusRing(button);
        return button;
    }

    // --- The ways the app speaks -------------------------------------------------------------------
    // One question that waits (confirm), one form to fill (fields), one list to pick from (choice).
    // Every window in the app goes through these, and none of them decides its own shape:
    // asksAtTheEdge does, once, for all of them.

    interface OnPick {
        void picked(int which);
    }

    /**
     * Whether a question belongs at the bottom edge rather than in the middle of the window.
     *
     * <p>DESIGN.md R6 used to say a surface that asks and waits is always a dialog in the middle, and
     * that a panel at the edge was for browsing and tuning. That held while the two were told apart by
     * what they do. On a phone held upright they are told apart by where the hand is instead: a
     * question in the centre of a tall window is a card the thumb cannot reach, floating above a sheet
     * the same thumb opened a moment earlier. So under 600dp every window docks to the bottom, question
     * or not, and the line R6 draws survives only where there is room for it to mean anything.
     *
     * <p>600dp is the break R7 already reads, and for its reason: Material's compact/medium line, and
     * the window is what answers — never the control that opened it.
     */
    static boolean asksAtTheEdge(final Context context) {
        return context.getResources().getConfiguration().screenWidthDp < 600;
    }

    /**
     * One question and, at most, two answers.
     *
     * @param negative the label for backing out, or null for a question that can only be agreed with
     */
    static void confirm(final Activity activity, final CharSequence title, final CharSequence message,
                        final CharSequence positive, final Runnable onPositive,
                        final CharSequence negative, final Runnable onNegative) {
        final Context ctx = dialogContext(activity);
        if (!asksAtTheEdge(ctx)) {
            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx);
            if (title != null) {
                builder.setTitle(title);
            }
            if (message != null) {
                builder.setMessage(message);
            }
            builder.setPositiveButton(positive, (d, which) -> run(onPositive));
            if (negative != null) {
                builder.setNegativeButton(negative, (d, which) -> run(onNegative));
            }
            builder.show();
            return;
        }
        TextView body = null;
        if (message != null) {
            final UiMetrics ui = UiMetrics.of(activity, Utils.isTvBox(activity));
            body = new TextView(ctx);
            body.setText(message);
            body.setTextColor(MaterialColors.getColor(ctx, R.attr.colorOnSurfaceVariant, Color.WHITE));
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
            final int pad = Utils.dpToPx(16);
            body.setPadding(pad, 0, pad, Utils.dpToPx(8));
        }
        sheet(activity, title, body, positive, onPositive, negative, onNegative);
    }

    /** A form: the strip built with {@link #dialogFields}, and the answer that accepts it. */
    static void fields(final Activity activity, final CharSequence title, final View strip,
                       final CharSequence positive, final Runnable onPositive) {
        final Context ctx = dialogContext(activity);
        if (!asksAtTheEdge(ctx)) {
            final AlertDialog dialog = fieldDialog(ctx, strip)
                    .setTitle(title)
                    .setPositiveButton(positive, (d, which) -> run(onPositive))
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            keyboardResizes(dialog);
            dialog.show();
            return;
        }
        sheet(activity, title, strip, positive, onPositive,
                activity.getString(android.R.string.cancel), null);
    }

    /**
     * A short list to pick from. A sheet at every width already — it is the browsing surface R6 always
     * put at the edge — so this exists to give it the same doorway as the two above, not to change it.
     */
    static void choice(final Activity activity, final CharSequence title,
                       final java.util.List<CharSequence> labels, final OnPick onPick) {
        ActionPanel.show(activity, title, labels, onPick::picked);
    }

    /**
     * The same list, with one of its rows already the answer. Separate from the plain
     * {@link #choice} above because a list that shows what is currently chosen is a different
     * question — "change this" rather than "do one of these" — and it needs a mark to say which.
     *
     * <p>This is the shape the settings screens raise, and there are twenty-one of them: the
     * preference framework builds its own dialog for every {@code ListPreference}, which is how a
     * floating card kept appearing in the middle of a window where everything else had moved to the
     * bottom. {@code SettingsActivity.onDisplayPreferenceDialog} is where that is taken back.
     *
     * @param checked the row that is the answer now, or -1 when none is
     */
    static void choice(final Activity activity, final CharSequence title,
                       final java.util.List<CharSequence> labels, final int checked,
                       final OnPick onPick) {
        final Context ctx = dialogContext(activity);
        if (!asksAtTheEdge(ctx)) {
            final CharSequence[] items = labels.toArray(new CharSequence[0]);
            new MaterialAlertDialogBuilder(ctx)
                    .setTitle(title)
                    .setSingleChoiceItems(items, checked, (d, which) -> {
                        d.dismiss();
                        onPick.picked(which);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        final UiMetrics ui = UiMetrics.of(activity, Utils.isTvBox(activity));
        final LinearLayout rows = new LinearLayout(ctx);
        rows.setOrientation(LinearLayout.VERTICAL);
        final Runnable[] dismiss = new Runnable[1];
        for (int index = 0; index < labels.size(); index++) {
            final int which = index;
            final com.google.android.material.radiobutton.MaterialRadioButton row =
                    new com.google.android.material.radiobutton.MaterialRadioButton(ctx);
            row.setText(labels.get(index));
            row.setChecked(index == checked);
            row.setTextColor(MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE));
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
            row.setMinHeight(ui.rowMinHeight());
            // The same plate every picker row carries: the ripple and the 2dp contour that is how
            // this app says focused (R5). A radio button brings its own background otherwise.
            row.setBackground(pickerRow(ctx, Color.TRANSPARENT));
            row.setPadding(Utils.dpToPx(12), Utils.dpToPx(8), Utils.dpToPx(12), Utils.dpToPx(8));
            row.setOnClickListener(v -> {
                // These are not in a RadioGroup — they are rows of the sheet's own column — so nothing
                // clears the others for us. Today the sheet closes in the same breath and the double
                // mark never gets a frame to appear in; that is timing, not correctness, and the day a
                // row does something before closing it would show two answers ticked at once.
                for (int other = 0; other < rows.getChildCount(); other++) {
                    final View sibling = rows.getChildAt(other);
                    if (sibling instanceof android.widget.Checkable) {
                        ((android.widget.Checkable) sibling).setChecked(sibling == v);
                    }
                }
                if (dismiss[0] != null) {
                    dismiss[0].run();
                }
                onPick.picked(which);
            });
            rows.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        sheet(activity, title, rows, null, null,
                activity.getString(android.R.string.cancel), null, dismiss);
    }

    /**
     * The sheet the two questions share: a header, whatever they carry, and the answers along the
     * bottom. Built the way every other panel is, so {@link #pickerWindow} decides the silhouette (R8)
     * and this is one more of that family rather than a dialog wearing a sheet.
     */
    private static Panel sheet(final Activity activity, final CharSequence title, final View content,
                               final CharSequence positive, final Runnable onPositive,
                               final CharSequence negative, final Runnable onNegative) {
        return sheet(activity, title, content, positive, onPositive, negative, onNegative, null);
    }

    /**
     * A scroller that ends at a row rather than through one.
     *
     * <p>A list too long for the sheet was sliced wherever the window ran out: half an address and
     * half of the badge beside it, which reads as a drawing error rather than as a list with more
     * below. Cut at the boundary it reads as a list, and the scroll indicator underneath is then what
     * says there is more — the same pair the subtitle results already use, and the same sentence.
     *
     * <p>Measured from the children rather than from a row height, because these rows are not all one
     * height: a machine that announced a name of its own carries two lines and one that did not
     * carries one. Their bounds do not exist yet at measure time, so the heights are added up in the
     * order the column will lay them out. A first row too tall for the sheet on its own keeps the
     * whole height — half a row beats no row.
     */
    private static ScrollView wholeRows(final Context ctx) {
        return new ScrollView(ctx) {
            @Override
            protected void onMeasure(final int widthSpec, final int heightSpec) {
                super.onMeasure(widthSpec, heightSpec);
                final int room = getMeasuredHeight();
                final View inner = getChildCount() > 0 ? getChildAt(0) : null;
                if (!(inner instanceof ViewGroup) || inner.getMeasuredHeight() <= room) {
                    return;
                }
                final ViewGroup rows = (ViewGroup) inner;
                int y = rows.getPaddingTop();
                int fits = 0;
                for (int i = 0; i < rows.getChildCount(); i++) {
                    final View row = rows.getChildAt(i);
                    if (row.getVisibility() == GONE) {
                        continue;
                    }
                    final ViewGroup.LayoutParams lp = row.getLayoutParams();
                    if (lp instanceof ViewGroup.MarginLayoutParams) {
                        y += ((ViewGroup.MarginLayoutParams) lp).topMargin
                                + ((ViewGroup.MarginLayoutParams) lp).bottomMargin;
                    }
                    y += row.getMeasuredHeight();
                    if (y > room) {
                        break;
                    }
                    fits = y;
                }
                if (fits > 0) {
                    setMeasuredDimension(getMeasuredWidth(), fits + rows.getPaddingBottom());
                }
            }
        };
    }

    /**
     * A sheet still on screen, for the one surface that changes while it is open: the network picker
     * renames itself as it walks into a machine, and closes itself when a row is taken. Everything
     * else here is opened and answered, and needs no handle at all.
     */
    static final class Panel {
        private final Dialog dialog;
        private final TextView header;

        Panel(final Dialog dialog, final TextView header) {
            this.dialog = dialog;
            this.header = header;
        }

        void setTitle(final CharSequence title) {
            if (header != null) {
                header.setText(title);
            }
        }

        void dismiss() {
            dialog.dismiss();
        }
    }

    /**
     * A surface you browse, raised the way every other window is. Not {@link #choice}: that one is a
     * list known in full when it opens, and this is one that fills in as machines answer on the
     * network, renames itself when it walks into one, and has to be told when it is closed so the
     * searching can stop.
     */
    static Panel panel(final Activity activity, final CharSequence title, final View content,
                       final Runnable onDismiss) {
        return panel(activity, title, content, null, null, onDismiss);
    }

    /**
     * The same, with the one thing the list cannot offer.
     *
     * <p>A panel's content is things to pick; an action is not one of them. Put a verb among them and
     * it reads as another row - which is what "Enter an address" did at the foot of the network list,
     * indistinguishable from the machines above it (R3: a control that does something is a pill, a
     * control that is a value is a row; R4: a verb with no frame of its own is banned). It belongs
     * where the sheet already keeps verbs, beside the one that backs out.
     */
    static Panel panel(final Activity activity, final CharSequence title, final View content,
                       @Nullable final CharSequence action, @Nullable final Runnable onAction,
                       final Runnable onDismiss) {
        final Panel panel = sheet(activity, title, content, action, onAction,
                activity.getString(android.R.string.cancel), null, null, true);
        if (onDismiss != null) {
            panel.dialog.setOnDismissListener(d -> onDismiss.run());
        }
        return panel;
    }

    /**
     * @param positive   null for a sheet whose content is the answer — a list closes when a row is
     *                   picked, and an accept button there would be a second way to say the same thing
     * @param dismissOut where the caller is handed the way to close this sheet, for exactly that case
     */
    private static Panel sheet(final Activity activity, final CharSequence title, final View content,
                               final CharSequence positive, final Runnable onPositive,
                               final CharSequence negative, final Runnable onNegative,
                               final Runnable[] dismissOut) {
        return sheet(activity, title, content, positive, onPositive, negative, onNegative,
                dismissOut, false);
    }

    /**
     * @param browsed true for a surface that is read top to bottom rather than answered - a list of
     *                things to pick, which is cut back to a whole row where it does not fit
     */
    private static Panel sheet(final Activity activity, final CharSequence title, final View content,
                               final CharSequence positive, final Runnable onPositive,
                               final CharSequence negative, final Runnable onNegative,
                               final Runnable[] dismissOut, final boolean browsed) {
        final Context ctx = dialogContext(activity);
        final UiMetrics ui = UiMetrics.of(activity, Utils.isTvBox(activity));
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);

        final LinearLayout column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        final int pad = Utils.dpToPx(10);
        column.setPadding(pad, pad, pad, pad);

        TextView heading = null;
        if (title != null) {
            final TextView header = new TextView(ctx);
            heading = header;
            header.setText(title);
            ViewCompat.setAccessibilityHeading(header, true);
            header.setTextColor(MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE));
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setPadding(Utils.dpToPx(6), Utils.dpToPx(10), Utils.dpToPx(6), Utils.dpToPx(10));
            column.addView(pickerHeader(ctx, ui, header));
        }
        if (content != null) {
            // A form can outgrow the window once a keyboard is up, so it scrolls. No fading band like
            // the other panels draw: a question is short by construction, and one that is not is a
            // form, which scrolls to its focused field rather than being read top to bottom.
            final ScrollView scroller = browsed ? wholeRows(ctx) : new ScrollView(ctx);
            if (browsed) {
                // The hairline says there is more below, which on a list that has been cut back to a
                // whole row is the only thing that does - the cut itself now looks like the end.
                scroller.setScrollIndicators(View.SCROLL_INDICATOR_BOTTOM);
            }
            scroller.addView(content);
            column.addView(scroller, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }

        if (dismissOut != null) {
            dismissOut[0] = dialog::dismiss;
        }
        // Backing out is backing out, whichever way it is done: the button, the Back key, a press
        // beyond the sheet. A sheet whose "Cancel" undoes something and whose Back quietly does not
        // would be the kind of difference nobody discovers until it has cost them the thing.
        if (onNegative != null) {
            dialog.setOnCancelListener(d -> onNegative.run());
        }

        final LinearLayout answers = new LinearLayout(ctx);
        answers.setOrientation(LinearLayout.HORIZONTAL);
        answers.setPadding(Utils.dpToPx(6), Utils.dpToPx(8), Utils.dpToPx(6), Utils.dpToPx(4));
        View backOut = null;
        if (negative != null) {
            backOut = answer(ctx, ui, negative, false, () -> {
                dialog.dismiss();
                run(onNegative);
            });
            answers.addView(backOut);
            if (positive != null) {
                answers.addView(new View(ctx), new LinearLayout.LayoutParams(Utils.dpToPx(8), 1));
            }
        }
        View accept = null;
        if (positive != null) {
            accept = answer(ctx, ui, positive, true, () -> {
                dialog.dismiss();
                run(onPositive);
            });
            answers.addView(accept);
        }
        if (answers.getChildCount() > 0) {
            column.addView(answers);
            stackWhenCramped(answers, backOut, accept);
        }

        pickerWindow(activity, ui, dialog, column);
        dialog.show();
        // Where a remote lands. A form lands in its first field — that is the thing the sheet was
        // opened to do, and landing on "Cancel" instead means the obvious press of the centre key
        // throws the sheet away rather than starting to type. A question with two answers lands on the
        // harmless one: a sheet that opens with "delete" under the cursor is one careless press away
        // from having done it. A list answers for itself, so there the first row takes it.
        final View field = firstFocusable(content);
        final View landing = field != null ? field
                : backOut != null ? backOut
                : accept;
        if (landing != null) {
            landing.post(landing::requestFocus);
        }
        return new Panel(dialog, heading);
    }

    /**
     * Two answers side by side until they no longer fit, then one above the other.
     *
     * <p>Material stacks a dialog's actions when their labels will not sit on one line, and this
     * sheet has to do it in code because its answers are two weighted halves rather than a
     * right-aligned pair: each half is the width of the sheet less its padding, halved, and a label
     * of thirteen characters in a language that writes them long does not fit that on a telephone.
     * What it looked like: "Скасу вати" beside "Ввест и адрес у", two pills gone square trying to
     * hold three lines each.
     *
     * <p>Measured rather than predicted. The labels are translated - by Weblate, into languages
     * nobody here reads - and the type scale changes with the device, so the only honest question is
     * whether the text wrapped once it was laid out. Asked after the first layout, and only then: a
     * sheet's answers do not change afterwards.
     *
     * <p>The affirmative goes on top when they stack, which is where Material puts it, and each takes
     * the full width - a pill that spans the sheet still reads as one control, which is the shape the
     * error screen and the empty states already use.
     */
    private static void stackWhenCramped(final LinearLayout answers, @Nullable final View backOut,
                                         @Nullable final View accept) {
        if (backOut == null || accept == null) {
            return;
        }
        answers.post(() -> {
            if (((MaterialButton) backOut).getLineCount() <= 1
                    && ((MaterialButton) accept).getLineCount() <= 1) {
                return;
            }
            final int gap = Utils.dpToPx(8);
            answers.setOrientation(LinearLayout.VERTICAL);
            // The affirmative goes on top, which is where Material puts it in a stack. Only that one
            // is moved: taking the focused view out of its parent and putting it back paints a
            // square-cornered block behind its label - seen, measured, and not worth understanding
            // when the view that has to move is the other one.
            answers.removeView(accept);
            answers.addView(accept, 0);
            for (int i = 0; i < answers.getChildCount(); i++) {
                final View child = answers.getChildAt(i);
                if (child != backOut && child != accept) {
                    // The 8dp between the two halves was a view of its own, and it belongs between
                    // them: moving the affirmative left it at the end of the row, where it spaced the
                    // sheet's own padding and the two pills touched. The gap is a margin here instead,
                    // so there is nothing left to end up in the wrong place.
                    child.setVisibility(View.GONE);
                    continue;
                }
                final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                params.bottomMargin = child == accept ? gap : 0;
                child.setLayoutParams(params);
            }
        });
    }

    /**
     * One answer. Filled for the reply that agrees, tonal for the one that backs out — the pair
     * DESIGN.md asks a dialog for, at a radius that makes the row part of the sheet rather than a
     * Material action bar that wandered into one.
     */
    private static MaterialButton answer(final Context ctx, final UiMetrics ui,
                                         final CharSequence label, final boolean filled,
                                         final Runnable onClick) {
        final MaterialButton button = new MaterialButton(ctx);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
        button.setMinHeight(ui.rowMinHeight());
        button.setCornerRadius(Utils.dpToPx(20));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        if (!filled) {
            // An edge, not a fill: DESIGN.md settled that pair (Q2 - "tonal spends the accent-container
            // colour on an action that is not chosen"), and the fill had a second problem of its own. It
            // was surfaceContainerHighest, exactly one step above the panel, so once the panel took the
            // container role a dialog is supposed to carry, the two were 11 apart where the page behind
            // the panel is 33. A stroke owes the panel nothing and reads the same on all four grounds.
            // Left at 1dp for focusRing() below to widen to the 2dp contour (R5).
            button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            button.setStrokeWidth(Utils.dpToPx(1));
            button.setStrokeColor(ColorStateList.valueOf(
                    MaterialColors.getColor(ctx, R.attr.colorOutline, Color.GRAY)));
            button.setTextColor(MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE));
        }
        Utils.focusRing(button);
        button.setOnClickListener(v -> onClick.run());
        button.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return button;
    }

    private static void run(final Runnable action) {
        if (action != null) {
            action.run();
        }
    }

    /**
     * The first thing inside a sheet's content that a remote can land on — a text field, a row of a
     * list. Null when the content is only text, which is what a confirmation carries.
     */
    private static View firstFocusable(final View content) {
        if (content == null) {
            return null;
        }
        if (content.isFocusable() && content.getVisibility() == View.VISIBLE) {
            return content;
        }
        if (content instanceof ViewGroup) {
            final ViewGroup group = (ViewGroup) content;
            for (int i = 0; i < group.getChildCount(); i++) {
                final View found = firstFocusable(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // --- The list panel -----------------------------------------------------------------------------
    // Lived in PlayerActivity, which meant a list of rows with posters and marks on them was something
    // only the player could raise. The browser wanted the same thing for its rooms, and the choice was
    // to copy two hundred lines or to move them; this is the move.

    /** The menu on screen, if any. One at a time, app-wide, which is what it always was. */
    private static android.app.Dialog openMenu;

    /** For the screens that close it on their own events — a media change, leaving. */
    static void dismissMenu() {
        if (openMenu != null) {
            openMenu.dismiss();
            openMenu = null;
        }
    }

    /** The open menu, for a caller that keeps a list of everything it must take down. */
    static android.app.Dialog openMenu() {
        return openMenu;
    }

    // A long label does not fit the width of a picker panel, and the two device classes want opposite
    // answers. On TV the row that has D-pad focus scrolls its text: focus makes the movement mean
    // something ("this row"), it is the 10-foot idiom, and only one row is ever moving. In touch mode
    // nothing is ever focused, so scrolling could only mean scrolling — six rows crawling in a list the
    // user is trying to scan is motion without a message, so there the label wraps to a second line and
    // ellipsizes instead, which shows more of it than a marquee does at any one moment. Scrolling also
    // needs the view focused or selected, and the focusable view is the row, not its texts, hence the
    // relay below; only the TextViews move, the poster/number box of a playlist row is a sibling.
    static void fitLongText(final Activity activity, final UiMetrics ui, final View row, final TextView... texts) {
        final boolean scroll = ui.deviceClass == UiMetrics.DeviceClass.TV && !Utils.isReducedMotion(activity);
        for (final TextView text : texts) {
            if (text == null) {
                continue;
            }
            if (scroll) {
                text.setSingleLine(true);
                text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
                text.setMarqueeRepeatLimit(-1);
                text.setHorizontalFadingEdgeEnabled(true);
            } else {
                text.setMaxLines(2);
                text.setEllipsize(TextUtils.TruncateAt.END);
            }
        }
        if (scroll) {
            row.setOnFocusChangeListener((v, hasFocus) -> {
                for (final TextView text : texts) {
                    if (text != null) {
                        text.setSelected(hasFocus);
                    }
                }
            });
        }
    }

    // A row in the native side-panel menus (audio / speed / more).
    static class MenuItem {
        final int iconRes;
        /** Artwork to lead the row with instead of the glyph — a room's poster. Null for everything else. */
        final String imageUrl;
        final CharSequence title;
        final CharSequence subtitle;
        final boolean checked;
        final Runnable action;
        /**
         * Chrome rather than a choice: a caption naming the group below it, or — with no title — a bare
         * rule. A list where a doorway into another list, a set of choices and an action all look alike
         * reads as unstructured, and the icon some of them carry reads as decoration rather than as the
         * thing that tells them apart. Not clickable and never focusable: a remote must not stop here.
         */
        final boolean chrome;

        static MenuItem caption(CharSequence title) {
            return new MenuItem(title, true);
        }

        static MenuItem rule() {
            return new MenuItem(null, true);
        }

        private MenuItem(CharSequence title, boolean chrome) {
            this.iconRes = 0;
            this.imageUrl = null;
            this.title = title;
            this.subtitle = null;
            this.checked = false;
            this.action = null;
            this.chrome = chrome;
        }

        MenuItem(CharSequence title, CharSequence subtitle, boolean checked, Runnable action) {
            this(0, title, subtitle, checked, action);
        }

        MenuItem(int iconRes, CharSequence title, CharSequence subtitle, boolean checked, Runnable action) {
            this(iconRes, null, title, subtitle, checked, action);
        }

        MenuItem(int iconRes, String imageUrl, CharSequence title, CharSequence subtitle,
                 boolean checked, Runnable action) {
            this.iconRes = iconRes;
            this.imageUrl = imageUrl;
            this.title = title;
            this.subtitle = subtitle;
            this.checked = checked;
            this.action = action;
            this.chrome = false;
        }
    }

    /** The rule the panel already draws under its title, reused wherever one group of rows ends. */
    /**
     * The rule between two groups of rows that have no name to separate them: a hairline of the outline
     * tone, inset to the panel's content edge, with the same air above it as below.
     *
     * <p>It replaced a card per group. A card inside a sheet is a container inside a container, and the
     * two tones it needs are 1.23:1 apart — so it read as noise rather than as structure, and it gave
     * the panel three different left edges: the group's name on one, the icons on a second, their labels
     * on a third. A list on the sheet itself has one edge, and a rule is what a boundary between two
     * groups of a list looks like.
     */
    private static View menuDivider(final Context ctx, final UiMetrics ui) {
        final View rule = new View(ctx);
        rule.setBackgroundColor(MaterialColors.getColor(ctx, R.attr.colorOutlineVariant,
                ContextCompat.getColor(ctx, R.color.divider)));
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Utils.dpToPx(1)));
        lp.setMargins(Utils.dpToPx(6), Utils.dpToPx(8), Utils.dpToPx(6), Utils.dpToPx(8));
        rule.setLayoutParams(lp);
        return rule;
    }

    /**
     * A caption naming the card under it, in the accent — the same coral the settings screen gives a
     * preference category, so a group boundary reads the same in both places. Indented to where the
     * titles of that group start, not to the icon column, and given more air above than below so it
     * belongs to what follows it.
     */
    private static View menuCaption(final Context ctx, final UiMetrics ui, CharSequence text) {
        final TextView caption = new TextView(ctx);
        caption.setText(text);
        caption.setTextColor(MaterialColors.getColor(ctx, R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.brand)));
        // Medium, not bold, at the size Material gives a list subheader — and started on the same line
        // the icons below it start on, 16dp into the row, so the panel has one left edge instead of
        // three. It keeps the accent: the settings screen names its sections in it, and one coral line
        // at the top of a group is a signature where eight coral glyphs under it were noise.
        caption.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textCaption());
        caption.setPadding(Utils.dpToPx(6), Utils.dpToPx(16), Utils.dpToPx(6), Utils.dpToPx(8));
        return caption;
    }

    // The panel every picker shares, matching the quality/playlist menus.
    static void menu(final Activity activity, final UiMetrics ui, final Runnable onShow, CharSequence menuTitle, List<MenuItem> items) {
        menu(activity, ui, onShow, menuTitle, items, 34, 48);
    }

    /**
     * @param posterWDp poster size for rows that carry artwork. The default is a thumbnail beside a
     *                  name that already says everything; a list where the poster is what tells the
     *                  rows apart — search results for a name typed from across the room — asks for
     *                  bigger, and nothing else about the panel changes.
     */
    static void menu(final Activity activity, final UiMetrics ui, final Runnable onShow, CharSequence menuTitle, List<MenuItem> items, int posterWDp, int posterHDp) {
        menu(activity, ui, onShow, menuTitle, items, posterWDp, posterHDp, null);
    }

    /**
     * @param back the step this panel was reached from, or null for a panel that is the first thing
     *             opened. When there is one, the header carries an arrow and the system's back gesture
     *             goes there instead of closing the panel.
     */
    static void menu(final Activity activity, final UiMetrics ui, final Runnable onShow,
                     CharSequence menuTitle, List<MenuItem> items, int posterWDp, int posterHDp,
                     Runnable back) {
        if (items == null || items.isEmpty()) {
            return;
        }
        final View[] currentRow = new View[1];
        // Where the D-pad starts when no row is checked — a panel of actions has nothing ticked, and
        // without this a remote opens onto whatever focus the dialog happened to pick.
        final View[] firstRow = new View[1];

        // The panels follow the appearance choice, like the dialogs do — see
        // Utils.dialogContext. Every view below is built against it, so a light app gets a
        // light panel and AMOLED gets a black one.
        final Context ctx = Dialogs.dialogContext(activity);
        final int onSurface = MaterialColors.getColor(ctx, R.attr.colorOnSurface, Color.WHITE);
        // What "chosen" looks like, shared with the toggle groups and the settings switch.
        final int selectedFill = MaterialColors.getColor(ctx, R.attr.colorSecondaryContainer,
                ContextCompat.getColor(ctx, R.color.brand_container));
        final int onSelected = MaterialColors.getColor(ctx, R.attr.colorOnSecondaryContainer, Color.WHITE);
        final int accent = MaterialColors.getColor(ctx, R.attr.colorPrimary,
                ContextCompat.getColor(ctx, R.color.brand));
        final LinearLayout listLayout = new LinearLayout(ctx);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        final int listPad = Utils.dpToPx(10);
        listLayout.setPadding(listPad, listPad, listPad, listPad);

        // A panel whose groups tell themselves apart needs no title of its own — see showMoreMenu. It
        // still gets the line the title would have sat on, because the close button lives there.
        TextView header = null;
        if (menuTitle != null) {
            header = new TextView(ctx);
            header.setText(menuTitle);
            header.setTextColor(onSurface);
            header.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textTitle());
            header.setTypeface(Typeface.DEFAULT_BOLD);
            header.setPadding(Utils.dpToPx(6), Utils.dpToPx(10), Utils.dpToPx(6), Utils.dpToPx(10));
        }
        listLayout.addView(Dialogs.pickerHeader(ctx, ui, header, back));

        // Each group of rows is its own card, the way a preference category is one in settings. The
        // holder is null between groups: the next row opens a new card, and a bare boundary needs no
        // rule because the gap between two cards already is one.
        // Whether anything is above the boundary yet, so the panel never opens on a rule.
        final boolean[] anyRow = {false};

        // A list where one row is the chosen one keeps the accent for that job and letters its icons in
        // the quiet ink — eight corals in a column say nothing, and the coral two rows down would stop
        // meaning "chosen". A list where nothing is chosen has no such claim on it: its icons take the
        // accent, the way the settings hub letters its own, which is what these rows are — doorways.
        boolean anyChosen = false;
        for (final MenuItem item : items) {
            if (item.checked) {
                anyChosen = true;
                break;
            }
        }
        final int iconInk = anyChosen
                ? MaterialColors.getColor(ctx, R.attr.colorOnSurfaceVariant,
                        ContextCompat.getColor(ctx, R.color.ink_secondary))
                : MaterialColors.getColor(ctx, R.attr.colorPrimary,
                        ContextCompat.getColor(ctx, R.color.brand));

        for (final MenuItem item : items) {
            if (item.chrome) {
                if (item.title != null) {
                    listLayout.addView(menuCaption(ctx, ui, item.title));
                } else if (anyRow[0]) {
                    listLayout.addView(menuDivider(ctx, ui));
                }
                continue;
            }
            final boolean isCurrent = item.checked;

            final LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            // 6dp inside the list's own 10dp, so the icon starts 16dp from the sheet's edge and its
            // label at 16 + 24 + 16 = 56dp — the two numbers Material states for a list item with a
            // leading icon. The vertical padding only keeps a two-line row off its own edges; the
            // height below does the rest.
            row.setPadding(Utils.dpToPx(6), Utils.dpToPx(8), Utils.dpToPx(6), Utils.dpToPx(8));
            row.setClickable(true);
            row.setFocusable(true);
            // A one-line row is 48dp and a one-line row that leads with an icon or a poster is 56dp —
            // Material states both, and this list has rows of each kind.
            final boolean leads = item.iconRes != 0
                    || (item.imageUrl != null && !item.imageUrl.isEmpty());
            row.setMinimumHeight(leads ? ui.dpS(56) : ui.rowMinHeight());
            row.setBackground(Dialogs.pickerRow(ctx, isCurrent ? selectedFill : Color.TRANSPARENT));
            if (isCurrent) {
                currentRow[0] = row;
            }
            if (firstRow[0] == null) {
                firstRow[0] = row;
            }

            // A room with artwork leads with it rather than with a glyph: in a list of rooms the poster is
            // what tells them apart, and the same corner radius the header's poster uses keeps the two
            // readings of "this film" looking like one thing. Cropped, not fitted — a row of posters with
            // ragged widths reads as broken, and losing a sliver of a 2:3 image costs nothing.
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                final ImageView art = new ImageView(ctx);
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                // Same placeholder as the header's poster slot, so a slow load is a quiet grey card
                // rather than a hole that shifts the text when it fills.
                art.setBackgroundColor(MaterialColors.getColor(ctx, R.attr.colorSurfaceContainerHighest,
                        ContextCompat.getColor(ctx, R.color.placeholder_card)));
                final int artCorner = Utils.dpToPx(4);
                art.setClipToOutline(true);
                art.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), artCorner);
                    }
                });
                final LinearLayout.LayoutParams artLp =
                        new LinearLayout.LayoutParams(ui.dpS(posterWDp), ui.dpS(posterHDp));
                artLp.setMarginEnd(Utils.dpToPx(16));
                art.setLayoutParams(artLp);
                row.addView(art);
                Glide.with(activity).load(item.imageUrl).into(art);
            } else if (item.iconRes != 0) {
                final ImageView icon = new ImageView(ctx);
                icon.setImageResource(item.iconRes);
                // One accent, one job: see iconInk above. On the chosen row the icon takes the ink that
                // belongs to the fill under it.
                icon.setImageTintList(ColorStateList.valueOf(isCurrent ? onSelected : iconInk));
                // 24dp, the size Material states for a list item's leading icon — through dpS, because
                // the row's own height goes through it, and a glyph that stays put in a row that grows
                // reads as a smaller glyph on every device the chrome scales up for.
                final int iconSize = ui.dpS(24);
                final LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.setMarginEnd(Utils.dpToPx(16));
                icon.setLayoutParams(iconLp);
                row.addView(icon);
            }

            final LinearLayout textBlock = new LinearLayout(ctx);
            textBlock.setOrientation(LinearLayout.VERTICAL);
            final LinearLayout.LayoutParams blockLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            blockLp.gravity = Gravity.CENTER_VERTICAL;
            textBlock.setLayoutParams(blockLp);

            final TextView title = new TextView(ctx);
            title.setText(item.title);
            title.setTextColor(isCurrent ? onSelected : onSurface);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBody());
            if (isCurrent) {
                title.setTypeface(Typeface.DEFAULT_BOLD);
            }
            // The poster took the glyph's place, so whatever the glyph was saying moves in beside the
            // name — for a room that is the padlock, and losing it would leave the password to be
            // discovered by tapping.
            if (item.imageUrl != null && !item.imageUrl.isEmpty() && item.iconRes != 0) {
                final Drawable mark = ContextCompat.getDrawable(activity, item.iconRes);
                if (mark != null) {
                    final int markBox = Math.round(title.getTextSize());
                    mark.setBounds(0, 0, markBox, markBox);
                    mark.setTintList(ColorStateList.valueOf(isCurrent ? onSelected : accent));
                    title.setCompoundDrawablesRelative(mark, null, null, null);
                    title.setCompoundDrawablePadding(Utils.dpToPx(6));
                }
            }
            textBlock.addView(title);

            TextView details = null;
            if (item.subtitle != null && item.subtitle.length() > 0) {
                details = new TextView(ctx);
                details.setText(item.subtitle);
                // On the row that is chosen it takes the ink of the fill it sits on: the quiet ink of
                // the panel measures about 1.6:1 against the accent, which is a second line nobody can
                // read on the one row the eye goes to first.
                details.setTextColor(isCurrent ? onSelected
                        : MaterialColors.getColor(ctx, R.attr.colorOnSurfaceVariant,
                                ContextCompat.getColor(ctx, R.color.ink_secondary)));
                details.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textCaption());
                textBlock.addView(details);
            }
            row.addView(textBlock);
            fitLongText(activity, ui, row, title, details);

            row.setOnClickListener(v -> {
                if (openMenu != null) {
                    openMenu.dismiss();
                }
                if (item.action != null) {
                    item.action.run();
                }
            });
            listLayout.addView(row);
            anyRow[0] = true;
        }

        final android.widget.ScrollView scrollView = new android.widget.ScrollView(ctx);
        scrollView.addView(listLayout);
        // The rows carry their own side padding; the card only keeps them off its rounded ends.
        // Nothing at the bottom: the list inside carries its own padding and the panel adds the
        // navigation bar's inset under that, so a third one here only opened a band of empty sheet
        // below the last row.
        scrollView.setPadding(0, Utils.dpToPx(8), 0, 0);

        final FrameLayout panel = new FrameLayout(ctx);
        panel.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // A list taller than its card is cut by the card's edge, and a row bisected with nothing to say so
        // reads as a rendering fault rather than as "there is more". The platform's own fading edge cannot
        // do it here — pickerWindow gives this card a background and clips it to its outline, and a fade is
        // composited before that, so it never appears (measured: the last row's glyph arrives at full
        // strength either way). A band of the card's own colour, over the list rather than inside it, does.
        // 28dp, the length the grid's rail already fades by, and only while there is something below.
        final View listFade = new View(ctx);
        listFade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.TRANSPARENT, MaterialColors.getColor(ctx, R.attr.colorSurface,
                        ContextCompat.getColor(ctx, R.color.sheet_surface))}));
        listFade.setVisibility(View.GONE);
        panel.addView(listFade, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ui.dpS(28), Gravity.BOTTOM));
        final Runnable syncListFade = () -> listFade.setVisibility(
                scrollView.canScrollVertically(1) ? View.VISIBLE : View.GONE);
        scrollView.getViewTreeObserver().addOnScrollChangedListener(syncListFade::run);
        scrollView.post(syncListFade);

        if (openMenu != null) {
            openMenu.dismiss();
        }
        openMenu = new android.app.Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
        Dialogs.pickerWindow(activity, ui, openMenu, panel);
        if (back != null) {
            Utils.panelBack(openMenu, back);
        }
        if (onShow != null) { onShow.run(); } else { openMenu.show(); }
        final View focus = currentRow[0] != null ? currentRow[0] : firstRow[0];
        if (focus != null) {
            focus.post(focus::requestFocus);
        }
    }
}

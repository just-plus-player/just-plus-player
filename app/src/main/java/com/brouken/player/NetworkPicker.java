package com.brouken.player;

import android.animation.TimeInterpolator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.util.TypedValue;
import android.view.animation.PathInterpolator;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the folders on this network and saves the one that is chosen.
 *
 * <p>One step: the machines that answered, and tapping one saves it. What it offers is not asked
 * here, because a saved host lists its own shares as the folders inside it — so there is nothing to
 * choose at this point, and the password, if one is wanted, is asked where every other network
 * password is asked: on the folder, when it refuses to open.
 *
 * <p>Three searches at once, into one list: the machines with shares on them, the media servers,
 * and the torrent servers — each announces itself by a different mechanism and is browsed by a
 * different protocol. A machine can answer more than one — a NAS with a media server and a
 * TorrServer on it answers all three — and every row is kept, because they lead to different places:
 * one to the files as they sit on the disk, the others to each server's own arrangement of them.
 *
 * <p>Typing an address stays available, because discovery is a convenience and never the only way
 * in — a network that answers nothing must not be a dead end.
 *
 * <p>The reason this exists rather than a form: on a television, with a remote, typing a hostname is
 * where people give up.
 */
final class NetworkPicker
        implements SmbDiscovery.Listener, DlnaDiscovery.Listener, TorrDiscovery.Listener {

    private final Activity activity;
    private final Runnable onSaved;
    private final Runnable onManual;
    private final LinearLayout list;
    private final List<SmbDiscovery.Host> hosts = new ArrayList<>();
    private final List<DlnaDiscovery.Server> servers = new ArrayList<>();
    private final List<TorrDiscovery.Server> torrents = new ArrayList<>();
    private final SmbDiscovery discovery;
    private final DlnaDiscovery mediaServers;
    private final TorrDiscovery torrentServers;

    private Dialogs.Panel dialog;
    /** How many of the three searches are still going. */
    private int searching = 3;
    /** The searching / nothing-found line, kept so it can be changed without a rebuild. */
    @Nullable
    private TextView note;
    /** The line that runs while the searches do, kept so it can be taken away when they stop. */
    @Nullable
    private LinearProgressIndicator progress;


    private NetworkPicker(final Activity activity, final Runnable onSaved, final Runnable onManual) {
        this.activity = activity;
        this.onSaved = onSaved;
        this.onManual = onManual;
        final Context context = Dialogs.dialogContext(activity);
        this.list = new LinearLayout(context);
        this.list.setOrientation(LinearLayout.VERTICAL);
        // 6dp, which is what the sheet sets its header and its answers to inside its own 10dp - so
        // the rows stand at the same 16dp as the buttons under them and one gutter runs down the
        // sheet. At 16dp they stood 26dp in and every row was visibly narrower than the two pills
        // below it. The same miss the fields strip had, and Dialogs.fieldGutter argues it in full -
        // not reused here because it answers 24dp for a window in the middle, and a panel is a sheet
        // at every width.
        this.list.setPadding(Utils.dpToPx(6), Utils.dpToPx(8), Utils.dpToPx(6), Utils.dpToPx(8));
        this.discovery = new SmbDiscovery(activity, this);
        this.mediaServers = new DlnaDiscovery(this);
        this.torrentServers = new TorrDiscovery(activity, this);
    }

    /**
     * @param onSaved  the browser refreshing itself once a folder is saved
     * @param onManual the address form, for a network that answered nothing
     */
    static void show(final Activity activity, final Runnable onSaved, final Runnable onManual) {
        final NetworkPicker picker = new NetworkPicker(activity, onSaved, onManual);
        final Context context = Dialogs.dialogContext(activity);
        // A surface you browse is a panel, which under 600dp is a sheet at the bottom edge (R6, R7).
        // This one was the last window in the app still opening as a card in the middle: it was built
        // straight off a Material builder, so nothing ever asked Dialogs.asksAtTheEdge on its behalf.
        // The scroller is Dialogs' own now — a sheet puts its content in one.
        picker.dialog = Dialogs.panel(activity, activity.getString(R.string.browse_add_network),
                picker.list, activity.getString(R.string.browse_network_manual),
                () -> picker.onManual.run(), picker::stopSearching);
        picker.rebuild();
        picker.discovery.start();
        picker.mediaServers.start();
        picker.torrentServers.start();
    }

    // ---- what the discovery says ----

    @Override
    public void onHost(final SmbDiscovery.Host host) {
        hosts.add(host);
        // Appended rather than rebuilding the list: they arrive over several seconds, and a list that
        // redraws itself under a remote takes the focus away from whatever was about to be pressed.
        hostRow(host, -1);
        found();
    }

    @Override
    public void onServer(final DlnaDiscovery.Server server) {
        servers.add(server);
        serverRow(server, -1);
        found();
    }

    @Override
    public void onServer(final TorrDiscovery.Server server) {
        torrents.add(server);
        torrentRow(server, -1);
        found();
    }

    /**
     * A machine has answered. The line that stood in for the list goes now, with the row that replaces
     * it - and not when the searches stop, which is the one moment the sheet must not move: that is
     * when the viewer is reading the list and reaching for a row, and a line taken out then drops every
     * row by its height and the sheet's top edge with them. Here it costs nothing, because what it
     * gives way to is arriving in the same frame, and until it does the list is one line of prose that
     * nobody can aim at.
     */
    private void found() {
        if (note != null && note.getVisibility() != View.GONE) {
            note.setVisibility(View.GONE);
        }
        parkFocus();
    }

    /**
     * The focus follows the first machine found, and only that one: it is what the dialog is for,
     * and leaving the remote below it means arriving at a list already scrolled past. Every row
     * after that appears without disturbing what is being read.
     *
     * <p>Until something answers there is no row at all: the way to type an address is the sheet's
     * own action now, beside the one that backs out, and the remote waits on the sheet's landing
     * until a machine gives it somewhere better to be.
     */
    private void parkFocus() {
        if (hosts.size() + servers.size() + torrents.size() == 1) {
            // The first machine takes the remote off wherever the sheet parked it - which is the
            // sheet's own landing, there being nothing in the list to land on when it opened.
            // focusFirstRow leaves the list alone once the remote is in it, so the second machine and
            // every one after it arrive quietly.
            focusFirstRow();
        }
    }

    @Override
    public void onFinished() {
        // Every search reports, and the line speaks for all of them: one still going means
        // something may still arrive.
        if (--searching > 0 || note == null) {
            return;
        }
        if (progress != null) {
            // hide(), which is Material's own answer to this and ends at INVISIBLE rather than GONE:
            // the line plays its disappearance and leaves its 4dp and the gap under it exactly where
            // they were, so the end of the search moves nothing at all.
            progress.hide();
        }
        if (nothingFound()) {
            // The one line the sheet has left says what came of it, in the place it already holds.
            settle();
            note.setText(R.string.browse_network_none);
        }
        // And nothing else: a sheet with machines in it gave its line to the first of them.
    }

    private void stopSearching() {
        discovery.stop();
        mediaServers.stop();
        torrentServers.stop();
    }

    private boolean nothingFound() {
        return hosts.isEmpty() && servers.isEmpty() && torrents.isEmpty();
    }

    private void rebuild() {
        list.removeAllViews();
        hostList();
    }

    private void hostList() {
        dialog.setTitle(activity.getString(R.string.browse_add_network));
        // Directly under the header and over everything else, which is where Material hangs an
        // indeterminate line: it belongs to the sheet and not to any row, and from up there it is the
        // one thing in it that never moves. The same widget the subtitle search runs.
        progress = new LinearProgressIndicator(list.getContext());
        progress.setIndeterminate(true);
        final LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.bottomMargin = Utils.dpToPx(8);
        list.addView(progress, barLp);
        if (searching > 0 || nothingFound()) {
            note(searching > 0 ? R.string.browse_network_searching : R.string.browse_network_none);
        }
        for (final SmbDiscovery.Host host : hosts) {
            hostRow(host, -1);
        }
        for (final DlnaDiscovery.Server server : servers) {
            serverRow(server, -1);
        }
        for (final TorrDiscovery.Server server : torrents) {
            torrentRow(server, -1);
        }
        focusFirstRow();
    }

    /** One machine. {@code at} is where to insert it, or -1 to append. */
    private void hostRow(final SmbDiscovery.Host host, final int at) {
        row(host.name, host.address, "SMB", at, () -> save(host));
    }

    /**
     * One media server. Marked as the protocol names it, because a machine that answered both
     * searches is on this list twice and the two rows lead to different places — and DLNA is what
     * the box, the television and the router all call it themselves.
     */
    private void serverRow(final DlnaDiscovery.Server server, final int at) {
        row(server.name, server.address, "DLNA", at, () -> save(server));
    }

    /**
     * One torrent server. Marked as the thing calls itself, for the same reason a media server is:
     * a box running both is on this list twice, and what the two rows open is not the same list of
     * files at all.
     */
    private void torrentRow(final TorrDiscovery.Server server, final int at) {
        row(server.name, server.address(), "TorrServer", at, () -> save(server));
    }

    /** Saves the host itself. Its shares are what the folder shows when it is opened. */
    private void save(final SmbDiscovery.Host host) {
        saved(new NetworkPlaces.Place(host.name, host.address, 0, "", ""));
    }

    /** Saves the server's control address, which is what a {@code Browse} needs. */
    private void save(final DlnaDiscovery.Server server) {
        saved(DlnaFiles.place(server.name, server.control, server.udn));
    }

    /** Saves the server's address; the torrents on it are the folders inside it. */
    private void save(final TorrDiscovery.Server server) {
        saved(TorrFiles.place(server.name, server.host, server.port, server.secure, "", ""));
    }

    private void saved(final NetworkPlaces.Place place) {
        NetworkPlaces.add(activity, place, "");
        stopSearching();
        dialog.dismiss();
        onSaved.run();
    }

    // ---- rows ----

    /**
     * What the sheet does when its content changes height: short4 200ms on the standard curve, so a
     * machine arriving reads as the sheet settling rather than as a jump. The scene is the window's own
     * root, because the thing that has to move is the sheet's top edge - a bound of a view well above
     * this list. Nothing at all under reduced motion, where an instant change is the point.
     */
    private void settle() {
        if (Utils.isReducedMotion(activity)) {
            return;
        }
        final View root = list.getRootView();
        if (!(root instanceof ViewGroup)) {
            return;
        }
        final Transition settle = new AutoTransition();
        settle.setDuration(200);
        final TimeInterpolator standard = new PathInterpolator(0.2f, 0f, 0f, 1f);
        settle.setInterpolator(standard);
        TransitionManager.beginDelayedTransition((ViewGroup) root, settle);
    }

    /** A line of prose where a list would be: searching, or nothing found, or a refusal. */
    private void note(final int message) {
        note = new TextView(list.getContext());
        note.setText(message);
        // No room above it: the gap under the running line is the gap over this one.
        note.setPadding(0, 0, 0, Utils.dpToPx(12));
        list.addView(note);
    }

    /**
     * A tappable machine: what it calls itself, where it answers, and what will be spoken to it.
     * Focusable, because on a television this list is walked with a remote.
     *
     * <p>The address is a line of its own rather than a word after the name — it is what tells two
     * boxes called Home apart, and it is the part nobody chose. It is left out when the machine
     * announced no name and the address is already the title.
     */
    private void row(final String name, final String address, final String kind, final int at,
                     final Runnable onTap) {
        final Context context = list.getContext();
        final UiMetrics ui = UiMetrics.of(activity, Utils.isTvBox(activity));
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(ui.rowMinHeight());
        row.setPadding(Utils.dpToPx(12), Utils.dpToPx(8), Utils.dpToPx(12), Utils.dpToPx(8));
        // The contour the whole app means by focus, rather than the platform's wash: a row here is
        // the same kind of thing as a row in the list behind it and has to answer a remote the same
        // way. Transparent fill, because none of these rows is a chosen one.
        row.setBackground(Dialogs.pickerRow(context, Color.TRANSPARENT));
        row.setOnClickListener(v -> onTap.run());

        final LinearLayout said = new LinearLayout(context);
        said.setOrientation(LinearLayout.VERTICAL);
        row.addView(said, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        said.addView(line(context, name, ui.textBody(), R.attr.colorOnSurface));
        if (!address.equals(name)) {
            said.addView(line(context, address, ui.textCaption(), R.attr.colorOnSurfaceVariant));
        }
        row.addView(badge(context, ui, kind));
        // Machines answer over several seconds and each one makes the sheet taller. Announced, so the
        // sheet grows into the new height instead of snapping to it - and so the line this row is
        // replacing leaves in the same movement.
        settle();
        list.addView(row, at < 0 ? list.getChildCount() : at);
    }

    /** One line of a row, cut at the end: a name and an address are both read from the front. */
    private static TextView line(final Context context, final String text, final float size,
                                 final int ink) {
        final TextView view = new TextView(context);
        view.setText(text);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(MaterialColors.getColor(context, ink, Color.WHITE));
        return view;
    }

    /**
     * Which protocol will be spoken to that machine, as a mark rather than as more of the address.
     * A NAS running shares, a media server and a TorrServer is three rows at one address, and the
     * protocol is the only thing that tells them apart — so it is read before the address is, which
     * a word tacked onto the end of a line is not.
     *
     * <p>An 8dp tile in the surface's own container colour (R2, R3): something to read, not
     * something to press, so it takes no contour, no edge and none of the accent.
     */
    private static TextView badge(final Context context, final UiMetrics ui, final String kind) {
        final TextView badge = new TextView(context);
        badge.setText(kind);
        badge.setSingleLine(true);
        badge.setIncludeFontPadding(false);
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.textBadge());
        badge.setTextColor(MaterialColors.getColor(context, R.attr.colorOnSurfaceVariant, Color.GRAY));
        badge.setPadding(Utils.dpToPx(8), Utils.dpToPx(4), Utils.dpToPx(8), Utils.dpToPx(4));
        final GradientDrawable plate = new GradientDrawable();
        plate.setCornerRadius(Utils.dpToPx(8));
        plate.setColor(MaterialColors.getColor(context, R.attr.colorSurfaceContainerHighest,
                Color.DKGRAY));
        badge.setBackground(plate);
        final LinearLayout.LayoutParams where = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        where.setMarginStart(Utils.dpToPx(12));
        badge.setLayoutParams(where);
        return badge;
    }

    /**
     * A remote has to land on something, and the dialog's own buttons are the wrong somewhere: the
     * list is what was opened. Only when nothing in it is focused already, so a row appearing does
     * not steal the focus from the row being read.
     */
    private void focusFirstRow() {
        // Posted, because a row that has just been added has not been laid out yet, and a view with
        // no width or height cannot take focus (View.canTakeFocus). That is why the remote used to
        // stay on "Cancel" for the whole life of the sheet: the list is empty when the sheet lands,
        // so the landing goes to the button, and every later attempt to move it to a machine was made
        // one layout too early and quietly failed.
        list.post(() -> {
            if (list.findFocus() != null) {
                // The remote is already somewhere in the list - a row arriving does not take it off
                // the row being read.
                return;
            }
            for (int i = 0; i < list.getChildCount(); i++) {
                final View child = list.getChildAt(i);
                if (child.isFocusable()) {
                    child.requestFocus();
                    return;
                }
            }
        });
    }

}

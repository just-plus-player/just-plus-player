package com.brouken.player;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.EditText;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuCompat;
import androidx.appcompat.widget.SearchView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.documentfile.provider.DocumentFile;
import com.brouken.player.together.Relay;
import com.brouken.player.together.Room;
import com.brouken.player.together.TogetherManager;
import com.brouken.player.update.UpdateUi;
import com.brouken.player.update.Updater;
import androidx.documentfile.provider.NetworkDocumentFile;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.shape.RelativeCornerSize;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.navigation.NavigationBarView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;
import java.util.Locale;

/**
 * The device, browsed — its own tree, walked from the storage volumes down, with nothing to pick
 * first. A folder is read with {@link File#listFiles()} through {@link DocumentFile#fromFile}, which
 * is what lets the listing, the ordering and the rows be written once.
 *
 * <p>That freedom is bought with a permission and there is no other currency for it. Up to API 29
 * {@code READ_EXTERNAL_STORAGE} opens the whole of shared storage; from API 30 nothing does except
 * all-files access, which is the thing scoped storage exists to force apps off. The alternative —
 * having the viewer nominate one folder through the system picker — was built first and rejected: it
 * is not browsing, and on a television there is no picker to nominate it with, since Android TV
 * answers {@code ACTION_OPEN_DOCUMENT_TREE} with a stub that swallows the intent. All-files access,
 * by contrast, can be granted there: the settings screen is served by {@code com.android.tv.settings}.
 *
 * <p>It answers with a single uri, as the pickers it will replace do, and leaves the playlist alone:
 * opening a file out of a folder already makes that folder the playlist
 * ({@code PlayerActivity.folderPlaylist}), so nothing is handed over and no intent has to carry a
 * list.
 */
public class BrowserActivity extends AppCompatActivity implements Dialogs.Chromed {

    /** Browse for a subtitle rather than for something to play. */
    public static final String EXTRA_SUBTITLES = "subtitles";

    /**
     * The four places this screen can be standing in. Only where they are listed changes with the
     * window (R7): the bar below 600dp, the rail from 600dp, the start page's cards on a television.
     *
     * <p>Favorites is first because it is what somebody who has one comes back for, and it is not
     * the default: {@link Prefs#getBrowseDest} restores whatever was last open, which on a first run
     * is Files - so an empty list is never the first thing anyone sees.
     */
    private enum Dest { FAVORITES, FILES, NETWORK, IPTV }

    private static final int REQUEST_STORAGE = 2;
    private static final int SEARCH_LIMIT = 500;

    private static final String STATE_TRAIL = "trail";
    private static final String STATE_DEST = "dest";

    private boolean subtitles;

    /**
     * True when this screen is standing in for a picker - fetching a subtitle, or answering somebody
     * who started it for a result - rather than being the app's front door. Then it carries no
     * navigation surface, no start page and none of the menu that is not about files.
     */
    private boolean picker;

    private Dest dest = Dest.FILES;
    private boolean tv;
    // When Back was last pressed with nothing left to step out of. A television's remote has Back under
    // the thumb and the launcher behind it, so leaving the app outright is worth a second press; a phone
    // has a gesture its owner makes on purpose and a launcher one tap away, and asks nothing.
    private long backPressedAt = Utils.BACK_NOT_PRESSED;
    /** The bar or the rail, whichever this window calls for; null on a television and in a picker. */
    private NavigationBarView nav;
    /** The television start page. Inflated always, on screen only there. */
    private View home;
    private View header;
    /** Shown while a listing is being read, and only once it has kept the screen waiting. */
    private LinearLayout skeleton;
    // How long a listing may take before it is worth saying anything, and what a skeleton is made of
    // once it is: see the comment on browse_skeleton in activity_browser.xml.
    // A fifth of a second, and deliberately short: a skeleton is the shape of the content, not a
    // sign that the app is busy. A spinner has to earn its appearance, because it says "wait"; this
    // says "here is where the rows will be", which is true from the first frame and costs nothing to
    // say early. The comments around it read "half a second" for a while and the constant did not,
    // which is how the two came apart.
    private static final long SKELETON_DELAY_MS = 200;
    private static final long SKELETON_PULSE_MS = 900;
    // How far down the breath goes. It was 0.45, a swing of more than half the plate's opacity, which
    // is a flash and not a breath - and on a folder that never answers it is the only thing on screen.
    private static final float SKELETON_DIM = 0.65f;
    /** The breath, held so it can be stopped. See {@link #pulse()}. */
    private ObjectAnimator skeletonPulse;
    // Held as one object: removeCallbacks cancels the instance it is given, and a method reference
    // written at two call sites is two objects, so the second would cancel nothing.
    private final Runnable skeletonIn = this::raiseSkeleton;

    /**
     * The folders standing open, outermost first. Empty means the volume list, which is the level
     * above every root and the only screen here that is not a directory.
     */
    private final List<Item> trail = new ArrayList<>();
    private final List<Item> rows = new ArrayList<>();

    private RecyclerView list;
    private View crumbsScroll;
    private LinearLayout crumbs;
    private View blank;
    private LinearLayout blankTiles;
    private TextView blankTitle;
    private TextView blankText;
    private MaterialButton blankAction;
    private Thread lister;
    private boolean asked;
    /**
     * Whether this screen has drawn a listing yet. Before the first one it has no memory of its own and
     * takes the player's - see the landing block of {@link #bind}; after it, its own is the newer.
     */
    private boolean landed;
    /** What is being searched for, or null when the list is just showing a folder. */
    private String query;
    /** Which listing the rows on screen belong to - destination, trail and query - or null for none. */
    private String bound;
    /**
     * What the screen is asking for right now, as opposed to {@link #bound}, which is what it is
     * showing. A listing answers on a background thread and posts its rows to this one, and by then
     * the viewer may have moved: the two are only the same while nothing has changed under the answer
     * on its way back.
     */
    private String sought;
    /**
     * The network folder standing open and the rows that were read to show it. A search inside it
     * filters these rather than asking the server again: they are already in hand, so the search
     * costs no request at all and answers on the keystroke. Written and read on the listing thread.
     */
    private volatile String openFolderKey;
    private volatile List<Item> openFolderListing;
    /** What the bar was last built for - see {@link #refreshMenu}. */
    private String menuKey;
    /**
     * The appearance this window was built in. A theme is chosen on another screen and this one is
     * only restarted when that screen closes, so without a look at it on the way back the shell keeps
     * the colours it was inflated in until the app is started again.
     */
    private String appearance;
    /** What {@link #browsingKey()} said when the index standing in memory was built. */
    private String browsing;

    /**
     * Two threads for the things only a read of the file can answer — a running time, a folder's
     * contents. Two rather than one, so a slow file does not hold up the row under it, and rather
     * than a thread per row, which a fast scroll would turn into dozens.
     */
    private final ExecutorService readers = Executors.newFixedThreadPool(2);
    private final Map<String, String> durations = new HashMap<>();
    /** The same answer in milliseconds, which is what the played run needs and a "1:56" cannot give. */
    private final Map<String, Long> lengths = new HashMap<>();
    /**
     * The same files as the system's own index knows them, for the one thing the system does better:
     * a picture of a video. Only the files the index answered for; anything the walk found behind it
     * is decoded the old way.
     */
    private final Map<String, Uri> stills = new HashMap<>();
    /**
     * The day a file was written, in the shortest form the viewer's locale has for one: dd.MM.yy in
     * Russian, MM/dd/yy in American English. Built once - a formatter per bound row is a formatter
     * per row of a folder of five thousand.
     */
    private final SimpleDateFormat shortDate = new SimpleDateFormat(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "ddMMyy"),
            Locale.getDefault());
    /** How a folder is drawn: one of {@code rows}, {@code tiles}, {@code columns}. */
    private String view = "rows";
    /** Sizes out of the index or the walk, so a row does not ask the filesystem for one twice. */
    private final Map<String, Long> sizes = new HashMap<>();
    /** When the file was last written, same source and same reason as {@link #sizes}. */
    private final Map<String, Long> dates = new HashMap<>();
    /**
     * The shorter side of the picture with a p after it - 1080p - which is how a video is named
     * everywhere outside a settings screen. Out of the index where the system knows it, and off the
     * retriever that is opened for the running time anyway where it does not.
     */
    private final Map<String, String> resolutions = new HashMap<>();
    /** The position each row was last bound with, so a refresh can rebind the ones that moved. */
    private final Map<String, Long> shown = new HashMap<>();
    /** What the player has remembered, read once per listing — see {@link Prefs#readPositions}. */
    private Map<String, Long> positions = Collections.emptyMap();
    /** {@link #videoFolders()}, once built - and null until it is. Written on the main thread. */
    private volatile Map<String, long[]> videoFolders;
    /** True while {@link #indexFolders()} is building it, so it is built once and not once per row. */
    private boolean indexing;
    /** Folder path to the newest video anywhere below it: what a folder's row shows a frame of. */
    private final Map<String, String> folderArt = new HashMap<>();
    /**
     * The picture a favorite was put aside with, by uri. A media server hands one over in its
     * listing, and a favorite is only an address - so what the row showed is written down with it
     * rather than asked for again. See {@link Favorites.Favorite}.
     */
    private final Map<String, String> favoriteArt = new HashMap<>();
    /**
     * True while the one storage volume is standing in for the destination's root. One volume is not
     * a choice, so it is not a screen of its own: Files opens inside it, and Back out of it leaves as
     * if it were the root, because it is.
     */
    private boolean volumeIsRoot;
    /** Every indexed video's path, filled by the same query. What search matches names against. */
    private final List<String> videoPaths = new ArrayList<>();

    /** A row: the name to show, and the folder or file behind it. The two differ only for a volume. */
    private static final class Item {
        final String name;
        final DocumentFile file;
        final boolean folder;
        /** A whole storage volume rather than a folder in one: no path above it, nothing to count. */
        final boolean volume;

        Item(final String name, final DocumentFile file, final boolean volume) {
            this(name, file, volume, file.isDirectory());
        }

        /**
         * With the kind already known. A listing has just asked the filesystem whether each entry is a
         * directory; asking again per row is a second stat for every file in the folder.
         */
        Item(final String name, final DocumentFile file, final boolean volume, final boolean folder) {
            this.name = name;
            this.file = file;
            this.folder = folder;
            this.volume = volume;
        }

        Item(final String name, final DocumentFile file) {
            this(name, file, false);
        }
    }

    @Override
    protected void onApplyThemeResource(final Resources.Theme theme, final int resid, final boolean first) {
        super.onApplyThemeResource(theme, resid, first);
        // Here rather than in onCreate, for the reason SettingsActivity records: when the appearance
        // differs from the system's, AppCompat sets the window theme again on the way to the recreated
        // activity, and an overlay applied in onCreate is gone by the time anything is drawn.
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
    protected void onCreate(final Bundle savedInstanceState) {
        // Before super, or AppCompat applies the old mode and then recreates.
        getDelegate().setLocalNightMode(Prefs.getNightMode(this));
        super.onCreate(savedInstanceState);

        // Prefs.isLight and never getConfiguration().uiMode — the class of defect §2.5 of the design
        // document records four times over. The configuration on this pass can still carry the
        // system's mode while the DayNight theme has already resolved the app's.
        final boolean night = !Prefs.isLight(this);
        if (night && Prefs.isAmoledBlack(this)) {
            getTheme().applyStyle(R.style.ThemeOverlay_JustPlus_Amoled, true);
        }
        // The status bar takes the window's own colour (see BaseTheme.Settings), so its icons follow
        // the appearance actually in force.
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(!night);

        // Uniform behaviour across versions, and the reason it has to be said out loud: from API 35
        // edge to edge is enforced and the decor insets nothing, so an activity must inset itself -
        // which is how the header came to sit under the status bar on a recent phone. Up to API 34
        // the decor still fits the window, and then the insets reported here are the same ones it has
        // already applied, so applying them again doubles the gap. Turning the fitting off makes both
        // versions behave the same way, which is the only way to get this right once.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        subtitles = getIntent().getBooleanExtra(EXTRA_SUBTITLES, false);
        // Started for a result, this screen is somebody's picker and has to stay one: the player is
        // waiting underneath for a uri, so destinations, a start page and a menu of other ways to
        // open something have no business here.
        picker = subtitles || getCallingActivity() != null;
        tv = Utils.isTvBox(this);
        appearance = appearanceKey();
        browsing = browsingKey();

        setContentView(R.layout.activity_browser);
        final MaterialToolbar bar = findViewById(R.id.browse_toolbar);
        // A Toolbar refuses the focus to its own items wherever the device claims a touchscreen -
        // AppCompat sets setTouchscreenBlocksFocus(true) in its constructor, on the reasoning that
        // what can be tapped need not be walked to. A television that declares a touchscreen anyway
        // is then left with a search and an add button no remote can reach: measured on tv_720p,
        // which reports android.hardware.touchscreen alongside android.hardware.type.television, and
        // so does every box built from tablet firmware. On a television the items have to be
        // reachable, and it is the app's own idea of a television that decides - not the flag.
        if (Utils.isTvBox(this)) {
            bar.setTouchscreenBlocksFocus(false);
        }
        list = findViewById(R.id.browse_list);
        crumbsScroll = findViewById(R.id.browse_crumbs_scroll);
        crumbs = findViewById(R.id.browse_crumbs);
        blank = findViewById(R.id.browse_blank);
        blankTiles = findViewById(R.id.browse_blank_tiles);
        blankTitle = findViewById(R.id.browse_blank_title);
        blankText = findViewById(R.id.browse_blank_text);
        blankAction = findViewById(R.id.browse_blank_action);
        header = findViewById(R.id.browse_header);
        home = findViewById(R.id.browse_home);
        skeleton = findViewById(R.id.browse_skeleton);
        setSupportActionBar(bar);
        bar.setNavigationOnClickListener(v -> up());

        // A grid of one column is a list, so one manager serves all three views and switching
        // between them is a span count rather than a different screen.
        view = Prefs.browseView(this);
        list.setLayoutManager(new GridLayoutManager(this, 1));
        // Two columns of rows stood shoulder to shoulder. A row states the space below it with a
        // margin and nothing at all beside it, which is exactly right while it is a list and wrong
        // the moment there are two of them: the backgrounds touch, and with a focus contour on each
        // the pair reads as one wide row. The same margin is put between them here, split so the
        // outer edges stay where a single column has them and the columns keep equal widths.
        //
        // Rows only. A tile carries 8dp of padding on every side of itself, so a grid of tiles is
        // already evenly spaced, and adding to it would take the width from the still.
        list.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull final Rect out, @NonNull final View child,
                                       @NonNull final RecyclerView parent,
                                       @NonNull final RecyclerView.State state) {
                final RecyclerView.LayoutManager manager = parent.getLayoutManager();
                final RecyclerView.Adapter<?> adapter = parent.getAdapter();
                final ViewGroup.LayoutParams params = child.getLayoutParams();
                if (!(manager instanceof GridLayoutManager) || adapter == null
                        || !(params instanceof GridLayoutManager.LayoutParams)) {
                    return;
                }
                final int spans = ((GridLayoutManager) manager).getSpanCount();
                final int column = ((GridLayoutManager.LayoutParams) params).getSpanIndex();
                final int position = parent.getChildAdapterPosition(child);
                if (spans < 2 || column < 0 || position == RecyclerView.NO_POSITION
                        || adapter.getItemViewType(position) != 0) {
                    return;
                }
                // The same 4dp browse_row asks for below itself.
                final int gap = Utils.dpToPx(4);
                out.left = column * gap / spans;
                out.right = gap - (column + 1) * gap / spans;
            }
        });
        // The list fills the window whatever is in it, so a change of contents need not measure the
        // list again - and a few more rows are kept built than the default two, because a short flick
        // back and forth otherwise rebinds what it just let go.
        list.setHasFixedSize(true);
        list.setItemViewCacheSize(8);
        list.setAdapter(new Rows());

        // From targetSdk 35 a window is edge to edge whether it asks to be or not, so an activity that
        // applies no insets draws its header under the status bar - which is what this one was doing.
        // The bars go on the root as padding and the bottom goes on the list, so the last row clears a
        // navigation bar while the list still scrolls under it. On a television every system inset is
        // 0, hence the overscan band synthesised beside them: the same 27dp at top and bottom that the
        // player keeps at its own edges.
        final UiMetrics metrics = UiMetrics.of(this, Utils.isTvBox(this));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.browse_root), (view, windowInsets) -> {
            final Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top + metrics.overscanV(), bars.right, 0);
            // Whichever of the two is at the foot of the window takes the navigation bar: the bottom
            // bar when there is one, the list itself otherwise. Padding both would leave a bar's
            // height of empty list above a bar already clear of the system's.
            final View bottomBar = nav != null && nav.getId() == R.id.browse_bar ? nav : null;
            if (bottomBar != null) {
                bottomBar.setPadding(0, 0, 0, bars.bottom);
                list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), 0);
            } else {
                list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                        bars.bottom + metrics.overscanV());
            }
            return windowInsets;
        });
        // The sides keep the same strip the settings list does, which on a television is the 48dp a
        // set may cut. Without it a row's focus contour is the first thing to go over the edge.
        final View root = findViewById(R.id.browse_root);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or2, ob) -> {
            final int rail = nav != null && nav.getId() == R.id.browse_rail ? nav.getWidth() : 0;
            final int usable = r - l - v.getPaddingLeft() - v.getPaddingRight() - rail;
            final int side = SettingsActivity.contentSideInset(this, usable);
            if (list.getPaddingLeft() != side) {
                list.setPadding(side, list.getPaddingTop(), side, list.getPaddingBottom());
                skeleton.setPadding(side, skeleton.getPaddingTop(), side, skeleton.getPaddingBottom());
                crumbsScroll.setPadding(side, crumbsScroll.getPaddingTop(), side,
                        crumbsScroll.getPaddingBottom());
            }
            // The number of tiles that fit is a question about the window, so it is answered where
            // the window answers for itself rather than once at startup.
            spanColumns();
        });

        blankAction.setOnClickListener(v -> grantAccess());
        registerBack();

        // Before the navigation is wired, because that is what puts the bar on the destination this
        // names. A trail without it was half the answer: a folder on a media server came back on a
        // fresh open with the bar still saying Files, and Back out of it walked a trail that belonged
        // to a destination the screen was not on.
        dest = destFromText(savedInstanceState != null
                ? savedInstanceState.getString(STATE_DEST) : Prefs.getBrowseDest(this));
        wireNavigation();
        // Started here rather than waited for later: the index is what lets a folder open out of one
        // query instead of a walk, and the first thing anybody does with this screen is open a folder.
        indexFolders();

        // A rotation restores exactly where it was; a fresh open picks up where browsing was left,
        // which is what makes coming back from the player feel like coming back rather than starting
        // over.
        trailFromText(savedInstanceState != null
                ? savedInstanceState.getString(STATE_TRAIL) : Prefs.getBrowseTrail(this));

        // ... and on a television that is also what decides whether the start page is what opens: a
        // remembered folder is where the viewer was, so landing on the showcase instead would be one
        // press of Back away from it anyway.
        if (tv && !picker && trail.isEmpty()) {
            showHome();
        }

        if (savedInstanceState == null && !picker) {
            maybeCheckForUpdate();
        }
    }

    // The launcher icon opens this screen, so the idle launch the update dialog waits for is this one.
    // It used to be the player's, and when the icon moved here the dialog was left behind with it: the
    // player's own check still runs for anybody arriving with a file, but only to light its button.
    private void maybeCheckForUpdate() {
        final Prefs prefs = new Prefs(this);
        if (!BuildConfig.ENABLE_UPDATE || !prefs.autoUpdate) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - prefs.updateLastCheck < Updater.CHECK_INTERVAL_MS) {
            return;
        }
        prefs.setUpdateLastCheck(now);
        Updater.find(info -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            final boolean offer = info != null && info.versionCode != prefs.updateSkippedVersionCode;
            prefs.setUpdatePending(offer ? info : null);
            if (offer) {
                UpdateUi.showAvailableDialog(this, Dialogs.dialogContext(this), info, () -> {
                    prefs.setUpdateSkippedVersionCode(info.versionCode);
                    prefs.setUpdatePending(null);
                }, false);
            }
        }));
    }

    @Override
    protected void onDestroy() {
        stopPulse();
        readers.shutdownNow();
        super.onDestroy();
    }

    /**
     * What the bar carries depends on where it is standing. Search is always there; the rest is the
     * menu behind the three dots, and a picker gets none of it - it is somebody else's screen for as
     * long as they are waiting for an answer.
     */
    @Override
    @SuppressLint("RestrictedApi")
    public boolean onPrepareOptionsMenu(final Menu menu) {
        // The popup behind the three dots drops the glyphs unless it is told to keep them, and a menu
        // of words with one picture in it reads as a fault rather than as a choice.
        if (menu instanceof MenuBuilder) {
            ((MenuBuilder) menu).setOptionalIconsVisible(true);
        }
        final MenuItem search = menu.findItem(R.id.browse_search);
        if (search != null) {
            // Neither at the top of Favorites nor at the top of Network. Both are a handful of rows
            // somebody chose or added themselves, and a search only means something once you are
            // inside a resource. It would also have run over the whole device from there - matches()
            // with no folder above it is every video the index knows.
            final boolean listOfPlaces = trail.isEmpty()
                    && (dest == Dest.FAVORITES || dest == Dest.NETWORK);
            search.setVisible(dest != Dest.IPTV && !listOfPlaces);
        }
        // Only where network folders are listed, which is the one level that has anything to add to:
        // inside a share there is nothing this button could add that the folder above it does not
        // already hold.
        // At the top of Network it adds a place; standing inside a torrent server it adds a magnet
        // link to that server, which is the one thing there is to add there. Same glyph either way,
        // because it means the same thing: put something new into what is on screen.
        final MenuItem add = menu.findItem(R.id.browse_add_network);
        final boolean onTorrServer = torrServerHere() != null;
        add.setVisible(((picker || dest == Dest.NETWORK) && trail.isEmpty()) || onTorrServer);
        // The same button in two places, and it does two different things: at the top of Network it
        // saves a server, and standing on a torrent server it hands that server a magnet. The glyph
        // says "put something new into what is on screen" either way, but the word has to be the one
        // for the place - a button that offers a network folder and then asks for a magnet is lying
        // twice over, in the tooltip and to anything reading the screen aloud.
        add.setTitle(onTorrServer ? R.string.browse_torrent_add : R.string.browse_add_network);
        // The star acts on the folder being stood in, so it appears once there is one to act on.
        final MenuItem favorite = menu.findItem(R.id.browse_favorite);
        final Item here = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        favorite.setVisible(!picker && dest != Dest.IPTV && here != null);
        if (here != null) {
            final boolean saved = Favorites.has(this, here.file.getUri());
            favorite.setIcon(saved ? R.drawable.ic_star_24dp : R.drawable.ic_star_outline_24dp);
            favorite.setTitle(saved ? R.string.browse_favorite_remove : R.string.browse_favorite_add);
        }
        menu.findItem(R.id.browse_open_link).setVisible(!picker);
        menu.findItem(R.id.browse_join_room).setVisible(!picker);
        menu.findItem(R.id.browse_settings).setVisible(!picker);
        // Not on somebody else's screen: a picker is open because another app is waiting for an
        // answer, and closing the task would be answering it with nothing.
        menu.findItem(R.id.browse_exit).setVisible(!picker);
        final MenuItem views = menu.findItem(R.id.browse_view);
        if (views != null) {
            views.setVisible(dest != Dest.IPTV);
            // What is on screen, not what is remembered: where two columns will not fit, what the
            // viewer is looking at is a list, and a glyph saying otherwise reads as a fault.
            final boolean twoFit = getResources().getConfiguration().screenWidthDp >= 600;
            views.setIcon("tiles".equals(view) ? R.drawable.ic_view_grid_24dp
                    : "columns".equals(view) && twoFit ? R.drawable.ic_view_columns_24dp
                    : R.drawable.ic_view_list_24dp);
        }
        // The glyphs are the player's, and the player's chrome is dark, so every one of them is a
        // hard white - which on a light bar is nothing at all. Tinted here rather than in the
        // drawables, which the controls over video still want white, and here rather than at
        // inflation, because the star and the view glyph are swapped for another white one every
        // time this runs. The colour is the one the three dots beside them already use.
        final int glyph = MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant, Color.WHITE);
        for (int index = 0; index < menu.size(); index++) {
            final Drawable icon = menu.getItem(index).getIcon();
            if (icon != null) {
                icon.mutate().setTint(glyph);
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
        if (item.getItemId() == R.id.browse_open_link) {
            OpenLink.ask(this, this::play);
            return true;
        }
        if (item.getItemId() == R.id.browse_join_room) {
            joinRoom();
            return true;
        }
        if (item.getItemId() == R.id.browse_view) {
            chooseView(nextView());
            invalidateOptionsMenu();
            return true;
        }
        if (item.getItemId() == R.id.browse_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        if (item.getItemId() == R.id.browse_exit) {
            finishAndRemoveTask();
            return true;
        }
        if (item.getItemId() == R.id.browse_add_network) {
            final Uri server = torrServerHere();
            if (server != null) {
                askForMagnet(server);
            } else {
                askForNetwork();
            }
            return true;
        }
        if (item.getItemId() == R.id.browse_favorite) {
            if (!trail.isEmpty()) {
                toggleFavorite(trail.get(trail.size() - 1));
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.browse, menu);
        // Two things behind the dots: somewhere to open, and the app's own screen. A rule between them
        // is what the reference draws and what the panels in the player already do with a group.
        MenuCompat.setGroupDividerEnabled(menu, true);
        final SearchView search = (SearchView) menu.findItem(R.id.browse_search).getActionView();
        if (search == null) {
            return true;
        }
        search.setQueryHint(getString(R.string.browse_search));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(final String text) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(final String text) {
                // Every keystroke re-runs the walk. show() interrupts the thread the last one was on,
                // so a fast typist leaves one search running rather than one per letter.
                query = text == null || text.trim().isEmpty() ? null : text.trim();
                show();
                return true;
            }
        });
        search.setOnCloseListener(() -> {
            query = null;
            show();
            return false;
        });
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        // The theme is picked in Settings, which repaints itself and knows nothing of the window it
        // was opened from. Compared rather than listened for: a preference change has no callback
        // that reaches a stopped activity, and this is the moment the viewer is looking at this one
        // again. Rebuilt only when it actually differs, so the ordinary return costs nothing.
        if (!appearanceKey().equals(appearance)) {
            recreate();
            return;
        }
        // Read on every arrival and not only on create: the all-files switch lives in Settings, which
        // is another app and hands back no result. Coming home from it is the only signal there is.
        // show() is what re-reads what the index was built under - see its head.
        show();
    }

    /**
     * What the media index was built under. Compared rather than listened for, exactly as the look
     * above it is: a preference change has no callback that reaches a stopped activity.
     *
     * <p>Access belongs in here beside the two settings, and its absence was a bug with a witness:
     * onCreate starts the index before anything has been granted, and a MediaStore query without the
     * grant is not refused - it answers for the files this app itself wrote, which is none of them.
     * That empty map is then held for the life of the screen, and an empty map hides nothing:
     * {@code indexedContents} knows no folder, the walk takes over, and {@code hideEmpty} is false for
     * every folder it lists. So the screen that came back from granting all-files access showed the
     * whole filesystem, and only a fresh process put it right.
     */
    private String browsingKey() {
        return Prefs.showHiddenFiles(this) + "|" + Prefs.ignoreNoMedia(this)
                + "|" + Utils.canListStorage(this);
    }

    /**
     * Everything about the look that is chosen rather than measured: which accent overlay, whether it
     * is the light one, and whether AMOLED black is on top of it. The overlay's own resource id
     * carries both the accent and the appearance, so a pick of either changes this string.
     */
    private String appearanceKey() {
        final boolean light = Prefs.isLight(this);
        return Prefs.accentOverlay(this, light) + "|" + light + "|" + Prefs.isAmoledBlack(this);
    }

    /**
     * The trail as one line per level, {@code name} then {@code path}. The name travels beside the
     * path because a volume's label is not its directory name: internal storage is
     * /storage/emulated/0, and a screen headed "0" is nobody's idea of where they are.
     */
    private String trailAsText() {
        final StringBuilder text = new StringBuilder();
        for (final Item folder : trail) {
            // The whole uri rather than its path: a network folder has no filesystem path to be
            // rebuilt from, and a local one is recovered from a file:// uri just as well.
            text.append(folder.name).append('\n').append(folder.file.getUri()).append('\n');
        }
        return text.toString();
    }

    /** The destination a remembered trail belongs to, or Files for anything unreadable. */
    private static Dest destFromText(final String name) {
        for (final Dest known : Dest.values()) {
            // IPTV is hidden while it is being built, so a trail remembered from before that would
            // reopen the app on a page with nothing on it and no item in the bar to leave by.
            if (known.name().equals(name) && known != Dest.IPTV) {
                return known;
            }
        }
        return Dest.FILES;
    }

    /** Fills {@link #trail} from what {@link #trailAsText} wrote, stopping at the first gone folder. */
    private void trailFromText(final String text) {
        trail.clear();
        if (text == null || text.isEmpty()) {
            return;
        }
        final String[] lines = text.split("\n");
        for (int i = 0; i + 1 < lines.length; i += 2) {
            final Item folder = itemFor(lines[i], Uri.parse(lines[i + 1]), i == 0, true);
            if (folder == null) {
                break;
            }
            trail.add(folder);
        }
    }

    /**
     * The row a remembered address stands for, or null when a local one has gone. The two things
     * that keep an address between sessions - the trail and the favorites - both come back through
     * here, because both saved a name beside a uri and nothing else.
     *
     * <p>{@code folder} is carried rather than asked for: over the network asking means a round
     * trip, and the answer was already known when the address was written down.
     */
    @Nullable
    private Item itemFor(final String name, final Uri where, final boolean top,
                         final boolean folder) {
        if (NetworkFiles.isNetwork(where)) {
            // Not checked for existence: that is a network round trip, and this runs on the way to
            // drawing. An address that has gone lists as empty and says so on a tap.
            //
            // ponytail: a favorited network *file* takes the same node as a folder would. Only its
            // uri and its name are ever read off it - the kind travels beside it - so a second
            // node type per protocol would be three classes to answer a question already answered.
            return new Item(name, NetworkFiles.folder(this, where, name), top, folder);
        }
        // A bare path is what earlier versions of the trail wrote.
        final String path = "file".equals(where.getScheme()) ? where.getPath() : where.toString();
        final File local = path == null ? null : new File(path);
        if (local == null || !local.exists() || local.isDirectory() != folder) {
            return null;
        }
        return new Item(name, DocumentFile.fromFile(local), top, folder);
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TRAIL, trailAsText());
        outState.putString(STATE_DEST, dest.name());
    }

    /** The all-files switch, and the blank state saying so when the device has no screen for it. */
    private void grantAccess() {
        if (!Utils.askForAllFiles(this)) {
            showBlank(R.string.browse_no_source, false);
        }
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions,
                                           @NonNull final int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_STORAGE) {
            show();
        }
    }

    /**
     * Every storage volume, as a row. The primary one always, and the removable ones — an SD card, a
     * stick in a television's socket, which is how most of what gets watched on one arrives — found
     * by walking up out of the private directory the system gives this app on each of them. That
     * detour is the portable way to enumerate volumes: {@code StorageManager} only began handing out
     * their paths at API 30, well above where this app runs.
     */
    private List<Item> volumes() {
        final List<Item> found = new ArrayList<>();
        final List<String> seen = new ArrayList<>();
        // Without the grant, a volume is worse than absent: /sdcard still lists its top-level folders
        // and every folder below shows its subfolders with the files gone - the filtered view - so a
        // browser offering them looks like a device full of empty folders.
        final File primary = Utils.canListStorage(this)
                ? Environment.getExternalStorageDirectory() : null;
        if (primary != null && primary.isDirectory()) {
            found.add(new Item(getString(R.string.browse_internal),
                    DocumentFile.fromFile(primary), true));
            seen.add(primary.getAbsolutePath());
        }
        for (final File appDir : Utils.canListStorage(this)
                ? ContextCompat.getExternalFilesDirs(this, null) : new File[0]) {
            if (appDir == null) {
                continue;
            }
            // <volume>/Android/data/<package>/files — four levels below the root.
            File root = appDir;
            for (int i = 0; i < 4 && root != null; i++) {
                root = root.getParentFile();
            }
            if (root == null || !root.isDirectory() || seen.contains(root.getAbsolutePath())) {
                continue;
            }
            seen.add(root.getAbsolutePath());
            found.add(new Item(volumeLabel(root), DocumentFile.fromFile(root), true));
        }
        return found;
    }

    /**
     * The saved network folders, which are volumes as far as a row is concerned: the top of a tree,
     * with no path above them and nothing about them in MediaStore. Their own destination now - they
     * sat at the bottom of the volume list until the shell had somewhere better to put them.
     */
    private List<Item> places() {
        final List<Item> found = new ArrayList<>();
        for (final NetworkPlaces.Place place : NetworkPlaces.all(this)) {
            found.add(new Item(place.name,
                    NetworkFiles.folder(this, place.uri(), place.name), true));
        }
        return found;
    }

    /**
     * What somebody has put aside, in the order they put it there. A list this short is remembered
     * by where its rows sit, so nothing re-sorts it.
     *
     * <p>A local favorite is an ordinary row - its parent path is what tells two folders called
     * Movies apart, and its count or its size costs a listing nobody notices. A network one is a
     * volume as far as the row is concerned, exactly as a saved place is: no path above it, and
     * nothing read off it until it is opened.
     *
     * <p>A local address that has gone drops out of the list and stays in the store. An unmounted
     * card comes back, and a favorite that quietly deleted itself when it was pulled out would not.
     */
    private List<Item> favorites() {
        final List<Item> found = new ArrayList<>();
        for (final Favorites.Favorite favorite : Favorites.all(this)) {
            // A network folder is a volume as far as the row is concerned, exactly as a saved place
            // is: counting what is inside it is a round trip. A file is not - what its row shows was
            // written down when it was put aside, so it costs the same as a local one: nothing.
            final boolean network = NetworkFiles.isNetwork(favorite.uri);
            final Item row = itemFor(favorite.name, favorite.uri, network && favorite.folder,
                    favorite.folder);
            if (row == null) {
                continue;
            }
            final String key = favorite.uri.toString();
            if (!favorite.art.isEmpty()) {
                favoriteArt.put(key, favorite.art);
            }
            if (favorite.size > 0) {
                sizes.put(key, favorite.size);
            }
            if (favorite.duration > 0) {
                lengths.put(key, favorite.duration);
                durations.put(key, Utils.formatMilis(favorite.duration));
            }
            found.add(row);
        }
        return found;
    }

    /**
     * What the top of the current destination holds. A picker has no destinations to divide them
     * between, so there the two kinds share one root the way they always did.
     */
    private List<Item> roots() {
        if (dest == Dest.FAVORITES) {
            return favorites();
        }
        if (dest == Dest.NETWORK) {
            return places();
        }
        final List<Item> found = volumes();
        if (picker) {
            found.addAll(places());
        }
        return found;
    }

    /**
     * Look for a folder on this network, or type an address in. The picker first, the form behind it:
     * a network that answers nothing must not be a dead end, and on a remote the form is where people
     * give up.
     */
    private void askForNetwork() {
        NetworkPicker.show(this, () -> {
            trail.clear();
            show();
        }, this::askForKind);
    }

    /**
     * Asks for a network folder and saves it. Four fields and a name, in the dialog family the rest
     * of the app uses; the password goes to the keystore and never into the address the app keeps.
     */
    /** Which protocol a typed place speaks. */
    private enum Kind { SMB, WEBDAV, TORRSERVER }

    /**
     * Which of them is being added, asked before the address rather than read off it.
     *
     * <p>It used to be inferred: an address beginning http was a WebDAV server and everything else a
     * share. That worked while the form of the address was the answer, and a torrent server ended
     * it — it is http exactly as WebDAV is, and telling them apart meant asking the server itself,
     * which is a guess that goes wrong precisely when the server is asleep and the place is being
     * added from memory. A wrong guess then saves a place that can never open and says nothing about
     * why.
     *
     * <p>A dialog rather than a panel: this asks one question and waits for it.
     */
    private void askForKind() {
        Dialogs.choice(this, getString(R.string.browse_place_kind),
                java.util.Arrays.<CharSequence>asList("SMB", "WebDAV", "TorrServer"),
                which -> askForPlace(Kind.values()[which]));
    }

    private void askForPlace(final Kind kind) {
        final Context dialogContext = Dialogs.dialogContext(this);
        final ViewGroup fields = Dialogs.dialogFields(dialogContext);
        // The example is the chosen protocol's own, because a field that shows every form at once
        // shows none of them.
        final String example;
        switch (kind) {
            case WEBDAV:
                example = "https://host:8080/dav";
                break;
            case TORRSERVER:
                // The port the server itself listens on unless it was told otherwise, read from the one
                // place that knows it. One address, not a pair: a second form beside it is read as
                // something that also has to be typed.
                example = "192.168.0.20:" + TorrFiles.DEFAULT_PORT;
                break;
            default:
                example = "192.168.0.10  •  192.168.0.10:445";
        }
        final EditText host = Dialogs.textField(fields, getString(R.string.browse_place_host), example);
        // A torrent server has no share to name: what sits under its address is a prefix, and that is
        // typed as part of the address or not at all.
        final EditText share = kind == Kind.TORRSERVER ? null
                : Dialogs.textField(fields, getString(R.string.browse_place_share),
                        getString(R.string.browse_place_share_optional));
        final EditText user = Dialogs.textField(fields, getString(R.string.browse_place_user));
        final EditText password = Dialogs.textField(fields, getString(R.string.browse_place_password));
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText name = Dialogs.textField(fields, getString(R.string.browse_place_name));
        Dialogs.fields(this, getString(R.string.browse_add_network), fields,
                getString(android.R.string.ok), () -> savePlace(kind,
                        host.getText().toString().trim(),
                        share == null ? "" : share.getText().toString().trim(),
                        user.getText().toString().trim(), password.getText().toString(),
                        name.getText().toString().trim()));
    }

    private void savePlace(final Kind kind, final String address, final String share,
                           final String user, final String password, final String name) {
        if (address.isEmpty()) {
            Notice.show(this, R.string.browse_place_needs_address, true, R.drawable.ic_dns_24dp);
            return;
        }
        String rest = address;
        String path = share;
        // A protocol pasted in front of the address no longer says which of them this is - that was
        // chosen - but it still says the one thing the address really carries: whether it is the
        // secure spelling.
        boolean secure = false;
        final int marker = address.indexOf("://");
        if (marker > 0) {
            final String named = address.substring(0, marker).toLowerCase(java.util.Locale.US);
            rest = address.substring(marker + 3);
            secure = "https".equals(named) || DavFiles.SCHEME_SECURE.equals(named)
                    || TorrFiles.SCHEME_SECURE.equals(named);
        }
        final int slash = rest.indexOf('/');
        if (slash >= 0) {
            // Whatever follows the host is where the files begin; a folder typed into the second
            // field as well is taken as sitting under it. For every address and not only a spelt-out
            // one: "192.168.0.20:9118/ts" is how a server behind a prefix gets written down, and
            // leaving the path on the end of the host made the port unparsable and the host a string
            // that resolves to nothing.
            path = join(rest.substring(slash + 1), share);
            rest = rest.substring(0, slash);
        }
        // An address may carry its own port, and a name of its own is a convenience rather than a
        // requirement: without one the row says host/share, which is what the person typed anyway.
        String hostOnly = rest;
        int port = 0;
        final int colon = rest.lastIndexOf(':');
        if (colon > 0) {
            try {
                port = Integer.parseInt(rest.substring(colon + 1));
                hostOnly = rest.substring(0, colon);
            } catch (final NumberFormatException ignored) {
                // Not a port. Part of the host, then.
            }
        }
        final String scheme;
        switch (kind) {
            case WEBDAV:
                scheme = secure ? DavFiles.SCHEME_SECURE : DavFiles.SCHEME;
                break;
            case TORRSERVER:
                scheme = secure ? TorrFiles.SCHEME_SECURE : TorrFiles.SCHEME;
                break;
            default:
                scheme = SmbSessions.SCHEME;
        }
        // A torrent server typed without a port is on the one it ships with. Not for the secure
        // spelling: that is a reverse proxy, and a proxy answers on 443.
        if (kind == Kind.TORRSERVER && port == 0 && !secure) {
            port = TorrFiles.DEFAULT_PORT;
        }
        final String label = !name.isEmpty() ? name
                : path.isEmpty() ? hostOnly : hostOnly + "/" + path;
        savePlace(new NetworkPlaces.Place(scheme, label, hostOnly, port, path, user), password);
    }

    private void savePlace(final NetworkPlaces.Place place, final String password) {
        NetworkPlaces.add(this, place, password);
        trail.clear();
        show();
    }

    /**
     * A network folder that came back empty because it would not open. A refused password is the one
     * failure the person can do something about from here, so it asks for one rather than reporting
     * a folder with nothing in it — which is what the same empty list would otherwise look like.
     */
    private void blamePlace(final Throwable why) {
        final Item folder = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        final NetworkPlaces.Place place =
                folder == null ? null : NetworkPlaces.forUri(this, folder.file.getUri());
        if (place != null && NetworkFiles.needsPassword(why)) {
            // A place saved by the picker has no user yet, so nothing of its was refused - it is
            // being asked for the first time. Saying "refused" there would be a lie about something
            // the person never did.
            final int message = place.user.isEmpty()
                    ? R.string.browse_network_needs_user : R.string.browse_place_password_refused;
            showBlank(message, false);
            askForCredentials(place, message);
            return;
        }
        if (why instanceof TorrFiles.StillLoading) {
            // Not unreachable: the server answered, and what it said was that it has no file list
            // for this torrent yet. Saying "cannot reach" would send somebody after the network.
            showBlank(R.string.browse_torrent_loading, false);
            return;
        }
        showBlank(R.string.browse_place_unreachable, false);
    }

    /** Asks for the user and password of a saved place, and re-lists with them. */
    private void askForCredentials(final NetworkPlaces.Place place, final int message) {
        final Context dialogContext = Dialogs.dialogContext(this);
        final ViewGroup fields = Dialogs.dialogFields(dialogContext);
        // The reason we are asking, above the boxes rather than as a dialog's message: a sheet has no
        // message slot, and the line is the only thing saying whether this is a place that wants a
        // password or one whose password stopped working.
        final TextView why = new TextView(dialogContext);
        why.setText(message);
        why.setTextColor(MaterialColors.getColor(dialogContext, R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(this, R.color.ink_secondary)));
        why.setPadding(0, 0, 0, Utils.dpToPx(8));
        fields.addView(why, 0);
        final EditText user = Dialogs.textField(fields, getString(R.string.browse_place_user));
        user.setText(place.user);
        final EditText password = Dialogs.textField(fields, getString(R.string.browse_place_password));
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Dialogs.fields(this, place.name, fields, getString(android.R.string.ok), () -> {
            // place.scheme, not the default: answering the prompt must not turn a WebDAV
            // server into a share, which the five-argument constructor would.
            NetworkPlaces.add(this, new NetworkPlaces.Place(place.scheme, place.name,
                            place.host, place.port, place.share,
                            user.getText().toString().trim()),
                    password.getText().toString());
            show();
        });
    }

    /**
     * What can be done to a row. Putting it aside is offered on every one of them; renaming a saved
     * network folder and taking it back out are offered on the two rows that are one.
     *
     * <p>A list rather than a screen: the row itself is the only place any of this belongs. On a
     * television a long press of the centre button is what opens it, which is the gesture saved
     * network places have ridden on since they existed.
     */
    private void rowMenu(final Item item) {
        final NetworkPlaces.Place place = placeOf(item);
        // A picker is somebody else's screen while they wait for a uri, so it offers nothing that is
        // not about answering them - and it has no Favorites destination to put anything in.
        final boolean canFavorite = !picker;
        final List<CharSequence> labels = new ArrayList<>();
        // The act beside its label rather than an index arithmetic behind both: what is offered
        // depends on the row, and a fixed order that has to be counted back to is one entry away
        // from renaming a place when it meant to hide a folder.
        final List<Runnable> acts = new ArrayList<>();
        // One glyph per line, in the same order: a panel where some rows carry one and some do not
        // is a panel with holes in it.
        final List<Integer> icons = new ArrayList<>();
        if (canFavorite) {
            final boolean saved = Favorites.has(this, item.file.getUri());
            labels.add(getString(saved
                    ? R.string.browse_favorite_remove : R.string.browse_favorite_add));
            // The star the toolbar shows for this row: filled while it is put aside.
            icons.add(saved ? R.drawable.ic_star_24dp : R.drawable.ic_star_outline_24dp);
            acts.add(() -> toggleFavorite(item));
        }
        if (place != null && TorrFiles.speaks(item.file.getUri())) {
            // On the server's own row, because that is the one row that stands for the server rather
            // than for something already on it.
            labels.add(getString(R.string.browse_torrent_add));
            icons.add(R.drawable.ic_add_24dp);
            acts.add(() -> askForMagnet(item.file.getUri()));
        }
        final String hash = TorrFiles.speaks(item.file.getUri())
                ? TorrFiles.hashOf(item.file.getUri()) : null;
        if (hash != null && item.folder) {
            labels.add(getString(R.string.browse_torrent_remove));
            icons.add(R.drawable.ic_delete_24dp);
            acts.add(() -> removeTorrent(item, hash));
        }
        if (place != null) {
            labels.add(getString(R.string.browse_place_rename));
            icons.add(R.drawable.ic_edit_24dp);
            acts.add(() -> renamePlace(place));
            labels.add(getString(R.string.browse_place_forget_confirm));
            // Not a bin: forgetting a server takes the row away and leaves everything on it alone.
            icons.add(R.drawable.ic_link_off_24dp);
            acts.add(() -> forgetPlace(place));
        }
        if (labels.isEmpty()) {
            return;
        }
        // The app's own panel, not an alert with a list in it: a surface you browse is a panel at the
        // edge (R6), and it is the same one the player raises for its tracks - so a list of actions
        // reads the same wherever it is met, and docks the way the window says rather than floating.
        ActionPanel.show(this, item.name, labels, icons, which -> acts.get(which).run());
    }

    /**
     * The glyph a row stands on when there is no picture for it. A file is a film and a folder is a
     * folder, except at the top of a network tree, where the row is a whole service and what it
     * speaks is the one thing its name does not say: four saved servers otherwise arrive as four
     * identical folders. Material's own glyphs, and only for the top - inside a share every folder
     * is an ordinary folder again, and a protocol stamped on each of them would say nothing.
     */
    private int glyphFor(final Item item) {
        if (!item.folder) {
            return R.drawable.ic_movie_24dp;
        }
        final Uri uri = item.file.getUri();
        if (!item.volume || !NetworkFiles.isNetwork(uri)) {
            return R.drawable.ic_folder_open_24dp;
        }
        final String scheme = uri.getScheme();
        if (SmbSessions.SCHEME.equals(scheme)) {
            return R.drawable.ic_storage_24dp;
        }
        if (DavFiles.speaks(uri)) {
            return R.drawable.ic_cloud_24dp;
        }
        if (DlnaFiles.speaks(uri)) {
            return R.drawable.ic_cast_24dp;
        }
        if (TorrFiles.speaks(uri)) {
            return R.drawable.ic_torrent_24dp;
        }
        return R.drawable.ic_folder_open_24dp;
    }

    @Override
    public void chrome(final boolean visible) {
        if (nav != null) {
            nav.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    /** Drops the media index and everything rolled up out of it, so the next listing rebuilds it. */
    private void forgetIndex() {
        videoFolders = null;
        videoPaths.clear();
        folderArt.clear();
    }

    /**
     * Puts a row aside, or takes it back out. The list it lands in is the one on screen often enough
     * - a removal from inside Favorites has to leave the screen it was removed from - so the listing
     * is asked for again wherever it could have changed.
     */
    private void toggleFavorite(final Item item) {
        final Uri uri = item.file.getUri();
        if (Favorites.has(this, uri)) {
            Favorites.remove(this, uri);
        } else {
            // Taken now, while the row that is being put aside still knows them. A media server
            // states its picture, its size and its running time in the listing and not in the
            // address, so this is the only moment they can be had without asking the server again.
            final Long known = lengths.get(uri.toString());
            Favorites.add(this, item.name, uri, item.folder,
                    NetworkFiles.artwork(item.file),
                    item.folder ? 0 : item.file.length(), known == null ? 0 : known);
        }
        refreshMenu();
        if (dest == Dest.FAVORITES && trail.isEmpty()) {
            show();
        } else if (list.getAdapter() != null) {
            // The rows are the same rows; what changed is the mark one of them carries, and the mark
            // is read at bind time. Without this it appeared the next time the listing was rebuilt,
            // which from the outside is a star that ignores the press that earned it.
            list.getAdapter().notifyDataSetChanged();
        }
    }

    /**
     * The torrent server whose own list is on screen, or null anywhere else — inside one of its
     * torrents included, where "add" would have to guess which server the row belongs to.
     */
    @Nullable
    private Uri torrServerHere() {
        if (trail.isEmpty()) {
            return null;
        }
        final Uri uri = trail.get(trail.size() - 1).file.getUri();
        return TorrFiles.speaks(uri) && TorrFiles.hashOf(uri) == null ? uri : null;
    }

    /** One thing asked of a torrent server, so the thread and the refresh are written once. */
    private interface Ask {
        void send() throws Exception;
    }

    /**
     * Hands a torrent server a magnet link. One field, because that is the whole of it: the server
     * works out the name, the files and the poster for itself, and the row appears on the refresh.
     */
    private void askForMagnet(final Uri place) {
        final Context dialogContext = Dialogs.dialogContext(this);
        final ViewGroup fields = Dialogs.dialogFields(dialogContext);
        final EditText link = Dialogs.textField(fields, getString(R.string.browse_torrent_link),
                "magnet:?xt=urn:btih:…");
        Dialogs.fields(this, getString(R.string.browse_torrent_add), fields,
                getString(android.R.string.ok), () -> {
                    final String magnet = link.getText().toString().trim();
                    if (!magnet.isEmpty()) {
                        onServer(() -> TorrFiles.add(this, place, magnet));
                    }
                });
    }

    /** Takes a torrent off the server. Confirmed, because nothing here puts it back. */
    private void removeTorrent(final Item item, final String hash) {
        // The question names the torrent and the server; the button only has to say what pressing it
        // does. "Delete from server" on it wrapped to two lines and repeated the sentence above it.
        Dialogs.confirm(this, getString(R.string.browse_torrent_remove_confirm, item.name), null,
                getString(R.string.browse_torrent_delete),
                () -> onServer(() -> {
                    TorrFiles.remove(this, item.file.getUri(), hash);
                    // Only once the server has taken it: remove throws on a refusal, and a favorite
                    // dropped for a torrent that is still there would be the one thing here nothing
                    // puts back.
                    Favorites.removeUnder(this, item.file.getUri(), hash);
                }),
                getString(android.R.string.cancel), null);
    }

    /**
     * One request to a torrent server, off the main thread, and the listing again afterwards. A
     * failure is said out loud rather than left as a list that did not change.
     */
    private void onServer(final Ask request) {
        new Thread(() -> {
            boolean failed = false;
            try {
                request.send();
            } catch (final Exception e) {
                // Whether it carried a message or not: an exception is the failure, and half of them
                // arrive with getMessage() null.
                e.printStackTrace();
                failed = true;
            }
            final boolean refused = failed;
            runOnUiThread(() -> {
                if (refused) {
                    Notice.show(this, R.string.browse_place_unreachable, true, R.drawable.ic_dns_24dp);
                }
                show();
            });
        }, "torr-command").start();
    }

    private void renamePlace(final NetworkPlaces.Place place) {
        final Context dialogContext = Dialogs.dialogContext(this);
        final ViewGroup fields = Dialogs.dialogFields(dialogContext);
        final EditText name = Dialogs.textField(fields, getString(R.string.browse_place_name));
        name.setText(place.name);
        name.setSelection(name.getText().length());
        Dialogs.fields(this, getString(R.string.browse_place_rename), fields,
                getString(android.R.string.ok), () -> {
                    final String renamed = name.getText().toString().trim();
                    if (!renamed.isEmpty()) {
                        NetworkPlaces.rename(this, place, renamed);
                        trail.clear();
                        show();
                    }
                });
    }

    private static String join(final String first, final String second) {
        final String head = first.replaceAll("^/+|/+$", "");
        final String tail = second.replaceAll("^/+|/+$", "");
        return head.isEmpty() ? tail : tail.isEmpty() ? head : head + "/" + tail;
    }

    private void forgetPlace(final NetworkPlaces.Place place) {
        Dialogs.confirm(this, getString(R.string.browse_place_forget, place.name), null,
                getString(R.string.browse_place_forget_confirm), () -> {
                    NetworkPlaces.remove(this, place);
                    // And what was put aside out of it. A favorite is an address, and this one's
                    // address has just stopped leading anywhere: the server is gone from the list,
                    // its password with it, and the row would sit in Favorites failing to open.
                    Favorites.removeUnder(this, place);
                    trail.clear();
                    show();
                },
                getString(android.R.string.cancel), null);
    }

    /** The saved place a row stands for, or null when the row is not one. */
    @Nullable
    private NetworkPlaces.Place placeOf(final Item item) {
        if (!(item.file instanceof NetworkDocumentFile)) {
            return null;
        }
        for (final NetworkPlaces.Place place : NetworkPlaces.all(this)) {
            if (place.uri().equals(item.file.getUri())) {
                return place;
            }
        }
        return null;
    }

    /** The name the system has for a volume, or its directory name where there is none to ask. */
    private String volumeLabel(final File root) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                final StorageManager storage = (StorageManager) getSystemService(STORAGE_SERVICE);
                final StorageVolume volume = storage == null ? null : storage.getStorageVolume(root);
                final String described = volume == null ? null : volume.getDescription(this);
                if (described != null && !described.isEmpty()) {
                    return described;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        final String name = root.getName();
        return name == null || name.isEmpty() ? root.getAbsolutePath() : name;
    }

    /** One step back out of the trail: to the folder above, then to the volume list, then out. */
    private void up() {
        if (volumeIsRoot && trail.size() == 1) {
            trail.clear();
        }
        if (trail.isEmpty()) {
            // At the top of a destination the arrow does what Back does: on a television that is out to
            // the start page, and everywhere else there is nothing above this screen.
            if (tv && !picker && home.getVisibility() != View.VISIBLE) {
                showHome();
            } else {
                finish();
            }
            return;
        }
        // The folder being left is where the remote came from, so that is where it goes back to.
        show(trail.remove(trail.size() - 1));
    }

    /**
     * Puts the destinations where this window can reach them: the bar under 600dp, the rail from
     * 600dp, and on a television neither - the start page's cards do it there, because a remote has
     * no thumb to reach a bar with. A picker gets none of the three.
     */
    private void wireNavigation() {
        home.findViewById(R.id.home_card_favorites).setOnClickListener(v -> choose(Dest.FAVORITES));
        home.findViewById(R.id.home_card_files).setOnClickListener(v -> choose(Dest.FILES));
        home.findViewById(R.id.home_card_network).setOnClickListener(v -> choose(Dest.NETWORK));
        home.findViewById(R.id.home_card_iptv).setOnClickListener(v -> choose(Dest.IPTV));
        home.findViewById(R.id.home_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        home.findViewById(R.id.home_link).setOnClickListener(v -> OpenLink.ask(this, this::play));
        home.findViewById(R.id.home_room).setOnClickListener(v -> joinRoom());
        // A circle, said in code because saying it in the style does not survive: Material 1.14 gives an
        // icon button a rounded square of its own that morphs on press, and an icon-only verb in this
        // app is a circle (R3). The relative size keeps it one whatever the button is sized at.
        final MaterialButton gear = home.findViewById(R.id.home_settings);
        // setShapeAppearance and not setShapeAppearanceModel: 1.14 gives a button a shape per state
        // and that list wins over a single model, so the model alone was ignored and the square stayed.
        gear.setShapeAppearance(gear.getShapeAppearanceModel().toBuilder()
                .setAllCornerSizes(new RelativeCornerSize(0.5f)).build());
        // The three standalone outlined actions take the focus ring every button in the app wears; their
        // ink is set in the layout, for the reason Utils.quietInk records.
        Utils.focusRing(home.findViewById(R.id.home_settings));
        Utils.focusRing(home.findViewById(R.id.home_link));
        Utils.focusRing(home.findViewById(R.id.home_room));
        // The card's own frame, and the contour on it when the remote arrives (R4, R5) - the same
        // drawable every row in this list carries, at the 12dp corner a card this tall takes, and at
        // the wider ring a card this size needs to show one.
        final int surface = MaterialColors.getColor(this, R.attr.colorSurfaceContainer, Color.DKGRAY);
        final int ring = getResources().getDimensionPixelSize(R.dimen.focus_ring_width_card);
        for (final int id : new int[]{R.id.home_card_favorites, R.id.home_card_files,
                R.id.home_card_network, R.id.home_card_iptv}) {
            home.findViewById(id).setBackground(
                    Dialogs.pickerRow(this, surface, false, Utils.dpToPx(16), ring));
        }

        if (tv || picker) {
            return;
        }
        attachNav(getResources().getConfiguration().screenWidthDp >= 600);
    }

    /**
     * Puts the destinations on the surface this width calls for, and takes them off the other one.
     * Called again on a rotation rather than through a rebuilt activity - see
     * {@link #onConfigurationChanged}.
     */
    private void attachNav(final boolean wide) {
        final NavigationBarView wanted = findViewById(wide ? R.id.browse_rail : R.id.browse_bar);
        if (wanted == nav) {
            return;
        }
        if (nav != null) {
            nav.setOnItemSelectedListener(null);
            nav.setVisibility(View.GONE);
        }
        nav = wanted;
        nav.setVisibility(View.VISIBLE);
        nav.setSelectedItemId(navId(dest));
        nav.setOnItemSelectedListener(item -> {
            // Only when it is a move. choose() marks the surface itself - the empty Favorites page
            // sends somebody to Files, and a bar still lit on Favorites after that would be lying -
            // and that mark comes back through here.
            final Dest chosen = destFor(item.getItemId());
            if (chosen != dest) {
                choose(chosen);
            }
            return true;
        });
    }

    /** Which destination an item of {@code browse_nav.xml} is. */
    private static Dest destFor(final int id) {
        if (id == R.id.dest_favorites) {
            return Dest.FAVORITES;
        }
        if (id == R.id.dest_network) {
            return Dest.NETWORK;
        }
        return id == R.id.dest_iptv ? Dest.IPTV : Dest.FILES;
    }

    /** Which item of {@code browse_nav.xml} a destination is. */
    private static int navId(final Dest of) {
        switch (of) {
            case FAVORITES:
                return R.id.dest_favorites;
            case NETWORK:
                return R.id.dest_network;
            case IPTV:
                return R.id.dest_iptv;
            default:
                return R.id.dest_files;
        }
    }

    /**
     * A rotation is not a new screen. The manifest keeps this activity through one now, because the
     * player is landscape and this is not: coming back from a film rebuilt the whole window - every
     * view inflated again, every still decoded again - and that is the flash on the way out of a
     * video. Nothing here depends on the orientation except which surface carries the destinations.
     */
    @Override
    public void onConfigurationChanged(@NonNull final Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (!tv && !picker) {
            attachNav(newConfig.screenWidthDp >= 600);
        }
    }

    /**
     * The television start page: the identity, the destinations as cards, and the two verbs under
     * them. It stands in place of the list rather than beside it, and the header goes with the list -
     * the page carries its own lockup, and a toolbar over it would be a second one.
     */
    private void showHome() {
        dropSkeleton();
        // A listing in flight belongs to the level being left. Back out of a folder on a share that is
        // still thinking about it and the rows land on a screen nobody is on any more - and this page
        // draws its ground as a beam across the corner rather than as a plate, so they came up through
        // it. Interrupted here, and refused again where it lands: the thread can already have posted.
        if (lister != null) {
            lister.interrupt();
        }
        if (home.getVisibility() != View.VISIBLE) {
            play(home);
        }
        home.setVisibility(View.VISIBLE);
        // The two views arrive() animates, stopped before they are hidden - or they go on being
        // drawn over this page. See stop().
        stop(header);
        stop((View) list.getParent());
        header.setVisibility(View.GONE);
        list.setVisibility(View.GONE);
        blank.setVisibility(View.GONE);
        crumbsScroll.setVisibility(View.GONE);
        // The leftmost card, which is where a remote expects to land - except while Favorites is
        // empty, because a page with nothing on it is not somewhere to put a viewer down.
        home.findViewById(Favorites.all(this).isEmpty()
                ? R.id.home_card_files : R.id.home_card_favorites).requestFocus();
    }

    /** Switching destination is not a step you can go back through: the trail starts again. */
    private void choose(final Dest chosen) {
        dest = chosen;
        // The bar or the rail follows, wherever the move came from - a card, a press on it, or the
        // button on an empty page. Set after dest, so the listener it wakes sees no move to make.
        if (nav != null) {
            nav.setSelectedItemId(navId(chosen));
        }
        trail.clear();
        query = null;
        // Stopped before it is hidden, or the start page goes on being drawn under this
        // destination for the rest of its own arrival. See stop().
        stop(home);
        home.setVisibility(View.GONE);
        header.setVisibility(View.VISIBLE);
        list.setVisibility(View.VISIBLE);
        invalidateOptionsMenu();
        show();
        arrive();
    }

    /**
     * The way a destination arrives: the same one this device opens an activity with.
     *
     * <p>Not a motion of ours. The transition an activity opens by is a resource like any other -
     * the theme's {@code windowAnimationStyle} names it and {@code activityOpenEnterAnimation} is the
     * half that plays on the window coming in - so it is asked for and played on the view that
     * changed. A television answered with a cross-fade and the owner's telephone with a slide from
     * the end edge, both measured off recordings of the settings screen opening; neither is written
     * down here, because whichever the device does is now what the browser does too.
     *
     * <p>A destination is not a new window, so what it plays on is what changed - the name of the
     * screen and the list under it. The bar or the rail the press landed on does not move, which is
     * what keeps it reading as one screen changing rather than two screens swapping.
     *
     * <p>Everything about it degrades quietly. A theme that names no such animation, a resource that
     * will not load, a device whose animator scale is zero - which is what "remove animations" means
     * - and the views are simply shown. Nothing here is newer than API 1 but the null checks.
     */
    private void arrive() {
        play((View) list.getParent());
        play(header);
    }

    /**
     * Stops whatever {@link #play} last started on a view, so that hiding it actually hides it.
     *
     * <p>A {@code ViewGroup} goes on drawing a child that is GONE for as long as that child still
     * holds a live {@code Animation} - which is what made the start page keep being painted under a
     * file list. It only shows when a card is pressed before the page it is on has finished
     * arriving, because that is the only moment the animation is still running when the page is
     * hidden, and a remote is quite fast enough to do it: measured on tv_720p as 300-500ms of two
     * screens drawn over each other, in both directions.
     *
     * <p>{@link #play} already clears the view it is about to animate. This is the other half - the
     * view being left, which nobody was animating and nobody was clearing.
     */
    private static void stop(final View view) {
        if (view != null) {
            view.clearAnimation();
        }
    }

    private void play(final View view) {
        if (view == null) {
            return;
        }
        view.clearAnimation();
        if (Utils.isReducedMotion(this)) {
            return;
        }
        final Animation animation = openAnimation();
        if (animation != null) {
            view.startAnimation(animation);
        }
    }

    /** The animation this device's theme gives a window that is opening, or null where it names none. */
    @Nullable
    private Animation openAnimation() {
        int style = 0;
        TypedArray attrs = obtainStyledAttributes(new int[]{ android.R.attr.windowAnimationStyle });
        try {
            style = attrs.getResourceId(0, 0);
        } finally {
            attrs.recycle();
        }
        if (style == 0) {
            return null;
        }
        int animation = 0;
        attrs = obtainStyledAttributes(style,
                new int[]{ android.R.attr.activityOpenEnterAnimation });
        try {
            animation = attrs.getResourceId(0, 0);
        } finally {
            attrs.recycle();
        }
        if (animation == 0) {
            return null;
        }
        try {
            return AnimationUtils.loadAnimation(this, animation);
        } catch (final Exception e) {
            // A vendor's own transition need not be an animation this can load - some are built in the
            // window manager and have no resource behind them at all. Then there is simply no motion.
            e.printStackTrace();
            return null;
        }
    }

    private int destTitle() {
        switch (dest) {
            case FAVORITES:
                return R.string.nav_favorites;
            case NETWORK:
                return R.string.nav_network;
            case IPTV:
                return R.string.nav_iptv;
            default:
                return R.string.browse_title;
        }
    }

    /**
     * Hands an address to the player the way an app outside would - one VIEW intent - so it arrives
     * on the same path a shared link takes, and Back comes straight back here.
     */
    private void play(final Uri uri) {
        startActivity(new Intent(Intent.ACTION_VIEW, uri).setClass(this, PlayerActivity.class));
    }

    /**
     * Joining needs no media of its own: the room says what it is playing. So this hands the player
     * the intent and the player opens the join menu once it is up - every piece of the watch party
     * lives over there, and none of it is worth a second copy here.
     */
    private void joinRoom() {
        final List<Dialogs.MenuItem> items = new ArrayList<>();
        items.add(new Dialogs.MenuItem(R.drawable.ic_search_24dp,
                getString(R.string.together_find), null, false, this::findRooms));
        items.add(new Dialogs.MenuItem(R.drawable.ic_link_24dp,
                getString(R.string.together_enter_code), null, false, this::askRoomCode));
        Dialogs.menu(this, UiMetrics.of(this, Utils.isTvBox(this)), null,
                getString(R.string.together_join), items);
    }

    /**
     * Ask the shared lobby who is watching what, and show the answers here.
     *
     * <p>The asking used to happen in the player: this screen handed it an intent and let it open its
     * own menu, on the grounds that every piece of the watch party lives over there. What that missed
     * is where the viewer is standing. Pressing "join" on this page took them to a player with no
     * media, drew a black screen, and put the menu on top of it — so the answer to "which room" was
     * asked somewhere they had not chosen to go. The party still lives in the player; only the
     * question moved, and the player is opened once there is an answer to open it with.
     */
    private void findRooms() {
        Relay.setBase(Prefs.getTogetherRelay(this));
        Notice.show(this, R.string.together_searching, false, R.drawable.ic_together_24dp);
        TogetherManager.discover(rooms -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            if (rooms.isEmpty()) {
                Notice.show(this, R.string.together_none_found, true, R.drawable.ic_together_24dp);
                return;
            }
            final List<Dialogs.MenuItem> items = new ArrayList<>();
            for (final org.json.JSONObject ad : rooms) {
                final String id = ad.optString("id");
                final boolean locked = ad.optInt("pwd") == 1;
                final String title = ad.optString("title", "").isEmpty()
                        ? ad.optString("name", id) : ad.optString("title");
                final String poster = ad.optString("poster", "");
                items.add(new Dialogs.MenuItem(
                        locked ? R.drawable.ic_lock_24dp
                                : poster.isEmpty() ? R.drawable.ic_together_24dp : 0,
                        poster, title,
                        getString(R.string.together_room_summary,
                                ad.optString("owner", ""), ad.optInt("members")),
                        false,
                        () -> {
                            if (locked) {
                                askRoomPassword(id);
                            } else {
                                openRoom(id, "");
                            }
                        }));
            }
            Dialogs.menu(this, UiMetrics.of(this, Utils.isTvBox(this)), null,
                    getString(R.string.together_find), items);
        });
    }

    private void askRoomCode() {
        final Context dialogContext = Dialogs.dialogContext(this);
        final ViewGroup fields = Dialogs.dialogFields(dialogContext);
        // Text, not digits: a room made in Lampa has a letter code.
        final EditText code = Dialogs.textField(fields, getString(R.string.together_code), "ABC234");
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        final EditText password = Dialogs.textField(fields, getString(R.string.together_password));
        Dialogs.fields(this, getString(R.string.together_join), fields,
                getString(android.R.string.ok), () -> {
                    final String entered =
                            code.getText().toString().trim().toUpperCase(java.util.Locale.US);
                    if (Room.isCode(entered)) {
                        openRoom(entered, password.getText().toString());
                    } else {
                        Notice.show(this, R.string.together_code_invalid, true, R.drawable.ic_together_24dp);
                    }
                });
    }

    private void askRoomPassword(final String code) {
        final Context dialogContext = Dialogs.dialogContext(this);
        final ViewGroup fields = Dialogs.dialogFields(dialogContext);
        final EditText input = Dialogs.textField(fields, getString(R.string.together_password));
        input.setText(Prefs.getTogetherPassword(this));
        input.setSelection(input.getText().length());
        Dialogs.fields(this, code, fields, getString(android.R.string.ok),
                () -> openRoom(code, input.getText().toString()));
    }

    /** The room is chosen; the player is what joins it and plays what it says. */
    private void openRoom(final String code, final String password) {
        startActivity(new Intent(this, PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_JOIN_CODE, code)
                .putExtra(PlayerActivity.EXTRA_JOIN_PASSWORD, password));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // A television remote's EXIT key commonly arrives as ESCAPE, and the framework only turns it
        // into Back when nothing consumed it — which is a fallback the player had to stop relying on
        // once its own handling started consuming keys (see PlayerActivity). Routing it here keeps the
        // two screens answering the same key the same way, and consuming it is what stops a second Back
        // being synthesised behind this one.
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && event.getRepeatCount() == 0) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * One step back out of the trail, then out of the screen.
     *
     * <p>Registered on the dispatcher rather than left to {@code onBackPressed}: the manifest turns on
     * {@code enableOnBackInvokedCallback}, so from Android 13 Back arrives through
     * {@code OnBackInvokedDispatcher} and an activity with no callback of its own is simply finished
     * without being asked — which is how Back out of a folder was leaving the app rather than stepping
     * up one level. AppCompat's dispatcher is the same mechanism on every version the app runs on.
     */
    private void registerBack() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!trail.isEmpty() && !(volumeIsRoot && trail.size() == 1)) {
                    up();
                    return;
                }
                // On a television the start page is a level of its own, so Back out of a destination
                // lands there rather than out of the app. Everywhere else the destination root is the
                // top: the bar and the rail are not a level you can go back through.
                if (tv && !picker && home.getVisibility() != View.VISIBLE) {
                    showHome();
                    return;
                }
                if (tv) {
                    final long now = SystemClock.elapsedRealtime();
                    if (now - backPressedAt > Utils.BACK_CONFIRM_WINDOW_MS) {
                        backPressedAt = now;
                        Notice.show(BrowserActivity.this, R.string.press_back_again, false,
                                R.drawable.ic_arrow_back_24dp);
                        return;
                    }
                }
                // Leaving, so finish outright rather than disabling this callback and asking the
                // dispatcher again: a callback switched off stays off, and an instance that survives
                // the trip out — anything that brings this activity back without a fresh onCreate —
                // would come back with Back dead and only the arrow and the crumbs still working.
                finish();
            }
        });
    }

    /**
     * Back from a television remote, below Android 13.
     *
     * <p>From Android 13 Back is delivered straight to {@code OnBackInvokedDispatcher} and never
     * reaches a view, which is why {@link #registerBack} is the whole story there. Below it, Back is
     * an ordinary key: {@code Activity.dispatchKeyEvent} offers it to the view hierarchy first and
     * only then to {@code KeyEvent.dispatch}, and {@code Activity.onKeyUp} calls {@code
     * onBackPressed()} <em>only</em> when the UP event says {@code isTracking()} — which is set by
     * {@code Activity.onKeyDown} on the way past, and therefore only when nothing swallowed the DOWN.
     * One view that takes the DOWN and does nothing with it is enough to leave Back dead for the rest
     * of the screen's life, and a television is where that happens: the same trap cost the player its
     * own Back once (see the note on {@code dispatchKeyEvent} in {@code PlayerActivity}).
     *
     * <p>So ask the dispatcher here instead of depending on tracking. Above Android 13 this never
     * runs, and a view that means to keep Back — the search field closing itself — still consumes the
     * UP before it can get here.
     */
    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && !event.isCanceled()) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Puts on screen whatever the current state calls for: storage that cannot be read yet, the
     * volume list, or a folder. Listing runs off the main thread — a directory of a few thousand
     * files takes long enough to drop frames, and longer than that on a slow television.
     */
    /**
     * Asks for the bar again, and only when what it draws has actually changed.
     *
     * <p>The bar belongs to the level rather than to the destination - the star acts on the folder
     * being stood in, and the search goes when Favorites is back at its own root - so it has to be
     * rebuilt as the level moves. It must not be rebuilt on every listing, though, and that is not a
     * matter of cost: **rebuilding the menu collapses an expanded action view, and the search is
     * one**. Expanding it empties its field, an empty field is a query change, a query change
     * re-lists - so a search asked for on every listing shut itself the instant it opened. The
     * button did nothing at all, in every destination, and that is how it was found.
     *
     * <p>What the bar draws is the destination, the folder being stood in, and whether that folder
     * is put aside. Typing in the search changes none of the three.
     */
    private void refreshMenu() {
        final Item here = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        final String signature = dest + "\u0000" + (here == null ? "" : here.file.getUri())
                + "\u0000" + (here != null && Favorites.has(this, here.file.getUri()));
        if (!signature.equals(menuKey)) {
            menuKey = signature;
            invalidateOptionsMenu();
        }
    }

    private void show() {
        show(null);
    }

    /**
     * Lists the folder the trail ends at.
     *
     * @param landing the row the remote is to land on once the listing is drawn - the folder just
     *                stepped out of - or null for the first row. It is not remembered anywhere: it
     *                travels with the listing it was asked with and dies with it, so a viewer who
     *                walks on while a server is still answering cannot be thrown at a stale row.
     */
    private void show(@Nullable final Item landing) {
        // The index behind the listing is held for the life of the screen by design - so a folder
        // hidden in Settings would go on counting towards its parents and go on lending them a
        // thumbnail, and an index built before access was granted would go on hiding nothing. Dropped
        // only when one of the three things it was built under actually moved, which an ordinary
        // return has not. Here rather than in onStart because the runtime permission dialog of API 29
        // and below pauses this screen without stopping it: onStart never runs, and the result
        // handler's own show() is the whole of the return.
        if (!browsingKey().equals(browsing)) {
            browsing = browsingKey();
            forgetIndex();
        }
        // The start page owns the screen while it is up, and onStart calls this on every arrival -
        // including the one back from Settings, which must not drop the viewer into a file list.
        if (home.getVisibility() == View.VISIBLE) {
            return;
        }
        if (dest == Dest.IPTV) {
            // Nothing is being listed here, so nothing in flight may land: a folder still being read
            // when this destination was chosen would otherwise paint its rows over this page.
            sought = null;
            setTitle(R.string.nav_iptv);
            showUpArrow();
            refreshMenu();
            crumbsScroll.setVisibility(View.GONE);
            showBlank(R.string.iptv_title, R.string.iptv_body);
            return;
        }
        // Only when there is nothing else to show. A network folder needs no access to this device's
        // storage, so demanding it first would make the storage grant a toll gate on a feature that
        // does not pass through storage at all - and the toolbar's own add button is reachable from
        // the blank screen, so the way in survives. In a picker the two kinds still share one root,
        // so there the old question stands: is there anything at all to list without the grant.
        final boolean needsStorage = dest == Dest.FILES
                && (!picker || NetworkPlaces.all(this).isEmpty());
        if (needsStorage && !Utils.canListStorage(this)) {
            sought = null;
            trail.clear();
            // The whole toolbar, not only the menu. This path returns before the code below that
            // draws it, and what it left standing was the last destination's: arriving at Files
            // without the grant kept the network folder's name, its up arrow and its crumb trail
            // over a page about storage permission.
            showChrome();
            if (Utils.permissionOpensStorage(this)) {
                if (!asked) {
                    asked = true;
                    requestPermissions(new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE },
                            REQUEST_STORAGE);
                    return;
                }
                // Refused, and on this version there is nothing else to offer.
                showBlank(R.string.browse_needs_access, false);
                return;
            }
            showBlank(R.string.browse_needs_access, true);
            return;
        }
        openSoleVolume();
        final Item folder = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        showChrome();
        Prefs.setBrowseTrail(this, dest.name(), trailAsText());
        // What is on screen belongs to the level being left, and listing a folder takes as long as the
        // folder is big. Left there, those rows stay clickable: tapping the same row again descended
        // into the same folder again, and a big folder could be walked into itself forever.
        final String wanted = trailAsText() + "\u0000" + dest + "\u0000" + query;
        if (!wanted.equals(bound) && !rows.isEmpty()) {
            rows.clear();
            if (list.getAdapter() != null) {
                list.getAdapter().notifyDataSetChanged();
            }
        }
        final String searching = query;
        // What this screen is now waiting for. The answer on its way back is checked against it.
        sought = wanted;
        // The page the last destination left behind goes now rather than when the skeleton arrives a
        // fifth of a second later. Leaving IPTV, that fifth of a second was its "in development" text
        // sitting over the folder that was already being read - the screen answering for a page the
        // viewer had just left.
        blank.setVisibility(View.GONE);
        if (lister != null) {
            lister.interrupt();
        }
        // Asked for on every listing and drawn on almost none: it carries a fifth of a second of patience,
        // and a listing that lands inside that cancels the appearance rather than undoing it.
        //
        // Only where the screen is empty. A skeleton stands in for content that is not there yet, and
        // the commonest listing of all - the same folder read again on the way back from the player -
        // has its content already on screen. Replacing it with grey for a moment would be the
        // screen going backwards.
        if (rows.isEmpty()) {
            waitForRows();
        }
        lister = new Thread(() -> {
            // The index first, for a local folder. It is a query over every video on the device and
            // onCreate has already started it; what this waits for is that one finishing, not a
            // second query. Listing without it is what put folders with nothing playable in them on
            // screen: indexedContents answers null while the map is null, the walk below takes over,
            // and the walk has nothing to hide a folder by. It showed up exactly where the race is
            // lost - a fresh process opening straight into Files, or one the system had reclaimed in
            // the background - and only on a library big enough for the query to be the slower of the
            // two. Measured at 8,000 videos: five cold starts, five wrong listings; at fifteen, none.
            //
            // Not for a search, which reads the index itself and states its own progress, and not for
            // a network folder, whose listing is a server away and has no local index to wait for.
            if (searching == null && folder != null
                    && "file".equals(folder.file.getUri().getScheme())) {
                videoFolders();
            }
            final List<Item> indexedRows = searching != null || folder == null ? null
                    : indexedContents(pathOf(folder.file));
            final boolean indexed = indexedRows != null;
            final List<Item> listed = indexedRows != null ? indexedRows
                    : searching != null ? matches(folder == null ? null : folder.file, searching)
                    : folder == null ? roots() : contentsOf(folder.file);
            // Read here rather than on the way in: it is a file, and the screen it belongs to is being
            // drawn. Every arrival reads it again, because the player has been writing to it.
            //
            // And after the listing, not before: listing a torrent folder is what fetches the
            // server's timecodes and writes them into this same file, so reading first showed a
            // progress one visit out of date - the whole point of asking the server being that it
            // knows where another player stopped.
            final Map<String, Long> read = Prefs.readPositions(this);
            // Kept for the search, which then asks the server nothing: this is the same folder it
            // would have had to list, already listed. Only a plain listing - a search's own answer
            // is not what the folder holds.
            if (searching == null && folder != null && folder.file instanceof NetworkDocumentFile) {
                openFolderKey = folder.file.getUri().toString();
                openFolderListing = listed;
            }
            // Read on the thread that listed, and only there: an empty network folder and one that
            // refused to open are the same empty list, and the difference is worth a prompt.
            final Throwable why = NetworkFiles.failure();
            if (!Thread.currentThread().isInterrupted()) {
                runOnUiThread(() -> {
                    // The screen may have moved on while this was reading - see showHome().
                    if (home.getVisibility() == View.VISIBLE) {
                        return;
                    }
                    // And it may have moved to another folder or another destination. The interrupt
                    // above is not enough on its own: it is read once, before this is posted, so a
                    // listing that got that far delivers whatever the viewer does next - and a read
                    // blocked on a server that will not answer never sees the interrupt at all. Left
                    // unchecked, the folder somebody walked away from replaced the one they were
                    // waiting for, seconds after they had stopped looking at it.
                    if (!wanted.equals(sought)) {
                        return;
                    }
                    dropSkeleton();
                    positions = read;
                    bound = wanted;
                    bind(listed, why, landing);
                    if (searching == null && folder != null && indexed) {
                        // The index answered; the disk gets its say behind the screen.
                        reconcile(folder.file, wanted);
                    }
                });
            }
        });
        lister.start();
    }

    /**
     * With one storage volume and nothing else to choose between, the Files destination opens inside
     * it rather than on a page holding a single row - which is what it was: one 96dp row in a column
     * 717dp tall, 82 % of it empty. Every library this was measured against does the same.
     *
     * <p>Done by pushing the volume onto the trail rather than by listing its contents at the root, so
     * the crumbs, the arrow and Back all keep working on the real shape of the tree; {@link #up} then
     * treats that one entry as the root, because with nothing beside it that is what it is.
     */
    private void openSoleVolume() {
        if (picker || dest != Dest.FILES || !Utils.canListStorage(this)) {
            volumeIsRoot = false;
            return;
        }
        // Asked on every pass and not only when the trail is empty: the trail comes back from the
        // preferences too, and from behind the player. Deciding only at the push left the flag false
        // on every return, and Back at the volume's own root then popped the trail, pushed it again
        // and went nowhere - a screen that looked stuck.
        final List<Item> vols = volumes();
        volumeIsRoot = vols.size() == 1;
        if (volumeIsRoot && trail.isEmpty()) {
            trail.add(vols.get(0));
        }
    }

    /**
     * The arrow appears only where there is a level above to take: inside a folder anywhere, and on a
     * television also at the top of a destination, where the level above is the start page. At the top
     * of a destination on a phone there is nothing above this screen, and an arrow that only closes the
     * app is an arrow that lies.
     *
     * <p>A picker keeps it in every state: there it means "answer nothing and go back", which is a
     * level above by any reading.
     */
    private void showUpArrow() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        final int floor = volumeIsRoot ? 1 : 0;
        actionBar.setDisplayHomeAsUpEnabled(picker || tv || trail.size() > floor);
    }

    /** Whether a fresh listing is the same files in the same order as the ones already bound. */
    private boolean sameRows(final List<Item> listed) {
        if (listed.size() != rows.size() || rows.isEmpty()) {
            return false;
        }
        for (int i = 0; i < listed.size(); i++) {
            if (!listed.get(i).file.getUri().equals(rows.get(i).file.getUri())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Rebinds only the rows whose played run has moved since they were bound - in practice the one
     * film that was just watched, on the way back from it.
     */
    private void rebindChangedRuns() {
        for (int i = 0; i < rows.size(); i++) {
            final String key = rows.get(i).file.getUri().toString();
            final Long now = positions.get(key);
            final Long was = shown.get(key);
            if (now == null ? was != null : !now.equals(was)) {
                if (now == null) {
                    shown.remove(key);
                } else {
                    shown.put(key, now);
                }
                list.getAdapter().notifyItemChanged(i);
            }
        }
    }

    /**
     * The trail as a row of steps, each one a way back to the level it names. Nothing is shown at the
     * volume list: there is no path above it, and a single crumb saying where you already are is a
     * label pretending to be a control.
     */
    /**
     * The toolbar and the crumb strip for wherever the trail points now.
     *
     * <p>One place, because {@link #show()} has more than one way out and each of them owes the
     * screen the same four calls — and the one that forgot them left the page it returned to wearing
     * the previous destination's title, arrow and crumbs.
     */
    private void showChrome() {
        final Item folder = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        setTitle(folder == null ? getString(destTitle()) : folder.name);
        showUpArrow();
        showCrumbs();
        refreshMenu();
    }

    private void showCrumbs() {
        crumbs.removeAllViews();
        if (trail.size() < 2) {
            crumbsScroll.setVisibility(View.GONE);
            return;
        }
        crumbsScroll.setVisibility(View.VISIBLE);
        final int surface = MaterialColors.getColor(this, R.attr.colorSurfaceContainerHighest,
                Color.DKGRAY);
        for (int i = 0; i < trail.size(); i++) {
            if (i > 0) {
                final TextView caret = new TextView(this);
                caret.setText("\u203A");
                caret.setTextColor(MaterialColors.getColor(this, R.attr.colorOnSurfaceVariant,
                        Color.GRAY));
                caret.setPadding(Utils.dpToPx(6), 0, Utils.dpToPx(6), 0);
                crumbs.addView(caret);
            }
            final int level = i;
            final boolean here = i == trail.size() - 1;
            final TextView crumb = new TextView(this);
            crumb.setText(trail.get(i).name);
            crumb.setSingleLine(true);
            crumb.setGravity(Gravity.CENTER);
            crumb.setIncludeFontPadding(false);
            crumb.setPadding(Utils.dpToPx(12), 0, Utils.dpToPx(12), 0);
            crumb.setTextColor(MaterialColors.getColor(this,
                    here ? R.attr.colorOnSurface : R.attr.colorOnSurfaceVariant, Color.WHITE));
            // A crumb is a control, so it carries its own frame (R4) and takes the focus contour on
            // it (R5). The one you are standing in is not a way anywhere, so it is a plate and not a
            // control - which is also why it is the only one that is not focusable.
            crumb.setBackground(Dialogs.pickerRow(this, surface, false, Utils.dpToPx(8)));
            if (!here) {
                crumb.setClickable(true);
                crumb.setFocusable(true);
                crumb.setOnClickListener(v -> {
                    // Over several levels the remote still lands on the way it came: the child of the
                    // crumb being clicked, taken before the trail is cut back to it.
                    final Item left = trail.get(level + 1);
                    while (trail.size() > level + 1) {
                        trail.remove(trail.size() - 1);
                    }
                    show(left);
                });
            }
            crumbs.addView(crumb, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, Utils.dpToPx(32)));
        }
        // The end of the path is where you are, so that is the end that has to be in view.
        crumbs.post(() -> crumbsScroll.scrollTo(crumbs.getWidth(), 0));
    }

    /**
     * Folders first, then the files worth offering, each group in reading order.
     *
     * <p>A folder with no video anywhere beneath it is left out entirely: there is nothing down there
     * to reach, and a screen of empty folders is a screen of dead ends. The judgement comes from
     * {@link #videoFolders()} - and only where that map has something to say about the folder being
     * listed. If MediaStore knows this folder has videos below it, then it knows which children they
     * are under, so a child it does not mention is genuinely empty. If it knows nothing about this
     * folder at all - a stick it never indexed, say - then its silence is not evidence and nothing is
     * hidden.
     *
     * <p>The cost of that: a folder holding only videos the media scanner has not picked up goes
     * missing rather than looking empty. The alternative is walking the whole subtree per row.
     */
    private List<Item> contentsOf(final DocumentFile folder) {
        return contentsOf(folder, false);
    }

    /**
     * @param walkOnly true from {@link #reconcile}, where the index has already answered and the walk
     *                 is only being asked what it missed
     */
    private List<Item> contentsOf(final DocumentFile folder, final boolean walkOnly) {
        if (!walkOnly) {
            final List<Item> indexed = indexedContents(pathOf(folder));
            if (indexed != null) {
                return indexed;
            }
        }
        // The index if it is here. A local listing now waits for it before it gets this far (see the
        // lister), so what reaches this line without one is a walk that was asked for outright
        // (walkOnly), a folder the index does not know, or a device whose provider would not answer -
        // and in each of those hiding is the wrong answer anyway. Nothing is ever removed from a list
        // already on screen; a row that vanishes under a thumb is worse than a folder that turns out
        // to be empty.
        final Map<String, long[]> withVideos = videoFolders;
        final boolean hideEmpty = withVideos != null && withVideos.containsKey(pathOf(folder));
        // Both read once for the folder rather than per entry: the loop below runs five thousand
        // times in a camera roll, and neither answer can change while it does.
        final boolean showHidden = Prefs.showHiddenFiles(this);
        final boolean ignoreNoMedia = Prefs.ignoreNoMedia(this);
        final List<Item> folders = new ArrayList<>();
        final List<Item> files = new ArrayList<>();
        // Whether a name is enough to tell a video from a photograph. On this device it is, and it is
        // what makes a camera roll cheap; off it, it is not - a DLNA title carries no extension at all
        // and the server states the type instead, which is why asking the file is right there and
        // ruinous here. Decided once for the folder, because its children all live in the same place.
        final boolean local = "file".equals(folder.getUri().getScheme());
        // Unsorted, and filtered before anything is sorted: a camera roll is five thousand
        // photographs and two hundred films, and ordering the five thousand to throw them away is
        // most of the wait. One stat per entry decides the kind; everything after it is the name.
        final DocumentFile[] children;
        try {
            children = folder.listFiles();
        } catch (final Exception e) {
            // A provider that has gone away throws from listFiles() rather than returning empty.
            e.printStackTrace();
            return folders;
        }
        for (final DocumentFile file : children) {
            final String name = file.getName();
            if (name == null || (!showHidden && name.startsWith("."))) {
                // What every file manager does, and the platform's own picker with it: a dot-prefixed
                // entry is housekeeping rather than content. Without this the first thing in a media
                // folder is .thumbnails, which is not something anyone came here to open. Filtered
                // here and not in listSorted, because the playlist has no viewer to mislead - it is
                // reading one folder's videos, not offering a place to browse.
                continue;
            }
            if (file.isDirectory()) {
                // MediaStore skips a .nomedia tree entirely, so a folder the index knows about cannot
                // be carrying one - which is what keeps this stat off every row and on only the
                // folders the index is silent about, exactly where the answer can differ.
                final boolean indexed = hideEmpty && withVideos.containsKey(pathOf(file));
                final boolean marked = local && !indexed && hasNoMedia(file);
                if (marked && !ignoreNoMedia) {
                    continue;
                }
                // A marked folder is exempt from the emptiness filter: the index is silent about it
                // because of the marker rather than because there is nothing below it, so its silence
                // is not the evidence this filter takes it for.
                if (hideEmpty && !indexed && !holdsAVideo(file) && !marked) {
                    continue;
                }
                folders.add(new Item(name, file, false, true));
                continue;
            }
            // On this device, by name: asking the provider for a type meant two more stats and a mime
            // lookup for every photograph in the folder. What that costs is a video whose name does
            // not say so - no extension, or a .dat - which the list no longer shows; every file
            // manager on the device makes the same trade. Off the device the type is the only answer
            // there is, and it is free, because it arrived with the listing.
            final boolean playable = subtitles
                    ? SubtitleUtils.hasSubtitleExtension(name.toLowerCase())
                    : local ? Utils.hasVideoExtension(name) : SubtitleUtils.isPlayableVideo(file);
            if (playable) {
                files.add(new Item(name, file, false, false));
                // Asked here, on the thread that is already reading this folder, rather than on the
                // main thread once per row: it is the same stat either way and this is the cheaper
                // place for it.
                final long bytes = file.length();
                if (bytes > 0) {
                    sizes.put(file.getUri().toString(), bytes);
                }
            }
        }
        Collections.sort(folders, (a, b) -> Utils.compareNatural(a.name, b.name));
        Collections.sort(files, (a, b) -> Utils.compareNatural(a.name, b.name));
        folders.addAll(files);
        return folders;
    }

    /**
     * A folder's contents out of the media index: one query for the files directly in it, and the
     * subfolders read off the index's own keys. Nothing is stated, nothing is opened, and the running
     * time and the size come back in the same cursor - so a folder of five thousand photographs costs
     * what its videos cost and no more. This is how the reference player is instant here.
     *
     * <p>Only offered where the index has something to say about this folder; the walk behind it
     * ({@link #reconcile}) is what keeps a file the scanner has not reached from being invisible.
     *
     * @return the rows, or null when the index knows nothing of this folder
     */
    private List<Item> indexedContents(final String path) {
        final Map<String, long[]> known = videoFolders;
        if (known == null || path == null || !known.containsKey(path)) {
            return null;
        }
        // Once for the listing, not once per row: the cursor below runs the length of a camera roll.
        final boolean showHidden = Prefs.showHiddenFiles(this);
        final List<Item> folders = new ArrayList<>();
        for (final String folder : known.keySet()) {
            if (path.equals(parentPath(folder))) {
                final File dir = new File(folder);
                folders.add(new Item(dir.getName(), DocumentFile.fromFile(dir), false, true));
            }
        }
        final List<Item> files = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                new String[]{ MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA,
                        MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DURATION,
                        MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.WIDTH,
                        MediaStore.MediaColumns.HEIGHT },
                MediaStore.MediaColumns.DATA + " LIKE ? AND " + MediaStore.MediaColumns.DATA
                        + " NOT LIKE ?",
                new String[]{ path + "/%", path + "/%/%" }, null)) {
            final int pathColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            final int idColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns._ID);
            final int sizeColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            final int lengthColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.DURATION);
            final int dateColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
            final int widthColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH);
            final int heightColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT);
            while (pathColumn >= 0 && cursor.moveToNext()) {
                final String file = cursor.getString(pathColumn);
                if (file == null) {
                    continue;
                }
                final File on = new File(file);
                if (!showHidden && on.getName().startsWith(".")) {
                    continue;
                }
                final DocumentFile document = DocumentFile.fromFile(on);
                files.add(new Item(on.getName(), document, false, false));
                // The two answers a row would otherwise open the file for, arriving with the row.
                final String key = document.getUri().toString();
                final long length = lengthColumn < 0 ? 0 : cursor.getLong(lengthColumn);
                if (length > 0) {
                    lengths.put(key, length);
                    durations.put(key, Utils.formatMilis(length));
                }
                if (idColumn >= 0) {
                    // The same file as the system knows it. A picture of it is then the one the system
                    // already made and keeps for every app on the telephone, instead of a frame this
                    // one decodes for itself - which was measured at a median of 1257ms a row on the
                    // owner's camera roll, against 21ms to read one back out of the cache.
                    stills.put(key, ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn)));
                }
                if (sizeColumn >= 0) {
                    sizes.put(key, cursor.getLong(sizeColumn));
                }
                if (dateColumn >= 0) {
                    // The index states seconds; everything else in this app counts milliseconds.
                    dates.put(key, cursor.getLong(dateColumn) * 1000L);
                }
                if (widthColumn >= 0 && heightColumn >= 0) {
                    resolution(key, cursor.getInt(widthColumn), cursor.getInt(heightColumn));
                }
            }
        } catch (final Exception e) {
            // A provider that will not answer is not a reason to show nothing: the walk still can.
            e.printStackTrace();
            return null;
        }
        if (subtitles) {
            // A subtitle is not in the video index, so that listing has to be the walk.
            return null;
        }
        Collections.sort(folders, (a, b) -> Utils.compareNatural(a.name, b.name));
        Collections.sort(files, (a, b) -> Utils.compareNatural(a.name, b.name));
        folders.addAll(files);
        return folders;
    }

    /**
     * The walk, behind the indexed listing rather than in front of it: anything on the disk that the
     * index did not mention is added to what is already on screen. A file the media scanner has not
     * reached yet - copied a moment ago, or sitting in MediaStore as {@code is_pending} - is the whole
     * reason this app walks the tree at all, and this is where that promise is kept.
     *
     * <p>Only ever adds. A row that vanishes from under a thumb is worse than a row that arrives late.
     */
    private void reconcile(final DocumentFile folder, final String wanted) {
        new Thread(() -> {
            final List<Item> walked = contentsOf(folder, true);
            final List<Item> missing = new ArrayList<>();
            final List<String> here = new ArrayList<>();
            runOnUiThread(() -> {
                for (final Item item : rows) {
                    here.add(item.file.getUri().toString());
                }
                for (final Item item : walked) {
                    if (!here.contains(item.file.getUri().toString())) {
                        missing.add(item);
                    }
                }
                if (missing.isEmpty() || !wanted.equals(bound)) {
                    return;
                }
                // Which row the remote is on, before the rows move under it. RecyclerView answers a
                // wholesale change by taking its children out of the view tree, and a child holding
                // the focus takes the focus out with it: the framework then hands it to the first
                // focusable view in the window, which here is the toolbar. So the row is found again
                // by address afterwards and landed on.
                final View held = tv ? list.getFocusedChild() : null;
                final Item keep = held == null ? null : (Item) held.getTag();
                rows.addAll(missing);
                Collections.sort(rows, (a, b) -> a.folder == b.folder
                        ? Utils.compareNatural(a.name, b.name) : a.folder ? -1 : 1);
                if (list.getAdapter() != null) {
                    list.getAdapter().notifyDataSetChanged();
                }
                if (keep != null) {
                    for (int i = 0; i < rows.size(); i++) {
                        if (rows.get(i).file.getUri().equals(keep.file.getUri())) {
                            land(i, true);
                            break;
                        }
                    }
                }
            });
        }).start();
    }

    /**
     * Whether somebody has written "not media" on this folder. One stat, and only ever for a folder
     * the media index says nothing about.
     *
     * <p>ponytail: the folder itself, not its ancestors. A tree is entered from its top and the top is
     * what carries the marker, so the only way past this is a trail or a favourite that was saved
     * before the marker was written - walking up to the volume on every row is not worth that.
     */
    private boolean hasNoMedia(final DocumentFile folder) {
        return new File(pathOf(folder), ".nomedia").exists();
    }

    /**
     * Whether this folder has a video directly in it, asked of the filesystem rather than of the
     * index. The safety valve on hiding a folder: MediaStore only knows what the media scanner has
     * reached, and a row it has not finalised is invisible to every app but the one that wrote it -
     * measured on the rig, where three perfectly playable files sat in MediaStore as
     * {@code is_pending=1} and their folder would have vanished. A folder that looks empty is a
     * nuisance; a folder that disappears takes its files with it.
     *
     * <p>One level deep, and only for the folders the index says nothing about, so it costs a listing
     * per candidate rather than a walk of the tree.
     */
    private boolean holdsAVideo(final DocumentFile folder) {
        for (final DocumentFile child : SubtitleUtils.listSorted(folder)) {
            if (!child.isDirectory() && SubtitleUtils.isPlayableVideo(child)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everything at or below this folder whose name carries {@code text} - files out of the index,
     * folders out of the same map that decides which folders are worth showing.
     *
     * <p>It was a filesystem walk to begin with, and that was the mistake: on a phone with a few
     * thousand files under the folder being searched, a walk per keystroke never finishes before the
     * next one replaces it, so the list simply never changed and the search looked dead. The
     * reference does not walk either - its search reads the same cached scan its listings do - and
     * asking the index we have already built is both instant and consistent with how folders are
     * filtered two methods up.
     *
     * <p>What that costs: a video the media scanner has not indexed cannot be found by name. It can
     * still be reached by browsing to it, which the folder filter takes care not to prevent.
     */
    private List<Item> matches(final DocumentFile folder, final String text) {
        final List<Item> found = new ArrayList<>();
        final String needle = text.toLowerCase();
        // Over the network the index has nothing to say - MediaStore knows this device and nothing
        // else - so the search is of the folder standing open, by name, and of nothing below it.
        //
        // Going down was built and measured against a real server and taken back out: a request per
        // folder is tens of seconds on a library of any size, and no budget or thread count makes an
        // answer that arrives after a minute feel like a search. The folder standing open is the one
        // listing that is already in hand, so this costs nothing at all - and the page says which
        // folder it looked in, so an empty answer is not mistaken for "the server has no such file".
        if (folder instanceof NetworkDocumentFile) {
            for (final Item row : rowsOf(folder)) {
                if (row.name.toLowerCase().contains(needle)) {
                    found.add(row);
                }
            }
            return found;
        }
        // A folder is "under" this one when its path starts with it; null means every volume at once,
        // which is what searching from the volume list has to mean.
        final String under = folder == null ? null : pathOf(folder) + "/";
        final boolean showHidden = Prefs.showHiddenFiles(this);
        for (final Map.Entry<String, long[]> entry : videoFolders().entrySet()) {
            final String path = entry.getKey();
            if (under != null && !path.startsWith(under)) {
                continue;
            }
            final String name = nameOf(path);
            if (name.toLowerCase().contains(needle) && (showHidden || !name.startsWith("."))) {
                final File dir = new File(path);
                if (dir.isDirectory()) {
                    found.add(new Item(name, DocumentFile.fromFile(dir)));
                }
            }
            if (found.size() >= SEARCH_LIMIT) {
                return found;
            }
        }
        for (final String path : videoPaths) {
            if (under != null && !path.startsWith(under)) {
                continue;
            }
            final String name = nameOf(path);
            if (!name.toLowerCase().contains(needle) || (!showHidden && name.startsWith("."))) {
                continue;
            }
            final File file = new File(path);
            if (file.isFile()) {
                found.add(new Item(name, DocumentFile.fromFile(file)));
            }
            if (found.size() >= SEARCH_LIMIT) {
                return found;
            }
        }
        return found;
    }

    /**
     * A network folder's rows: the ones read to put it on screen where they are still the right
     * ones, and a fresh listing otherwise - which is what a folder reached by a remembered trail,
     * before it has ever been shown, needs.
     */
    private List<Item> rowsOf(final DocumentFile folder) {
        final List<Item> kept = openFolderListing;
        return kept != null && folder.getUri().toString().equals(openFolderKey)
                ? kept : contentsOf(folder);
    }

    /** The last segment of a path. */
    private static String nameOf(final String path) {
        final int cut = path.lastIndexOf('/');
        return cut >= 0 && cut + 1 < path.length() ? path.substring(cut + 1) : path;
    }

    private void bind(final List<Item> listed, @Nullable final Throwable why,
                      @Nullable final Item landing) {
        // The same folder, listed again, is the common case: every return from the player and from
        // Settings re-lists what is already on screen. Rebinding all of it redoes work that was right
        // - and the one thing that can have changed while this screen was away is where the viewer
        // stopped in a file, so that is what is rebound.
        final boolean same = sameRows(listed);
        rows.clear();
        rows.addAll(listed);
        if (list.getAdapter() != null) {
            if (same) {
                rebindChangedRuns();
            } else {
                list.getAdapter().notifyDataSetChanged();
            }
        }
        if (rows.isEmpty()) {
            if (why != null) {
                blamePlace(why);
                return;
            }
            if (dest == Dest.FAVORITES && trail.isEmpty() && query == null) {
                // Not "no videos in this folder": there is no folder, and the way to fill this one is
                // somewhere else entirely - so the button goes there rather than offering an act this
                // screen cannot perform.
                showBlank(R.string.browse_no_favorites, R.string.browse_title,
                        () -> choose(Dest.FILES));
                return;
            }
            if (dest == Dest.NETWORK && trail.isEmpty() && query == null) {
                // Not "no videos in this folder": there is no folder yet, and adding one is the whole
                // content of this destination until there is.
                showBlank(R.string.browse_no_places, R.string.browse_add_network, this::askForNetwork);
                showProtocols();
                return;
            }
            // Which folder was looked in, when only one was: over the network the search does not go
            // down, and "nothing here" would be read as "the server has no such file".
            showBlank(query != null
                    ? searchInOneFolder() ? R.string.browse_no_matches_folder
                            : R.string.browse_no_matches
                    : subtitles ? R.string.browse_empty_subtitles : R.string.browse_empty, false);
            return;
        }
        blank.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        // A remote already on a row stays there. This listing is most often the same folder read
        // again on the way back from the player, and the row it is standing on is the file that was
        // just watched: moving it to the top would undo the viewer's own place in the list. Going up
        // does not reach this - the rows were cleared, and the focus left the list with them.
        if (tv && list.getFocusedChild() != null) {
            return;
        }
        // Which row to arrive on: the folder just stepped out of, and failing that - on this screen's
        // very first listing only - the file the player last had open.
        //
        // The first pick of a session finishes this screen (see chose()), so the way back from the
        // player builds it again: the trail comes back from preferences and the row would not, leaving
        // the folder reopened at the top with what was just watched somewhere below it. The second pick
        // and every one after it never reaches this, because that browser is still standing under the
        // player with its focus and its scroll intact - which is also why this is for the first listing
        // alone. After it, this screen's own memory of where the viewer is is the newer of the two.
        final Uri where = landing != null ? landing.file.getUri()
                : landed ? null : Prefs.lastMedia(this);
        landed = true;
        // By address, not by position: the folder may have been sorted, filtered or changed on disk
        // while the viewer was inside. An address that is not in this listing at all - a file deleted,
        // a stream played from somewhere else entirely - lands nowhere, which is row 0 on a television
        // and "leave the list where it is" on a telephone.
        int at = -1;
        if (where != null) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).file.getUri().equals(where)) {
                    at = i;
                    break;
                }
            }
        }
        if (!tv) {
            // A telephone has no contour to place; the row only has to be on screen.
            if (at > 0) {
                list.scrollToPosition(at);
            }
            return;
        }
        land(Math.max(at, 0), true);
    }

    /**
     * Puts the remote on row {@code at} once the layout that binds it has run - posted, because until
     * then the row has no view to focus and {@code findViewHolderForAdapterPosition} answers null.
     *
     * <p>A row below the first screenful is scrolled to first and then landed on by one more pass:
     * the scroll is what lays it out, and it lays it out at the bottom edge, which is where walking
     * down to it with the remote would have left it. One retry, never a loop.
     */
    private void land(final int at, final boolean mayScroll) {
        list.post(() -> {
            if (at >= rows.size()) {
                return;
            }
            final RecyclerView.ViewHolder holder = list.findViewHolderForAdapterPosition(at);
            if (holder != null) {
                holder.itemView.requestFocus();
                return;
            }
            if (mayScroll) {
                list.scrollToPosition(at);
                land(at, false);
                return;
            }
            final View first = list.getChildAt(0);
            if (first != null) {
                first.requestFocus();
            }
        });
    }

    /**
     * Whether a search's hits all sit in the folder standing open. Over the network they do - the
     * search is of that folder and does not go down - so the folder a hit is in is the one already
     * named in the crumbs, and printing it under every row repeats it as many times as there are
     * rows. On this device the search asks the index, which spans the whole tree, so there the
     * folder is the one thing a hit's name does not say.
     */
    private boolean searchInOneFolder() {
        final Item here = trail.isEmpty() ? null : trail.get(trail.size() - 1);
        return here != null && here.file instanceof NetworkDocumentFile;
    }

    /** The blank page with a heading over it: a place that exists and has nothing in it yet. */
    private void showBlank(final int title, final int message) {
        showBlank(message, false);
        blankTitle.setText(title);
        blankTitle.setVisibility(View.VISIBLE);
    }

    /**
     * The three protocols this destination can hold, as one line under the message. A page that says
     * only "nothing yet" leaves the viewer to guess what the button would even accept; these are the
     * answer, and they are the app's real list - SMB, WebDAV and DLNA are what
     * {@link NetworkFiles#isNetwork} admits, so nothing here promises a protocol that would refuse.
     *
     * <p>Text rather than three plates: a value is text unless it is pickable, and nothing here is.
     */
    private void showProtocols() {
        blankTiles.removeAllViews();
        final TextView line = new TextView(this);
        line.setText("SMB · WebDAV · DLNA · TorrServer");
        line.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        line.setTextColor(MaterialColors.getColor(this, android.R.attr.textColorSecondary, Color.GRAY));
        if (tv) {
            line.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        }
        blankTiles.addView(line);
        blankTiles.setVisibility(View.VISIBLE);
    }

    /**
     * The blank page with the one action worth offering on it - the storage grant, or the first
     * network folder. {@code actionLabel} 0 leaves the page without a button.
     */
    private void showBlank(final int message, final int actionLabel, final Runnable action) {
        showBlank(message, false);
        if (actionLabel == 0) {
            return;
        }
        blankAction.setText(actionLabel);
        blankAction.setOnClickListener(v -> action.run());
        blankAction.setVisibility(View.VISIBLE);
        blankAction.requestFocus();
    }

    private void showBlank(final int message, final boolean offerAccess) {
        dropSkeleton();
        blankTitle.setVisibility(View.GONE);
        blankTiles.setVisibility(View.GONE);
        rows.clear();
        if (list.getAdapter() != null) {
            list.getAdapter().notifyDataSetChanged();
        }
        list.setVisibility(View.GONE);
        blank.setVisibility(View.VISIBLE);
        blankText.setText(message);
        blankAction.setVisibility(offerAccess ? View.VISIBLE : View.GONE);
        if (offerAccess) {
            blankAction.setText(R.string.browse_grant_access);
            blankAction.setOnClickListener(v -> grantAccess());
            blankAction.requestFocus();
        }
    }

    /**
     * Hands the file over — as a result to whoever is waiting for one, and otherwise by playing it.
     *
     * <p>The difference is not cosmetic. Opened from the player's empty state, this screen was
     * started for a result and the player is still underneath: answering and finishing is the whole
     * transaction. Opened by {@link #reopenFrom} it stands in the player's place with nothing under
     * it, so a result would go to nobody and finishing would empty the task and close the app -
     * which is exactly what a second pick used to do.
     *
     * <p>Playing it also seats the player above this screen, so Back comes straight back here with
     * no arrangement of any kind. Which is the structure that was wanted all along, and the reason a
     * pick after the first needs none of the machinery the first one does.
     */
    private void chose(final DocumentFile file) {
        if (getCallingActivity() != null) {
            setResult(RESULT_OK, new Intent().setData(file.getUri()));
            finish();
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, file.getUri())
                .setClass(this, PlayerActivity.class));
    }

    /**
     * Opens the browser over the player, and takes the player down with it, so that Back out of a
     * file lands back in the folder it came from instead of out of the app.
     *
     * <p>It has to be done this way round because the player is {@code singleTask}: there can only be
     * one of it in the task, so the browser cannot sit under a second instance while the first one
     * plays. Whoever calls this is on their way out — and the screen this leaves behind has nothing
     * under it, which is what {@link #chose} has to know about.
     */
    static void reopenFrom(final Activity player) {
        player.startActivity(new Intent(player, BrowserActivity.class));
    }

    /**
     * The shell again, with nothing of the player left above it: what the player calls when there is
     * nothing to play any more.
     *
     * <p>Not {@link #reopenFrom}, because by now there is usually a shell in the task already - the one
     * the file was picked in. Starting a plain intent would stack a second one on top of it, and Back
     * out of that would land on the first, which reads as the app refusing to close. CLEAR_TOP brings
     * the instance that is there forward and finishes whatever sits above it; SINGLE_TOP keeps it from
     * being rebuilt when it is already on top.
     */
    static void returnTo(final Activity player) {
        player.startActivity(new Intent(player, BrowserActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    private void descend(final Item folder) {
        trail.add(folder);
        show();
    }

    /**
     * Changes how the folder is drawn and remembers it.
     *
     * <p>The adapter is replaced rather than told: a tile and a row are different view types, and the
     * views already built for the old one are of no use to the new one. The listing itself does not
     * move, so nothing is read again.
     */
    /**
     * The view after this one, skipping the one the window has no room for.
     *
     * <p>Two lists need the width of two, and a telephone held upright has the width of one - the
     * 600dp the app already uses to decide between a bar and a rail is the same question asked about
     * the same window. Offering a third press that changes nothing is worse than having two views.
     */
    private String nextView() {
        final boolean wide = getResources().getConfiguration().screenWidthDp >= 600;
        if ("rows".equals(view)) {
            return "tiles";
        }
        if ("tiles".equals(view)) {
            return wide ? "columns" : "rows";
        }
        return "rows";
    }

    /**
     * Starts the wait for a listing. Nothing is drawn yet: what is drawn, and when, is
     * {@link #raiseSkeleton()} a fifth of a second from now if the folder has not answered by then.
     */
    private void waitForRows() {
        skeleton.removeCallbacks(skeletonIn);
        skeleton.postDelayed(skeletonIn, SKELETON_DELAY_MS);
    }

    /**
     * The folder answered, or the screen moved on. The skeleton goes at once rather than serving out
     * some minimum time: it covers the list, so holding it would be holding back the rows themselves.
     */
    private void dropSkeleton() {
        skeleton.removeCallbacks(skeletonIn);
        stopPulse();
        if (skeleton.getVisibility() == View.VISIBLE) {
            skeleton.setVisibility(View.GONE);
            skeleton.removeAllViews();
        }
    }

    /**
     * Builds the skeleton for the view that is about to be filled and shows it.
     *
     * <p>Rebuilt on every raise rather than kept: the shape depends on the view the viewer chose, the
     * width of the window and the height there is to fill, and all three can have changed since the
     * last folder. It is cheap - a handful of inflations of two layouts with nothing in them.
     *
     * <p>It pulses rather than shimmers. A shimmer is a gradient travelling across every block, which
     * is a drawable and an animator per block; the whole plate breathing says the same thing - this is
     * not the content, it is the wait - for one animator on one view.
     */
    private void raiseSkeleton() {
        final int spans = spanCount();
        final boolean tiles = "tiles".equals(view);
        final int usable = Math.max(1,
                list.getWidth() - list.getPaddingLeft() - list.getPaddingRight());
        final int itemHeight = tiles
                ? usable / spans * 9 / 16 + Utils.dpToPx(60)
                : Utils.dpToPx(tv ? 96 : 72);
        final int room = list.getHeight() > 0 ? list.getHeight()
                : getResources().getDisplayMetrics().heightPixels;

        skeleton.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(this);
        // One more line than fits, so the last one runs off the bottom edge: a skeleton that ends
        // short of the fold says the folder holds exactly that many files, which nobody knows yet.
        for (int line = 0; line <= room / Math.max(1, itemHeight); line++) {
            final LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < spans; column++) {
                final View cell = inflater.inflate(
                        tiles ? R.layout.browse_skeleton_tile : R.layout.browse_skeleton_row, row, false);
                if (tv && !tiles) {
                    // The row these stand in for is resized in code on a television, to be read from
                    // three metres (see RowView) - so the same numbers are applied here. Without it the
                    // skeleton was a third shorter than what replaced it, and the list jumped.
                    cell.setMinimumHeight(Utils.dpToPx(96));
                    final View block = cell.findViewById(R.id.browse_skeleton_frame);
                    final ViewGroup.LayoutParams blockLp = block.getLayoutParams();
                    blockLp.width = Utils.dpToPx(136);
                    blockLp.height = Utils.dpToPx(77);
                    block.setLayoutParams(blockLp);
                }
                row.addView(cell, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            skeleton.addView(row);
        }

        // A skeleton stands in for content that is not there yet, so nothing else may hold the screen
        // while it does. The page left from the last empty answer was being drawn straight through
        // it - "nothing matches that" lettered over the grey of the search that was still running,
        // which is two answers at once and one of them out of date.
        blank.setVisibility(View.GONE);
        skeleton.setAlpha(1f);
        skeleton.setVisibility(View.VISIBLE);
        pulse();
    }

    /**
     * The breath: one animator that reverses for as long as the plate shows.
     *
     * <p>It used to be two halves calling each other through {@code withEndAction}, and that is a
     * trap: {@code withEndAction} runs when the animation is cancelled as well as when it finishes,
     * and {@link #dropSkeleton} cancels while the plate is still visible. So the cancelled half
     * started another half, which outlived the drop and was still running when the next folder raised
     * a skeleton and started a second chain. Two chains driving one alpha is not a slower breath, it
     * is a stutter - and the longer the wait, the more of them there were, which made the one screen
     * this was written for (a server that never answers) the worst it ever looked.
     */
    private void pulse() {
        stopPulse();
        skeletonPulse = ObjectAnimator.ofFloat(skeleton, View.ALPHA, 1f, SKELETON_DIM);
        skeletonPulse.setDuration(SKELETON_PULSE_MS);
        skeletonPulse.setRepeatCount(ValueAnimator.INFINITE);
        skeletonPulse.setRepeatMode(ValueAnimator.REVERSE);
        // Eased at both ends, so the turn is a breath rather than a bounce off a wall.
        skeletonPulse.setInterpolator(new AccelerateDecelerateInterpolator());
        skeletonPulse.start();
    }

    /** Stops the breath and forgets it, so nothing of it can outlive the plate it belongs to. */
    private void stopPulse() {
        if (skeletonPulse != null) {
            skeletonPulse.cancel();
            skeletonPulse = null;
        }
    }

    private void chooseView(final String chosen) {
        if (chosen.equals(view)) {
            return;
        }
        view = chosen;
        Prefs.setBrowseView(this, chosen);
        spanColumns();
        list.setAdapter(new Rows());
    }

    /**
     * How many columns the window holds.
     *
     * <p>One for the list and two for the pair of them, because that is what they are. For tiles it
     * is a question about the window: a tile below about 170dp is a picture nobody can read a name
     * under, and on a television the same tile is read from three metres and wants to be bigger.
     */
    private void spanColumns() {
        if (!(list.getLayoutManager() instanceof GridLayoutManager)) {
            return;
        }
        final GridLayoutManager grid = (GridLayoutManager) list.getLayoutManager();
        final int spans = spanCount();
        if (grid.getSpanCount() != spans) {
            grid.setSpanCount(spans);
        }
    }

    /** How many columns the current view comes to in the window as it stands. */
    private int spanCount() {
        final int usable = list.getWidth() - list.getPaddingLeft() - list.getPaddingRight();
        if ("tiles".equals(view)) {
            return usable <= 0 ? 2 : Math.max(2, usable / Utils.dpToPx(tv ? 220 : 170));
        }
        if ("columns".equals(view)) {
            // Kept as the choice and drawn as one column where two will not fit: a folder opened
            // upright on a telephone is a list, and turning the telephone gives the second column
            // back without anybody having to ask for it again.
            return getResources().getConfiguration().screenWidthDp >= 600 ? 2 : 1;
        }
        return 1;
    }

    private final class Rows extends RecyclerView.Adapter<RowView> {

        @NonNull
        @Override
        public RowView onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
            final boolean tile = viewType == 1;
            return new RowView(LayoutInflater.from(parent.getContext())
                    .inflate(tile ? R.layout.browse_tile : R.layout.browse_row, parent, false), tile);
        }

        @Override
        public int getItemViewType(final int position) {
            return "tiles".equals(view) ? 1 : 0;
        }

        @Override
        public void onBindViewHolder(@NonNull final RowView holder, final int position) {
            holder.bind(rows.get(position));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private final class RowView extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView path;
        private final TextView duration;
        private final TextView meta;
        private final ImageView star;
        private final FrameLayout box;
        private final FrameLayout frame;
        private final boolean tile;

        RowView(final View view, final boolean tile) {
            super(view);
            this.tile = tile;
            name = view.findViewById(R.id.browse_row_name);
            path = view.findViewById(R.id.browse_row_path);
            duration = view.findViewById(R.id.browse_row_duration);
            meta = view.findViewById(R.id.browse_row_meta);
            star = view.findViewById(R.id.browse_row_star);
            frame = view.findViewById(R.id.browse_row_frame);
            // Built here and never again: a row is recycled, and making a new box, a new poster and a
            // new request on every bind is what redrew the whole list on every arrival.
            // R3: 8dp up to 56dp tall, 12dp for a card 72dp or taller. A row's still is 54dp on a
            // telephone and 77 on a television; a tile's is most of a column and always the larger.
            box = Utils.previewBox(view.getContext(), Utils.dpToPx(tile || tv ? 12 : 8), Utils.dpToPx(24));
            frame.addView(box, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            if (tv && !tile) {
                // The layout is sized for a phone; a set reads its rows from three metres, so the
                // still and the type go up and the row with them. One place, because every row in
                // the app is this one. A tile is already as wide as its column and measures its own
                // height, so none of this is its business.
                view.setMinimumHeight(Utils.dpToPx(96));
                final ViewGroup.LayoutParams lp = frame.getLayoutParams();
                lp.width = Utils.dpToPx(136);
                lp.height = Utils.dpToPx(77);
                frame.setLayoutParams(lp);
                name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                path.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                duration.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            }
            // The plate every row inside a player panel wears, at the corner R3 gives a card this
            // tall: a clipped ripple, and the 2dp contour that is how this app says "focused".
            // A list row is drawn on it rather than through it: one step off the ground is what
            // separates a row from the window behind it and gives the still something to sit on.
            // A tile keeps the ground - its picture is its own plate, and a second one around the
            // name turns a grid of stills into a grid of cards.
            view.setBackground(Dialogs.pickerRow(view.getContext(), tile ? Color.TRANSPARENT
                    : MaterialColors.getColor(view.getContext(), R.attr.colorSurfaceContainer,
                            Color.TRANSPARENT), false, Utils.dpToPx(12)));
        }

        void bind(final Item item) {
            name.setText(item.name);
            // The tag is what a late answer checks itself against: the row it was asked for may have
            // been recycled onto another file by the time a duration or a folder's contents come back.
            itemView.setTag(item);
            duration.setVisibility(View.GONE);
            meta.setVisibility(View.GONE);
            // Not on the Favorites list itself, where every row is one and the mark says nothing.
            star.setVisibility(dest == Dest.FAVORITES && trail.isEmpty() ? View.GONE
                    : Favorites.has(BrowserActivity.this, item.file.getUri()) ? View.VISIBLE
                    : View.GONE);

            // Rebound, not rebuilt: Glide cancels what the poster was loading and the glyph stands in
            // until the new frame arrives, so a recycled row never shows the previous file's still and
            // never costs a view.
            final Context ctx = itemView.getContext();
            // A media server hands the picture over with the listing, so a network row can have one
            // without anything being read: everywhere else over the network the glyph stands.
            // Off the listing where there is one, and otherwise off what the favorite was put aside
            // with - the Favorites list has no listing behind it to carry a server's picture.
            // A folder too, now: a torrent is a folder, and its poster is the one picture in this
            // whole list that arrived without anything being decoded. What stays barred to a folder
            // is the frame below - there is no file there to take one from.
            String served = NetworkFiles.artwork(item.file);
            if (served == null) {
                served = favoriteArt.get(item.file.getUri().toString());
            }

            final Uri indexed = item.folder ? null : stills.get(item.file.getUri().toString());
            Utils.bindPreview(box, served == null ? null : Uri.parse(served),
                    item.folder ? null : indexed != null ? indexed : item.file.getUri(),
                    glyphFor(item), Utils.dpToPx(item.folder ? 32 : 24));
            if (!item.folder && !item.volume && lengths.containsKey(item.file.getUri().toString())) {
                // The track is the scale the run sits on, so it appears where a run can: with a length
                // known. A folder has no position at all, and a file whose length nobody can state -
                // a share, or a server that did not say - would carry a bar that can never fill, which
                // on a still with no picture in it is a mark with nothing to mean.
                Utils.playedTrack(box);
            }

            if (query != null) {
                // A hit is somewhere else by definition, so where it is matters more here than it
                // does in a plain listing - for a file as much as for a folder. Two exceptions. A
                // volume or a saved place is the top of a tree and has nothing above it to print:
                // searching Network found one and lettered it "/ctl", off its control address. And a
                // search of one folder has the same answer for every row, already written in the
                // crumbs - under a tile it was a second line of its own, under the name, saying it.
                final String parent = item.volume || searchInOneFolder() ? null : parentOf(item);
                path.setText(parent);
                path.setVisibility(parent == null ? View.GONE : View.VISIBLE);
                if (!item.folder) {
                    videoMeta(item, ctx);
                    describeVideo(item, this);
                }
            } else if (item.volume) {
                // A volume is the top of everything, so there is no path above it to print, and
                // counting the videos loose in its root says nothing about what is on it.
                //
                // A saved network place is the exception: its name is whatever the server called
                // itself or somebody typed, and the address underneath says which machine that is.
                // Two boxes can both be called Home, and a machine running a media server and a
                // torrent server is in this list twice under one name.
                //
                // Unless the address is the name, which is what a machine that never announced one
                // is called: printing it twice fills a line with nothing. The same rule the picker
                // states while the machine is being chosen.
                final String where = NetworkFiles.address(item.file.getUri());
                final boolean says = where != null && !where.equals(item.name);
                path.setText(where);
                path.setVisibility(says ? View.VISIBLE : View.GONE);
            } else if (item.folder) {
                // Where it is, which is the one thing a folder's name does not say — two folders
                // called Movies on two volumes are otherwise the same row twice.
                final String parent = parentOf(item);
                path.setText(parent);
                path.setVisibility(parent == null ? View.GONE : View.VISIBLE);
                describeFolder(item, this);
            } else {
                path.setVisibility(View.GONE);
                videoMeta(item, ctx);
                describeVideo(item, this);
            }

            itemView.setOnClickListener(v -> {
                if (item.folder) {
                    descend(item);
                } else {
                    chose(item.file);
                }
            });
            // Every row has something to do to it now that any of them can be put aside, and a long
            // press is where every list in this app puts that.
            itemView.setLongClickable(true);
            itemView.setOnLongClickListener(v -> {
                rowMenu(item);
                return true;
            });
        }

        /**
         * The frame a folder shows: the newest video under it, put into the same still every file row
         * has. A folder of films is a picture of one of them, which is how every library this was
         * measured against draws a folder, and the glyph stays the fallback for a folder that has none.
         */
        void art(final Item item, final Uri source) {
            if (itemView.getTag() != item || source == null) {
                return;
            }
            Utils.bindPreview(box, null, source, R.drawable.ic_folder_open_24dp, Utils.dpToPx(32));
        }

            /**
         * How big the file is: out of the index when the listing came from there, and off the
         * filesystem otherwise. A stat per row is nothing on a folder of ten and is a folder of five
         * thousand scrolling badly.
         */
        String sizeOf(final Item item, final Context ctx) {
            final Long known = sizes.get(item.file.getUri().toString());
            final long bytes = known != null ? known : item.file.length();
            return bytes > 0 ? Formatter.formatShortFileSize(ctx, bytes) : null;
        }

        /**
         * The line under a video's name: what it is, how big it is, when it was made - the three
         * things a listing can state about a file without opening it a second time. A tile keeps the
         * size alone: its line is a chip on the picture, the width of a running time.
         *
         * <p>The date is numeric, in the shortest form the locale has: spelled out - "8 сент. 2025" -
         * the line ran past the end of the row and was cut mid-number, and a four-digit year cost the
         * same two characters again on anything from another year: "1080p · 734 МБ · 11.07.20…". Which
         * is how the reference this follows draws it too, and it is avoidable.
         */
        void videoMeta(final Item item, final Context ctx) {
            final String size = sizeOf(item, ctx);
            if (tile) {
                setMeta(size, null);
                return;
            }
            final String key = item.file.getUri().toString();
            String said = resolutions.get(key);
            if (said == null) {
                // A media server states it in the listing, so it is here before anything is read.
                said = DlnaFiles.resolution(item.file);
                if (said != null) {
                    resolutions.put(key, said);
                }
            }
            final Long when = dates.get(key);
            final long written = when != null ? when : item.file.lastModified();
            final StringBuilder line = new StringBuilder();
            for (final String part : new String[]{ said, size,
                    written > 0 ? shortDate.format(written) : null }) {
                if (part != null && !part.isEmpty()) {
                    if (line.length() > 0) {
                        line.append(" · ");
                    }
                    line.append(part);
                }
            }
            setMeta(line.length() == 0 ? null : line.toString(), null);
        }

        /**
         * The line under a network folder's name: what the listing said about the folder, which is
         * all there is without opening it.
         */
        void networkMeta(final Item item, final Context ctx) {
            final long bytes = item.file.length();
            final long written = item.file.lastModified();
            setMeta(bytes > 0 ? Formatter.formatShortFileSize(ctx, bytes) : null,
                    written > 0 ? shortDate.format(written) : null);
        }

    /**
         * The meta line: what there is of it, joined by the separator the app uses for a list.
         *
         * <p>On a tile it is a chip on the picture rather than a line under the name, so it takes the
         * first half alone - the videos in a folder, the size of a file. The second half is what a
         * folder weighs, which is a sentence for a list and does not belong on a plate the size of a
         * running time.
         */
        void setMeta(final String first, final String second) {
            final String line = tile ? first
                    : first == null ? second
                    : second == null ? first : first + " · " + second;
            meta.setText(line);
            meta.setVisibility(line == null ? View.GONE : View.VISIBLE);
        }

        /** Fills in what only a read of the file itself can say, and only if this row still wants it. */
        void late(final Item item, final String runningTime, final String count, final String size) {
            late(item, runningTime, count, size, -1f);
        }

        void late(final Item item, final String runningTime, final String count, final String size,
                  final float played) {
            if (itemView.getTag() != item) {
                return;
            }
            if (lengths.containsKey(item.file.getUri().toString())) {
                // The length can arrive after the row does - off the retriever, or off the server's
                // own answer - and the track comes with it.
                Utils.playedTrack(box);
            }
            if (played > 0) {
                Utils.playedRun(box, played);
            }
            final Long at = positions.get(item.file.getUri().toString());
            if (at != null) {
                shown.put(item.file.getUri().toString(), at);
            }
            if (runningTime != null) {
                duration.setText(runningTime);
                duration.setVisibility(View.VISIBLE);
            }
            if (count != null || size != null) {
                setMeta(count, size);
            }
        }
    }

    /** The folder holding this one, as a path, for the line under its name. */
    private String parentOf(final Item item) {
        final String own = item.file.getUri().getPath();
        if (own == null) {
            return null;
        }
        final int cut = own.lastIndexOf('/');
        return cut > 0 ? own.substring(0, cut) : null;
    }

    /**
     * The running time of a video, read off the file and remembered. A retriever open is tens of
     * milliseconds, so it happens on the pool and only for rows in view — a folder of two hundred
     * files costs the eight that are on screen.
     */
    /**
     * How far through a file the viewer got, as a fraction, or -1 when either half of the sum is
     * missing. Both halves arrive by their own route: the position out of the player's file, the
     * length off the retriever that was already reading this file for its running time.
     */
    private float playedFraction(final String uri) {
        final Long position = positions.get(uri);
        final Long length = lengths.get(uri);
        if (position == null || length == null || length <= 0) {
            return -1f;
        }
        return (float) position / length;
    }

    private void describeVideo(final Item item, final RowView row) {
        final String key = item.file.getUri().toString();
        // A torrent server states no running time for a file and cannot be asked for one without
        // being made to fetch the file and probe it - which is what a torrent backend can least
        // afford. So there is no bar to draw, and what a row can say instead is where the viewer
        // stopped, which came back with the listing and cost nothing.
        if (TorrFiles.speaks(item.file.getUri())) {
            // The server measures one running time per torrent, so a torrent of one file - which is
            // most films - has one to state. Written down either way as asked-and-answered, so no
            // retriever is ever set on an address it cannot open.
            final long stated = TorrFiles.runningTime(item.file);
            if (stated > 0) {
                lengths.put(key, stated);
            }
            durations.put(key, stated > 0 ? Utils.formatMilis(stated) : "");
            final Long at = positions.get(key);
            final boolean stopped = at != null && at > 0;
            row.late(item, stated > 0 ? Utils.formatMilis(stated) : null,
                    stopped ? row.sizeOf(item, this) : null,
                    stopped ? getString(R.string.browse_stopped_at, Utils.formatMilis(at)) : null);
            return;
        }
        if (!durations.containsKey(key)) {
            // A server states how long its file plays for, and that is the only way to know here: the
            // file is a stream, and opening it to ask would be a network round trip per row.
            final long stated = DlnaFiles.runningTime(item.file);
            if (stated > 0) {
                lengths.put(key, stated);
                durations.put(key, Utils.formatMilis(stated));
            }
        }
        final String known = durations.get(key);
        if (known != null) {
            row.late(item, known.isEmpty() ? null : known, null, null, playedFraction(key));
            return;
        }
        readers.execute(() -> {
            String formatted = "";
            final MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(this, item.file.getUri());
                final String ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                if (ms != null) {
                    final long length = Long.parseLong(ms);
                    formatted = Utils.formatMilis(length);
                    lengths.put(key, length);
                }
                // The same open file, asked one more question: what the row says the video is.
                resolution(key, number(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                        number(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            } catch (Exception e) {
                // A file the retriever cannot open says nothing, and a row without a time is fine.
                e.printStackTrace();
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                    // Nothing to do about a retriever that will not close.
                }
            }
            final String answer = formatted;
            durations.put(key, answer);
            runOnUiThread(() -> {
                row.late(item, answer.isEmpty() ? null : answer, null, null, playedFraction(key));
                if (row.itemView.getTag() == item) {
                    row.videoMeta(item, this);
                }
            });
        });
    }

    /**
     * How a video is named: the shorter side, which is the number people say. A portrait clip shot on
     * a telephone is 1080 x 1920 and is a 1080p clip, not a 1920p one.
     */
    private void resolution(final String key, final int width, final int height) {
        final int side = Math.min(width, height);
        if (side > 0) {
            resolutions.put(key, side + "p");
        }
    }

    /** One of the retriever's numbers, or 0 where it said nothing readable. */
    private static int number(final MediaMetadataRetriever retriever, final int what) {
        try {
            return Integer.parseInt(String.valueOf(retriever.extractMetadata(what)));
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * How many videos a folder holds and what they weigh, counting everything beneath it. Read off
     * {@link #videoFolders()}, so it costs a lookup rather than a listing.
     */
    private void describeFolder(final Item item, final RowView row) {
        if (NetworkFiles.isNetwork(item.file.getUri())) {
            // Nothing below a network folder has been counted and counting it is a round trip, so what
            // its row states is what the listing already said about the folder itself - which for a
            // torrent is the whole torrent's size and the day the server took it in.
            row.networkMeta(item, this);
            return;
        }
        final Map<String, long[]> known = videoFolders;
        if (known == null) {
            // Not here yet: start it, and the rows rebind when it lands. A count is worth waiting for
            // in the background and never worth waiting for in front of the viewer.
            indexFolders();
            return;
        }
        final long[] tally = known.get(pathOf(item.file));
        if (tally == null) {
            return;
        }
        row.late(item, null,
                getResources().getQuantityString(R.plurals.browse_videos, (int) tally[0], tally[0]),
                tally[1] > 0 ? Formatter.formatShortFileSize(this, tally[1]) : null);
        final String art = folderArt.get(pathOf(item.file));
        if (art != null) {
            row.art(item, Uri.fromFile(new File(art)));
        }
    }

    /**
     * Which folders hold videos anywhere beneath them, and how many, as
     * {@code path -> {count, bytes}}.
     *
     * <p>Built from a single MediaStore query rather than by walking the tree, which is the trick the
     * reference player turns and the only reason this is affordable: one query hands back the path of
     * every video on the device, and rolling each of those up through its ancestors gives a recursive
     * count for every folder at once. Walking shared storage for the same answer would mean stat-ing
     * tens of thousands of files before the first row could be drawn.
     *
     * <p>Held for the life of the screen. A folder that gains a video while the browser is open will
     * be one listing behind, which is worth an outdated count and not worth a rescan per level.
     */
    /**
     * Builds the index off the main thread, once, and rebinds the rows when it lands so the counts
     * appear the way a running time does. Nothing waits for it: the listing it would have blocked is
     * already on screen by then.
     */
    private void indexFolders() {
        if (videoFolders != null || indexing) {
            return;
        }
        indexing = true;
        new Thread(() -> {
            final Map<String, long[]> built = videoFolders();
            runOnUiThread(() -> {
                indexing = false;
                if (list.getAdapter() != null) {
                    list.getAdapter().notifyDataSetChanged();
                }
            });
        }).start();
    }

    private synchronized Map<String, long[]> videoFolders() {
        if (videoFolders != null) {
            return videoFolders;
        }
        final Map<String, long[]> found = new HashMap<>();
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL),
                new String[]{ MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE,
                        MediaStore.MediaColumns.DATE_MODIFIED },
                null, null, null)) {
            final int pathColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            final int sizeColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            final int dateColumn = cursor == null
                    ? -1 : cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
            final Map<String, Long> newest = new HashMap<>();
            while (pathColumn >= 0 && cursor.moveToNext()) {
                final String path = cursor.getString(pathColumn);
                if (path == null) {
                    continue;
                }
                final long size = sizeColumn < 0 ? 0 : cursor.getLong(sizeColumn);
                final long modified = dateColumn < 0 ? 0 : cursor.getLong(dateColumn);
                videoPaths.add(path);
                // Every folder from the one holding it up to the root, so a count is recursive by
                // construction and an ancestor with nothing of its own still knows what is below it.
                String folder = parentPath(path);
                while (folder != null) {
                    long[] tally = found.get(folder);
                    if (tally == null) {
                        tally = new long[2];
                        found.put(folder, tally);
                    }
                    tally[0]++;
                    tally[1] += size;
                    // The newest video below a folder is what its row shows a frame of - the one thing
                    // a folder of films can be a picture of, and this pass is already reading it.
                    final Long seen = newest.get(folder);
                    if (seen == null || modified > seen) {
                        newest.put(folder, modified);
                        folderArt.put(folder, path);
                    }
                    folder = parentPath(folder);
                }
            }
        } catch (Exception e) {
            // No media permission, or a provider that will not answer. Not remembered: kept, an empty
            // map is indistinguishable from a device with no videos on it and would hide nothing for
            // as long as the screen lives, where the next listing can simply ask again.
            e.printStackTrace();
            return found;
        }
        videoFolders = found;
        return found;
    }

    /** The path above this one, or null at the top. */
    private static String parentPath(final String path) {
        final int cut = path.lastIndexOf('/');
        return cut > 0 ? path.substring(0, cut) : null;
    }

    private static String pathOf(final DocumentFile file) {
        final String path = file.getUri().getPath();
        return path == null ? "" : path;
    }
}

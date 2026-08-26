package com.brouken.player;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;

import androidx.documentfile.provider.DocumentFile;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class SubtitleUtils {

    /**
     * Span types that say how a subtitle should <em>look</em>, as opposed to what it means. Colour,
     * size and typeface are the file's opinion about presentation, and presentation is the viewer's
     * to set — so these are dropped and the app's own style applies to every line alike.
     *
     * <p>What is deliberately not here: {@code StyleSpan} (italic and bold), {@code UnderlineSpan} and
     * {@code StrikethroughSpan}. Those carry meaning rather than decoration — italics are how a
     * subtitle marks a voice off screen, a narrator, a thought, or a line in another language — and
     * stripping them would lose information, not styling. Media3's own language-feature spans (ruby,
     * text emphasis, vertical text) stay for the same reason.
     */
    private static final Class<?>[] LOOK_SPANS = {
            ForegroundColorSpan.class, BackgroundColorSpan.class,
            AbsoluteSizeSpan.class, RelativeSizeSpan.class, TypefaceSpan.class,
    };

    /**
     * The same cues with the file's own presentation taken out, so that every subtitle looks the way
     * the viewer set it and nothing else.
     *
     * <p>This replaces the "Embedded styles" switch. That switch had to choose between two bad ends:
     * on, an ASS file could render itself unreadable with no recourse; off, Media3's
     * {@code removeAllEmbeddedStyling} stripped every span there is, italics included, and a voice off
     * screen became indistinguishable from a line of dialogue. Taking out colour, size and typeface
     * while leaving the typographic marks gives one uniform look with nothing lost.
     *
     * <p>Returns the group unchanged when there was nothing to take out, which is the common case: a
     * plain SRT carries little more than italics.
     */
    static CueGroup withoutEmbeddedLook(final CueGroup group) {
        boolean changed = false;
        final List<Cue> cues = new ArrayList<>(group.cues.size());
        for (final Cue cue : group.cues) {
            final Cue stripped = withoutEmbeddedLook(cue);
            changed |= stripped != cue;
            cues.add(stripped);
        }
        return changed
                ? new CueGroup(ImmutableList.copyOf(cues), group.presentationTimeUs)
                : group;
    }

    private static Cue withoutEmbeddedLook(final Cue cue) {
        final boolean ownSize = cue.textSize != Cue.DIMEN_UNSET || cue.textSizeType != Cue.TYPE_UNSET;
        Spannable text = null;
        if (cue.text instanceof Spanned) {
            final Spanned spanned = (Spanned) cue.text;
            for (final Class<?> type : LOOK_SPANS) {
                final Object[] spans = spanned.getSpans(0, spanned.length(), type);
                if (spans.length == 0) {
                    continue;
                }
                if (text == null) {
                    text = new SpannableString(cue.text);
                }
                for (final Object span : spans) {
                    text.removeSpan(span);
                }
            }
        }
        if (text == null && !ownSize && !cue.windowColorSet) {
            return cue;
        }
        final Cue.Builder builder = cue.buildUpon();
        if (text != null) {
            builder.setText(text);
        }
        if (ownSize) {
            builder.setTextSize(Cue.DIMEN_UNSET, Cue.TYPE_UNSET);
        }
        if (cue.windowColorSet) {
            // The box the file wanted behind its own text. The viewer's background setting is the one
            // that decides here.
            builder.clearWindowColor();
        }
        return builder.build();
    }

    public static String getSubtitleMime(Uri uri) {
        final String path = uri.getPath();
        if (path.endsWith(".ssa") || path.endsWith(".ass")) {
            return MimeTypes.TEXT_SSA;
        } else if (path.endsWith(".vtt")) {
            return MimeTypes.TEXT_VTT;
        } else if (path.endsWith(".ttml") ||  path.endsWith(".xml") || path.endsWith(".dfxp")) {
            return MimeTypes.APPLICATION_TTML;
        } else {
            return MimeTypes.APPLICATION_SUBRIP;
        }
    }

    /** Extensions a subtitle can arrive with. The format is read back off the name, so this is
     *  also the list of names {@link #getSubtitleLanguage} and the cache are willing to recognise. */
    static final String[] EXTENSIONS = { ".srt", ".ass", ".ssa", ".vtt", ".ttml" };

    /** Whether a file name ends in one of {@link #EXTENSIONS}. */
    static boolean hasSubtitleExtension(final String name) {
        for (final String extension : EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    public static String getSubtitleLanguage(Uri uri) {
        final String path = uri.getPath().toLowerCase();

        // Any subtitle extension, not only .srt: a downloaded copy is named after what it turned out
        // to be, and the language sits in the same place whatever that was.
        if (hasSubtitleExtension(path)) {
            int last = path.lastIndexOf(".");
            int prev = last;

            for (int i = last; i >= 0; i--) {
                prev = path.indexOf(".", i);
                if (prev != last)
                    break;
            }

            int len = last - prev;

            if (len >= 2 && len <= 6) {
                // TODO: Validate lang
                return path.substring(prev + 1, last);
            }
        }

        return null;
    }

    /*
    public static DocumentFile findUriInScope(DocumentFile documentFileTree, Uri uri) {
        for (DocumentFile file : documentFileTree.listFiles()) {
            if (file.isDirectory()) {
                final DocumentFile ret = findUriInScope(file, uri);
                if (ret != null)
                    return ret;
            } else {
                final Uri fileUri = file.getUri();
                if (fileUri.toString().equals(uri.toString())) {
                    return file;
                }
            }
        }
        return null;
    }
    */

    public static DocumentFile findUriInScope(Context context, Uri scope, Uri uri) {
        DocumentFile treeUri = DocumentFile.fromTreeUri(context, scope);
        String[] trailScope = getTrailFromUri(scope);
        String[] trailVideo = getTrailFromUri(uri);

        for (int i = 0; i < trailVideo.length; i++) {
            if (i < trailScope.length) {
                if (!trailScope[i].equals(trailVideo[i]))
                    break;
            } else {
                treeUri = treeUri.findFile(trailVideo[i]);
                if (treeUri == null)
                    break;
            }
            if (i + 1 == trailVideo.length)
                return treeUri;
        }
        return null;
    }

    public static DocumentFile findDocInScope(DocumentFile scope, DocumentFile doc) {
        if (doc == null || scope == null)
            return null;
        for (DocumentFile file : scope.listFiles()) {
            if (file.isDirectory()) {
                final DocumentFile ret = findDocInScope(file, doc);
                if (ret != null)
                    return ret;
            } else {
                //if (doc.length() == file.length() && doc.lastModified() == file.lastModified() && doc.getName().equals(file.getName())) {
                // lastModified is zero when opened from Solid Explorer
                final String docName = doc.getName();
                final String fileName = file.getName();
                if (docName == null || fileName == null) {
                    continue;
                }
                if (doc.length() == file.length() && docName.equals(fileName)) {
                    return file;
                }
            }
        }
        return null;
    }

    public static String getTrailPathFromUri(Uri uri) {
        String path = uri.getPath();
        String[] array = path.split(":");
        if (array.length > 1) {
            return array[array.length - 1];
        } else {
            return path;
        }
    }

    public static String[] getTrailFromUri(Uri uri) {
        if ("org.courville.nova.provider".equals(uri.getHost()) && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path.startsWith("/external_files/")) {
                return path.substring("/external_files/".length()).split("/");
            }
        }
        return getTrailPathFromUri(uri).split("/");
    }

    private static String getFileBaseName(String name) {
        if (name.indexOf(".") > 0)
            return name.substring(0, name.lastIndexOf("."));
        return name;
    }

    public static DocumentFile findSubtitle(DocumentFile video) {
        DocumentFile dir = video.getParentFile();
        return findSubtitle(video, dir);
    }

    public static DocumentFile findSubtitle(DocumentFile video, DocumentFile dir) {
        String videoName = getFileBaseName(video.getName());
        int videoFiles = 0;

        if (dir == null || !dir.isDirectory())
            return null;

        List<DocumentFile> candidates = new ArrayList<>();

        for (DocumentFile file : dir.listFiles()) {
            final String fileName = file.getName();
            if (fileName != null && fileName.startsWith("."))
                continue;
            if (isSubtitleFile(file))
                candidates.add(file);
            if (isVideoFile(file))
                videoFiles++;
        }

        if (videoFiles == 1 && candidates.size() == 1) {
            return candidates.get(0);
        }

        if (candidates.size() >= 1) {
            for (DocumentFile candidate : candidates) {
                if (candidate.getName().startsWith(videoName + '.')) {
                    return candidate;
                }
            }
        }

        return null;
    }

    public static DocumentFile findNext(DocumentFile video) {
        DocumentFile dir = video.getParentFile();
        return findNext(video, dir);
    }

    public static DocumentFile findNext(DocumentFile video, DocumentFile dir) {
        if (dir == null) {
            return null;
        }

        try {
            DocumentFile[] list = dir.listFiles();
            Arrays.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

            final String videoName = video.getName();
            boolean matchFound = false;

            for (DocumentFile file : list) {
                if (file.getName().equals(videoName)) {
                    matchFound = true;
                } else if (matchFound) {
                    if (isVideoFile(file)) {
                        return file;
                    }
                }
            }
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean isVideoFile(DocumentFile file) {
        return file.isFile() && file.getType().startsWith("video/");
    }

    public static boolean isSubtitleFile(DocumentFile file) {
        if (!file.isFile())
            return false;
        return hasSubtitleExtension(file.getName().toLowerCase());
    }

    public static boolean isSubtitle(Uri uri, String mimeType) {
        if (mimeType != null) {
            for (String mime : Utils.supportedMimeTypesSubtitle) {
                if (mimeType.equals(mime)) {
                    return true;
                }
            }
            if (mimeType.equals("text/plain") || mimeType.equals("text/x-ssa") || mimeType.equals("application/octet-stream") ||
                    mimeType.equals("application/ass") || mimeType.equals("application/ssa") || mimeType.equals("application/vtt")) {
                return true;
            }
        }
        if (uri != null) {
            if (Utils.isSupportedNetworkUri(uri)) {
                String path = uri.getPath();
                if (path != null) {
                    path = path.toLowerCase();
                    for (String extension : Utils.supportedExtensionsSubtitle) {
                        if (path.endsWith("." + extension)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * Drops every machine-translated copy in the cache. Called when the translation setting changes:
     * a copy made under the previous choice would keep being served for everything watched recently,
     * so the new choice would look like it did nothing. They cost one request each to rebuild.
     */
    static void clearTranslatedCache(Context context) {
        final File[] files = context.getCacheDir().listFiles((dir, name) ->
                name.startsWith("subs.") && name.contains(".auto.") && hasSubtitleExtension(name));
        if (files == null) {
            return;
        }
        for (final File file : files) {
            file.delete();
        }
    }

    /**
     * Empties the cache of the copies made while opening a subtitle by hand.
     *
     * <p>Deliberately spares the {@code subs.*} files: those are what the online search downloaded and
     * what makes a second watch cost no request and no quota. This used to delete everything, so opening
     * one external subtitle threw away every subtitle found for every film watched recently — and the
     * next search paid for them all again. Trimming those is {@code Utils.trimSubtitleCache}'s job.
     */
    public static void clearCache(Context context) {
        try {
            for (File file : context.getCacheDir().listFiles()) {
                if (file.isFile() && !file.getName().startsWith("subs.")) {
                    file.delete();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static MediaItem.SubtitleConfiguration buildSubtitle(Context context, Uri uri, String subtitleName, boolean selected) {
        final String subtitleMime = SubtitleUtils.getSubtitleMime(uri);
        final String subtitleLanguage = SubtitleUtils.getSubtitleLanguage(uri);
        if (subtitleLanguage == null && subtitleName == null)
            subtitleName = Utils.getFileName(context, uri);
        // A name that is nothing but digits is not a name: it is what is left of a content:// URI whose
        // display name the resolver would not hand over, and the picker showed it as "1000000160".
        // Dropped, so the row falls back to its track number instead of reciting a MediaStore id.
        if (subtitleName != null && subtitleName.matches("\\d+"))
            subtitleName = null;

        MediaItem.SubtitleConfiguration.Builder subtitleConfigurationBuilder = new MediaItem.SubtitleConfiguration.Builder(uri)
                // Becomes the track's Format.id, which is how a selected subtitle is remembered and
                // restored across a player rebuild. Without it every external subtitle carries a null id
                // and the restore matches the first text track instead of the chosen one.
                .setId(uri.toString())
                .setMimeType(subtitleMime)
                .setLanguage(subtitleLanguage)
                .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                .setLabel(subtitleName);
        if (selected) {
            subtitleConfigurationBuilder.setSelectionFlags(C.SELECTION_FLAG_DEFAULT);
        }
        return subtitleConfigurationBuilder.build();
    }

    public static float normalizeFontScale(float fontScale, boolean small) {
        // https://bbc.github.io/subtitle-guidelines/#Presentation-font-size
        float newScale;
        // ¯\_(ツ)_/¯
        if (fontScale > 1.01f) {
            if (fontScale >= 1.99f) {
                // 2.0
                newScale = (small ? 1.15f : 1.2f);
            } else {
                // 1.5
                newScale = (small ? 1.0f : 1.1f);
            }
        } else if (fontScale < 0.99f) {
            if (fontScale <= 0.26f) {
                // 0.25
                newScale = (small ? 0.65f : 0.8f);
            } else {
                // 0.5
                newScale = (small ? 0.75f : 0.9f);
            }
        } else {
            newScale = (small ? 0.85f : 1.0f);
        }
        return newScale;
    }
}

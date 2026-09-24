package com.brouken.player;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.documentfile.provider.NetworkDocumentFile;

import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;

import java.util.ArrayList;
import java.util.List;

/**
 * A folder on an SMB share, dressed as a {@link DocumentFile} so that the browser and the player
 * treat it as they treat any other folder — the same rows, the same sort, the same next-file lookup
 * and the same folder playlist, with nothing said about the network.
 */
final class SmbFiles {

    private static final int FILE_ATTRIBUTE_DIRECTORY = 0x10;

    private SmbFiles() {
    }

    /** The folder at this address. Nothing is asked of the network until it is listed. */
    static DocumentFile folder(final Context context, final Uri uri, final String name) {
        return new NetworkDocumentFile(null, uri, name, true, 0, 0, new Lister(context));
    }

    /**
     * The file at this address, named after its last path segment. Enough for the two things the
     * player asks of a played file: what it is called, and which folder it sits in.
     */
    static DocumentFile file(final Uri uri) {
        return new NetworkDocumentFile(null, uri, nameOf(uri), false, 0, 0, null);
    }

    /** The folder holding this address, or null at the root of a share. */
    static DocumentFile parent(final Context context, final Uri uri) {
        final java.util.List<String> segments = uri.getPathSegments();
        if (segments.size() < 2) {
            return null;
        }
        final Uri.Builder above = uri.buildUpon().path("");
        for (int i = 0; i < segments.size() - 1; i++) {
            above.appendPath(segments.get(i));
        }
        final Uri parent = above.build();
        return folder(context, parent, nameOf(parent));
    }

    private static String nameOf(final Uri uri) {
        final java.util.List<String> segments = uri.getPathSegments();
        return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
    }

    /** Lists over SMB. One instance is shared by a whole tree, so it holds only the context. */
    private static final class Lister implements NetworkDocumentFile.Children {

        private final Context context;

        Lister(final Context context) {
            this.context = context.getApplicationContext();
        }

        @NonNull
        @Override
        public DocumentFile[] of(@NonNull final NetworkDocumentFile folder) {
            if (folder.getUri().getPathSegments().isEmpty()) {
                return sharesOf(folder);
            }
            final List<FileIdBothDirectoryInformation> entries;
            try {
                entries = SmbSessions.list(context, folder.getUri());
            } catch (final Exception e) {
                // An empty folder and a folder that would not open look identical from here, so the
                // reason is left for the caller rather than only printed.
                e.printStackTrace();
                NetworkFiles.failed(e);
                return new DocumentFile[0];
            }
            NetworkFiles.listed();
            final List<DocumentFile> children = new ArrayList<>(entries.size());
            for (final FileIdBothDirectoryInformation entry : entries) {
                final String name = entry.getFileName();
                if (name == null || ".".equals(name) || "..".equals(name)) {
                    continue;
                }
                final boolean directory = (entry.getFileAttributes() & FILE_ATTRIBUTE_DIRECTORY) != 0;
                children.add(new NetworkDocumentFile(folder,
                        folder.getUri().buildUpon().appendPath(name).build(),
                        name, directory, directory ? 0 : entry.getEndOfFile(),
                        entry.getLastWriteTime() == null ? 0 : entry.getLastWriteTime().toEpochMillis(),
                        this));
            }
            return children.toArray(new DocumentFile[0]);
        }

        /**
         * At the root of a host there are no directories to list - there is a list of shares, which
         * is a different question asked over a different pipe. They come back as folders, so nothing
         * above here has to know that the first level of a network place is special.
         */
        @NonNull
        private DocumentFile[] sharesOf(@NonNull final NetworkDocumentFile host) {
            final String address = host.getUri().getHost();
            if (address == null) {
                return new DocumentFile[0];
            }
            final List<String> names;
            try {
                names = SmbShares.of(address, host.getUri().getPort(),
                        NetworkPlaces.user(context, address, ""),
                        NetworkPlaces.password(context, address, ""));
            } catch (final Exception e) {
                e.printStackTrace();
                NetworkFiles.failed(e);
                return new DocumentFile[0];
            }
            NetworkFiles.listed();
            final List<DocumentFile> shares = new ArrayList<>(names.size());
            for (final String name : names) {
                shares.add(new NetworkDocumentFile(host,
                        host.getUri().buildUpon().appendPath(name).build(),
                        name, true, 0, 0, this));
            }
            return shares.toArray(new DocumentFile[0]);
        }
    }
}

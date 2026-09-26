package androidx.documentfile.provider;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A {@link DocumentFile} whose contents live on another machine.
 *
 * <p>The browser's rows, sort, breadcrumbs and search — and the player's next-file lookup and
 * folder playlist — are all written against {@code DocumentFile}. A network folder that <em>is</em>
 * one inherits every bit of that unchanged, which is why this class exists rather than a parallel
 * model.
 *
 * <p>ponytail: declared in {@code androidx.documentfile.provider} because {@code DocumentFile}'s
 * constructor is package-private. The alternative is our own node interface and a dozen signature
 * changes across two activities and SubtitleUtils. Revisit only if androidx ever restricts the
 * package.
 *
 * <p>Read-only: nothing here creates, renames or deletes. A protocol that grows write support
 * overrides those four methods.
 */
public class NetworkDocumentFile extends DocumentFile {

    private static final DocumentFile[] NONE = new DocumentFile[0];

    /** Lists a folder's children, on the caller's thread. */
    public interface Children {
        @NonNull
        DocumentFile[] of(@NonNull NetworkDocumentFile folder);
    }

    private final Uri uri;
    private final String name;
    private final boolean directory;
    private final long length;
    private final long lastModified;
    private final Children children;

    public NetworkDocumentFile(@Nullable final DocumentFile parent, @NonNull final Uri uri,
                               @NonNull final String name, final boolean directory,
                               final long length, final long lastModified,
                               @Nullable final Children children) {
        super(parent);
        this.uri = uri;
        this.name = name;
        this.directory = directory;
        this.length = length;
        this.lastModified = lastModified;
        this.children = children;
    }

    @Override
    public Uri getUri() {
        return uri;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * Read off the name, because there is no provider to ask: a file on a share carries no type of
     * its own. The same answer {@code RawDocumentFile} gives for a plain file, so callers that ask a
     * DocumentFile what it holds get the same kind of answer wherever it lives - and a null here is
     * what kept network folders out of the player's folder playlist.
     */
    @Override
    public String getType() {
        if (directory) {
            return "vnd.android.document/directory";
        }
        final int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            final String extension = name.substring(dot + 1).toLowerCase(java.util.Locale.US);
            final String mime = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension);
            if (mime != null) {
                return mime;
            }
        }
        return "application/octet-stream";
    }

    @Override
    public boolean isDirectory() {
        return directory;
    }

    @Override
    public boolean isFile() {
        return !directory;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public long lastModified() {
        return lastModified;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public boolean canRead() {
        return true;
    }

    @Override
    public boolean canWrite() {
        return false;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public DocumentFile[] listFiles() {
        return children == null ? NONE : children.of(this);
    }

    @Override
    public DocumentFile createFile(@NonNull final String mimeType, @NonNull final String displayName) {
        return null;
    }

    @Override
    public DocumentFile createDirectory(@NonNull final String displayName) {
        return null;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public boolean renameTo(@NonNull final String displayName) {
        return false;
    }
}

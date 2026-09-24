package com.brouken.player;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;

/**
 * Typing an address into a player is the exception, so it lives behind a plain input dialog rather
 * than a surface of its own. Prefilled from the clipboard when that already holds a playable link —
 * the usual way one arrives here, and the only bearable one with a TV remote.
 *
 * <p>Asked from two places, which is why it is not a method on either of them: the shell's menu and
 * its start page, and the player's own gear menu while something is playing. Each does its own thing
 * with the address, so this validates it and hands it on.
 */
final class OpenLink {

    private OpenLink() {
    }

    interface Opener {
        void open(Uri uri);
    }

    static void ask(final Activity activity, final Opener opener) {
        final Context dialogContext = Dialogs.dialogContext(activity);
        final ViewGroup fields = Dialogs.dialogFields(dialogContext);
        final EditText input =
                Dialogs.textField(fields, activity.getString(R.string.field_url), "https://");
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        final Uri pasted = clipboardUri(activity);
        if (pasted != null) {
            input.setText(pasted.toString());
            input.setSelection(input.getText().length());
        }
        Dialogs.fields(activity, activity.getString(R.string.empty_state_link), fields,
                activity.getString(android.R.string.ok), () -> {
                    final Uri uri = Uri.parse(input.getText().toString().trim());
                    if (!Utils.isSupportedNetworkUri(uri)) {
                        Notice.show(activity, R.string.error_link_invalid, true, R.drawable.ic_link_off_24dp);
                        return;
                    }
                    opener.open(uri);
                });
    }

    private static Uri clipboardUri(final Context context) {
        final ClipboardManager cm =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = cm != null ? cm.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0) {
            return null;
        }
        final CharSequence text = clip.getItemAt(0).coerceToText(context);
        if (text == null) {
            return null;
        }
        final Uri uri = Uri.parse(text.toString().trim());
        return Utils.isSupportedNetworkUri(uri) ? uri : null;
    }
}

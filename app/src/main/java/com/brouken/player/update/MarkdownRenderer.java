package com.brouken.player.update;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Markdown-to-{@link Spanned} renderer for the update dialog's release notes. Handles only
 * the small subset that appears in GitHub release bodies — ATX headings, bullet lists, bold/italic,
 * inline code, {@code [text](url)} links and bare URLs — with no external dependency and nothing
 * newer than API 1, so it works on every supported device. Malformed markup (unclosed {@code **},
 * stray markers) is left as literal text rather than throwing.
 */
public final class MarkdownRenderer {

    private MarkdownRenderer() {}

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[*\\-+]\\s+(.*)$");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");
    private static final Pattern BOLD = Pattern.compile("(\\*\\*|__)(.+?)\\1");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])[*_](?!\\s)(.+?)(?<!\\s)[*_](?![*\\w])");
    private static final Pattern CODE = Pattern.compile("`([^`]+)`");

    /** Renders the given markdown into a styled, link-enabled {@link CharSequence}. */
    public static CharSequence render(final String markdown) {
        final SpannableStringBuilder out = new SpannableStringBuilder();
        if (markdown == null) {
            return out;
        }
        final String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean lastBlank = true; // suppress leading blank lines
        for (final String rawLine : lines) {
            final String line = rawLine;
            if (line.trim().isEmpty()) {
                if (!lastBlank) {
                    out.append("\n");
                    lastBlank = true;
                }
                continue;
            }
            if (out.length() > 0 && !endsWithNewline(out)) {
                out.append("\n");
            }
            lastBlank = false;

            final Matcher heading = HEADING.matcher(line);
            final Matcher bullet = BULLET.matcher(line);
            if (heading.matches()) {
                final int start = out.length();
                appendInline(out, heading.group(2).trim());
                out.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(1.15f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (bullet.matches()) {
                out.append("•  ");
                appendInline(out, bullet.group(1));
            } else {
                appendInline(out, line);
            }
        }

        // Trim a trailing newline left by a final blank line.
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }

        // Bare URLs (Markdown links already stripped their raw URL, so no overlap).
        Linkify.addLinks(out, Linkify.WEB_URLS);
        return out;
    }

    /** Appends {@code text} to {@code out}, applying inline links, bold, italic and code spans. */
    private static void appendInline(final SpannableStringBuilder out, final String text) {
        final int base = out.length();
        out.append(text);
        applyLinks(out, base);
        applyWrapped(out, base, BOLD, 2, new SpanFactory() {
            public Object create() { return new StyleSpan(android.graphics.Typeface.BOLD); }
        });
        applyWrapped(out, base, ITALIC, 1, new SpanFactory() {
            public Object create() { return new StyleSpan(android.graphics.Typeface.ITALIC); }
        });
        applyWrapped(out, base, CODE, 1, new SpanFactory() {
            public Object create() { return new TypefaceSpan("monospace"); }
        });
    }

    /** Replaces {@code [text](url)} occurrences (from {@code base} onward) with linked {@code text}. */
    private static void applyLinks(final SpannableStringBuilder out, final int base) {
        Matcher m = LINK.matcher(out.subSequence(base, out.length()));
        while (m.find()) {
            final int start = base + m.start();
            final int end = base + m.end();
            final String label = m.group(1);
            final String url = m.group(2);
            out.replace(start, end, label);
            out.setSpan(new URLSpan(url), start, start + label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            // Rebuild the matcher over the mutated tail.
            m = LINK.matcher(out.subSequence(base, out.length()));
        }
    }

    /**
     * Finds {@code pattern} matches (from {@code base} onward), strips the {@code markerLen}-char
     * delimiter on each side and spans the inner content. The last regex group is the inner text.
     */
    private static void applyWrapped(final SpannableStringBuilder out, final int base,
                                     final Pattern pattern, final int markerLen, final SpanFactory factory) {
        Matcher m = pattern.matcher(out.subSequence(base, out.length()));
        while (m.find()) {
            final int start = base + m.start();
            final int end = base + m.end();
            final String inner = m.group(m.groupCount());
            out.replace(start, end, inner);
            out.setSpan(factory.create(), start, start + inner.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            m = pattern.matcher(out.subSequence(base, out.length()));
        }
    }

    private static boolean endsWithNewline(final SpannableStringBuilder out) {
        return out.length() > 0 && out.charAt(out.length() - 1) == '\n';
    }

    private interface SpanFactory {
        Object create();
    }
}

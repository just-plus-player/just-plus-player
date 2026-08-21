package com.brouken.player;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.media3.common.text.Cue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Machine-translates a subtitle file into the wanted language, for the titles where nobody ever wrote
 * one. Ukrainian is the case this exists for: on old and on brand-new releases there is often no
 * Ukrainian track at all while a Russian one nearly always exists — and between two languages that
 * close a machine rendering reads naturally, which is not true of English into Ukrainian.
 *
 * <p>Several endpoints, tried in the order the viewer put them in and each switchable, because they are
 * strangers' free services and they come and go without notice. Within one afternoon of 2026-08-21 one
 * of them answered, then returned 502, then answered again; six hosts that were listed as working on
 * 2026-08-05 no longer resolve at all, while one written off that day turned out to be up. A list
 * editable from the settings screen is how none of that has to be a reason to cut a release.
 *
 * <p>They are not interchangeable, and the difference is the whole shape of this class. Google takes
 * <b>POST</b>, so the entire file goes in one request (measured: 5000 cues, 374 KB, under four seconds;
 * a typical film under one). Every other survivor is a Lingva/Mozhi-family proxy that puts the text in
 * the URL and answers 414 or 431 past a few kilobytes — with Cyrillic, where one character encodes to
 * six, that is about nine subtitle lines, so the same film costs roughly 134 sequential requests. Hence
 * {@link Backend#maxChars}, hence the batching, and hence keeping Google first.
 *
 * <p>Translating cue by cue as it is displayed — what PotPlayer's plugins do — is a thousand requests
 * per film either way, and stalls on every line of a weak mobile link.
 *
 * <p>Markup needs no handling here. The file is parsed by Media3, which has already turned
 * {@code <i>}, {@code {\an8}} and the rest into spans, so the plain text that gets sent carries none of
 * it. The only markup written back out is the italics around a kept source line.
 */
final class SubtitleTranslate {

    private SubtitleTranslate() {}

    /**
     * One keyless endpoint.
     *
     * <p>Two of these differ by a single character and the symptom of confusing them looks exactly like
     * a dead host: Mozhi and ProjectSegfau answer {@code translated-text}, SimplyTranslate
     * {@code translated_text}.
     */
    private static final class Backend {
        /** Stored in the setting, so it must outlive display-name changes. */
        final String id;
        final String label;
        /**
         * Format for a GET: source language, target language, encoded text. Null for the one endpoint
         * that takes POST, which is also the only one with no length ceiling.
         */
        final String get;
        /** JSON field holding the result; null for Google, whose answer is a nested array. */
        final String field;
        /** Encoded characters of text per request, ignored for POST. */
        final int maxChars;

        Backend(String id, String label, String get, String field, int maxChars) {
            this.id = id;
            this.label = label;
            this.get = get;
            this.field = field;
            this.maxChars = maxChars;
        }
    }

    /**
     * Encoded characters of text per request. SimplyTranslate answered a 2781-character URL and refused
     * a 4140-character one with 431 on 2026-08-21; this is the text alone, so the host and parameters
     * still have to fit in front of it — hence the margin rather than the measured edge.
     *
     * <p>A ceiling in characters and not in lines because that is what the hosts enforce, and the two
     * are not proportional: one Cyrillic character encodes to six, so nine Russian lines fill what
     * thirty English ones would.
     */
    private static final int GET_MAX_CHARS = 2400;

    /**
     * A sanity bound for the POST endpoint rather than a measured ceiling — no ceiling was found. An
     * anime season pack on a bad mobile link is better split than timed out.
     */
    private static final int POST_MAX_LINES = 1500;

    /**
     * The endpoints offered. Only the two that were verified answering on 2026-08-21, and both remain a
     * setting rather than a constant because that is what the day one of them dies is for.
     *
     * <p>Note that the fallback is not a second opinion: everything here reaches Google Translate, the
     * proxy by passing {@code engine=google}. It exists for the day the direct endpoint refuses, not to
     * translate better — so the order below is also the order of cost, one request against about a
     * hundred and thirty.
     *
     * <p>Mozhi is one project on many volunteer hosts, so each host is its own entry: they are
     * interchangeable — all five returned byte-identical output — and listing them separately is what
     * makes the redundancy visible and reorderable instead of hidden in here. All eight that answered on
     * 2026-08-21 returned byte-identical output.
     *
     * <p>Ordered by median latency over two rounds of five consecutive requests each — the only thing
     * measurable in one sitting, and a poor proxy for reliability: the same host moved between 0.95s and
     * 0.47s across the two rounds, so treat the order as roughly right and nothing finer. The two real
     * reliability signals both push down: {@code canine.tools} goes last of the Mozhi hosts as the only
     * live one with a recorded failure (dead on 2026-08-05).
     *
     * <p>Deliberately absent, all probed on 2026-08-21 unless noted:
     * <ul>
     *   <li>{@code engine=yandex}, which Mozhi also proxies keylessly: measured against 120 short
     *       command lines from a real film, it turned imperatives into infinitives in about 7% of them
     *       ("Пий" as "П'ючи", "Ідемо" as "Йти") where Google made no such error. Better on isolated
     *       interjections, far worse on the register subtitles are mostly made of.
     *   <li>{@code lingva.ml} — host up and {@code /languages} answers, the translate route 500s. The
     *       likeliest to come back, since the domain is still there.
     *   <li>{@code mozhi.aryak.me} 500, {@code translate.projectsegfau.lt} 500,
     *       {@code nyc1.mz.ggtyler.dev} timeout, and {@code mozhi.frontendfriendly.xyz},
     *       {@code mo.zorki.nl}, {@code translate.bus-hit.me}, {@code mozhi.pabloferreiro.es},
     *       {@code mozhi.itsdanie.li}, {@code translate.datenschutz.dev} — no longer resolve at all.
     *   <li>{@code api.mymemory.translated.net} — keyless and alive, but a translation memory rather
     *       than an engine: asked for one short line it returned the Russian source verbatim as a
     *       "match", which this code cannot tell from a real translation. Also 500 characters per
     *       request and a daily allowance of one film.
     *   <li>LibreTranslate — every public mirror dead or gone from DNS, the official instance key-gated,
     *       and Argos pivots non-English pairs through English, which is the double translation the
     *       whole ru→uk preference exists to avoid.
     *   <li>{@code translate.plausibility.cloud}, {@code lingva.garudalinux.org}, {@code lingva.lunar.icu},
     *       {@code lingva.thedaviddelta.com}, {@code translate.dr460nf1r3.org}, {@code mozhi.canine.tools},
     *       {@code translate.bus-hit.me} — dead since 2026-08-05. The Garuda one served a Cloudflare
     *       interstitial with HTTP <em>200</em>, which is what the first-byte check in {@link #request}
     *       is for: any host can start doing that.
     * </ul>
     */
    private static final Backend[] BACKENDS = {
            new Backend("google", "Google Translate", null, null, 0),
            mozhi("bloat", "bloat.cat", "mozhi.bloat.cat"),
            mozhi("catsarch", "catsarch.com", "mozhi.catsarch.com"),
            mozhi("ducks", "ducks.party", "mozhi.ducks.party"),
            mozhi("pussthecat", "pussthecat.org", "mozhi.pussthecat.org"),
            mozhi("adminforge", "adminforge.de", "mozhi.adminforge.de"),
            mozhi("privacyredirect", "privacyredirect.com", "translate.privacyredirect.com"),
            mozhi("canine", "canine.tools", "mozhi.canine.tools"),
            // Last of the Mozhi hosts, and not on latency — by that it would sit third. It is served
            // from Russia, and the language this whole feature exists to produce is Ukrainian; it is
            // here at the project owner's explicit decision, as late as keeping the Mozhi hosts in one
            // group allows. Do not re-sort this group by the measured timings.
            mozhi("dc09", "dc09.xyz", "mzh.dc09.xyz"),
            new Backend("simply", "SimplyTranslate",
                    "https://simplytranslate.org/api/translate/?engine=google&from=%s&to=%s&text=%s",
                    "translated_text", GET_MAX_CHARS),
    };

    /**
     * One Mozhi host. They differ only in the host name, and note the field: Mozhi answers
     * {@code translated-text} with a hyphen where SimplyTranslate uses an underscore. Getting that wrong
     * looks exactly like a dead host, which is why the two are built in different places.
     */
    private static Backend mozhi(String id, String label, String host) {
        return new Backend("mozhi-" + id, "Mozhi · " + label,
                "https://" + host + "/api/translate?engine=google&from=%s&to=%s&text=%s",
                "translated-text", GET_MAX_CHARS);
    }

    /** What a fresh install uses: everything that was answering when this was written, in that order. */
    static final String DEFAULT_BACKENDS = "google,mozhi-bloat,mozhi-catsarch,mozhi-ducks,"
            + "mozhi-pussthecat,mozhi-adminforge,mozhi-privacyredirect,mozhi-canine,mozhi-dc09,simply";

    /** Every endpoint by id, for the editor that picks and orders them. */
    static LinkedHashMap<String, String> backends() {
        final LinkedHashMap<String, String> all = new LinkedHashMap<>();
        for (final Backend backend : BACKENDS) {
            all.put(backend.id, backend.label);
        }
        return all;
    }

    /** The enabled endpoints, in the stored order. Unknown ids are ignored, so a removed one is not a crash. */
    private static List<Backend> chain(String enabled) {
        final List<Backend> chain = new ArrayList<>();
        for (final String id : Utils.splitLanguages(enabled)) {
            for (final Backend backend : BACKENDS) {
                if (backend.id.equals(id)) {
                    chain.add(backend);
                    break;
                }
            }
        }
        return chain;
    }

    /**
     * How many requests one file may cost across the whole chain. A runaway guard, not a rate policy —
     * the real count is the chunk count, which for a film on a GET endpoint is already over a hundred.
     */
    private static final int MAX_REQUESTS = 400;

    private static final int TIMEOUT_SEC = 30;

    /** The search's client, so this reuses its connection pool rather than opening one of its own. */
    private static final OkHttpClient CLIENT = SubtitleSearch.CLIENT.newBuilder()
            .readTimeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            .build();

    /**
     * Languages worth translating {@code target} from, best first. Not a setting: it follows from the
     * target, and asking a viewer to rank source languages is asking them to know which pairs a machine
     * handles well.
     *
     * <p>Ukrainian comes from Russian before English — the two are close enough that the rendering keeps
     * sentence case and register, where English into Ukrainian flattens both ("Hey." arrives as a
     * greeting, and half the lines lose their capital). Everything else goes straight to English, which
     * is what most films were written in and therefore what humans have actually subtitled.
     *
     * @param targetIso3 the wanted language, ISO 639-2/T
     * @return source languages, never containing the target itself
     */
    static List<String> sourcesFor(String targetIso3) {
        final List<String> sources = new ArrayList<>(2);
        if ("ukr".equals(targetIso3)) {
            sources.add("rus");
        }
        if (!"eng".equals(targetIso3)) {
            sources.add("eng");
        }
        return sources;
    }

    /**
     * Translates one subtitle file and writes the result as SRT.
     *
     * @param source   a local subtitle file, in any format Media3 can parse
     * @param fromIso3 language of {@code source}, ISO 639-2/T. Passed to the endpoints explicitly:
     *                 left to detect it themselves, they read a two-word cue as the wrong language
     * @param toIso3   language to translate into, ISO 639-2/T
     * @param target   file to write; deleted again unless the whole file came back
     * @param withOriginal keep the source line under the translated one, in italics. Whether a machine
     *                     rendering is any good is the one thing a viewer cannot otherwise check; with
     *                     the original in view a mangled line reads as a mangled line rather than as a
     *                     broken player
     * @param enabledBackends comma-separated endpoint ids, in the order to try them
     * @return {@code target} as a URI, or null when nothing usable arrived
     */
    static Uri translate(Context context, Uri source, String fromIso3, String toIso3, File target,
                         boolean withOriginal, String enabledBackends) {
        final String sl = twoLetter(fromIso3);
        final String tl = twoLetter(toIso3);
        final List<Backend> chain = chain(enabledBackends);
        if (sl == null || tl == null || chain.isEmpty()) {
            return null;
        }
        final SubtitleTimeline timeline =
                SubtitleTimeline.load(context, source, SubtitleUtils.getSubtitleMime(source));
        if (timeline == null) {
            return null;
        }

        // One request line per subtitle line rather than per cue: a two-speaker cue is one cue and two
        // lines, and flattening it loses the layout that makes it readable. linesPerBlock is what puts
        // the breaks back in the right places afterwards.
        final List<String> lines = new ArrayList<>();
        final int[] linesPerBlock = new int[timeline.size()];
        for (int block = 0; block < timeline.size(); block++) {
            final StringBuilder text = new StringBuilder();
            for (final Cue cue : timeline.cuesAt(block)) {
                if (cue.text == null) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(cue.text);
            }
            for (final String line : text.toString().split("\n")) {
                final String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                    linesPerBlock[block]++;
                }
            }
        }
        if (lines.isEmpty()) {
            return null;
        }

        // Lines with no letter in them are held back rather than sent: music notes and stage directions
        // come back changed, or do not come back at all — and a line that does not come back takes the
        // alignment of everything after it with it.
        final List<String> sendable = new ArrayList<>();
        for (final String line : lines) {
            if (hasLetter(line)) {
                sendable.add(line);
            }
        }
        final List<String> translated = sendable.isEmpty()
                ? Collections.<String>emptyList()
                : viaChain(sendable, sl, tl, chain);
        if (translated == null) {
            return null;
        }

        final List<String> merged = new ArrayList<>(lines.size());
        int next = 0;
        for (final String line : lines) {
            merged.add(hasLetter(line) ? translated.get(next++) : line);
        }
        return write(timeline, linesPerBlock, merged, withOriginal ? lines : null, target);
    }

    /**
     * Walks the chain until the whole list is translated. An endpoint that stops answering keeps what it
     * already produced and the next one carries on from there, rather than the file starting over: a
     * host that dies halfway through a film has already cost a hundred requests, and asking its
     * successor to repeat them is rude to a free service and slow for the viewer.
     *
     * @return every line translated, or null when the chain ran out with work left
     */
    private static List<String> viaChain(List<String> lines, String sl, String tl,
                                         List<Backend> chain) {
        final int[] budget = {MAX_REQUESTS};
        final List<String> done = new ArrayList<>(lines.size());
        for (final Backend backend : chain) {
            if (done.size() == lines.size()) {
                break;
            }
            try {
                viaBackend(backend, lines.subList(done.size(), lines.size()), sl, tl, budget, done);
            } catch (IOException e) {
                Utils.log("translate: " + backend.id + " gave up after " + done.size()
                        + " of " + lines.size() + " line(s) — " + e);
            }
        }
        if (done.size() != lines.size()) {
            return null;
        }
        Utils.log("translate: " + sl + " to " + tl + ", " + done.size() + " line(s)");
        return done;
    }

    /**
     * One endpoint, batched to whatever it will accept, appending each finished chunk to {@code into} so
     * the work survives the endpoint failing partway.
     */
    private static void viaBackend(Backend backend, List<String> lines, String sl, String tl,
                                   int[] budget, List<String> into) throws IOException {
        int from = 0;
        while (from < lines.size()) {
            final int to = chunkEnd(backend, lines, from);
            into.addAll(aligned(backend, lines.subList(from, to), sl, tl, budget));
            from = to;
        }
    }

    /** Where this endpoint's next request has to stop: a line count for POST, an encoded length for GET. */
    private static int chunkEnd(Backend backend, List<String> lines, int from) {
        if (backend.get == null) {
            return Math.min(lines.size(), from + POST_MAX_LINES);
        }
        int size = 0;
        for (int i = from; i < lines.size(); i++) {
            // +3 for the encoded newline that joins it to the previous line.
            final int length = Uri.encode(lines.get(i)).length() + 3;
            if (i > from && size + length > backend.maxChars) {
                return i;
            }
            size += length;
        }
        return lines.size();
    }

    /**
     * One chunk, and the two halves separately when what came back does not line up with what went out.
     * Splitting is the recovery for a mismatch, not the normal path: without it one miscounted line
     * shifts every subtitle after it by one, which on screen reads as a working translation that goes
     * wrong halfway through the film.
     *
     * @return a list exactly as long as {@code lines}, untranslated where the endpoint would not agree
     * @throws IOException the endpoint is not answering. There is nothing to recover from then, and
     *                     halving the request over and over would only ask it again
     */
    private static List<String> aligned(Backend backend, List<String> lines, String sl, String tl,
                                        int[] budget) throws IOException {
        final List<String> out = request(backend, lines, sl, tl, budget);
        if (out.size() == lines.size()) {
            return out;
        }
        if (lines.size() == 1) {
            return new ArrayList<>(lines); // the original line beats a hole in the subtitle
        }
        Utils.log("translate: " + backend.id + " sent " + lines.size() + ", got " + out.size()
                + ", splitting");
        final int half = lines.size() / 2;
        final List<String> result = new ArrayList<>(lines.size());
        result.addAll(aligned(backend, lines.subList(0, half), sl, tl, budget));
        result.addAll(aligned(backend, lines.subList(half, lines.size()), sl, tl, budget));
        return result;
    }

    /** One request. The lines come back newline-separated, in order, each with a trailing carriage return. */
    private static List<String> request(Backend backend, List<String> lines, String sl, String tl,
                                        int[] budget) throws IOException {
        if (budget[0]-- <= 0) {
            throw new IOException("too many requests for one file");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("cancelled");
        }
        final String joined = TextUtils.join("\n", lines);
        final Request.Builder builder = new Request.Builder();
        if (backend.get == null) {
            // POST rather than GET: as a query string this endpoint stops at about 250 cues with a 400,
            // and working around that is the batching the other endpoints have to do.
            builder.url("https://translate.googleapis.com/translate_a/single"
                            + "?client=gtx&dt=t&sl=" + sl + "&tl=" + tl)
                    .post(new FormBody.Builder().add("q", joined).build());
        } else {
            // Uri.encode and not okhttp's own: Lingva takes the text as a path segment, so the newlines
            // and any slash in the dialogue have to be escaped rather than left to divide the path.
            builder.url(String.format(Locale.US, backend.get, sl, tl, Uri.encode(joined)));
        }
        final String body;
        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            final ResponseBody payload = response.body();
            if (!response.isSuccessful() || payload == null) {
                throw new IOException("HTTP " + response.code());
            }
            body = payload.string();
        }
        // A host that has started serving an interstitial answers 200 with HTML; parsing that throws,
        // and an unguarded throw here would end the whole chain instead of moving to the next endpoint.
        final String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            throw new IOException("not JSON: " + trimmed.substring(0, Math.min(40, trimmed.length())));
        }
        final String text;
        try {
            text = backend.field == null ? fromGoogle(trimmed) : new JSONObject(trimmed)
                    .optString(backend.field, "");
        } catch (Exception e) {
            throw new IOException("unparseable answer: " + e);
        }
        // Empty is a failure, not a translation of nothing: taken as success, every cue would vanish.
        if (text.isEmpty()) {
            throw new IOException("empty answer");
        }
        final List<String> out = new ArrayList<>(lines.size());
        for (final String line : text.split("\n")) {
            out.add(line.trim()); // also drops the carriage return appended to every line
        }
        return out;
    }

    /**
     * Google answers with segments of its own choosing rather than one per line. The newlines that were
     * sent survive inside them, so concatenating rebuilds the line split — where one segment ends is the
     * endpoint's own business.
     */
    private static String fromGoogle(String body) throws Exception {
        final JSONArray segments = new JSONArray(body).getJSONArray(0);
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < segments.length(); i++) {
            text.append(segments.getJSONArray(i).optString(0));
        }
        return text.toString();
    }

    /**
     * Rebuilds the file with the original timings — the one thing never touched.
     *
     * @param originals the source lines to keep under the translated ones, or null for translation only
     */
    private static Uri write(SubtitleTimeline timeline, int[] linesPerBlock, List<String> translated,
                            List<String> originals, File target) {
        int number = 0;
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(target),
                StandardCharsets.UTF_8)) {
            int line = 0;
            for (int block = 0; block < timeline.size(); block++) {
                final int count = linesPerBlock[block];
                if (count == 0) {
                    continue;
                }
                final StringBuilder text = new StringBuilder();
                for (int i = 0; i < count; i++) {
                    if (i > 0) {
                        text.append('\n');
                    }
                    text.append(translated.get(line + i));
                }
                // Whole block under whole block, not line by line: a two-speaker cue interleaved with
                // its own source is unreadable, while translation above original keeps both readable.
                if (originals != null) {
                    for (int i = 0; i < count; i++) {
                        text.append("\n<i>").append(originals.get(line + i)).append("</i>");
                    }
                }
                line += count;
                writer.write(++number + "\n"
                        + timestamp(timeline.startUs(block)) + " --> " + timestamp(timeline.endUs(block))
                        + "\n" + text + "\n\n");
            }
        } catch (Exception e) {
            Utils.log("translate: " + e);
            number = 0;
        }
        if (number == 0) {
            target.delete();
            return null;
        }
        return Uri.fromFile(target);
    }

    private static String timestamp(long timeUs) {
        final long ms = Math.max(timeUs, 0) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d,%03d",
                ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000);
    }

    /** A line of punctuation or music notes has nothing to translate. */
    private static boolean hasLetter(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (Character.isLetter(line.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** The endpoints speak ISO 639-1; the priority lists are written in ISO 639-2/T. */
    private static String twoLetter(String iso3) {
        final List<String> two = OpenSubtitles.toIso639_1(Collections.singletonList(iso3));
        return two.isEmpty() ? null : two.get(0);
    }
}

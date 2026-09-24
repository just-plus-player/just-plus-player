package com.brouken.player;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds the torrent servers on this network.
 *
 * <p>Two ways of asking, at once, because the first one is off by default.
 *
 * <ol>
 *   <li><b>mDNS.</b> {@code _torrserver._tcp} through the framework's own {@link NsdManager} — the
 *       same rung {@link SmbDiscovery} uses for {@code _smb._tcp}: no dependency, no permission, and
 *       it answers with a name and a port. This is what TorrServer's {@code enableBonjour} setting
 *       announces.
 *   <li><b>The sweep.</b> A {@code GET /echo} on port 8090 across the local subnet. Crude, and the
 *       one that finds a server nobody has turned Bonjour on for — which is every server out of the
 *       box, since the setting ships off.
 * </ol>
 *
 * <p>Results arrive as they are found rather than at the end. Nothing is remembered: a found server
 * is a row until someone saves it.
 */
final class TorrDiscovery {

    /** A server that answered, by either route. */
    static final class Server {
        final String host;
        final int port;
        /** What it calls itself, or its version, or the address again when neither was had. */
        final String name;
        /** True only for a server announced over https; the sweep only ever knocks on plain http. */
        final boolean secure;

        Server(final String host, final int port, @Nullable final String name,
               final boolean secure) {
            this.host = host;
            this.port = port;
            this.name = name == null || name.isEmpty() ? host : name;
            this.secure = secure;
        }

        /** {@code host:port}, which is what a row shows under the name. */
        String address() {
            return host + ":" + port;
        }
    }

    interface Listener {
        /** On the main thread, once per server. */
        void onServer(Server server);

        /** On the main thread, when both rungs have finished or the deadline passed. */
        void onFinished();
    }

    /** The type TorrServer announces itself under when Bonjour is on. */
    private static final String SERVICE = "_torrserver._tcp";
    /** As long as the other two searches, so the one list they share settles at one moment. */
    private static final long DEADLINE_MS = 6000;
    private static final int SWEEP_THREADS = 32;
    private static final int KNOCK_TIMEOUT_MS = 400;

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService sweepers = Executors.newFixedThreadPool(SWEEP_THREADS);

    @Nullable
    private NsdManager nsd;
    @Nullable
    private NsdManager.DiscoveryListener nsdListener;

    TorrDiscovery(final Context context, final Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        mdns();
        new Thread(this::sweep, "torr-sweep").start();
        main.postDelayed(this::stop, DEADLINE_MS);
    }

    void stop() {
        if (stopped.getAndSet(true)) {
            return;
        }
        main.removeCallbacksAndMessages(null);
        if (nsd != null && nsdListener != null) {
            try {
                nsd.stopServiceDiscovery(nsdListener);
            } catch (final Exception ignored) {
                // Already stopped, or never started.
            }
        }
        sweepers.shutdownNow();
        main.post(listener::onFinished);
    }

    private void found(final String host, final int port, @Nullable final String name,
                       final boolean secure) {
        if (stopped.get() || host == null || !seen.add(host + ":" + port)) {
            return;
        }
        final Server server = new Server(host, port, name, secure);
        main.post(() -> {
            if (!stopped.get()) {
                listener.onServer(server);
            }
        });
    }

    // ---- rung 1: mDNS ----

    private void mdns() {
        nsd = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            return;
        }
        nsdListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(final String type) {
            }

            @Override
            public void onServiceFound(final NsdServiceInfo info) {
                resolve(info);
            }

            @Override
            public void onServiceLost(final NsdServiceInfo info) {
            }

            @Override
            public void onDiscoveryStopped(final String type) {
            }

            @Override
            public void onStartDiscoveryFailed(final String type, final int code) {
            }

            @Override
            public void onStopDiscoveryFailed(final String type, final int code) {
            }
        };
        try {
            nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, nsdListener);
        } catch (final Exception ignored) {
            // No mDNS on this device or this network. The sweep is still going.
        }
    }

    private void resolve(final NsdServiceInfo info) {
        final NsdManager manager = nsd;
        if (manager == null) {
            return;
        }
        try {
            manager.resolveService(info, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(final NsdServiceInfo failed, final int code) {
                }

                @Override
                public void onServiceResolved(final NsdServiceInfo resolved) {
                    final InetAddress host = resolved.getHost();
                    if (host == null) {
                        return;
                    }
                    final int port = resolved.getPort() > 0
                            ? resolved.getPort() : TorrFiles.DEFAULT_PORT;
                    // The service name is what the server was told to call itself, which is the
                    // whole reason this rung is worth having.
                    found(host.getHostAddress(), port, resolved.getServiceName(), false);
                }
            });
        } catch (final Exception ignored) {
            // resolveService throws when one is already in flight on older versions.
        }
    }

    // ---- rung 2: the sweep ----

    /**
     * ponytail: plain http on the default port only, and that is a real limit rather than a corner
     * cut small. A server on a port somebody chose is not findable by knocking — measured on the
     * owner's own network, where two of them sit on 9118 and 9119 and no list of likely ports would
     * have held either. Widening this does not fix that: a port range across a subnet is thousands
     * of connects, tens of seconds, and still a guess.
     *
     * <p>The mechanism for a moved port is the rung above — a server with {@code enableBonjour} on
     * announces whatever port it is actually on — and for a server with it off, the address form.
     * So this rung is for the default install, and nothing here pretends otherwise.
     */
    private void sweep() {
        for (final int[] range : SmbDiscovery.subnets()) {
            final int network = range[0];
            for (int host = range[1]; host <= range[2]; host++) {
                if (stopped.get()) {
                    return;
                }
                final int address = network | host;
                sweepers.execute(() -> knock(address));
            }
        }
    }

    private void knock(final int address) {
        if (stopped.get()) {
            return;
        }
        final String dotted = ((address >> 24) & 0xFF) + "." + ((address >> 16) & 0xFF) + "."
                + ((address >> 8) & 0xFF) + "." + (address & 0xFF);
        if (seen.contains(dotted + ":" + TorrFiles.DEFAULT_PORT)) {
            return;
        }
        // The connect first, and only then the request: most of a subnet has nothing on this port,
        // and a refused connect costs a millisecond where an HTTP call costs its whole timeout.
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(dotted, TorrFiles.DEFAULT_PORT),
                    KNOCK_TIMEOUT_MS);
        } catch (final Exception nothingThere) {
            return;
        }
        if (TorrFiles.answers("http://" + dotted + ":" + TorrFiles.DEFAULT_PORT)) {
            // Nothing here knows a name, so the version /echo answered with is the caption. It is
            // what the server's own page shows in the same place.
            found(dotted, TorrFiles.DEFAULT_PORT, null, false);
        }
    }
}

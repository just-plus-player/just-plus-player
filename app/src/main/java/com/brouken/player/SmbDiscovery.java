package com.brouken.player;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Finds the machines on this network that serve files.
 *
 * <p>Three ways of asking, at once, because no single one is reliable. The reference player asks
 * only the oldest — jcifs browsing workgroups over a NetBIOS broadcast — and on a network of
 * current Windows machines that frequently answers nothing while the shares themselves open fine.
 *
 * <ol>
 *   <li><b>mDNS.</b> {@code _smb._tcp} through the framework's own {@link NsdManager}: no
 *       dependency, no permission, and it answers with a <em>name</em>. Every NAS worth the word
 *       announces itself this way — measured on the owner's network, where the Synology is the only
 *       thing that replies.
 *   <li><b>NetBIOS.</b> A node status request broadcast to UDP 137. What the reference does, without
 *       jcifs: routers and Windows boxes that still run NetBIOS answer with their machine name.
 *   <li><b>The sweep.</b> A connect to port 445 on every address of the local subnet. Crude, and the
 *       one that always works — 254 addresses in a couple of seconds, measured.
 * </ol>
 *
 * <p>Results arrive as they are found rather than at the end, so the list fills in while the slower
 * rungs are still going. Nothing is remembered: a found host is a row until someone saves it.
 */
final class SmbDiscovery {

    /** A machine that answered, by whichever route. */
    static final class Host {
        final String address;
        /** What it calls itself, or the address again when nothing said. */
        final String name;

        Host(final String address, final String name) {
            this.address = address;
            this.name = name == null || name.isEmpty() ? address : name;
        }
    }

    interface Listener {
        /** On the main thread, once per address, first name to arrive wins. */
        void onHost(Host host);

        /** On the main thread, when every rung has finished or the deadline passed. */
        void onFinished();
    }

    private static final int SMB_PORT = 445;
    private static final int NETBIOS_PORT = 137;
    /** Long enough for a slow NAS to answer mDNS, short enough not to look stuck. */
    private static final long DEADLINE_MS = 6000;
    private static final int SWEEP_THREADS = 32;
    private static final int SWEEP_TIMEOUT_MS = 400;

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

    SmbDiscovery(final Context context, final Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    void start() {
        mdns();
        new Thread(this::netbios, "smb-netbios").start();
        new Thread(this::sweep, "smb-sweep").start();
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

    private void found(final String address, @Nullable final String name) {
        if (stopped.get() || address == null || !seen.add(address)) {
            return;
        }
        final Host host = new Host(address, name);
        main.post(() -> {
            if (!stopped.get()) {
                listener.onHost(host);
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
            nsd.discoverServices("_smb._tcp", NsdManager.PROTOCOL_DNS_SD, nsdListener);
        } catch (final Exception ignored) {
            // No mDNS on this device or this network. Two rungs left.
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
                    if (host != null) {
                        // The service name is the machine's own label — "LevNas" rather than an
                        // address — which is the whole reason this rung is worth having.
                        found(host.getHostAddress(), resolved.getServiceName());
                    }
                }
            });
        } catch (final Exception ignored) {
            // resolveService throws when one is already in flight on older versions.
        }
    }

    // ---- rung 2: NetBIOS node status ----

    /**
     * The wildcard node status query the reference player's jcifs sends, written out by hand: 50
     * bytes to the broadcast address, and every machine still speaking NetBIOS answers with its
     * name table. The first entry of type 0x00 that is not a group name is the machine name.
     */
    private void netbios() {
        final byte[] request = new byte[50];
        request[0] = 0x52;              // transaction id, any
        request[1] = 0x53;
        request[2] = 0x00;              // flags: a query
        request[3] = 0x00;
        request[5] = 0x01;              // one question
        request[12] = 0x20;             // the encoded name that follows is 32 bytes
        // "*" padded with spaces, in the half-ASCII encoding NetBIOS uses: each byte becomes two
        // characters, 'A' plus the high nibble and 'A' plus the low.
        request[13] = 0x43;             // '*' is 0x2A, so 'A'+2 and 'A'+10 -> 'C','K'
        request[14] = 0x4B;
        for (int i = 15; i < 45; i++) {
            request[i] = 0x41;          // and fifteen NULs, each 'A','A'
        }
        request[45] = 0x00;             // end of the name
        request[47] = 0x21;             // question type NBSTAT
        request[49] = 0x01;             // question class IN

        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(700);
            for (final InetAddress broadcast : broadcasts()) {
                if (stopped.get()) {
                    return;
                }
                try {
                    socket.send(new DatagramPacket(request, request.length, broadcast, NETBIOS_PORT));
                } catch (final IOException ignored) {
                    // One interface refusing a broadcast says nothing about the others.
                }
            }
            final byte[] buffer = new byte[512];
            final long until = System.currentTimeMillis() + DEADLINE_MS;
            while (!stopped.get() && System.currentTimeMillis() < until) {
                final DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(reply);
                } catch (final IOException timeout) {
                    continue;
                }
                found(reply.getAddress().getHostAddress(), nameFrom(reply));
            }
        } catch (final Exception ignored) {
            // No socket, no rung. The others carry on.
        }
    }

    /** The machine name out of a node status reply, or null when it holds none. */
    @Nullable
    private static String nameFrom(final DatagramPacket reply) {
        final byte[] data = reply.getData();
        // Header (12) + the echoed question (34 + 4) + the resource record's own preamble.
        int at = 56;
        if (reply.getLength() <= at) {
            return null;
        }
        final int count = data[at] & 0xFF;
        at++;
        for (int i = 0; i < count && at + 17 <= reply.getLength(); i++, at += 18) {
            final int type = data[at + 15] & 0xFF;
            final boolean group = (data[at + 16] & 0x80) != 0;
            if (type != 0x00 || group) {
                continue;
            }
            final String name = new String(data, at, 15).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return null;
    }

    private List<InetAddress> broadcasts() {
        final List<InetAddress> found = new ArrayList<>();
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                final NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (final InterfaceAddress address : network.getInterfaceAddresses()) {
                    if (address.getBroadcast() != null) {
                        found.add(address.getBroadcast());
                    }
                }
            }
        } catch (final Exception ignored) {
            // Nothing to enumerate. The sweep needs the same answer and will find none either.
        }
        if (found.isEmpty()) {
            try {
                found.add(InetAddress.getByName("255.255.255.255"));
            } catch (final Exception ignored) {
                // Then there is no broadcast to send to at all.
            }
        }
        return found;
    }

    // ---- rung 3: the sweep ----

    private void sweep() {
        for (final int[] range : subnets()) {
            final int network = range[0];
            final int first = range[1];
            final int last = range[2];
            for (int host = first; host <= last; host++) {
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
        if (seen.contains(dotted)) {
            return;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(dotted, SMB_PORT), SWEEP_TIMEOUT_MS);
            found(dotted, null);
        } catch (final Exception ignored) {
            // Nothing listening, which is the answer for most of a subnet.
        }
    }

    /**
     * The local subnets worth sweeping, as {network, firstHost, lastHost}. Only /24 and smaller: a
     * /16 is 65 000 connects and nobody's home network is one.
     *
     * <p>Shared with {@link TorrDiscovery}, which sweeps the same addresses for a different port:
     * which subnets a phone may knock on is a question about the phone, not about the protocol.
     */
    static List<int[]> subnets() {
        final List<int[]> ranges = new ArrayList<>();
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                final NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (final InterfaceAddress address : network.getInterfaceAddresses()) {
                    if (!(address.getAddress() instanceof Inet4Address)) {
                        continue;
                    }
                    // A private address on a small subnet, and nothing else. That is what keeps the
                    // sweep off somebody else's network without asking for a permission to read the
                    // Wi-Fi state: mobile data hands out a carrier address, not 192.168 or 10.
                    final int prefix = address.getNetworkPrefixLength();
                    if (prefix < 24 || prefix > 30 || !address.getAddress().isSiteLocalAddress()) {
                        continue;
                    }
                    final byte[] octets = address.getAddress().getAddress();
                    final int own = ((octets[0] & 0xFF) << 24) | ((octets[1] & 0xFF) << 16)
                            | ((octets[2] & 0xFF) << 8) | (octets[3] & 0xFF);
                    final int mask = prefix == 32 ? -1 : ~((1 << (32 - prefix)) - 1);
                    ranges.add(new int[] { own & mask, 1, (~mask & 0xFFFFFFFF) - 1 });
                }
            }
        } catch (final Exception ignored) {
            // No interfaces to read, so nothing to sweep.
        }
        return ranges;
    }

    /** Waits for the pool to drain, for a caller that wants to know the sweep is over. */
    void awaitSweep() {
        try {
            sweepers.awaitTermination(DEADLINE_MS, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}

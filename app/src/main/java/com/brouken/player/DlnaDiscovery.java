package com.brouken.player;

import android.os.Handler;
import android.os.Looper;
import android.util.Xml;

import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Finds the media servers on this network.
 *
 * <p>UPnP's own search: an {@code M-SEARCH} to the multicast group {@code 239.255.255.250:1900},
 * answered by every media server on the network with the address of its device description. The
 * description is then read for two things — what the server calls itself, and where its
 * ContentDirectory takes a {@code Browse} — which is everything a saved place needs.
 *
 * <p>The replies are unicast back to the socket that asked, so nothing here listens on a multicast
 * group and no multicast permission is needed.
 *
 * <p><b>One socket per interface.</b> A multicast datagram from an unbound socket leaves by whatever
 * the routing table calls the default way out, which on a machine with a VPN or a virtual adapter is
 * not the network the server is on: measured on the developer's own machine, where an unbound search
 * found nothing at all and the same search bound to the Wi-Fi address found two devices at once. A
 * phone with a VPN up is the same situation.
 */
final class DlnaDiscovery {

    /** A media server that answered. */
    static final class Server {
        /** What it calls itself. */
        final String name;
        final String address;
        /** Where a {@code Browse} goes. */
        final String control;
        /**
         * What it calls itself when nobody is looking: the UPnP device name, {@code uuid:...}, which
         * is the same after a restart when the address is not. Empty for a device that omitted it.
         */
        final String udn;

        Server(final String name, final String address, final String control, final String udn) {
            this.name = name == null || name.isEmpty() ? address : name;
            this.address = address;
            this.control = control;
            this.udn = udn == null ? "" : udn.trim();
        }
    }

    interface Listener {
        /** On the main thread, once per server. */
        void onServer(Server server);

        /** On the main thread, when the deadline has passed. */
        void onFinished();
    }

    private static final String GROUP = "239.255.255.250";
    private static final int SSDP_PORT = 1900;
    private static final String TARGET = "urn:schemas-upnp-org:device:MediaServer:1";
    /** As long as the search the shares use, so the one list they share settles at one moment. */
    private static final long DEADLINE_MS = 6000;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());

    DlnaDiscovery(final Listener listener) {
        this.listener = listener;
    }

    void start() {
        final List<InetAddress> local = addresses();
        if (local.isEmpty()) {
            // No network to ask on. Said at once rather than after the deadline.
            main.post(this::stop);
            return;
        }
        for (final InetAddress address : local) {
            new Thread(() -> search(address), "dlna-ssdp").start();
        }
        main.postDelayed(this::stop, DEADLINE_MS);
    }

    void stop() {
        if (stopped.getAndSet(true)) {
            return;
        }
        main.removeCallbacksAndMessages(null);
        main.post(listener::onFinished);
    }

    /** The search itself, which both the listing search and the look for one known device send. */
    private static byte[] request() {
        return ("M-SEARCH * HTTP/1.1\r\n"
                + "HOST: " + GROUP + ":" + SSDP_PORT + "\r\n"
                + "MAN: \"ssdp:discover\"\r\n"
                // Seconds a server may wait before answering, so that a hundred of them do not
                // answer at once. Two, because this search is in front of somebody waiting.
                + "MX: 2\r\n"
                + "ST: " + TARGET + "\r\n\r\n").getBytes();
    }

    /**
     * The one server that announced this device name, or null when nothing answered for it before the
     * deadline. Runs on the thread that asks - it is called from a listing, which is already off the
     * main thread - and gives up on the first match rather than waiting the whole deadline out.
     *
     * <p>The USN of a reply names the device without anything being fetched, so every server that is
     * not the one being looked for costs one string comparison.
     */
    @Nullable
    static Server find(@Nullable final String udn, final long withinMs) {
        if (udn == null || udn.isEmpty()) {
            return null;
        }
        final long until = System.currentTimeMillis() + withinMs;
        final Set<String> asked = new HashSet<>();
        for (final InetAddress from : addresses()) {
            final Server found = lookFrom(from, udn, until, asked);
            if (found != null || System.currentTimeMillis() >= until) {
                return found;
            }
        }
        return null;
    }

    @Nullable
    private static Server lookFrom(final InetAddress from, final String udn, final long until,
                                   final Set<String> asked) {
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(from, 0))) {
            socket.setSoTimeout(500);
            final InetAddress group = InetAddress.getByName(GROUP);
            final byte[] request = request();
            for (int i = 0; i < 2; i++) {
                try {
                    socket.send(new DatagramPacket(request, request.length, group, SSDP_PORT));
                } catch (final Exception ignored) {
                    // One interface refusing the group says nothing about the others.
                }
            }
            final byte[] buffer = new byte[4096];
            while (System.currentTimeMillis() < until) {
                final DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(reply);
                } catch (final Exception timeout) {
                    continue;
                }
                final String text = new String(reply.getData(), 0, reply.getLength());
                final String usn = header(text, "usn");
                if (usn != null && !usn.contains(udn)) {
                    continue;
                }
                final String location = header(text, "location");
                if (location == null || location.isEmpty() || !asked.add(location)) {
                    continue;
                }
                final Server server = describing(location);
                if (server != null && udn.equals(server.udn)) {
                    return server;
                }
            }
        } catch (final Exception ignored) {
            // No socket on this interface, no search on it.
        }
        return null;
    }

    private void search(final InetAddress from) {
        final byte[] request = request();
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(from, 0))) {
            socket.setSoTimeout(700);
            final InetAddress group = InetAddress.getByName(GROUP);
            // Three times: this is UDP to a multicast group, and a lost search is a server missing
            // from the list rather than an error anybody sees.
            for (int i = 0; i < 3 && !stopped.get(); i++) {
                try {
                    socket.send(new DatagramPacket(request, request.length, group, SSDP_PORT));
                } catch (final Exception ignored) {
                    // One interface refusing the group says nothing about the others.
                }
                Thread.sleep(200);
            }
            final byte[] buffer = new byte[4096];
            final long until = System.currentTimeMillis() + DEADLINE_MS;
            while (!stopped.get() && System.currentTimeMillis() < until) {
                final DatagramPacket reply = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(reply);
                } catch (final Exception timeout) {
                    continue;
                }
                describe(header(new String(reply.getData(), 0, reply.getLength()), "location"));
            }
        } catch (final Exception ignored) {
            // No socket on this interface, no search on it.
        }
    }

    /** A header of an SSDP reply, which is HTTP's own format with no body. */
    @Nullable
    private static String header(final String reply, final String name) {
        for (final String line : reply.split("\r\n")) {
            final int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().toLowerCase(Locale.US).equals(name)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    /**
     * Reads a device description for the name and the control address. Every server on the network
     * answers the search once per interface it heard it on, so a description is fetched once.
     */
    private void describe(@Nullable final String location) {
        if (location == null || location.isEmpty() || stopped.get() || !seen.add(location)) {
            return;
        }
        final Server server = describing(location);
        if (server == null) {
            return;
        }
        main.post(() -> {
            if (!stopped.get()) {
                listener.onServer(server);
            }
        });
    }

    /** Fetches a device description and reads a server out of it, or null for anything else. */
    @Nullable
    private static Server describing(final String location) {
        try {
            final Request request = new Request.Builder().url(location).build();
            try (Response response = PlayerActivity.MEDIA_HTTP_CLIENT.newCall(request).execute()) {
                final ResponseBody body = response.body();
                if (!response.isSuccessful() || body == null) {
                    return null;
                }
                return found(location, body.string());
            }
        } catch (final Exception e) {
            // A device that announces itself and will not describe itself is not a row.
            e.printStackTrace();
            return null;
        }
    }

    @Nullable
    private static Server found(final String location, final String description) throws Exception {
        String name = null;
        String base = null;
        String control = null;
        String udn = null;
        boolean contentDirectory = false;
        final XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new java.io.StringReader(description));
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                event = parser.next()) {
            if (event != XmlPullParser.START_TAG) {
                continue;
            }
            final String tag = parser.getName() == null ? "" : parser.getName().toLowerCase(Locale.US);
            if ("udn".equals(tag) && control == null) {
                // Kept until the service is found, for the same reason the name is: on a server whose
                // ContentDirectory sits in an embedded device it is that device that is being saved.
                udn = parser.nextText();
            } else if ("friendlyname".equals(tag) && control == null) {
                // Kept until the service is found, so that on a server whose ContentDirectory sits in
                // an embedded device it is that device's name rather than the box's.
                name = parser.nextText();
            } else if ("urlbase".equals(tag)) {
                // UPnP 1.0's way of saying where relative addresses in here begin. Deprecated since,
                // and still written by devices in use.
                base = parser.nextText();
            } else if ("servicetype".equals(tag)) {
                contentDirectory = parser.nextText().contains("ContentDirectory");
            } else if ("controlurl".equals(tag) && contentDirectory) {
                control = parser.nextText();
                contentDirectory = false;
            }
        }
        if (control == null) {
            // Something else on the network that answered a media server's search.
            return null;
        }
        final HttpUrl against = HttpUrl.parse(base != null && !base.isEmpty() ? base : location);
        final HttpUrl resolved = against == null ? null : against.resolve(control.trim());
        if (resolved == null) {
            return null;
        }
        return new Server(name, resolved.host(), resolved.toString(), udn);
    }

    /** Every address this device has on a network of its own, one search each. */
    private static List<InetAddress> addresses() {
        final List<InetAddress> found = new ArrayList<>();
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                final NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) {
                    continue;
                }
                for (final InterfaceAddress address : network.getInterfaceAddresses()) {
                    if (address.getAddress() instanceof Inet4Address
                            && address.getAddress().isSiteLocalAddress()) {
                        found.add(address.getAddress());
                    }
                }
            }
        } catch (final Exception ignored) {
            // Nothing to enumerate, so nothing to search from.
        }
        return found;
    }
}

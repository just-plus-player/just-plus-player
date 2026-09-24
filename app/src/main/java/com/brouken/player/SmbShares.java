package com.brouken.player;

import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.rapid7.client.dcerpc.mssrvs.ServerService;
import com.rapid7.client.dcerpc.mssrvs.dto.NetShareInfo1;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Asks a host which folders it offers.
 *
 * <p>{@code NetShareEnumAll} over the {@code IPC$} named pipe, which smbj gives us the pipe for and
 * nothing above: the DCERPC layer is what the extra dependency is for. Without it a host found on
 * the network is an address with the folder name left to be guessed, and guessing found six of this
 * NAS's eleven shares — the ones with ordinary names.
 */
final class SmbShares {

    /** Disk trees only: bit 0-3 hold the type, and 0 is a disk. */
    private static final int TYPE_MASK = 0x0F;
    private static final int TYPE_DISK = 0x00;
    /** Set on the administrative shares — {@code C$}, {@code IPC$} — which nobody browses films from. */
    private static final int TYPE_SPECIAL = 0x80000000;

    private SmbShares() {
    }

    /**
     * The shares this host will admit to, in the order it lists them. Throws rather than returning
     * empty when the host refused: the caller has a password to offer.
     */
    static List<String> of(final String host, final int port, final String user,
                           final String password) throws IOException {
        final SmbConfig config = SmbConfig.builder()
                .withTimeout(10, TimeUnit.SECONDS)
                .withSoTimeout(15, TimeUnit.SECONDS)
                .withDfsEnabled(false)
                .build();
        // Its own connection, and a short-lived one: this runs once, before any share is known, so
        // there is nothing for SmbSessions to key a cached connection on.
        try (SMBClient client = new SMBClient(config);
             Connection connection = client.connect(host, port > 0 ? port : SMBClient.DEFAULT_PORT)) {
            final Session session = connection.authenticate(user.isEmpty()
                    ? AuthenticationContext.anonymous()
                    : new AuthenticationContext(user, password.toCharArray(), null));
            final ServerService service =
                    new ServerService(com.rapid7.client.dcerpc.transport.SMBTransportFactories.SRVSVC
                            .getTransport(session));
            final List<String> names = new ArrayList<>();
            for (final NetShareInfo1 share : service.getShares1()) {
                final int type = share.getType();
                if ((type & TYPE_MASK) != TYPE_DISK || (type & TYPE_SPECIAL) != 0) {
                    continue;
                }
                final String name = share.getNetName();
                if (name != null && !name.isEmpty()) {
                    names.add(name);
                }
            }
            return names;
        } catch (final IOException e) {
            throw e;
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }
}

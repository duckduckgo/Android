package dev.verifyandroid.probe;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.NoRouteToHostException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Opens a TCP connection to each host and sends a hand-built TLS ClientHello, so the caller controls
 * where the server name (SNI) sits and how the hello is cut into TCP segments.
 *
 * A reply from the server means the connection got through. A reset or close before any reply means
 * something on the path (AppTP) cut it. NO_ADDRESS means the name never resolved, so nothing was sent:
 * a DNS-level block, or no network.
 *
 * adb shell am start -S -W -n dev.verifyandroid.probe/.ProbeActivity --es hosts a.com,b.com --es mode split --es run 1
 *
 * Modes:
 *   single   one write, SNI first
 *   window   one write, SNI after ~400 bytes of padding, like real hellos. The kernel cuts the write into segments
 *            no larger than the receive window AppTP advertised in its SYN-ACK, so a too-small window cuts the SNI
 *   split    two writes, cut right after the SNI: the SNI is complete in the first segment
 *   late-sni two writes, cut right before the SNI: the SNI only arrives in the second segment
 *   ip       an HTTPS GET of https://host/cdn-cgi/trace (Cloudflare), logging the public IP the server saw
 *
 * The tunnel MTU carries a whole hello in one segment, so split and late-sni cut it with explicit writes.
 */
public class ProbeActivity extends Activity {

    private static final String TAG = "VerifyProbe";
    private static final int PADDING_BYTES = 1600;
    private static final int WINDOW_PADDING_BYTES = 400;
    private static final int SPLIT_PAUSE_MS = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String hosts = getIntent().getStringExtra("hosts");
        String mode = getIntent().getStringExtra("mode");
        String run = getIntent().getStringExtra("run");
        int timeoutMs = getIntent().getIntExtra("timeout", 5000);
        final String probeMode = mode == null ? "single" : mode;

        new Thread(() -> {
            if (hosts != null) {
                for (String host : hosts.split(",")) {
                    probe(run, host.trim(), probeMode, timeoutMs);
                }
            }
            Log.i(TAG, "DONE run=" + run);
            finish();
        }).start();
    }

    private void probe(String run, String host, String mode, int timeoutMs) {
        if (mode.equals("ip")) {
            publicIp(run, host, timeoutMs);
            return;
        }
        long start = System.currentTimeMillis();
        String outcome;
        int replyBytes = 0;
        try (Socket socket = new Socket()) {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(timeoutMs);
            try {
                socket.connect(new InetSocketAddress(host, 443), timeoutMs);
            } catch (SocketTimeoutException e) {
                // Nothing was sent yet, so this says nothing about AppTP.
                throw new ConnectFailed("CONNECT_TIMEOUT");
            } catch (ConnectException | NoRouteToHostException e) {
                throw new ConnectFailed("CONNECT_FAILED");
            }

            boolean lateSni = mode.equals("late-sni");
            byte[] sni = sniExtension(host);
            byte[] hello;
            if (mode.equals("window")) {
                hello = clientHello(sni, true, WINDOW_PADDING_BYTES);
            } else {
                hello = clientHello(sni, lateSni, PADDING_BYTES);
            }
            OutputStream out = socket.getOutputStream();
            if (mode.equals("split") || lateSni) {
                // Extensions start after 5 (record) + 4 (handshake) + 2 + 32 + 1 + 32 (session) + 2 + 14 (ciphers)
                // + 2 (compression) + 2 (extensions length) = 96 bytes.
                int cut = lateSni ? hello.length - sni.length : 96 + sni.length;
                out.write(hello, 0, cut);
                out.flush();
                Thread.sleep(SPLIT_PAUSE_MS);
                out.write(hello, cut, hello.length - cut);
            } else {
                out.write(hello);
            }
            out.flush();

            InputStream in = socket.getInputStream();
            int read = in.read(new byte[5]);
            if (read > 0) {
                replyBytes = read;
                outcome = "ALLOWED";
            } else {
                outcome = "CLOSED";
            }
        } catch (UnknownHostException e) {
            outcome = "NO_ADDRESS";
        } catch (ConnectFailed e) {
            outcome = e.getMessage();
        } catch (SocketTimeoutException e) {
            outcome = "TIMEOUT";
        } catch (IOException e) {
            outcome = e.getMessage() != null && e.getMessage().toLowerCase().contains("reset") ? "RESET" : "ERROR:" + e.getClass().getSimpleName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = "ERROR:interrupted";
        }
        Log.i(TAG, "RESULT run=" + run + " host=" + host + " mode=" + mode + " outcome=" + outcome
                + " reply_bytes=" + replyBytes + " ms=" + (System.currentTimeMillis() - start));
    }

    private void publicIp(String run, String host, int timeoutMs) {
        long start = System.currentTimeMillis();
        String outcome;
        String ip = "";
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL("https://" + host + "/cdn-cgi/trace").openConnection();
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new ConnectFailed("ERROR:http-" + status);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII))) {
                for (String line; (line = reader.readLine()) != null; ) {
                    if (line.startsWith("ip=")) ip = line.substring(3);
                }
            }
            outcome = ip.isEmpty() ? "ERROR:no-ip" : "ALLOWED";
        } catch (UnknownHostException e) {
            outcome = "NO_ADDRESS";
        } catch (ConnectFailed e) {
            outcome = e.getMessage();
        } catch (SocketTimeoutException e) {
            outcome = "TIMEOUT";
        } catch (IOException e) {
            outcome = "ERROR:" + e.getClass().getSimpleName();
        } finally {
            if (connection != null) connection.disconnect();
        }
        Log.i(TAG, "RESULT run=" + run + " host=" + host + " mode=ip outcome=" + outcome + " ip=" + ip
                + " ms=" + (System.currentTimeMillis() - start));
    }

    private static byte[] sniExtension(String host) {
        byte[] name = host.getBytes(StandardCharsets.US_ASCII);
        Bytes list = new Bytes().u8(0).u16(name.length).raw(name);
        Bytes data = new Bytes().u16(list.size()).raw(list.toByteArray());
        return extension(0x0000, data.toByteArray());
    }

    private static byte[] clientHello(byte[] sni, boolean sniLast, int paddingBytes) {
        SecureRandom random = new SecureRandom();
        byte[] keyShare = new byte[32];
        random.nextBytes(keyShare);

        Bytes common = new Bytes()
                .raw(extension(0x000a, new Bytes().u16(4).u16(0x001d).u16(0x0017).toByteArray()))
                .raw(extension(0x000b, new byte[] {1, 0}))
                .raw(extension(0x000d, new Bytes().u16(16)
                        .u16(0x0403).u16(0x0804).u16(0x0401).u16(0x0503)
                        .u16(0x0805).u16(0x0501).u16(0x0806).u16(0x0601).toByteArray()))
                .raw(extension(0x002b, new Bytes().u8(4).u16(0x0304).u16(0x0303).toByteArray()))
                .raw(extension(0x0033, new Bytes().u16(36).u16(0x001d).u16(32).raw(keyShare).toByteArray()));
        byte[] padding = extension(0x0015, new byte[paddingBytes]);

        Bytes extensions = new Bytes();
        if (sniLast) {
            extensions.raw(padding).raw(common.toByteArray()).raw(sni);
        } else {
            extensions.raw(sni).raw(common.toByteArray()).raw(padding);
        }

        byte[] clientRandom = new byte[32];
        byte[] sessionId = new byte[32];
        random.nextBytes(clientRandom);
        random.nextBytes(sessionId);
        Bytes body = new Bytes()
                .u16(0x0303)
                .raw(clientRandom)
                .u8(32).raw(sessionId)
                .u16(14).u16(0x1301).u16(0x1302).u16(0x1303).u16(0xc02b).u16(0xc02f).u16(0xc02c).u16(0xc030)
                .u8(1).u8(0)
                .u16(extensions.size()).raw(extensions.toByteArray());

        Bytes handshake = new Bytes().u8(0x01).u24(body.size()).raw(body.toByteArray());
        return new Bytes().u8(0x16).u16(0x0301).u16(handshake.size()).raw(handshake.toByteArray()).toByteArray();
    }

    private static byte[] extension(int type, byte[] data) {
        return new Bytes().u16(type).u16(data.length).raw(data).toByteArray();
    }

    private static final class ConnectFailed extends IOException {
        ConnectFailed(String outcome) {
            super(outcome);
        }
    }

    private static final class Bytes {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Bytes u8(int v) {
            out.write(v & 0xff);
            return this;
        }

        Bytes u16(int v) {
            return u8(v >> 8).u8(v);
        }

        Bytes u24(int v) {
            return u8(v >> 16).u16(v);
        }

        Bytes raw(byte[] b) {
            out.write(b, 0, b.length);
            return this;
        }

        int size() {
            return out.size();
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}

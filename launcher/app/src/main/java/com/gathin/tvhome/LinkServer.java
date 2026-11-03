package com.gathin.tvhome;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * A tiny web page on the home network, opened from a QR code on the TV: the phone pastes the Google Calendar
 * secret address into it, and the TV saves it. Runs only while the QR dialog is open.
 */
final class LinkServer {
    interface Saved { void onSaved(); }

    private final Context ctx;
    private final int port;
    private final Saved callback;
    private ServerSocket server;
    private volatile boolean running;

    LinkServer(Context ctx, int port, Saved callback) {
        this.ctx = ctx.getApplicationContext();
        this.port = port;
        this.callback = callback;
    }

    int getPort() { return port; }

    boolean start() {
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(port));
        } catch (Exception e) {
            return false;
        }
        running = true;
        Thread t = new Thread(this::loop, "link-server");
        t.setDaemon(true);
        t.start();
        return true;
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) { }
    }

    private void loop() {
        while (running) {
            try {
                final Socket s = server.accept();
                // One thread per connection: phone browsers open spare idle connections that would block a single loop.
                new Thread(() -> {
                    try (Socket c = s) {
                        c.setSoTimeout(5000);
                        handle(c);
                    } catch (Exception ignored) { }
                }, "link-conn").start();
            } catch (Exception e) {
                if (!running) return;
            }
        }
    }

    private void handle(Socket s) throws Exception {
        InputStream in = s.getInputStream();
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) >= 0) {
            head.write(b);
            int n = head.size();
            if (n >= 4) {
                byte[] x = head.toByteArray();
                if (x[n - 4] == '\r' && x[n - 3] == '\n' && x[n - 2] == '\r' && x[n - 1] == '\n') break;
            }
            if (n > 16384) return;
        }
        String h = head.toString("UTF-8");
        boolean post = h.startsWith("POST");
        int len = 0;
        for (String line : h.split("\r\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                try { len = Integer.parseInt(line.substring(15).trim()); } catch (Exception ignored) { }
            }
        }
        String message = null;
        boolean ok = false;
        if (post && len > 0 && len < 8192) {
            byte[] body = new byte[len];
            int got = 0;
            while (got < len) {
                int r = in.read(body, got, len - got);
                if (r < 0) break;
                got += r;
            }
            String url = "";
            for (String pair : new String(body, 0, got, StandardCharsets.UTF_8).split("&")) {
                if (pair.startsWith("url=")) url = URLDecoder.decode(pair.substring(4), "UTF-8");
            }
            ok = CalendarData.setMyUrl(ctx, url);
            message = ok ? "Saved. You can look at the TV now." : "That is not a web address. Go back and try again.";
            if (ok) {
                CalendarData.setMyOn(ctx, true);
                new Thread(() -> CalendarData.refresh(ctx, true, null)).start();
                if (callback != null) callback.onSaved();
            }
        }
        String page = "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>"
                + "<title>Glass TV calendar</title><style>body{font-family:sans-serif;background:#0b1220;color:#fff;padding:20px}"
                + "textarea{width:100%;height:140px;font-size:16px;border-radius:12px;padding:10px;box-sizing:border-box}"
                + "button{margin-top:14px;width:100%;font-size:18px;padding:14px;border:0;border-radius:14px;background:#0a84ff;color:#fff}"
                + "</style></head><body><h2>Google Calendar link</h2>"
                + (message != null ? "<p><b>" + message + "</b></p>" : "")
                + "<p>Paste the <b>Secret address in iCal format</b>. It is read only.</p>"
                + "<ol><li>Open <a style='color:#38bdf8' href='https://calendar.google.com/calendar/u/0/r/settings'>Google Calendar settings</a> in the phone browser "
                + "(use Chrome and tick <i>Desktop site</i>; the Google Calendar app does not show it).</li>"
                + "<li>Under <i>Settings for my calendars</i> tap your calendar.</li>"
                + "<li>Scroll to <i>Integrate calendar</i> and copy <i>Secret address in iCal format</i> (it ends in basic.ics).</li>"
                + "<li>Paste it below and press Send to TV.</li></ol>"
                + "<form method=post><textarea name=url placeholder='https://calendar.google.com/calendar/ical/.../basic.ics'></textarea>"
                + "<button type=submit>Send to TV</button></form></body></html>";
        byte[] out = page.getBytes(StandardCharsets.UTF_8);
        OutputStream os = s.getOutputStream();
        os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: " + out.length
                + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        os.write(out);
        os.flush();
    }
}

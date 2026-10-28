package dev.nightfall.launcher;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Tiny HTTPS helpers. Call off the main thread. */
final class Net {
    private Net() {
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Nightfall-Launcher");
        if (c.getResponseCode() / 100 != 2) {
            int code = c.getResponseCode();
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        return c;
    }

    static String get(String url) throws IOException {
        HttpURLConnection c = open(url);
        try {
            InputStream in = c.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    static void download(String url, File dest) throws IOException {
        HttpURLConnection c = open(url);
        try {
            InputStream in = c.getInputStream();
            OutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            c.disconnect();
        }
    }
}

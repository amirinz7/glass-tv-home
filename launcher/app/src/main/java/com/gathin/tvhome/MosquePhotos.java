package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Random;

/**
 * Pictures of mosques for the prayer page, Malaysia and the world, from Wikimedia Commons (free licences).
 * One new picture is fetched each time the page opens until every place on the list is kept; they stay on the TV.
 */
final class MosquePhotos {
    private MosquePhotos() {}

    static final String[] PLACES = {
            "Putra Mosque Putrajaya", "Sultan Salahuddin Abdul Aziz Shah Mosque", "Masjid Negara Kuala Lumpur",
            "Crystal Mosque Kuala Terengganu", "Masjid Jamek Kuala Lumpur", "Tengku Tengah Zaharah Mosque Putrajaya floating",
            "Masjid al-Haram Mecca", "Prophet's Mosque Medina", "Sheikh Zayed Grand Mosque Abu Dhabi",
            "Sultan Ahmed Mosque Istanbul", "Hassan II Mosque Casablanca", "Nasir al-Mulk Mosque Shiraz",
            "Dome of the Rock Jerusalem", "Badshahi Mosque Lahore",
    };

    private static File dir(Context c) {
        File d = new File(c.getFilesDir(), "mosques");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    /** A kept picture chosen at random, sized for the screen, or null when none is kept yet. */
    static Bitmap random(Context c) {
        File[] all = dir(c).listFiles((d, n) -> n.endsWith(".jpg"));
        if (all == null || all.length == 0) return null;
        File f = all[new Random().nextInt(all.length)];
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            int sample = 1;
            while (o.outWidth / sample > 2400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), o2);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Fetches the next missing picture in the background; {@code done} runs on the main thread when one was saved. */
    static void fetchNext(final Context ctx, final Runnable done) {
        final Context c = ctx.getApplicationContext();
        final SharedPreferences p = c.getSharedPreferences("mosques", Context.MODE_PRIVATE);
        final int idx = p.getInt("next", 0);
        if (idx >= PLACES.length) return;
        new Thread(() -> {
            boolean saved = false;
            try {
                String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search&gsrnamespace=6&gsrlimit=10"
                        + "&gsrsearch=" + URLEncoder.encode(PLACES[idx] + " filetype:bitmap", "UTF-8")
                        + "&prop=imageinfo&iiprop=url%7Csize%7Cmime&iiurlwidth=1920";
                JSONObject pages = new JSONObject(text(url)).getJSONObject("query").getJSONObject("pages");
                String best = null;
                int bestIndex = Integer.MAX_VALUE;
                java.util.Iterator<String> it = pages.keys();
                while (it.hasNext()) {
                    JSONObject pg = pages.getJSONObject(it.next());
                    JSONObject info = pg.getJSONArray("imageinfo").getJSONObject(0);
                    if (!"image/jpeg".equals(info.optString("mime"))) continue;
                    int w = info.optInt("width"), h = info.optInt("height");
                    if (w < 1600 || w < h * 1.3) continue;
                    int index = pg.optInt("index", 99);
                    if (index < bestIndex) { bestIndex = index; best = info.optString("thumburl", info.optString("url")); }
                }
                if (best != null) {
                    byte[] data = bytes(best);
                    if (data != null && data.length > 20_000) {
                        try (FileOutputStream out = new FileOutputStream(new File(dir(c), "mosque_" + idx + ".jpg"))) { out.write(data); }
                        saved = true;
                    }
                }
            } catch (Exception e) {
                android.util.Log.w("TvHome.Mosque", PLACES[idx] + " failed: " + e);
                return;      // network trouble: try the same place next time
            }
            p.edit().putInt("next", idx + 1).apply();
            if (saved && done != null) new Handler(Looper.getMainLooper()).post(done);
        }, "mosque-photo").start();
    }

    private static String text(String url) throws Exception {
        return new String(bytes(url), "UTF-8");
    }

    private static byte[] bytes(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(10_000);
        h.setReadTimeout(20_000);
        h.setRequestProperty("User-Agent", "GlassTV-Home/4.1 (personal TV launcher)");
        try (InputStream in = h.getInputStream(); ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            byte[] b = new byte[16384];
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            return bo.toByteArray();
        } finally {
            h.disconnect();
        }
    }
}

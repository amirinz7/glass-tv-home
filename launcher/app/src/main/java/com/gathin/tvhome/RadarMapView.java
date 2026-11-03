package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * Apple Weather Regional Precipitation Radar Map Card View.
 * Displays local geography, radar cloud reflectivity heatmaps, and location temperature badge.
 */
public class RadarMapView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint landPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radarPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int temp = 29;
    private String locationName = "Putrajaya";

    public RadarMapView(Context context) {
        this(context, null);
    }

    public RadarMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(0xFF131D2A);

        landPaint.setStyle(Paint.Style.FILL);
        landPaint.setColor(0xFF1C2B3C);

        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setColor(0xCCFFFFFF);

        badgePaint.setStyle(Paint.Style.FILL);
        badgePaint.setColor(0xFF1E3A8A);

        badgeStroke.setStyle(Paint.Style.STROKE);
        badgeStroke.setStrokeWidth(1.5f);
        badgeStroke.setColor(0xFFFFFFFF);
    }

    private android.graphics.Bitmap mapBmp;
    private float markerX, markerY;
    private boolean loading;

    /**
     * A real map: dark street tiles (CARTO) around the place with the latest rain radar (RainViewer) over
     * them. Fetched once on a background thread; until it arrives, or if the network fails, the sketch is drawn.
     */
    public void loadMap(final double lat, final double lon) {
        if (loading || mapBmp != null) return;
        loading = true;
        new Thread(() -> {
            try {
                final int z = 7, n = 1 << z;
                double latRad = Math.toRadians(lat);
                double fx = (lon + 180.0) / 360.0 * n;
                double fy = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * n;
                int tx = (int) Math.floor(fx), ty = (int) Math.floor(fy);
                android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(768, 768, android.graphics.Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                String host = null, path = null;
                try {
                    org.json.JSONObject j = new org.json.JSONObject(fetchText("https://api.rainviewer.com/public/weather-maps.json"));
                    host = j.optString("host", "https://tilecache.rainviewer.com");
                    org.json.JSONArray past = j.getJSONObject("radar").getJSONArray("past");
                    path = past.getJSONObject(past.length() - 1).getString("path");
                } catch (Exception ignored) { }
                Paint pp = new Paint(Paint.FILTER_BITMAP_FLAG);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int x = tx + dx, y = ty + dy;
                        android.graphics.Bitmap base = fetchTile("https://basemaps.cartocdn.com/dark_all/" + z + "/" + x + "/" + y + ".png");
                        if (base != null) c.drawBitmap(base, (dx + 1) * 256, (dy + 1) * 256, pp);
                        if (path != null) {
                            android.graphics.Bitmap rad = fetchTile(host + path + "/256/" + z + "/" + x + "/" + y + "/2/1_1.png");
                            if (rad != null) { pp.setAlpha(210); c.drawBitmap(rad, (dx + 1) * 256, (dy + 1) * 256, pp); pp.setAlpha(255); }
                        }
                    }
                }
                final float mx = (float) ((fx - tx + 1) * 256), my = (float) ((fy - ty + 1) * 256);
                post(() -> { mapBmp = out; markerX = mx; markerY = my; invalidate(); });
            } catch (Exception e) {
                android.util.Log.w("TvHome.Radar", "map failed: " + e);
            } finally {
                loading = false;
            }
        }, "radar-map").start();
    }

    private static String fetchText(String url) throws Exception {
        java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        h.setConnectTimeout(8000); h.setReadTimeout(8000);
        h.setRequestProperty("User-Agent", "GlassTV-Home");
        try (java.io.InputStream in = h.getInputStream(); java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            return bo.toString("UTF-8");
        } finally { h.disconnect(); }
    }

    private static android.graphics.Bitmap fetchTile(String url) {
        try {
            java.net.HttpURLConnection h = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            h.setConnectTimeout(8000); h.setReadTimeout(8000);
            h.setRequestProperty("User-Agent", "GlassTV-Home");
            try (java.io.InputStream in = h.getInputStream()) {
                return android.graphics.BitmapFactory.decodeStream(in);
            } finally { h.disconnect(); }
        } catch (Exception e) {
            return null;
        }
    }

    public void setDetails(int temp, String loc) {
        this.temp = temp;
        this.locationName = loc;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;

        // Clip to rounded corners
        Path clipPath = new Path();
        clipPath.addRoundRect(new RectF(0, 0, w, h), 14f * density, 14f * density, Path.Direction.CW);
        canvas.clipPath(clipPath);

        if (mapBmp != null) {
            // Real map: the part of the 768 px picture that fits the view, centred on the place.
            float scale = Math.max(w / 512f, h / 512f);
            float srcW = w / scale, srcH = h / scale;
            float l = Math.max(0, Math.min(768 - srcW, markerX - srcW / 2)), t = Math.max(0, Math.min(768 - srcH, markerY - srcH / 2));
            canvas.drawBitmap(mapBmp, new android.graphics.Rect((int) l, (int) t, (int) (l + srcW), (int) (t + srcH)),
                    new RectF(0, 0, w, h), null);
            float px = (markerX - l) * scale, py = (markerY - t) * scale;
            badgePaint.setColor(0xFF0A84FF);
            canvas.drawCircle(px, py, 6f * density, badgePaint);
            canvas.drawCircle(px, py, 6f * density, badgeStroke);
            textPaint.setTextAlign(Paint.Align.LEFT);
            textPaint.setTextSize(11f * density);
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setFakeBoldText(true);
            canvas.drawText(locationName + "  " + temp + "\u00B0", px + 10f * density, py + 4f * density, textPaint);
            return;
        }

        // 1. Sea / Ocean background (Straits of Malacca)
        canvas.drawRect(0, 0, w, h, bgPaint);

        // 2. West Coast Landmass Polygon (Selangor, KL, N. Sembilan)
        Path land = new Path();
        land.moveTo(w * 0.22f, 0);
        land.lineTo(w, 0);
        land.lineTo(w, h);
        land.lineTo(w * 0.48f, h);
        land.lineTo(w * 0.36f, h * 0.85f); // Port Dickson
        land.lineTo(w * 0.28f, h * 0.65f); // Sepang
        land.lineTo(w * 0.16f, h * 0.45f); // Klang / Pandamaran
        land.lineTo(w * 0.20f, h * 0.25f); // Kuala Selangor
        land.lineTo(w * 0.12f, 0);          // Sabak Bernam
        land.close();
        canvas.drawPath(land, landPaint);

        // 3. Radar Precipitation Heatmap Overlays (Apple Weather Doppler return)
        // Storm cell 1 (Violet / Magenta core)
        RadialGradient cell1 = new RadialGradient(
                w * 0.38f, h * 0.45f, w * 0.32f,
                new int[]{0xDDDB2777, 0xAA7C3AED, 0x662563EB, 0x001E3A8A},
                new float[]{0f, 0.35f, 0.70f, 1f},
                Shader.TileMode.CLAMP
        );
        radarPaint.setShader(cell1);
        canvas.drawCircle(w * 0.38f, h * 0.45f, w * 0.32f, radarPaint);

        // Storm cell 2 (Amber / Orange / Crimson precipitation band)
        RadialGradient cell2 = new RadialGradient(
                w * 0.52f, h * 0.68f, w * 0.26f,
                new int[]{0xCCE11D48, 0x99F59E0B, 0x4410B981, 0x000D9488},
                new float[]{0f, 0.40f, 0.75f, 1f},
                Shader.TileMode.CLAMP
        );
        radarPaint.setShader(cell2);
        canvas.drawCircle(w * 0.52f, h * 0.68f, w * 0.26f, radarPaint);

        // 4. Map Geography Labels (Carefully positioned to avoid overlap)
        textPaint.setTextAlign(Paint.Align.LEFT);

        // Kuala Lumpur with city marker dot
        textPaint.setTextSize(9f * density);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setFakeBoldText(true);
        canvas.drawCircle(w * 0.59f, h * 0.43f, 2f * density, textPaint);
        canvas.drawText("Kuala Lumpur", w * 0.62f, h * 0.45f, textPaint);

        // Region & Town Labels
        textPaint.setTextSize(7f * density);
        textPaint.setColor(0xA0FFFFFF);
        textPaint.setFakeBoldText(false);
        canvas.drawText("Bagan Nakhoda Omar", w * 0.16f, h * 0.16f, textPaint);
        canvas.drawText("Kuala Selangor", w * 0.24f, h * 0.28f, textPaint);
        canvas.drawText("SELANGOR", w * 0.30f, h * 0.36f, textPaint);
        canvas.drawText("Pandamaran", w * 0.12f, h * 0.52f, textPaint);
        canvas.drawText("Mentakab", w * 0.74f, h * 0.28f, textPaint);
        canvas.drawText("Sepang", w * 0.35f, h * 0.75f, textPaint);
        canvas.drawText("Seremban", w * 0.64f, h * 0.65f, textPaint);
        canvas.drawText("Port Dickson", w * 0.52f, h * 0.84f, textPaint);

        // Sea label in bottom right corner
        textPaint.setTextSize(6.5f * density);
        textPaint.setColor(0x70FFFFFF);
        canvas.drawText("MALACCA STRAIT", w * 0.70f, h * 0.92f, textPaint);

        // 5. My Location Badge (Circular disc: temp e.g. 29 + 'My Location')
        float badgeX = w * 0.38f;
        float badgeY = h * 0.58f;
        float bRadius = 9f * density;

        canvas.drawCircle(badgeX, badgeY, bRadius, badgePaint);
        canvas.drawCircle(badgeX, badgeY, bRadius, badgeStroke);

        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(7.5f * density);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(String.valueOf(temp), badgeX, badgeY + (2.5f * density), textPaint);

        textPaint.setTextSize(7f * density);
        textPaint.setFakeBoldText(false);
        textPaint.setColor(0xEEFFFFFF);
        canvas.drawText("My Location", badgeX, badgeY + (11.5f * density), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }
}

package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hardware-accelerated animated weather background matching Apple Weather's atmospheric canvas.
 * Renders dynamic sky gradients, sun lens flares, multi-layer rainfall, thunderstorm lightning flashes,
 * procedural drifting clouds, and twinkling night stars at a buttery smooth 60fps.
 */
public class WeatherBackgroundView extends View {

    public enum ConditionType {
        NONE,
        SUNNY,
        CLOUDY,
        DRIZZLE,
        RAIN,
        THUNDERSTORM,
        FOG,
        SNOW,
        NIGHT
    }

    private ConditionType currentCondition = ConditionType.CLOUDY;
    private boolean isAnimating = false;
    private long lastFrameTime = 0;
    private final Random random = new Random();

    // Paints
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightningPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sunGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cloudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Gradients
    private LinearGradient bgGradient;
    private int cachedW = 0;
    private int cachedH = 0;

    // Rain particles
    private static class RainDrop {
        float x, y;
        float length;
        float speed;
        float width;
        int alpha;
        int layer; // 0: back, 1: mid, 2: front
    }
    private final List<RainDrop> rainDrops = new ArrayList<>();

    // Splash ripples
    private static class SplashRipple {
        float x, y;
        float radius;
        float maxRadius;
        int alpha;
    }
    private final List<SplashRipple> ripples = new ArrayList<>();

    // Stars
    private static class Star {
        float x, y;
        float size;
        float phase;
        float speed;
        int baseAlpha;
    }
    private final List<Star> stars = new ArrayList<>();

    // Procedural Clouds
    private static class CloudPuff {
        float x, y;
        float rx, ry;
        float speed;
        int alpha;
    }
    private final List<CloudPuff> cloudPuffs = new ArrayList<>();

    // Snow
    private static class Flake {
        float x, y, r, speed, phase;
        int alpha;
    }
    private final List<Flake> flakes = new ArrayList<>();

    // Sun / Lens Flare State
    private float sunPulseTime = 0f;

    // Lightning State
    private long nextLightningTime = 0;
    private float lightningAlpha = 0f;
    private final Path lightningPath = new Path();
    private boolean hasActiveBolt = false;

    // Wind (0..1), hot day, and heavy rain: set from the weather text by the home screen.
    private float windStrength = 0f;
    private boolean hot = false;
    private boolean heavyRain = false;
    private float[] wx = new float[0], wy = new float[0], wl = new float[0], ws = new float[0];
    private float shimmerTime = 0f;
    private boolean secondFlash = false;
    private boolean sheetOnly = false;

    public void setWind(float w) {
        w = Math.max(0f, Math.min(1f, w));
        if (Math.abs(w - windStrength) > 0.01f) { windStrength = w; invalidate(); }
    }

    public void setHot(boolean h) {
        if (hot != h) { hot = h; invalidate(); }
    }

    /** Heavy rain (hujan lebat): more, longer, faster drops than ordinary rain or drizzle. */
    public void setHeavyRain(boolean h) {
        if (heavyRain != h) { heavyRain = h; rebuildGradientsAndParticles(); invalidate(); }
    }

    public WeatherBackgroundView(Context context) {
        this(context, null);
    }

    public WeatherBackgroundView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        rainPaint.setStyle(Paint.Style.STROKE);
        rainPaint.setStrokeCap(Paint.Cap.ROUND);

        lightningPaint.setStyle(Paint.Style.FILL);
        lightningPaint.setColor(0xE0FFFFFF);

        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeWidth(1.5f);

        starPaint.setStyle(Paint.Style.FILL);
        cloudPaint.setStyle(Paint.Style.FILL);
        sunGlowPaint.setStyle(Paint.Style.FILL);
        flarePaint.setStyle(Paint.Style.FILL);
    }

    public void setCondition(ConditionType condition) {
        if (this.currentCondition != condition) {
            this.currentCondition = condition;
            rebuildGradientsAndParticles();
            invalidate();
        }
    }

    public ConditionType getCondition() {
        return currentCondition;
    }

    private boolean overlayMode = false;
    private boolean lowSpecMode = false;

    public void setOverlayMode(boolean overlayMode) {
        this.overlayMode = overlayMode;
        invalidate();
    }

    public boolean isOverlayMode() {
        return overlayMode;
    }

    public void setLowSpecMode(boolean lowSpecMode) {
        this.lowSpecMode = lowSpecMode;
        rebuildGradientsAndParticles();
    }

    public boolean isLowSpecMode() {
        return lowSpecMode;
    }

    public void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            lastFrameTime = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    public void stopAnimation() {
        isAnimating = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cachedW = w;
        cachedH = h;
        rebuildGradientsAndParticles();
    }

    private void rebuildGradientsAndParticles() {
        if (cachedW <= 0 || cachedH <= 0) return;

        // 1. Sky Gradients based on Apple Weather color palettes
        switch (currentCondition) {
            case SUNNY:
                // Radiant day sky: vibrant azure blue blending into softer bright cerulean
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF1C68C5, 0xFF3180D8, 0xFF4A96E6},
                        new float[]{0f, 0.45f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case CLOUDY:
                // Muted overcast sky: soft slate blue-grey
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF243447, 0xFF35485E, 0xFF435B74},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case RAIN:
                // Rain sky: deep atmospheric oceanic slate
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF162334, 0xFF213247, 0xFF2F455F},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case THUNDERSTORM:
                // Dark storm sky: charcoal stormy blue
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF0E1624, 0xFF172232, 0xFF202C3D},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
            case FOG:
                bgGradient = new LinearGradient(0, 0, 0, cachedH,
                        new int[]{0xFF4A5A6B, 0xFF6C7C8C, 0xFF8D9AA6}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                break;
            case SNOW:
                bgGradient = new LinearGradient(0, 0, 0, cachedH,
                        new int[]{0xFF4F6482, 0xFF7F93AE, 0xFFB3C2D6}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
                break;
            case NIGHT:
            default:
                // Starry night: deep midnight sapphire
                bgGradient = new LinearGradient(
                        0, 0, 0, cachedH,
                        new int[]{0xFF070D18, 0xFF0D1728, 0xFF14223A},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP
                );
                break;
        }
        bgPaint.setShader(bgGradient);

        // 2. Initialize Rain drops if drizzle, rain or storm
        rainDrops.clear();
        ripples.clear();
        if (currentCondition == ConditionType.DRIZZLE || currentCondition == ConditionType.RAIN || currentCondition == ConditionType.THUNDERSTORM) {
            int count;
            if (currentCondition == ConditionType.THUNDERSTORM) {
                count = lowSpecMode ? 90 : 180;
            } else if (currentCondition == ConditionType.RAIN) {
                count = heavyRain ? (lowSpecMode ? 90 : 190) : (lowSpecMode ? 40 : 80);
            } else { // DRIZZLE
                count = lowSpecMode ? 20 : 40;
            }

            for (int i = 0; i < count; i++) {
                RainDrop drop = new RainDrop();
                drop.x = random.nextFloat() * cachedW * 1.2f - (cachedW * 0.1f);
                drop.y = random.nextFloat() * cachedH;
                drop.layer = random.nextInt(3);
                if (currentCondition == ConditionType.DRIZZLE) {
                    // Delicate translucent rintik hujan
                    drop.length = 8f + random.nextFloat() * 8f;
                    drop.speed = 8f + random.nextFloat() * 5f;
                    drop.width = 1.0f;
                    drop.alpha = 35 + random.nextInt(40);
                } else if (drop.layer == 0) {
                    // Background drizzle
                    drop.length = 12f + random.nextFloat() * 10f;
                    drop.speed = 14f + random.nextFloat() * 6f;
                    drop.width = 1.2f;
                    drop.alpha = 50 + random.nextInt(50);
                } else if (drop.layer == 1) {
                    // Midground rain
                    drop.length = 22f + random.nextFloat() * 14f;
                    drop.speed = 22f + random.nextFloat() * 8f;
                    drop.width = 1.8f;
                    drop.alpha = 90 + random.nextInt(60);
                } else {
                    // Foreground heavy streak
                    drop.length = 34f + random.nextFloat() * 18f;
                    drop.speed = 32f + random.nextFloat() * 10f;
                    drop.width = 2.4f;
                    drop.alpha = 140 + random.nextInt(70);
                }
                if (heavyRain && currentCondition == ConditionType.RAIN) {
                    drop.length *= 1.35f;
                    drop.speed *= 1.25f;
                    drop.alpha = Math.min(255, drop.alpha + 25);
                }
                rainDrops.add(drop);
            }
        }

        // Wind streaks: thin curved lines that blow across the screen.
        int wn = lowSpecMode ? 6 : 14;
        wx = new float[wn]; wy = new float[wn]; wl = new float[wn]; ws = new float[wn];
        for (int i = 0; i < wn; i++) {
            wx[i] = random.nextFloat() * cachedW;
            wy[i] = random.nextFloat() * cachedH * 0.85f;
            wl[i] = cachedW * (0.06f + random.nextFloat() * 0.10f);
            ws[i] = 0.7f + random.nextFloat() * 0.9f;
        }

        // 3. Initialize Stars if night
        stars.clear();
        if (currentCondition == ConditionType.NIGHT) {
            int starCount = 80;
            for (int i = 0; i < starCount; i++) {
                Star s = new Star();
                s.x = random.nextFloat() * cachedW;
                s.y = random.nextFloat() * (cachedH * 0.75f);
                s.size = 1f + random.nextFloat() * 2.2f;
                s.phase = random.nextFloat() * 6.28f;
                s.speed = 1.5f + random.nextFloat() * 2.5f;
                s.baseAlpha = 70 + random.nextInt(140);
                stars.add(s);
            }
        }

        // 4. Initialize Cloud Puffs if cloudy or partly cloudy
        cloudPuffs.clear();
        flakes.clear();
        if (currentCondition == ConditionType.SNOW) {
            int n = lowSpecMode ? 45 : 110;
            for (int i = 0; i < n; i++) {
                Flake f = new Flake();
                f.x = random.nextFloat() * cachedW;
                f.y = random.nextFloat() * cachedH;
                f.r = 1.5f + random.nextFloat() * 3.2f;
                f.speed = 0.6f + random.nextFloat() * 1.4f;
                f.phase = random.nextFloat() * 6.28f;
                f.alpha = 130 + random.nextInt(110);
                flakes.add(f);
            }
        }
        if (currentCondition == ConditionType.CLOUDY || currentCondition == ConditionType.RAIN
                || currentCondition == ConditionType.DRIZZLE || currentCondition == ConditionType.THUNDERSTORM
                || currentCondition == ConditionType.FOG || currentCondition == ConditionType.SNOW) {
            boolean fog = currentCondition == ConditionType.FOG;
            int puffs = fog ? 10 : 8;
            for (int i = 0; i < puffs; i++) {
                CloudPuff p = new CloudPuff();
                p.x = random.nextFloat() * cachedW;
                p.y = (cachedH * 0.05f) + (random.nextFloat() * (cachedH * 0.45f));
                p.rx = (cachedW * 0.22f) + (random.nextFloat() * (cachedW * 0.25f));
                p.ry = (cachedH * 0.16f) + (random.nextFloat() * (cachedH * 0.18f));
                p.speed = 0.25f + (random.nextFloat() * 0.45f);
                p.alpha = fog ? 45 + random.nextInt(35) : 15 + random.nextInt(25);
                if (fog) { p.y = random.nextFloat() * cachedH; p.speed = 0.15f + random.nextFloat() * 0.3f; }
                cloudPuffs.add(p);
            }
        }

        nextLightningTime = SystemClock.uptimeMillis() + 4000 + random.nextInt(5000);
        lightningAlpha = 0f;
        hasActiveBolt = false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (cachedW <= 0 || cachedH <= 0 || currentCondition == ConditionType.NONE) return;

        long now = SystemClock.uptimeMillis();
        float dt = (lastFrameTime > 0) ? Math.min((now - lastFrameTime) / 1000f, 0.05f) : 0.016f;
        lastFrameTime = now;

        // 1. Draw base sky gradient (only when not in transparent wallpaper overlay mode)
        if (!overlayMode) {
            canvas.drawRect(0, 0, cachedW, cachedH, bgPaint);
        }

        // 2. Weather Specific Layers
        switch (currentCondition) {
            case SUNNY:
                if (!overlayMode) drawSunnyAtmosphere(canvas, dt);
                else drawSpinningSun(canvas, dt);
                break;
            case CLOUDY:
                drawClouds(canvas, dt);
                break;
            case FOG:
                drawClouds(canvas, dt);
                break;
            case SNOW:
                drawClouds(canvas, dt);
                drawSnow(canvas, dt);
                break;
            case DRIZZLE:
                drawClouds(canvas, dt);
                drawRainAndRipples(canvas, dt, false);
                break;
            case RAIN:
                drawClouds(canvas, dt);
                drawRainAndRipples(canvas, dt, false);
                break;
            case THUNDERSTORM:
                drawClouds(canvas, dt);
                drawRainAndRipples(canvas, dt, true);
                drawLightning(canvas, now, dt);
                break;
            case NIGHT:
                if (!overlayMode) drawNightSky(canvas, dt);
                else drawShootingStars(canvas, dt);
                break;
        }

        if (windStrength > 0.15f) drawWind(canvas, dt);

        if (isAnimating) {
            postInvalidateOnAnimation();
        }
    }

    /** Streaks of wind, faster the stronger it blows. */
    private void drawWind(Canvas canvas, float dt) {
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeCap(Paint.Cap.ROUND);
        ripplePaint.setStrokeWidth(2f);
        int shown = Math.max(2, (int) (wx.length * windStrength));
        for (int i = 0; i < shown && i < wx.length; i++) {
            wx[i] += ws[i] * (300f + 900f * windStrength) * dt;
            if (wx[i] - wl[i] > cachedW) {
                wx[i] = -wl[i];
                wy[i] = random.nextFloat() * cachedH * 0.85f;
            }
            float k = Math.min(1f, Math.min(wx[i] + wl[i], cachedW - wx[i]) / (wl[i] * 2f));
            if (k < 0f) k = 0f;
            int a = (int) (70 * windStrength * Math.min(1f, k + 0.3f));
            ripplePaint.setColor(Color.argb(a, 255, 255, 255));
            float bend = (float) Math.sin(wx[i] * 0.01f + i) * 12f;
            windPath.reset();
            windPath.moveTo(wx[i] - wl[i], wy[i]);
            windPath.quadTo(wx[i] - wl[i] * 0.4f, wy[i] + bend, wx[i], wy[i] - bend * 0.3f);
            canvas.drawPath(windPath, ripplePaint);
        }
    }

    private final Path windPath = new Path();

    /** A hot day: the air wobbles above the ground in slow bright bands. */
    private void drawHeatShimmer(Canvas canvas, float dt) {
        shimmerTime += dt;
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeCap(Paint.Cap.ROUND);
        ripplePaint.setStrokeWidth(3f);
        int bands = lowSpecMode ? 3 : 6;
        for (int b = 0; b < bands; b++) {
            float y = cachedH * (0.55f + 0.07f * b);
            float shade = 0.5f + 0.5f * (float) Math.sin(shimmerTime * 0.9f + b);
            ripplePaint.setColor(Color.argb((int) (14 + 22 * shade), 255, 236, 190));
            windPath.reset();
            windPath.moveTo(0, y);
            for (int x = 0; x <= cachedW; x += cachedW / 12) {
                float yy = y + (float) Math.sin(x * 0.012f + shimmerTime * 2.4f + b * 1.7f) * 6f;
                windPath.lineTo(x, yy);
            }
            canvas.drawPath(windPath, ripplePaint);
        }
    }

    private float sunSpin;

    /** A sun in the corner whose rays turn very slowly, drawn in front of the tiles. */
    private void drawSpinningSun(Canvas canvas, float dt) {
        sunSpin = (sunSpin + dt * (hot ? 9f : 5f)) % 360f;
        sunPulseTime += dt;
        float cx = cachedW * 0.92f, cy = cachedH * 0.10f, r = cachedH * (hot ? 0.085f : 0.07f);
        flarePaint.setStyle(Paint.Style.FILL);
        // A soft warm wash from the corner, breathing slowly; stronger when it is hot.
        float breathe = 1f + 0.08f * (float) Math.sin(sunPulseTime * 1.3f);
        RadialGradient wash = new RadialGradient(cx, cy, cachedW * (hot ? 0.55f : 0.40f) * breathe,
                new int[]{Color.argb(hot ? 90 : 55, 255, 214, 120), Color.argb(0, 255, 214, 120)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        sunGlowPaint.setShader(wash);
        canvas.drawCircle(cx, cy, cachedW * 0.6f, sunGlowPaint);
        if (hot) drawHeatShimmer(canvas, dt);
        flarePaint.setColor(0x33FFE082);
        canvas.drawCircle(cx, cy, r * 2.4f * breathe, flarePaint);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setStrokeCap(Paint.Cap.ROUND);
        ripplePaint.setStrokeWidth(Math.max(3f, r * 0.12f));
        ripplePaint.setColor(0x88FFD54F);
        canvas.save();
        canvas.rotate(sunSpin, cx, cy);
        for (int i = 0; i < 12; i++) {
            double a = Math.toRadians(i * 30);
            float len = (i % 2 == 0) ? r * 0.95f : r * 0.6f;
            canvas.drawLine(cx + (float) Math.cos(a) * r * 1.35f, cy + (float) Math.sin(a) * r * 1.35f,
                    cx + (float) Math.cos(a) * (r * 1.35f + len), cy + (float) Math.sin(a) * (r * 1.35f + len), ripplePaint);
        }
        canvas.restore();
        flarePaint.setColor(0xCCFFE082);
        canvas.drawCircle(cx, cy, r, flarePaint);
        flarePaint.setColor(0xAAFFF8E1);
        canvas.drawCircle(cx, cy, r * 0.7f, flarePaint);
    }

    private float shootX, shootY, shootLife = -1f, shootWait = 2f;

    /** Twinkling stars and an occasional shooting star across the whole screen, in front of the tiles. */
    private void drawShootingStars(Canvas canvas, float dt) {
        // A crescent moon with a halo, top right.
        float mx = cachedW * 0.90f, my = cachedH * 0.11f, mr = cachedH * 0.045f;
        RadialGradient halo = new RadialGradient(mx, my, mr * 4.5f,
                new int[]{0x40DDEBFF, 0x00DDEBFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        sunGlowPaint.setShader(halo);
        canvas.drawCircle(mx, my, mr * 4.5f, sunGlowPaint);
        // The crescent: a disc minus a slightly shifted disc, so nothing is erased behind it.
        Path moon = new Path(), bite = new Path();
        moon.addCircle(mx, my, mr, Path.Direction.CW);
        bite.addCircle(mx + mr * 0.45f, my - mr * 0.15f, mr * 0.88f, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        starPaint.setColor(0xF0F4F8FF);
        canvas.drawPath(moon, starPaint);
        for (Star s : stars) {
            s.phase += s.speed * dt;
            float twinkle = (float) ((Math.sin(s.phase) + 1.0) / 2.0);
            int alpha = (int) (s.baseAlpha * (0.25f + 0.75f * twinkle));
            starPaint.setColor(Color.argb(Math.min(255, alpha), 255, 255, 255));
            canvas.drawCircle(s.x, s.y, s.size, starPaint);
        }
        if (shootLife < 0) {
            shootWait -= dt;
            if (shootWait <= 0) {
                shootX = cachedW * (0.25f + random.nextFloat() * 0.7f);
                shootY = cachedH * (0.02f + random.nextFloat() * 0.3f);
                shootLife = 0f;
            }
        } else {
            shootLife += dt;
            float k = shootLife / 1.1f;
            if (k >= 1f) { shootLife = -1f; shootWait = 3f + random.nextFloat() * 5f; }
            else {
                float dx = -cachedW * 0.22f, dy = cachedH * 0.14f;
                float hx = shootX + dx * k, hy = shootY + dy * k;
                ripplePaint.setStyle(Paint.Style.STROKE);
                ripplePaint.setStrokeCap(Paint.Cap.ROUND);
                ripplePaint.setStrokeWidth(3f);
                ripplePaint.setColor(Color.argb((int) (230 * (1f - k)), 255, 255, 255));
                canvas.drawLine(hx, hy, hx - dx * 0.35f, hy - dy * 0.35f, ripplePaint);
                starPaint.setColor(Color.argb((int) (255 * (1f - k)), 255, 255, 255));
                canvas.drawCircle(hx, hy, 3.5f, starPaint);
            }
        }
    }

    private void drawSunnyAtmosphere(Canvas canvas, float dt) {
        sunPulseTime += dt;
        float sunCenterX = cachedW * 0.35f;
        float sunCenterY = cachedH * 0.05f;

        // Animated sun core glow
        float pulse = (float) Math.sin(sunPulseTime * 1.2f) * 0.05f;
        float coreRadius = cachedW * (0.35f + pulse);
        RadialGradient sunRadial = new RadialGradient(
                sunCenterX, sunCenterY, coreRadius,
                new int[]{0xAAFFFFFF, 0x44FFE082, 0x15FFB300, 0x001E88E5},
                new float[]{0f, 0.25f, 0.55f, 1f},
                Shader.TileMode.CLAMP
        );
        sunGlowPaint.setShader(sunRadial);
        canvas.drawCircle(sunCenterX, sunCenterY, coreRadius, sunGlowPaint);

        // Optical Lens Flare Rings & Disks (Apple Weather style)
        // Flare 1: warm cyan-gold ring
        float flare1X = sunCenterX + (cachedW * 0.12f) + (float) Math.sin(sunPulseTime * 0.8f) * 8f;
        float flare1Y = sunCenterY + (cachedH * 0.18f);
        flarePaint.setColor(0x2280D8FF);
        canvas.drawCircle(flare1X, flare1Y, cachedW * 0.08f, flarePaint);

        // Flare 2: soft warm amber disc
        float flare2X = sunCenterX + (cachedW * 0.24f);
        float flare2Y = sunCenterY + (cachedH * 0.36f);
        flarePaint.setColor(0x18FFE082);
        canvas.drawCircle(flare2X, flare2Y, cachedW * 0.05f, flarePaint);

        // Flare 3: subtle violet halo near bottom
        float flare3X = sunCenterX + (cachedW * 0.38f);
        float flare3Y = sunCenterY + (cachedH * 0.58f);
        flarePaint.setColor(0x14CE93D8);
        canvas.drawCircle(flare3X, flare3Y, cachedW * 0.14f, flarePaint);
    }

    private void drawNightSky(Canvas canvas, float dt) {
        // Moon radial glow at top right
        float moonX = cachedW * 0.85f;
        float moonY = cachedH * 0.14f;
        float moonRadius = cachedW * 0.15f;
        RadialGradient moonRadial = new RadialGradient(
                moonX, moonY, moonRadius,
                new int[]{0x35E0F2FE, 0x1590CAF9, 0x000D1728},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP
        );
        sunGlowPaint.setShader(moonRadial);
        canvas.drawCircle(moonX, moonY, moonRadius, sunGlowPaint);

        // Twinkling Stars
        for (Star s : stars) {
            s.phase += s.speed * dt;
            float twinkle = (float) ((Math.sin(s.phase) + 1.0) / 2.0);
            int alpha = (int) (s.baseAlpha * (0.4f + 0.6f * twinkle));
            starPaint.setColor(Color.argb(alpha, 255, 255, 255));
            canvas.drawCircle(s.x, s.y, s.size, starPaint);
        }
    }

    private void drawClouds(Canvas canvas, float dt) {
        for (CloudPuff p : cloudPuffs) {
            p.x += p.speed * 60f * dt * (1f + windStrength * 3f);
            if (p.x - p.rx > cachedW) {
                p.x = -p.rx;
            }

            // Over a wallpaper the haze is fainter than a whole sky.
            int a = overlayMode && currentCondition != ConditionType.FOG ? (int) (p.alpha * 0.6f) : p.alpha;
            RadialGradient cloudGrad = new RadialGradient(
                    p.x, p.y, Math.max(p.rx, p.ry),
                    new int[]{Color.argb(a, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
            cloudPaint.setShader(cloudGrad);
            canvas.drawOval(p.x - p.rx, p.y - p.ry, p.x + p.rx, p.y + p.ry, cloudPaint);
        }
    }

    private void drawSnow(Canvas canvas, float dt) {
        for (int i = 0; i < flakes.size(); i++) {
            Flake f = flakes.get(i);
            f.y += f.speed * 60f * dt;
            f.phase += dt * 1.2f;
            f.x += (float) Math.sin(f.phase) * 0.6f;
            if (f.y > cachedH + 6) {
                f.y = -6;
                f.x = random.nextFloat() * cachedW;
            }
            rainPaint.setStyle(Paint.Style.FILL);
            rainPaint.setColor(Color.argb(f.alpha, 255, 255, 255));
            canvas.drawCircle(f.x, f.y, f.r, rainPaint);
            rainPaint.setStyle(Paint.Style.STROKE);
        }
    }

    private void drawRainAndRipples(Canvas canvas, float dt, boolean heavy) {
        float angleTan = 0.10f + windStrength * 0.40f + (heavy ? 0.10f : 0f); // slant grows with the wind
        if (overlayMode) {
            // Rain darkens the picture a little; the heavier, the darker.
            int wash = heavy ? 70 : (currentCondition == ConditionType.RAIN ? (heavyRain ? 60 : 28) : 0);
            if (wash > 0) canvas.drawColor(Color.argb(wash, 6, 12, 24));
        }

        for (int i = 0; i < rainDrops.size(); i++) {
            RainDrop drop = rainDrops.get(i);
            drop.y += drop.speed * 60f * dt;
            drop.x += (drop.speed * 60f * dt) * angleTan;

            // Draw streak
            rainPaint.setStrokeWidth(drop.width);
            rainPaint.setColor(Color.argb(drop.alpha, 220, 235, 255));
            canvas.drawLine(
                    drop.x,
                    drop.y,
                    drop.x + (drop.length * angleTan),
                    drop.y + drop.length,
                    rainPaint
            );

            // Check if drop hit bottom of screen
            if (drop.y > cachedH) {
                // Occasional splash ripple on screen bottom floor
                if (ripples.size() < 16 && random.nextFloat() < 0.25f) {
                    SplashRipple r = new SplashRipple();
                    r.x = drop.x;
                    r.y = cachedH - (random.nextFloat() * 30f);
                    r.radius = 1f;
                    r.maxRadius = 6f + random.nextFloat() * 10f;
                    r.alpha = 80;
                    ripples.add(r);
                }

                // Reset drop to top of sky
                drop.y = -drop.length - random.nextFloat() * 40f;
                drop.x = random.nextFloat() * cachedW * 1.2f - (cachedW * 0.1f);
            }
        }

        // Draw and update splash ripples
        for (int i = ripples.size() - 1; i >= 0; i--) {
            SplashRipple r = ripples.get(i);
            r.radius += 24f * dt;
            float progress = r.radius / r.maxRadius;
            if (progress >= 1.0f) {
                ripples.remove(i);
            } else {
                int currentAlpha = (int) (r.alpha * (1f - progress));
                ripplePaint.setColor(Color.argb(currentAlpha, 220, 240, 255));
                canvas.drawOval(
                        r.x - r.radius * 1.6f,
                        r.y - r.radius * 0.6f,
                        r.x + r.radius * 1.6f,
                        r.y + r.radius * 0.6f,
                        ripplePaint
                );
            }
        }
    }

    private void drawLightning(Canvas canvas, long now, float dt) {
        if (now >= nextLightningTime && lightningAlpha <= 0f) {
            // Trigger lightning flash
            lightningAlpha = 0.95f;
            nextLightningTime = now + 3000 + random.nextInt(7000);
            // Sheet lightning lights up the clouds with no bolt; forked bolts and double flashes are also common.
            sheetOnly = random.nextFloat() < 0.3f;
            secondFlash = random.nextFloat() < 0.4f;
            generateLightningPath();
            hasActiveBolt = !sheetOnly;
        }

        if (lightningAlpha > 0f) {
            // Screen flash wash
            int flashColor = Color.argb((int) (lightningAlpha * 140), 225, 240, 255);
            canvas.drawColor(flashColor);

            // Draw bolt
            if (hasActiveBolt && lightningAlpha > 0.4f) {
                lightningPaint.setStrokeWidth(3f);
                lightningPaint.setStyle(Paint.Style.STROKE);
                lightningPaint.setColor(Color.argb((int) (lightningAlpha * 230), 255, 255, 255));
                canvas.drawPath(lightningPath, lightningPaint);
            }

            // Rapid decay
            lightningAlpha -= dt * 4.5f;
            if (lightningAlpha <= 0f) {
                lightningAlpha = 0f;
                hasActiveBolt = false;
                if (secondFlash) { // the quick second flash, a little weaker
                    secondFlash = false;
                    lightningAlpha = 0.7f;
                    hasActiveBolt = !sheetOnly;
                }
            }
        }
    }

    private void generateLightningPath() {
        lightningPath.reset();
        float startX = cachedW * (0.25f + random.nextFloat() * 0.5f);
        float startY = 0;
        lightningPath.moveTo(startX, startY);

        float curX = startX;
        float curY = startY;
        int segments = 10 + random.nextInt(6);
        float segHeight = (cachedH * 0.65f) / segments;

        for (int i = 0; i < segments; i++) {
            curY += segHeight + (random.nextFloat() * 8f - 4f);
            curX += (random.nextFloat() * 60f - 30f);
            lightningPath.lineTo(curX, curY);

            // Occasional fork branch
            if (random.nextFloat() < 0.35f) {
                float forkX = curX + (random.nextFloat() * 50f - 25f);
                float forkY = curY + (segHeight * 0.7f);
                lightningPath.moveTo(curX, curY);
                lightningPath.lineTo(forkX, forkY);
                lightningPath.moveTo(curX, curY);
            }
        }
    }
}

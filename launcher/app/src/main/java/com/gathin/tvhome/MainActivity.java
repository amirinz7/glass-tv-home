package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gathin.tvhome.qrcode.QrBitmapUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    private static java.lang.ref.WeakReference<MainActivity> sInstance;

    public static MainActivity getInstance() {
        return sInstance != null ? sInstance.get() : null;
    }

    public static boolean isForeground() {
        MainActivity inst = getInstance();
        return inst != null && !inst.isFinishing() && !inst.isDestroyed() && !inst.hiddenSinceResume;
    }

    public static void openSettingsFromGlobal() {
        MainActivity inst = getInstance();
        if (inst != null) {
            inst.runOnUiThread(() -> {
                if (inst.screensaverActive) inst.dismissScreensaver();
                inst.showSettingsPopup();
            });
        }
    }

    private boolean isHomeLongPressed = false;
    private final Runnable longPressHomeRunnable = new Runnable() {
        @Override
        public void run() {
            isHomeLongPressed = true;
            SoundEffects.playSelect();
            if (screensaverActive) dismissScreensaver();
            showSettingsPopup();
        }
    };

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private DockDecoration tray;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    /** The focus: one dark shape gliding between tiles and pills (see Glider). */
    private Glider glider;
    /** Whether the glider is on the top menu, so a move down travels dark. */
    private boolean gliderOnPill;
    private final int[] pillAt = new int[2], rootAt = new int[2];
    /** The top-right buttons and the two rectangles used to line them up with the dock. */
    private View topRow, topSettings;
    private final Rect alignCard = new Rect(), alignButton = new Rect();
    /** How much of the space between the side paddings the top shelf may use. */
    private static final float HERO_WIDTH_SHARE = 0.40f;
    /** The name is one line, and may run wider than the description, up to this share (capped to avoid overlapping Islamic shelf). */
    private static final float HERO_NAME_WIDTH_SHARE = 0.45f;
    private static final float HERO_TITLE_MAX_SP = 40f;
    /** The breathing room between the shelf's last line and the top of the app container (50dp gives airy, balanced spacing). */
    private static final float HERO_GAP_DP = 50f;
    private static final float HERO_TITLE_MIN_SP = 26f;
    /** The shelf's width in pixels, worked out once from the screen. */
    private int heroMaxWidth, heroNameMaxWidth;
    private View heroShelf;
    private TextView heroName;
    private TextView heroDesc;
    private TextView heroWhere;
    /** The place on screen and a few lines about it; null for the user's own wallpapers. */
    private String placeName;
    private String placeText = "", placeCountry = "";
    private int ambientColor = -1;
    private boolean heroShown;
    /** The glow behind the tiles. It follows every app, while the shelf only names dock apps. */
    private boolean ambientShown;

    /** The colour the glass is blending from, how far along it is, and the blend itself. */
    private int glowFrom;
    private float glowMix = 1f;
    private android.animation.ValueAnimator glowBlend;
    private Bitmap ambientShape;
    /** Where the shelf's bottom edge belongs; its top follows once the lines have been measured. */
    private float heroBottom;
    private View btnInputs;
    private TextView btnWifi;
    private View btnCalendar;
    private View btnControl;
    private View btnAssistant;
    private View btnCandyBox;
    private View badgeSettingsUpdate;
    private TextView ampmView, dateView, greetingView;
    private TextView shamsiDate;
    private TextView prayerTimesView;
    private TextView weatherAirQualityView;
    private View nowPlayingCard;
    private ImageView cardAlbumArt;
    private View cardTrackInfo;
    private TextView cardSubtitle;
    private TextView cardTitle;
    private ImageView btnInlinePrev;
    private ImageView btnInlinePlayPause;
    private ImageView btnInlineNext;
    private ImageView btnInlineClose;
    private boolean nowPlayingDisabledByUser = false;
    private String lastPlayedTrackKey = "";
    private NowPlayingManager.Listener nowPlayingListener;
    private View islamicShelf;
    private View ayahBlock;
    private TextView ayahLabel, ayahArabic, ayahText, prayerHeader;
    private boolean ayahRunning;
    private boolean homeWeatherRunning;
    private TextView lblSubuh, lblSyuruk, lblZohor, lblAsar, lblMaghrib, lblIsyak;
    private TextView timeSubuh, timeSyuruk, timeZohor, timeAsar, timeMaghrib, timeIsyak;
    private boolean shamsi;
    private String vpnApp;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private boolean tilesAnimatedOnce;
    /** Grid rows left showing under the dock (config rows_below_dock). */
    private int rowsBelowDockSetting;
    /** The longest Home holds its first frame while it puts itself together (see holdFirstFrame). */
    private static final long FIRST_FRAME_WAIT_MS = 1200;
    private static final long RETURN_SETTLE_MS = 6000;
    /** True while Home rebuilds on return; focus passes through the pills then and must not light them. */
    private boolean resuming;
    /** True while Home's first frame is held back (see holdFirstFrame). */
    private boolean firstFrameHeld;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Rotating wallpaper (Config.wallpapers), the view that shows it and the bitmap on screen. */
    private List<File> wallpapers = Collections.emptyList();
    private long wallpaperIntervalMs;
    /** Decodes the next picture off the main thread, so a switch never stutters the D-pad animations. */
    private final java.util.concurrent.ExecutorService wallDecoder = java.util.concurrent.Executors.newSingleThreadExecutor();
    private long wallShownAt;
    private android.widget.ImageView wallView;
    private android.graphics.Bitmap wallBitmap;
    /** Dynamic atmospheric weather canvas particle overlay over the wallpaper */
    private WeatherBackgroundView weatherOverlayView;
    String testWeatherOverride = null;
    /** Settings kept for the places refresh, which runs after onCreate. */
    private Config config;
    /** Pictures of places are on (see PlaceWallpapers); they are dimmed behind the tiles. */
    private boolean placesMode;
    private volatile boolean placesRefreshRunning;
    private static final long SCREENSAVER_TIMEOUT_MS = 10L * 60 * 1000L; // 10 minutes idle -> screensaver
    private static final long TV_SLEEP_TIMEOUT_MS = SCREENSAVER_TIMEOUT_MS + (2L * 60 * 60 * 1000L); // 2 hours later -> TV off
    private long lastUserInteraction = android.os.SystemClock.elapsedRealtime();
    private boolean screensaverActive = false;
    private View screensaverView;
    private boolean wallpaperFullscreen = false;
    private View mainContent;
    private static final long DOUBLE_BACK_DELAY_MS = 350L;
    private Runnable singleBackRunnable = null;
    private boolean doubleBackPending = false;
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadApps(false);
        }
    };
    /** Moves the ayat / doa / zikir box on every 30 seconds. */
    private final Runnable ayahRotate = new Runnable() {
        @Override
        public void run() {
            try {
                if (islamicShelf != null && islamicShelf.getVisibility() == View.VISIBLE && isIslamicShelfEnabled()
                        && WeeklyAyah.shown(MainActivity.this) && MinbarManager.getDisplayMode(MainActivity.this) != MinbarManager.MODE_MINBAR) {
                    WeeklyAyah.Ayah a = WeeklyAyah.stored(MainActivity.this);
                    if (a == null) a = WeeklyAyah.last(MainActivity.this);
                    showAyah(a);
                }
            } catch (Exception ignored) { }
            long now = System.currentTimeMillis();
            long step = ZikirList.intervalMs(MainActivity.this);
            handler.postDelayed(this, step - (now % step) + 50L);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateShamsiDate();
            updatePrayerTimes();
            updateDateAndPeriod();
            updateWifiLabel();
            updateWeatherAqi();
            WeatherAirQuality.syncIfDue(MainActivity.this, MainActivity.this::updateWeatherAqi);
            updateIslamicShelf();
            refreshPlacesIfDue();
            refreshOnlineWallpapersIfDue();
            checkInactivity();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    private final Runnable nowPlayingPoll = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                NowPlayingManager.updateCurrentMedia(MainActivity.this);
                handler.postDelayed(this, 3000);
            }
        }
    };

    public boolean isWeatherWidgetEnabled() {
        return getSharedPreferences("theme_prefs", MODE_PRIVATE).getBoolean("weather_widget_enabled", true);
    }

    public void setWeatherWidgetEnabled(boolean enabled) {
        getSharedPreferences("theme_prefs", MODE_PRIVATE).edit().putBoolean("weather_widget_enabled", enabled).apply();
        if (weatherAirQualityView != null) {
            weatherAirQualityView.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = new java.lang.ref.WeakReference<>(this);
        SoundEffects.init(this);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        COLUMNS = cfg.columns;
        reducedMotion = cfg.reducedMotion;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        root = findViewById(R.id.root);
        // The tiles' entrance plays once per TV boot. Android reclaims the launcher during long stays
        // in big apps; starting again then is a return to Home, not a first launch.
        long bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        android.content.SharedPreferences launch = getSharedPreferences("launch", MODE_PRIVATE);
        if (Math.abs(launch.getLong("boot_at", 0) - bootAt) < 60_000) tilesAnimatedOnce = true;
        else launch.edit().putLong("boot_at", bootAt).apply();
        ensureSystemScreensaverAndSleepSettings();
        root.setBackgroundColor(cfg.backgroundColor);
        config = cfg;
        wallpapers = cfg.wallpapers;
        wallpaperIntervalMs = getWallpaperIntervalMs();
        placesMode = cfg.places;
        mainContent = findViewById(R.id.mainContent);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        // Leaving the app tiles (settings, Wi-Fi, football, prayer times ...) puts the home wallpaper back.
        findViewById(android.R.id.content).getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            if (!appWallActive || newF == null || grid == null) return;
            boolean inGrid = false;
            for (android.view.ViewParent pr = newF.getParent(); pr != null; pr = pr.getParent()) {
                if (pr == grid) { inGrid = true; break; }
            }
            if (!inGrid) scheduleAppWallpaper(null, "");
        });
        Backdrop.setWorker(wallDecoder, handler);
        heroShelf = findViewById(R.id.heroShelf);
        heroName = findViewById(R.id.heroName);
        heroDesc = findViewById(R.id.heroDesc);
        heroWhere = findViewById(R.id.heroWhere);
        int usable = getResources().getDisplayMetrics().widthPixels - (int) ((55 + 56) * density);
        heroMaxWidth = Math.round(usable * HERO_WIDTH_SHARE);
        heroNameMaxWidth = Math.round(usable * HERO_NAME_WIDTH_SHARE);
        if (heroName != null) heroName.setMaxWidth(heroNameMaxWidth);
        if (heroDesc != null) heroDesc.setMaxWidth(heroMaxWidth);

        String activeCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Malaysia");
        if ("Custom".equalsIgnoreCase(activeCat)) {
            placesMode = false;
            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            boolean singleMode = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("custom_single_mode", false);
            String singleFile = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("custom_single_file", null);
            if (singleMode && singleFile != null) {
                File sf = new File(customDir, singleFile);
                if (sf.exists() && sf.length() > 0) {
                    wallpapers = Collections.singletonList(sf);
                } else if (files != null && files.length > 0) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    wallpapers = new ArrayList<>(Arrays.asList(files));
                }
            } else if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
            } else if (cfg.wallpaperOnline && wallpapers.isEmpty()) {
                wallpapers = OnlineWallpapers.list(this);
            }
        } else {
            placesMode = false;
            List<File> catFiles = OnlineWallpapers.listByCategory(this, activeCat);
            if (!catFiles.isEmpty()) wallpapers = catFiles;
            else if (cfg.wallpaperOnline && wallpapers.isEmpty()) wallpapers = OnlineWallpapers.list(this);
            if (wallpapers.isEmpty()) {
                new Thread(() -> {
                    OnlineWallpapers.syncCategory(getApplicationContext(), activeCat);
                    handler.post(() -> {
                        if (isDestroyed()) return;
                        List<File> fresh = OnlineWallpapers.listByCategory(MainActivity.this, activeCat);
                        if (!fresh.isEmpty()) {
                            wallpapers = fresh;
                            showNextWallpaper(false);
                        }
                    });
                }).start();
            }
        }
        if (!wallpapers.isEmpty()) showNextWallpaper(false);
        else if (cfg.wallpaper != null && !placesMode) setWallpaper(decodeWallpaper(cfg.wallpaper), false);
        if (!isWallpaperDescEnabled() && heroShelf != null) {
            heroShelf.setVisibility(View.GONE);
            heroShelf.setAlpha(0f);
        }
        // The shelf grows and shrinks with the name and the description, so it hangs from its
        // bottom edge: every time it is measured, its top is worked out again. Positioning it by
        // the height it happened to have beforehand is what let a tall block run into the dock.
        heroShelf.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            if (heroBottom > 0) v.setY(heroBottom - v.getHeight());
        });
        ampmView = findViewById(R.id.ampm);
        dateView = findViewById(R.id.date);
        greetingView = findViewById(R.id.greeting);
        updateDateAndPeriod();
        weatherAirQualityView = findViewById(R.id.weatherAirQuality);
        if (weatherAirQualityView != null) {
            weatherAirQualityView.setVisibility(isWeatherWidgetEnabled() ? View.VISIBLE : View.GONE);
            weatherAirQualityView.setOnClickListener(v -> {
                startActivity(new Intent(this, WeatherActivity.class));
            });
            weatherAirQualityView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (sportsCard != null && sportsCard.getVisibility() == View.VISIBLE
                            && sportsCard.getChildCount() > 1 && !appsScrolledDown) {
                        sportsCard.getChildAt(1).requestFocus();
                        return true;
                    }
                    if (nowPlayingCard != null && nowPlayingCard.getVisibility() == View.VISIBLE) {
                        if (cardTrackInfo != null) cardTrackInfo.requestFocus();
                        else nowPlayingCard.requestFocus();
                        return true;
                    }
                    focusTile(0);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    View target = (btnInputs != null && btnInputs.getVisibility() == View.VISIBLE) ? btnInputs
                            : (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) ? btnWifi
                            : findViewById(R.id.btnSettings);
                    if (target != null) {
                        target.requestFocus();
                        return true;
                    }
                }
                return false;
            });
            weatherAirQualityView.setOnFocusChangeListener((v, hasFocus) -> {
                float scale = hasFocus ? 1.05f : 1.0f;
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
            });
        }
        updateWeatherAqi();
        WeatherAirQuality.syncIfDue(this, this::updateWeatherAqi);

        // Now Playing Widget Card (matching bravia-glass-tv UI)
        nowPlayingCard = findViewById(R.id.nowPlayingCard);
        if (nowPlayingCard != null) {
            cardAlbumArt = findViewById(R.id.cardAlbumArt);
            cardTrackInfo = findViewById(R.id.cardTrackInfo);
            cardSubtitle = findViewById(R.id.cardSubtitle);
            cardTitle = findViewById(R.id.cardTitle);
            btnInlinePrev = findViewById(R.id.btnInlinePrev);
            btnInlinePlayPause = findViewById(R.id.btnInlinePlayPause);
            btnInlineNext = findViewById(R.id.btnInlineNext);
            btnInlineClose = findViewById(R.id.btnInlineClose);

            View.OnClickListener openDialogClick = v -> {
                SoundEffects.playSelect();
                showNowPlayingDialog();
            };
            if (cardAlbumArt != null) cardAlbumArt.setOnClickListener(openDialogClick);
            if (cardTrackInfo != null) cardTrackInfo.setOnClickListener(openDialogClick);

            if (btnInlinePlayPause != null) {
                btnInlinePlayPause.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    NowPlayingManager.togglePlayPause();
                    handler.postDelayed(() -> NowPlayingManager.updateCurrentMedia(MainActivity.this), 250);
                });
            }
            if (btnInlineNext != null) {
                btnInlineNext.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    NowPlayingManager.skipToNext();
                    handler.postDelayed(() -> NowPlayingManager.updateCurrentMedia(MainActivity.this), 300);
                });
            }
            if (btnInlinePrev != null) {
                btnInlinePrev.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    NowPlayingManager.skipToPrevious();
                    handler.postDelayed(() -> NowPlayingManager.updateCurrentMedia(MainActivity.this), 300);
                });
            }
            if (btnInlineClose != null) {
                btnInlineClose.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    disableNowPlaying();
                });
            }

            View.OnFocusChangeListener inlineFocus = (v, hasFocus) -> {
                if (hasFocus) SoundEffects.playNav();
            };
            if (cardAlbumArt != null) cardAlbumArt.setOnFocusChangeListener(inlineFocus);
            if (cardTrackInfo != null) cardTrackInfo.setOnFocusChangeListener(inlineFocus);
            if (btnInlinePrev != null) btnInlinePrev.setOnFocusChangeListener(inlineFocus);
            if (btnInlinePlayPause != null) btnInlinePlayPause.setOnFocusChangeListener(inlineFocus);
            if (btnInlineNext != null) btnInlineNext.setOnFocusChangeListener(inlineFocus);
            if (btnInlineClose != null) btnInlineClose.setOnFocusChangeListener(inlineFocus);

            View.OnKeyListener inlineKey = (v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusTile(0);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                        weatherAirQualityView.requestFocus();
                        return true;
                    }
                }
                return false;
            };
            if (cardAlbumArt != null) cardAlbumArt.setOnKeyListener(inlineKey);
            if (cardTrackInfo != null) cardTrackInfo.setOnKeyListener(inlineKey);
            if (btnInlinePrev != null) btnInlinePrev.setOnKeyListener(inlineKey);
            if (btnInlinePlayPause != null) btnInlinePlayPause.setOnKeyListener(inlineKey);
            if (btnInlineNext != null) btnInlineNext.setOnKeyListener(inlineKey);
            if (btnInlineClose != null) btnInlineClose.setOnKeyListener(inlineKey);
        }
        NowPlayingManager.init(this);
        nowPlayingListener = this::updateNowPlayingUi;
        NowPlayingManager.registerListener(nowPlayingListener);

        islamicShelf = findViewById(R.id.islamicShelf);
        if (islamicShelf != null) {
            ayahBlock = findViewById(R.id.ayahBlock);
            if (ayahBlock != null) {
                ayahBlock.setFocusable(true);
                ayahBlock.setClickable(true);
                ayahBlock.setOnClickListener(v -> {
                    SoundEffects.playSelect();
                    showIslamicDetailDialog();
                });
                ayahBlock.setOnFocusChangeListener((v, hasFocus) -> {
                    setAyahBoxLook(hasFocus);
                    if (hasFocus) SoundEffects.playNav();
                    float scale = hasFocus ? 1.03f : 1.0f;
                    v.animate().scaleX(scale).scaleY(scale).setDuration(150).start();
                });
                ayahBlock.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        View pt = findViewById(R.id.prayerTable);
                        if (pt != null && pt.getVisibility() == View.VISIBLE) {
                            pt.requestFocus();
                            return true;
                        }
                        focusTile(Math.min(adapter != null ? adapter.getItemCount() - 1 : 3, 3));
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        View settings = findViewById(R.id.btnSettings);
                        if (settings != null) {
                            settings.requestFocus();
                            return true;
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                            weatherAirQualityView.requestFocus();
                            return true;
                        }
                    }
                    return false;
                });
            }
            ayahLabel = findViewById(R.id.ayahLabel);
            ayahArabic = findViewById(R.id.ayahArabic);
            ayahText = findViewById(R.id.ayahText);
            ayahRumi = findViewById(R.id.ayahRumi);
            setAyahBoxLook(false);
            prayerHeader = findViewById(R.id.prayerHeader);

            int uWidth = getResources().getDisplayMetrics().widthPixels - (int) ((55 + 56) * density);
            int iMaxWidth = Math.round(uWidth * 0.38f);
            if (ayahArabic != null) ayahArabic.setMaxWidth(iMaxWidth);
            if (ayahText != null) ayahText.setMaxWidth(iMaxWidth);

            View prayerTableView = findViewById(R.id.prayerTable);
            if (prayerTableView != null) {
                prayerTableView.setFocusable(true);
                prayerTableView.setClickable(true);
                prayerTableView.setOnClickListener(v -> startActivity(new Intent(this, PrayerActivity.class)));
                prayerTableView.setOnKeyListener((v, keyCode, ev) -> {
                    if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP && ayahBlock != null && ayahBlock.getVisibility() == View.VISIBLE) {
                        ayahBlock.requestFocus();
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        View settings = findViewById(R.id.btnSettings);
                        if (settings != null) { settings.requestFocus(); return true; }
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        focusTile(Math.min(adapter != null ? adapter.getItemCount() - 1 : 3, 3));
                        return true;
                    }
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        if (sportsCard != null && sportsCard.getVisibility() == View.VISIBLE && sportsCard.getChildCount() > 1) {
                            sportsCard.getChildAt(1).requestFocus();
                            return true;
                        }
                        if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                            weatherAirQualityView.requestFocus();
                            return true;
                        }
                    }
                    return false;
                });
                prayerTableView.setOnFocusChangeListener((v, has) -> {
                    android.graphics.drawable.GradientDrawable g = null;
                    if (has) {
                        g = new android.graphics.drawable.GradientDrawable();
                        g.setColor(0x14FFFFFF);
                        g.setCornerRadius(16 * density);
                        g.setStroke((int) (1.5f * density), 0xCCFFFFFF);
                    }
                    v.setBackground(g);
                });
            }
            lblSubuh = findViewById(R.id.lblSubuh);
            lblSyuruk = findViewById(R.id.lblSyuruk);
            lblZohor = findViewById(R.id.lblZohor);
            lblAsar = findViewById(R.id.lblAsar);
            lblMaghrib = findViewById(R.id.lblMaghrib);
            lblIsyak = findViewById(R.id.lblIsyak);

            timeSubuh = findViewById(R.id.timeSubuh);
            timeSyuruk = findViewById(R.id.timeSyuruk);
            timeZohor = findViewById(R.id.timeZohor);
            timeAsar = findViewById(R.id.timeAsar);
            timeMaghrib = findViewById(R.id.timeMaghrib);
            timeIsyak = findViewById(R.id.timeIsyak);

            islamicShelf.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
                if (heroBottom > 0) v.setY(heroBottom - v.getHeight());
            });
            updateIslamicShelf();
        }

        btnWifi = findViewById(R.id.btnWifi);
        if (!cfg.showNetworkPill) btnWifi.setVisibility(View.GONE);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = cfg.shamsiDate;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        prayerTimesView = findViewById(R.id.prayerTimes);
        if (prayerTimesView != null) {
            prayerTimesView.setOnClickListener(v -> showPrayerPopup());
            prayerTimesView.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    View inputs = findViewById(R.id.btnInputs);
                    View wifi = findViewById(R.id.btnWifi);
                    View settings = findViewById(R.id.btnSettings);
                    if (inputs != null && inputs.getVisibility() == View.VISIBLE) inputs.requestFocus();
                    else if (wifi != null && wifi.getVisibility() == View.VISIBLE) wifi.requestFocus();
                    else if (settings != null) settings.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusTile(lastFocused % COLUMNS);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    showPrayerPopup();
                    return true;
                }
                return false;
            });
        }
        updatePrayerTimes();
        PrayerTimes.syncIfDue(this, this::updatePrayerTimes);
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                // The dock's line is set before any row is placed. Set afterwards, the grid kept its
                // rows where the first layout had put them, and Home came back with the dock and its
                // tray hundreds of pixels up the screen until focus slid them down.
                parkDock();
                super.onLayoutChildren(recycler, state);
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        tray = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(tray);
        // The top-right buttons line up with the dock, which is only known once the tiles are laid out.
        grid.getViewTreeObserver().addOnGlobalLayoutListener(this::alignTopButtons);
        // Glass shows the blurred wallpaper behind it: the dock tray (drawn by the grid) and the
        // top-bar pills redraw when a new blurred copy is ready.
        Backdrop.watch(grid);
        setUpGlider();
        holdFirstFrame();
        for (int i = 0; i < grid.getItemDecorationCount(); i++) {
            if (grid.getItemDecorationAt(i) instanceof DockDecoration) {
                ((DockDecoration) grid.getItemDecorationAt(i)).setGlider(glider);
            }
        }
        // The grid only shows the shape on the tray.
        glider.addCompanion(grid, tray.area);
        for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnCalendar, R.id.btnAssistant, R.id.btnControl, R.id.btnCandyBox, R.id.btnSettings, R.id.btnPower}) {
            View pill = findViewById(id);
            if (pill == null) continue;
            float radius = 21f * density;
            pill.setBackground(new GlassDrawable(pill, radius, density, glider));
            glider.addCompanion(pill);
            Backdrop.watch(pill);
            pill.setOnFocusChangeListener((v, has) -> {
                if (has) moveTopHighlight(v);
            });
        }
        if (prayerTimesView != null) {
            prayerTimesView.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    SoundEffects.playNav();
                    glider.hide();
                }
            });
        }
        // The apps rise into place on the first launch after the TV starts, and only then. Setting
        // the animation arms it for the grid's first draw, so on any later fresh start (Android
        // closing Home while an app was open) it is not set at all: armed, it drew the dock's tray
        // empty while the apps faded in.
        if (!tilesAnimatedOnce) {
            int enterAnim = reducedMotion ? R.anim.tile_enter_reduced : R.anim.tile_enter;
            float enterDelay = reducedMotion ? 0f : 0.06f;
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, enterAnim), enterDelay));
        }
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        rowsBelowDockSetting = cfg.rowsBelowDock;
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            parkDock();
            updateTopFade();
        });

        // Rows feather out under the top bar instead of being cut off. The fade needs an offscreen
        // layer and draws the grid twice, on every frame of every animation, so it is on only while a
        // row is actually in the band: it switches on in the same frame a row scrolls into it.
        gridWrap = findViewById(R.id.gridWrap);

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateTopFade();
                boolean away = rv.computeVerticalScrollOffset() > Math.round(8 * density);
                if (away != appsScrolledDown) {
                    appsScrolledDown = away;
                    if (sportsCard != null) {
                        sportsCard.animate().cancel();
                        sportsCard.animate().alpha(away ? 0f : 1f).setDuration(150).start();
                    }
                }
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glider.scrolled(dy);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (reducedMotion) {
                // Stiff and almost critically damped: settles in a few frames instead of oscillating,
                // and stops once the highlight is within 1.5 px of its tile.
                s.setSpring(new SpringForce().setStiffness(1400f).setDampingRatio(0.95f));
                s.setMinimumVisibleChange(1.5f);
            } else {
                // Critically damped: glides onto the tile and stops there, with no bounce back.
                s.setSpring(new SpringForce().setStiffness(900f).setDampingRatio(1.0f));
            }
        }

        btnInputs = findViewById(R.id.btnInputs);
        btnAssistant = findViewById(R.id.btnAssistant);
        btnCalendar = findViewById(R.id.btnCalendar);
        btnControl = findViewById(R.id.btnControl);
        if (btnControl != null) btnControl.setOnClickListener(v -> ModernSettingsDialog.showControl(this));
        if (btnCalendar != null) btnCalendar.setOnClickListener(v -> startActivity(new Intent(this, CalendarActivity.class)));
        btnCandyBox = findViewById(R.id.btnCandyBox);
        View btnSettings = findViewById(R.id.btnSettings);
        badgeSettingsUpdate = findViewById(R.id.badgeSettingsUpdate);
        updateSettingsBadge();
        View btnPower = findViewById(R.id.btnPower);
        if (btnPower != null) btnPower.setOnClickListener(v -> showPowerMenu());
        if (btnInputs != null) btnInputs.setOnClickListener(v -> showInputs());
        if (btnWifi != null) {
            btnWifi.setOnClickListener(v -> showWifiPopup());
            btnWifi.setOnLongClickListener(v -> {
                open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
                return true;
            });
        }
        if (btnAssistant != null) {
            btnAssistant.setOnClickListener(v -> triggerGoogleAssistant());
        }
        if (btnCandyBox != null) {
            btnCandyBox.setOnClickListener(v -> showCandyBoxPopup());
            btnCandyBox.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), view.getWidth() / 2f);
                }
            });
            btnCandyBox.setClipToOutline(true);
            updateTopBarUserIcon();
        }
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> showSettingsPopup());
            btnSettings.setOnLongClickListener(v -> {
                if (moduleCards) {
                    showModuleCards();
                    return true;
                }
                open(systemIntent(Settings.ACTION_SETTINGS));
                return true;
            });
        }

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                v.performClick();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (ayahBlock != null && ayahBlock.getVisibility() == View.VISIBLE) {
                    ayahBlock.requestFocus();
                    return true;
                }
                // Back to the dock tile Up came from, not wherever the focus finder lands.
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                // Along the row, in the order the buttons are drawn: Inputs, Wi-Fi, Calendar, mic, user, Settings, Power.
                View[] order = {btnInputs, btnWifi, btnCalendar, btnAssistant, btnCandyBox, btnControl, btnSettings, btnPower};
                int at = -1;
                for (int i = 0; i < order.length; i++) if (order[i] == v) at = i;
                int step = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                for (int j = at + step; at >= 0 && j >= 0 && j < order.length; j += step) {
                    if (order[j] != null && order[j].getVisibility() == View.VISIBLE) {
                        order[j].requestFocus();
                        return true;
                    }
                }
                if (step > 0) return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (v.getId() == R.id.btnPower) {
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnSettings) {
                    if (btnCandyBox != null && btnCandyBox.getVisibility() == View.VISIBLE) {
                        btnCandyBox.requestFocus();
                        return true;
                    }
                    if (btnAssistant != null && btnAssistant.getVisibility() == View.VISIBLE) {
                        btnAssistant.requestFocus();
                        return true;
                    }
                    if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                        btnWifi.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnCandyBox) {
                    if (btnAssistant != null && btnAssistant.getVisibility() == View.VISIBLE) {
                        btnAssistant.requestFocus();
                        return true;
                    }
                    if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                        btnWifi.requestFocus();
                        return true;
                    }
                    if (btnInputs != null && btnInputs.getVisibility() == View.VISIBLE) {
                        btnInputs.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnAssistant) {
                    if (btnWifi != null && btnWifi.getVisibility() == View.VISIBLE) {
                        btnWifi.requestFocus();
                        return true;
                    }
                    if (btnInputs != null && btnInputs.getVisibility() == View.VISIBLE) {
                        btnInputs.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnInputs || (v.getId() == R.id.btnWifi && (btnInputs == null || btnInputs.getVisibility() != View.VISIBLE))) {
                    if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                        weatherAirQualityView.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnInputs && prayerTimesView != null && prayerTimesView.getVisibility() == View.VISIBLE) {
                    prayerTimesView.requestFocus();
                    return true;
                }
                return v.getId() == R.id.btnInputs
                        || (v.getId() == R.id.btnWifi && (btnInputs == null || btnInputs.getVisibility() != View.VISIBLE));
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (v.getId() == R.id.btnPower) return true;
                if (v.getId() == R.id.btnSettings && btnPower != null && btnPower.getVisibility() == View.VISIBLE) {
                    btnPower.requestFocus();
                    return true;
                }
                if (v.getId() == R.id.btnWifi) {
                    if (btnAssistant != null && btnAssistant.getVisibility() == View.VISIBLE) {
                        btnAssistant.requestFocus();
                        return true;
                    }
                    if (btnCandyBox != null && btnCandyBox.getVisibility() == View.VISIBLE) {
                        btnCandyBox.requestFocus();
                        return true;
                    }
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnAssistant) {
                    if (btnCandyBox != null && btnCandyBox.getVisibility() == View.VISIBLE) {
                        btnCandyBox.requestFocus();
                        return true;
                    }
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
                if (v.getId() == R.id.btnCandyBox) {
                    if (btnSettings != null) {
                        btnSettings.requestFocus();
                        return true;
                    }
                }
                return v.getId() == R.id.btnSettings;
            }
            return false;
        };
        if (btnInputs != null) btnInputs.setOnKeyListener(pillKeys);
        if (btnWifi != null) btnWifi.setOnKeyListener(pillKeys);
        if (btnAssistant != null) btnAssistant.setOnKeyListener(pillKeys);
        if (btnCalendar != null) btnCalendar.setOnKeyListener(pillKeys);
        if (btnControl != null) btnControl.setOnKeyListener(pillKeys);
        if (btnCandyBox != null) btnCandyBox.setOnKeyListener(pillKeys);
        if (btnSettings != null) btnSettings.setOnKeyListener(pillKeys);
        if (btnPower != null) btnPower.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
            if (!onTile && (newF == null || !(isTopPill(newF) || inMenu(newF)))) glider.hide();
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }

        // Silent background update check after 5 seconds on launch
        handler.postDelayed(() -> AppUpdateManager.checkForUpdates(this, false), 5000);

        if (getIntent() != null && getIntent().getBooleanExtra("open_settings", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showSettingsPopup();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_candy_box", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showCandyBoxPopup();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_azan_settings", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showAzanSettingsDialog();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_clean_cache", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showCleanTvCacheDialog();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_wallpaper", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showWallpaperDialog();
            }, 100);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_now_playing", false)) {
            handler.postDelayed(() -> {
                if (screensaverActive) dismissScreensaver();
                showNowPlayingDialog();
            }, 100);
        }
    }

    /**
     * A theme's full-screen picture behind everything. Decoded once, downsampled to the screen so a 4K
     * JPEG costs a 1080p bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV.
     */
    private android.graphics.Bitmap decodeWallpaper(File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
            return android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the plain colour stays.
            return null;
        }
    }

    // ---- Wallpaper that follows the focused app (Netflix, Viu...) ----
    private String appWallPending;
    private android.graphics.Bitmap appWallSaved;
    /** The wallpaper the home screen itself is showing (rotation, places...), which an app picture never replaces. */
    private android.graphics.Bitmap baseWallBitmap;
    /** The wallpaper on screen, shared with the weather and prayer pages so they can sit on the same picture. */
    static android.graphics.Bitmap shownWallpaper;
    static boolean shownDim;
    /** The home wallpaper itself (never an app's picture), for the pages that open over it. */
    static android.graphics.Bitmap baseShown;
    static boolean baseDim;
    private boolean appWallActive;
    private boolean settingAppWall;
    private final java.util.Map<String, android.graphics.Bitmap> appWallCache = new java.util.HashMap<>();
    private final Runnable appWallTask = this::applyAppWallpaper;

    /** Waits a moment so scrolling past tiles does not flash pictures. */
    private String appWallLabel = "";

    private void scheduleAppWallpaper(String key, String label) {
        appWallPending = key != null && key.indexOf('/') > 0 ? key.substring(0, key.indexOf('/')) : null;
        appWallLabel = label == null ? "" : label;
        handler.removeCallbacks(appWallTask);
        handler.postDelayed(appWallTask, 120);
    }

    /**
     * A picture in app-wallpapers/ named after the app: the package name (com.netflix.ninja.jpg) or the app's
     * name as shown (netflix.jpg, youtubemusic.jpg, disney.jpg). Exact names only, so youtube.jpg is for YouTube
     * and never for YouTube Music.
     */
    private File appWallpaperFile(String pkg) {
        if (pkg == null) return null;
        File dir = new File(getExternalFilesDir(null), "app-wallpapers");
        File[] extFiles = dir.listFiles();
        // The pictures shipped inside the app are the fallback: they cannot be lost when the app is reinstalled.
        File[] built = builtinAppWallpapers();
        int ne = extFiles == null ? 0 : extFiles.length, nb = built == null ? 0 : built.length;
        if (ne + nb == 0) return null;
        File[] all = new File[ne + nb];
        if (extFiles != null) System.arraycopy(extFiles, 0, all, 0, ne);
        if (built != null) System.arraycopy(built, 0, all, ne, nb);
        // config.json may name the picture for an app: "app_wallpapers": {"Netflix": "netflix.png", "com.netflix.ninja": "netflix.png"}
        try {
            File cfgFile = new File(getExternalFilesDir(null), "config.json");
            if (cfgFile.isFile()) {
                org.json.JSONObject map = new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(cfgFile.toPath()), "UTF-8")).optJSONObject("app_wallpapers");
                if (map != null) {
                    String named = null;
                    java.util.Iterator<String> keys = map.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (k.equalsIgnoreCase(pkg) || (!appWallLabel.isEmpty() && k.equalsIgnoreCase(appWallLabel))) { named = map.optString(k); break; }
                    }
                    if (named != null && !named.isEmpty()) {
                        String want = named.toLowerCase(Locale.ROOT).replaceAll("\\.[a-z]+$", "");
                        for (File f : all) {
                            String fn = f.getName().toLowerCase(Locale.ROOT);
                            if (fn.equals(named.toLowerCase(Locale.ROOT)) || fn.replaceAll("\\.[a-z]+$", "").equals(want)) return f;
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        final String low = pkg.toLowerCase(Locale.ROOT);
        final String label = appWallLabel.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        File byLabel = null, byPackage = null, byLoose = null;
        final String lowPkg = low;
        for (File f : all) {
            String n = f.getName().toLowerCase(Locale.ROOT);
            int dot = n.lastIndexOf('.');
            if (dot <= 0) continue;
            String ext = n.substring(dot + 1);
            if (!ext.equals("jpg") && !ext.equals("jpeg") && !ext.equals("png")) continue;
            String base = n.substring(0, dot);
            String baseKey = base.replaceAll("[^a-z0-9]", "");
            if (baseKey.isEmpty()) continue;
            if (low.equals(base) || ("com.android.vending".equals(low) && (baseKey.equals("playstore") || baseKey.equals("googleplay") || baseKey.equals("googleplaystore")))) byPackage = f;
            else if (!label.isEmpty() && label.equals(baseKey)) byLabel = f;
            else if (!label.isEmpty() && baseKey.length() >= 3 && !baseKey.equals("youtube")
                    && wallCore(label).equals(wallCore(baseKey)) && byLoose == null) byLoose = f;
            else if (!label.isEmpty() && baseKey.length() >= 3 && !baseKey.equals("youtube")
                    && (label.startsWith(baseKey) || label.endsWith(baseKey) || baseKey.startsWith(label)) && byLoose == null) byLoose = f;
        }
        return byPackage != null ? byPackage : byLabel != null ? byLabel : byLoose;
    }

    private volatile boolean builtinCopied;

    /** Copies the app pictures packed in the APK (assets/app-wallpapers) to a folder once, so they can be decoded like any file. */
    private File[] builtinAppWallpapers() {
        File out = new File(getFilesDir(), "app-wallpapers-builtin");
        try {
            String[] names = getAssets().list("app-wallpapers");
            if (names == null) return null;
            if (!builtinCopied) {
                out.mkdirs();
                for (String n : names) {
                    File f = new File(out, n);
                    if (f.length() > 0) continue;
                    try (java.io.InputStream in = getAssets().open("app-wallpapers/" + n); java.io.OutputStream o = new java.io.FileOutputStream(f)) {
                        byte[] buf = new byte[16384]; int r;
                        while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
                    }
                }
                builtinCopied = true;
            }
        } catch (Exception e) { android.util.Log.w("TvHome", "built-in app wallpapers: " + e); }
        return out.listFiles();
    }

    /** An app's name without the filler words, so google-play, playstore and Google Play Store are the same. */
    private static String wallCore(String key) {
        String k = key.replace("google", "").replace("store", "").replace("plus", "").replace("tv", "")
                .replace("hotstar", "").replace("app", "");
        return k.isEmpty() ? key : k;
    }

    private void applyAppWallpaper() {
        final String pkg = appWallPending;
        final File file = appWallpaperFile(pkg);
        if (file == null) {
            // Not an app with its own picture: back to the wallpaper that was there before.
            if (appWallActive) {
                appWallActive = false;
                appWallSaved = null;
                final android.graphics.Bitmap back = baseWallBitmap != null ? baseWallBitmap : wallBitmap;
                if (back != null && back != wallBitmap) changeWallpaperSmoothly(back, pkg);
            }
            return;
        }
        android.graphics.Bitmap cached = appWallCache.get(file.getPath());
        if (cached != null) {
            showAppWallpaper(cached, pkg);
            return;
        }
        wallDecoder.execute(() -> {
            final android.graphics.Bitmap bmp = decodeWallpaper(file);
            handler.post(() -> {
                if (bmp == null || isDestroyed() || !java.util.Objects.equals(pkg, appWallPending)) return;
                if (appWallCache.size() >= 3) appWallCache.clear();
                appWallCache.put(file.getPath(), bmp);
                showAppWallpaper(bmp, pkg);
            });
        });
    }

    private void showAppWallpaper(android.graphics.Bitmap bmp, String pkg) {
        if (bmp == wallBitmap) return;
        if (!appWallActive) baseWallBitmap = wallBitmap;
        appWallActive = true;
        changeWallpaperSmoothly(bmp, pkg);
    }

    /** Makes the glass copy first, so the glass and the picture change together (no jump). */
    private void changeWallpaperSmoothly(final android.graphics.Bitmap bmp, final String pkg) {
        final android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        wallDecoder.execute(() -> {
            final android.graphics.Bitmap blur = Backdrop.blur(bmp, dm.widthPixels, dm.heightPixels, placesMode ? 0xFF7A7A7A : 0);
            handler.post(() -> {
                if (isDestroyed() || !java.util.Objects.equals(pkg, appWallPending)) return;
                settingAppWall = true;
                try { setWallpaper(bmp, true, blur); } finally { settingAppWall = false; }
            });
        });
    }

    /** Puts a picture behind everything, cross-fading from the previous one when asked. */
    private void setWallpaper(android.graphics.Bitmap bmp, boolean fade) {
        setWallpaper(bmp, fade, null);
    }

    /** With {@code blur} made in advance, the glass changes in step with the picture. */
    private void setWallpaper(android.graphics.Bitmap bmp, boolean fade, android.graphics.Bitmap blur) {
        if (bmp == null) return;
        if (!settingAppWall) {
            baseWallBitmap = bmp;
            baseShown = bmp;
            baseDim = placesMode;
            // The rotation moved on while an app picture is up: keep the app picture, return to this one later.
            if (appWallActive) return;
        }
        shownWallpaper = bmp;
        shownDim = placesMode;
        if (wallView == null) {
            wallView = new android.widget.ImageView(this);
            wallView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            // Photos are bright: dim them so the clock, names and tiles stay readable.
            if (placesMode) wallView.setColorFilter(0xFF7A7A7A, android.graphics.PorterDuff.Mode.MULTIPLY);
            root.addView(wallView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ensureWeatherOverlayView();
        }
        android.graphics.Bitmap old = wallBitmap;
        wallBitmap = bmp;
        if (fade && old != null && !reducedMotion) {
            // Cross-fade with two centre-cropped layers. (A TransitionDrawable stretches each picture to the
            // view, then snaps to centre-crop when it ends: that was the picture floating bigger, then shrinking.)
            final android.widget.ImageView ghost = new android.widget.ImageView(this);
            ghost.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            ghost.setImageBitmap(old);
            if (placesMode) ghost.setColorFilter(0xFF7A7A7A, android.graphics.PorterDuff.Mode.MULTIPLY);
            wallView.setImageBitmap(bmp);
            root.addView(ghost, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            ghost.animate().alpha(0f).setDuration(900)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.4f))
                    .withEndAction(() -> root.removeView(ghost)).start();
            // The glass fades to the new picture over the same time as the wallpaper itself.
            if (blur != null) Backdrop.present(blur, 900);
            else blurBackdrop(bmp, 900);
        } else {
            wallView.setImageBitmap(bmp);
            if (blur != null) Backdrop.present(blur, 0);
            else blurBackdrop(bmp);
        }
        // An opaque picture covers the whole screen, so the black window and the plain background
        // under it are never seen. Painting them anyway cost two full-screen passes on every frame,
        // and this TV's graphics chip, not the app, is what holds each frame back.
        if (!bmp.hasAlpha()) {
            root.setBackground(null);
            getWindow().setBackgroundDrawable(null);
        }
        applyDarkModeFilter();
    }

    /** Hands the picture now on screen to the glass, which blurs it once, off the main thread. */
    private void blurBackdrop(android.graphics.Bitmap bmp) {
        blurBackdrop(bmp, 0);
    }

    private void blurBackdrop(android.graphics.Bitmap bmp, long fadeMs) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Backdrop.update(bmp, dm.widthPixels, dm.heightPixels, placesMode ? 0xFF7A7A7A : 0, wallDecoder, handler, fadeMs);
    }

    private int getWallpaperIntervalMinutes() {
        return getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getInt("wallpaper_interval_minutes", 5);
    }

    private String getWallpaperIntervalSummary() {
        int min = getWallpaperIntervalMinutes();
        if (min == -30) return "30 Sec";
        if (min == 0) return "Manual";
        if (min == 60) return "1 Hour";
        return min + " Min";
    }

    private long getWallpaperIntervalMs() {
        int min = getWallpaperIntervalMinutes();
        if (min == -30) return 30_000L;
        if (min <= 0) return 0;
        return min * 60_000L;
    }

    private boolean isWallpaperDescEnabled() {
        return getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("show_wallpaper_desc", true);
    }

    private void setWallpaperDescEnabled(boolean enabled) {
        getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit().putBoolean("show_wallpaper_desc", enabled).apply();
    }

    /** Shows the next picture from the wallpapers folder; the position survives restarts. */
    private void showNextWallpaper(boolean fade) {
        if (wallpapers == null || wallpapers.isEmpty()) return;
        android.content.SharedPreferences prefs = getSharedPreferences("wallpaper", MODE_PRIVATE);
        int last = prefs.getInt("index", -1);
        boolean shuffle = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("shuffle_mode", true);
        int next;
        if (shuffle && wallpapers.size() > 1) {
            java.util.Random rnd = new java.util.Random();
            do {
                next = rnd.nextInt(wallpapers.size());
            } while (next == last && wallpapers.size() > 1);
        } else {
            next = (last + 1) % wallpapers.size();
        }
        prefs.edit().putInt("index", next).apply();
        wallShownAt = android.os.SystemClock.elapsedRealtime();
        final File file = wallpapers.get(next);
        // On a cold start, name the place straight away: waiting for the decode showed the app's name
        // at full size and shrank it a moment later. After that the name changes with the picture,
        // or it would name the next place over the last one's picture while that one decodes.
        if (placeName == null) setPlace(file);
        final boolean firstPicture = wallBitmap == null;
        wallDecoder.execute(() -> {
            android.graphics.Bitmap bmp = decodeWallpaper(file);
            // After the first picture, the blur is made here, before the picture is shown, so the glass
            // changes with the wallpaper rather than after it. The first picture of a fresh start does
            // not wait for its blur: Home is held for it, and the glass catches up a moment later.
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.Bitmap blur = firstPicture ? null
                    : Backdrop.blur(bmp, dm.widthPixels, dm.heightPixels, placesMode ? 0xFF7A7A7A : 0);
            handler.post(() -> {
                if (isDestroyed()) return;
                setWallpaper(bmp, fade, blur);
                setPlace(file);
            });
        });
    }

    /** Switches to the next picture every wallpaper interval while Home is in front. */
    private final Runnable wallTick = new Runnable() {
        @Override
        public void run() {
            // While an app's own picture is up the shuffle waits, so leaving it brings back the picture that was there.
            if (wallpapers != null && wallpapers.size() >= 2 && wallpaperIntervalMs > 0 && !appWallActive) {
                showNextWallpaper(true);
            }
            if (wallpaperIntervalMs > 0) {
                handler.postDelayed(this, wallpaperIntervalMs);
            }
        }
    };

    /** Fetches the week's places in the background, then switches the rotation to them. */
    private void refreshPlacesIfDue() {
        if (!placesMode || placesRefreshRunning || !PlaceWallpapers.due(this)) return;
        placesRefreshRunning = true;
        new Thread(() -> {
            boolean changed = PlaceWallpapers.refresh(getApplicationContext());
            handler.post(() -> {
                placesRefreshRunning = false;
                if (!changed || isDestroyed()) return;
                List<File> places = PlaceWallpapers.list(this);
                if (places.isEmpty()) return;
                // Last week's pictures are gone: show one of this week's straight away, so the
                // name on the shelf and the picture behind it always match.
                wallpapers = places;
                showNextWallpaper(wallBitmap != null);
                if (!isFinishing() && hasWindowFocus()) startWallpaperRotation();
            });
        }, "place-wallpapers").start();
    }

    private boolean onlineRefreshRunning;

    /** Fetches fresh 4K online wallpapers in the background when due. */
    private void refreshOnlineWallpapersIfDue() {
        if (config == null || !config.wallpaperOnline || onlineRefreshRunning || !OnlineWallpapers.due(this)) return;
        onlineRefreshRunning = true;
        new Thread(() -> {
            boolean changed = OnlineWallpapers.sync(getApplicationContext(), config.wallpaperFeed);
            handler.post(() -> {
                onlineRefreshRunning = false;
                if (!changed || isDestroyed()) return;
                List<File> fresh = OnlineWallpapers.list(this);
                if (fresh.isEmpty()) return;
                List<File> combined = new ArrayList<>(config.wallpapers);
                for (File f : fresh) {
                    if (!combined.contains(f)) combined.add(f);
                }
                wallpapers = combined;
                if (wallBitmap == null) showNextWallpaper(false);
                if (!isFinishing() && hasWindowFocus()) startWallpaperRotation();
            });
        }, "online-wallpapers").start();
    }

    /** Clears the TV's app caches when due, once Home has sat idle on screen for a while. */
    private final Runnable cacheClean = () -> {
        if (hasWindowFocus() && config != null) CacheCleaner.maybeRun(this, config.cacheCleanDays);
    };

    /** Whenever Home comes back: picks the rotation up where the interval left it. */
    private void startWallpaperRotation() {
        refreshPlacesIfDue();
        refreshOnlineWallpapersIfDue();
        handler.removeCallbacks(wallTick);
        wallpaperIntervalMs = getWallpaperIntervalMs();
        if (wallpapers == null || wallpapers.size() < 2 || wallpaperIntervalMs <= 0) return;
        long waited = android.os.SystemClock.elapsedRealtime() - wallShownAt;
        // A picture that fell due while you were in an app waits a few seconds: decoding, blurring
        // and fading it the moment you come back competed with Home for the TV's slow CPU.
        handler.postDelayed(wallTick, Math.max(RETURN_SETTLE_MS, wallpaperIntervalMs - waited));
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_settings", false)) {
            if (screensaverActive) dismissScreensaver();
            showSettingsPopup();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_candy_box", false)) {
            if (screensaverActive) dismissScreensaver();
            showCandyBoxPopup();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_azan_settings", false)) {
            if (screensaverActive) dismissScreensaver();
            showAzanSettingsDialog();
            return;
        }
        if (intent != null && intent.hasExtra("set_islamic_mode")) {
            int mode = intent.getIntExtra("set_islamic_mode", MinbarManager.MODE_DOA);
            MinbarManager.setDisplayMode(this, mode);
            updateIslamicShelf();
        }
        if (intent != null && intent.getBooleanExtra("open_islamic_detail", false)) {
            if (screensaverActive) dismissScreensaver();
            showIslamicDetailDialog();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_clean_cache", false)) {
            if (screensaverActive) dismissScreensaver();
            showCleanTvCacheDialog();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_wallpaper", false)) {
            if (screensaverActive) dismissScreensaver();
            showWallpaperDialog();
            return;
        }
        if (intent != null && intent.getBooleanExtra("open_now_playing", false)) {
            if (screensaverActive) dismissScreensaver();
            showNowPlayingDialog();
            return;
        }
        if (intent != null && intent.getBooleanExtra("screensaver", false)) {
            showNightstandScreensaver();
            return;
        }
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        if (hiddenSinceResume) return;
        if (screensaverActive) {
            dismissScreensaver();
            return;
        }
        // Single home pressed on Home
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
        closeMenu(false);
        if (movingPos >= 0) finishMove();
        focusTile(0);
    }

    /**
     * Home went behind another app since it was last resumed. A Home press that brings it back
     * arrives before onResume, so this tells it apart from a Home press on Home itself.
     */
    private boolean hiddenSinceResume;

    @Override
    protected void onStop() {
        hiddenSinceResume = true;
        handler.removeCallbacks(cacheClean);
        handler.removeCallbacks(ayahRotate);
        handler.removeCallbacks(nowPlayingPoll);
        super.onStop();
    }

    private TextView ayahRumi;

    /** Doa harian box: the home screen glass, a white rim while selected. */
    private void setAyahBoxLook(boolean focused) {
        if (ayahBlock == null) return;
        GlassDrawable glass = new GlassDrawable(ayahBlock, 16 * density, density, null, 0.4f);
        Backdrop.watch(ayahBlock);
        if (!focused) {
            ayahBlock.setBackground(glass);
            return;
        }
        android.graphics.drawable.GradientDrawable tint = new android.graphics.drawable.GradientDrawable();
        tint.setCornerRadius(16 * density);
        tint.setColor(0x33FFFFFF);
        tint.setStroke(Math.round(1.5f * density), 0xCCFFFFFF);
        ayahBlock.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{glass, tint}));
    }

    private TextView prayerCountdownView;

    private void updatePrayerCountdown() {
        if (prayerCountdownView == null) {
            prayerCountdownView = findViewById(R.id.prayerCountdown);
            if (prayerCountdownView == null) return;
            prayerCountdownView.setBackground(new GlassDrawable(prayerCountdownView, 14 * density, density, null, 0.5f));
            Backdrop.watch(prayerCountdownView);
        }
        try {
            prayerCountdownView.setText("" + PrayerTimes.getNextCountdown(this));
            prayerCountdownView.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            prayerCountdownView.setVisibility(View.GONE);
        }
    }

    private boolean appsScrolledDown;
    private LinearLayout sportsCard;
    private android.graphics.Typeface sportsFont;

    private void updateSports() {
                if (sportsCard == null) {
            sportsCard = findViewById(R.id.sportsCard);
            if (sportsCard == null) return;
            try { sportsFont = getResources().getFont(R.font.inter); } catch (Exception e) { sportsFont = android.graphics.Typeface.SANS_SERIF; }
        }
        SportsNow.load(getApplicationContext(), list -> {
            if (isFinishing() || isDestroyed()) return;
            renderSports(list);
        });
    }

    /** Football box under the weather: a header, then one glass box per team with place in the table, last result and next match. */
    private void renderSports(java.util.List<SportsNow.Team> teams) {
        sportsCard.removeAllViews();
        if (teams == null || teams.isEmpty()) {
            sportsCard.setVisibility(View.GONE);
            return;
        }
        TextView head = sportsText("FOOTBALL", 9.5f, 0xB3FFFFFF, 600);
        head.setLetterSpacing(0.14f);
        head.setPadding(Math.round(4 * density), 0, 0, Math.round(3 * density));
        sportsCard.addView(head);
        for (SportsNow.Team t : teams) {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setPadding(Math.round(8 * density), Math.round(4 * density), Math.round(9 * density), Math.round(4 * density));
            box.setFocusable(true);
            box.setClickable(true);
            final String teamName = t.name;
            box.setOnClickListener(v -> showFixtures(teamName));
            box.setOnFocusChangeListener((v, has) -> {
                setSportsBoxLook(v, has);
                v.animate().scaleX(has ? 1.03f : 1f).scaleY(has ? 1.03f : 1f).setDuration(120).start();
            });
            final int boxIndex = sportsCard.getChildCount() - 1;
            box.setOnKeyListener((v, keyCode, ev) -> {
                if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && boxIndex <= 0) {
                    if (weatherAirQualityView != null) weatherAirQualityView.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    View pt = findViewById(R.id.prayerTable);
                    if (pt != null && pt.getVisibility() == View.VISIBLE && islamicShelf != null && islamicShelf.getVisibility() == View.VISIBLE) {
                        pt.requestFocus();
                        return true;
                    }
                    if (ayahBlock != null && ayahBlock.getVisibility() == View.VISIBLE) {
                        ayahBlock.requestFocus();
                        return true;
                    }
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && boxIndex + 1 >= sportsCard.getChildCount() - 1) {
                    if (nowPlayingCard != null && nowPlayingCard.getVisibility() == View.VISIBLE) {
                        if (cardTrackInfo != null) cardTrackInfo.requestFocus(); else nowPlayingCard.requestFocus();
                    } else {
                        focusTile(0);
                    }
                    return true;
                }
                return false;
            });
            // Same glass as the prayer row: it samples the wallpaper, so it follows it when it changes.
            // No glass box: just a thin rounded outline that lights up when selected.
            setSportsBoxLook(box, false);

            android.widget.ImageView logo = new android.widget.ImageView(this);
            logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams logoLp = new LinearLayout.LayoutParams(Math.round(30 * density), Math.round(30 * density));
            logoLp.rightMargin = Math.round(8 * density);
            box.addView(logo, logoLp);
            loadBadge(t.badge, logo);

            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            LinearLayout top = new LinearLayout(this);
            top.addView(sportsText(t.name, 11.5f, 0xFFFFFFFF, 600), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            if (!t.table.isEmpty()) top.addView(sportsText(t.table, 9.5f, 0xFFFFD76A, 700));
            col.addView(top);
            if (!t.last.isEmpty()) {
                int c = "W".equals(t.outcome) ? 0xFF7BE495 : "L".equals(t.outcome) ? 0xFFFF8A80 : 0xFFFFD76A;
                col.addView(sportsText("Last  " + t.last, 9.5f, c, 600));
            }
            if (!t.next.isEmpty()) col.addView(sportsText("Next  " + t.next, 9f, 0xCCFFFFFF, 500));
            if (!t.stadium.isEmpty()) col.addView(sportsText("" + t.stadium, 9f, 0x99FFFFFF, 500));
            box.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            boxLp.bottomMargin = Math.round(4 * density);
            sportsCard.addView(box, boxLp);
        }
        sportsCard.setAlpha(appsScrolledDown ? 0f : 1f);
        sportsCard.setVisibility(View.VISIBLE);
    }

    /**
     * Google Home has no app for Android TV (it is a phone app), so when it is missing the tile opens
     * Google Assistant instead, which controls the same home devices by voice.
     */
    void openGoogleHome() {
        String[] packages = {"com.google.android.apps.chromecast.app", "com.google.android.apps.googlehome"};
        for (String pkg : packages) {
            Intent in = getPackageManager().getLaunchIntentForPackage(pkg);
            if (in == null) in = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (in == null) {
                // Some builds list no launcher entry: open whatever main screen the app has.
                try {
                    java.util.List<android.content.pm.ResolveInfo> l = getPackageManager().queryIntentActivities(
                            new Intent(Intent.ACTION_MAIN).setPackage(pkg), 0);
                    if (!l.isEmpty()) {
                        in = new Intent(Intent.ACTION_MAIN).setClassName(pkg, l.get(0).activityInfo.name);
                    }
                } catch (Exception ignored) { }
            }
            if (in != null) {
                try {
                    startActivity(in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (Exception ignored) { }
            }
        }
        // There is no Google Home app for most Android TVs. Use its website if a browser is installed, else the store page.
        Intent web = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://home.google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (web.resolveActivity(getPackageManager()) != null) {
            Toast.makeText(this, "Google Home is not installed on this TV. Opening its website.", Toast.LENGTH_LONG).show();
            try { startActivity(web); return; } catch (Exception ignored) { }
        }
        Toast.makeText(this, "Google Home is not installed on this TV. Opening the Play Store page.", Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=com.google.android.apps.chromecast.app"))
                    .setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) { }
    }

    /** Thin outline, no fill; a soft light fill and brighter outline while selected. */
    private void setSportsBoxLook(View box, boolean focused) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(10 * density);
        g.setColor(focused ? 0x26FFFFFF : 0x00000000);
        g.setStroke(Math.max(1, Math.round((focused ? 1.2f : 0.6f) * density)), focused ? 0xCCFFFFFF : 0x33FFFFFF);
        box.setBackground(g);
    }

    /** Full-screen football page for a club: the whole league table (P W D L GF GA GD Pts) and its matches to the end of the season. */
    private void showFixtures(String teamName) {
        final String id = SportsNow.idFor(teamName);
        if (id == null) return;
        SoundEffects.playSelect();
        final android.app.Dialog dialog = new android.app.Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        final LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(Math.round(48 * density), Math.round(28 * density), Math.round(48 * density), Math.round(20 * density));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xF0141A26, 0xF00A0E16});
        page.setBackground(bg);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageView crest = new android.widget.ImageView(this);
        LinearLayout.LayoutParams crestLp = new LinearLayout.LayoutParams(Math.round(40 * density), Math.round(40 * density));
        crestLp.rightMargin = Math.round(12 * density);
        titleRow.addView(crest, crestLp);
        loadBadge(SportsNow.logoUrl(id), crest);
        TextView title = sportsText("" + teamName, 24f, 0xFFFFFFFF, 700);
        titleRow.addView(title);
        page.addView(titleRow);
        final TextView sub = sportsText("Loading...", 12f, 0xB3FFFFFF, 500);
        sub.setPadding(0, Math.round(2 * density), 0, Math.round(10 * density));
        page.addView(sub);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        final TextView tabTable = sportsTab("League table");
        final TextView tabFix = sportsTab("All matches");
        tabs.addView(tabTable);
        tabs.addView(tabFix);
        page.addView(tabs);

        final ScrollView scroll = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollLp.topMargin = Math.round(10 * density);
        page.addView(scroll, scrollLp);

        final SportsNow.Standing[] table = new SportsNow.Standing[1];
        final java.util.List<String[]>[] fixtures = new java.util.List[1];
        final int[] mode = {0};
        final Runnable render = () -> {
            list.removeAllViews();
            tabTable.setAlpha(mode[0] == 0 ? 1f : 0.55f);
            tabFix.setAlpha(mode[0] == 1 ? 1f : 0.55f);
            if (mode[0] == 0) {
                if (table[0] == null) return;
                if (table[0].rows.isEmpty()) { list.addView(sportsText("No league table available. Check the internet connection.", 13f, 0xCCFFFFFF, 500)); return; }
                sub.setText(table[0].league + (table[0].season.isEmpty() ? "" : "  |  Season " + table[0].season));
                list.addView(sportsTableRow(null, table[0].mine));
                for (String[] r : table[0].rows) {
                    list.addView(sportsTableRow(r, table[0].mine));
                    list.addView(sportsDivider());
                }
            } else {
                if (fixtures[0] == null) return;
                int shown = 0;
                for (String[] r : fixtures[0]) {
                    boolean played = !r[2].isEmpty();
                    String text = r[0] + "     " + r[1] + (played ? "     " + r[2] : r[3].isEmpty() ? "" : "     " + r[3]);
                    TextView line = sportsText(text, 13.5f, played ? 0xCCFFFFFF : 0xFFFFFFFF, played ? 500 : 600);
                    if (played) {
                        int rc = r[2].startsWith("W") ? 0xFF4ADE80 : r[2].startsWith("D") ? 0xFFFACC15 : 0xFFF87171;
                        android.text.SpannableString sp = new android.text.SpannableString(text);
                        int from = text.lastIndexOf(r[2]);
                        if (from >= 0) {
                            sp.setSpan(new android.text.style.ForegroundColorSpan(rc), from, from + r[2].length(), 0);
                            sp.setSpan(new android.text.style.StyleSpan(Typeface.BOLD), from, from + r[2].length(), 0);
                        }
                        line.setText(sp);
                    }
                    line.setPadding(Math.round(10 * density), Math.round(8 * density), Math.round(10 * density), Math.round(8 * density));
                    line.setFocusable(true);
                    line.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0x26FFFFFF : 0));
                    list.addView(line);
                    list.addView(sportsDivider());
                    shown++;
                }
                if (shown == 0) list.addView(sportsText("No matches found. Check the internet connection.", 13f, 0xCCFFFFFF, 500));
            }
        };
        tabTable.setOnClickListener(v -> { mode[0] = 0; render.run(); });
        tabFix.setOnClickListener(v -> { mode[0] = 1; render.run(); });
        tabTable.setOnFocusChangeListener((v, has) -> { if (has) { mode[0] = 0; render.run(); } });
        tabFix.setOnFocusChangeListener((v, has) -> { if (has) { mode[0] = 1; render.run(); } });
        tabTable.setNextFocusDownId(View.NO_ID);

        dialog.setContentView(page);
        showGlass(dialog);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
        }
        tabTable.requestFocus();
        new Thread(() -> {
            final SportsNow.Standing st = SportsNow.standings(id);
            final java.util.List<String[]> fx = SportsNow.schedule(id);
            handler.post(() -> {
                if (!dialog.isShowing()) return;
                table[0] = st;
                fixtures[0] = fx;
                if (!st.league.isEmpty()) sub.setText(st.league + (st.season.isEmpty() ? "" : "  |  Season " + st.season));
                else sub.setText("");
                render.run();
            });
        }, "football-page").start();
    }

    private View sportsDivider() {
        View v = new View(this);
        v.setBackgroundColor(0x33FFFFFF);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(density))));
        return v;
    }

    private TextView sportsTab(String label) {
        TextView t = sportsText(label, 14f, 0xFFFFFFFF, 700);
        t.setPadding(Math.round(16 * density), Math.round(8 * density), Math.round(16 * density), Math.round(8 * density));
        t.setFocusable(true);
        t.setClickable(true);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(18 * density);
        g.setColor(0x22FFFFFF);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = Math.round(8 * density);
        t.setLayoutParams(lp);
        return t;
    }

    /** One line of the league table; {@code r == null} makes the header. The club being viewed is highlighted. */
    private View sportsTableRow(String[] r, String myId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Math.round(10 * density), Math.round((r == null ? 4 : 5) * density), Math.round(10 * density), Math.round(5 * density));
        boolean mine = r != null && myId.equals(r[10]);
        int color = r == null ? 0x99FFFFFF : 0xFFFFFFFF;
        String[] cells = r == null ? new String[]{"#", "Club", "P", "W", "D", "L", "GF", "GA", "GD", "Pts"} : r;
        int[] widths = {34, 0, 38, 38, 38, 38, 42, 42, 46, 46};
        for (int i = 0; i < 10; i++) {
            if (i == 1) {
                LinearLayout club = new LinearLayout(this);
                club.setGravity(Gravity.CENTER_VERTICAL);
                if (r != null) {
                    android.widget.ImageView logo = new android.widget.ImageView(this);
                    LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(Math.round(22 * density), Math.round(22 * density));
                    ll.rightMargin = Math.round(8 * density);
                    club.addView(logo, ll);
                    loadBadge(r[11], logo);
                }
                club.addView(sportsText(cells[1], 13f, color, mine || r == null ? 700 : 500), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(club, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                continue;
            }
            TextView t = sportsText(cells[i], 13f, i == 9 && r != null ? 0xFFFFD76A : color, i == 9 || r == null ? 700 : 500);
            t.setGravity(Gravity.CENTER);
            row.addView(t, new LinearLayout.LayoutParams(Math.round(widths[i] * density), ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (r != null) {
            row.setFocusable(true);
            final boolean isMine = mine;
            row.setBackgroundColor(isMine ? 0x22FFD76A : 0);
            row.setOnFocusChangeListener((v, has) -> v.setBackgroundColor(has ? 0x30FFFFFF : isMine ? 0x22FFD76A : 0));
        }
        return row;
    }

    private static final java.util.Map<String, android.graphics.Bitmap> BADGES = new java.util.HashMap<>();

    /** Club logo from TheSportsDB, fetched once and kept in memory. */
    private void loadBadge(String url, android.widget.ImageView target) {
        if (url == null || url.isEmpty()) return;
        android.graphics.Bitmap have = BADGES.get(url);
        if (have != null) { target.setImageBitmap(have); return; }
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url.contains("thesportsdb") ? url + "/tiny" : url).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                try (java.io.InputStream in = c.getInputStream()) {
                    android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeStream(in);
                    if (b == null) return;
                    BADGES.put(url, b);
                    handler.post(() -> target.setImageBitmap(b));
                } finally { c.disconnect(); }
            } catch (Exception ignored) { }
        }, "badge").start();
    }

    private TextView sportsText(String value, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(color);
        t.setTextSize(sp);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setTypeface(sportsFont);
        try { t.setFontVariationSettings("'wght' " + weight); } catch (Exception ignored) { }
        return t;
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (ModernSettingsDialog.returnToControl) {
            ModernSettingsDialog.returnToControl = false;
            handler.postDelayed(() -> { if (!isFinishing() && !isDestroyed()) ModernSettingsDialog.showControl(MainActivity.this); }, 250);
        }
        try {
            android.graphics.Bitmap showing = appWallActive ? shownWallpaper : baseWallBitmap;
            if (showing != null) blurBackdrop(showing);
        } catch (Throwable ignored) { }
        updateSports();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }
        tilesReady = false;
        promptsPaused = false;
        resuming = true;
        if (btnCandyBox instanceof FrameLayout) {
            FrameLayout fb = (FrameLayout) btnCandyBox;
            for (int i = 0; i < fb.getChildCount(); i++) {
                View c = fb.getChildAt(i);
                if (c instanceof com.airbnb.lottie.LottieAnimationView) {
                    com.airbnb.lottie.LottieAnimationView l = (com.airbnb.lottie.LottieAnimationView) c;
                    if (!l.isAnimating()) l.resumeAnimation();
                }
            }
        }
        reloadApps(true);
        updateShamsiDate();
        updatePrayerTimes();
        updateDateAndPeriod();
        updateWifiLabel();
        updateWeatherAqi();
        WeatherAirQuality.syncIfDue(this, this::updateWeatherAqi);
        updateIslamicShelf();
        updateInputsButton();
        updateTopBarUserIcon();
        updateSettingsBadge();
        applyDarkModeFilter();
        handler.postDelayed(() -> AppUpdateManager.checkForUpdates(this, false), 1500);
        Location.Zone chosenPlace = Location.chosen(this);
        String zoneCode = (chosenPlace != null) ? chosenPlace.code : "WLY01";
        Azan.schedule(this, zoneCode);
        Azan.fetch(this);
        NowPlayingManager.updateCurrentMedia(this);
        startWallpaperRotation();
        handler.postDelayed(minuteTick, MINUTE_MS);
        handler.removeCallbacks(ayahRotate);
        handler.postDelayed(ayahRotate, ZikirList.intervalMs(this) - (System.currentTimeMillis() % ZikirList.intervalMs(this)) + 50L);
        handler.removeCallbacks(nowPlayingPoll);
        handler.postDelayed(nowPlayingPoll, 2000);
        handler.removeCallbacks(cacheClean);
        handler.postDelayed(cacheClean, CacheCleaner.IDLE_MS);
        if (weatherOverlayView != null && weatherOverlayView.getVisibility() == View.VISIBLE) {
            weatherOverlayView.startAnimation();
        }
        hiddenSinceResume = false;
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        long started = android.os.SystemClock.uptimeMillis();
        boolean rebuilt = reloadTiles();
        // Nothing installed or removed: Home comes back exactly as it was left, with no rebuild and
        // no shelf, glow or selection fading back in.
        if (restoreFocus && rebuilt) {
            glassShown = false;
            glider.hideNow();
            heroShown = false;
            heroShelf.setAlpha(0f);
            ambientShown = false;
            ambient.setAlpha(0f);
            syncGlassGlow();
        }
        android.util.Log.i("TvHome.Perf", "tiles " + (rebuilt ? "rebuilt" : "unchanged") + " in "
                + (android.os.SystemClock.uptimeMillis() - started) + " ms");
        // The tiles rise into place on the first launch only. Replayed on every return from an app,
        // it showed the glass tray empty for a moment and held Home back while the tiles arrived.
        if (animate && !reducedMotion && !tilesAnimatedOnce) grid.scheduleLayoutAnimation();
        if (animate) tilesAnimatedOnce = true;

        if (restoreFocus) {
            // Follow the same app, or the nearest remaining slot when it was removed.
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.getItemCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    boolean reloadTiles() {
        updateTopBarUserIcon();
        if (adapter != null) adapter.invalidateCache();
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps, Cards.load(Cards.storeFile(this))), none) : none;
        boolean rebuilt = adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none));
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
        return rebuilt;
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            resuming = false;
            View now = getCurrentFocus();
            if (now != null && isTopPill(now)) moveTopHighlight(now);
            showCardPrompt();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (btnCandyBox instanceof FrameLayout) {
            FrameLayout fb = (FrameLayout) btnCandyBox;
            for (int i = 0; i < fb.getChildCount(); i++) {
                View c = fb.getChildAt(i);
                if (c instanceof com.airbnb.lottie.LottieAnimationView) {
                    com.airbnb.lottie.LottieAnimationView l = (com.airbnb.lottie.LottieAnimationView) c;
                    if (l.isAnimating()) l.pauseAnimation();
                }
            }
        }
        unregisterReceiver(packageReceiver);
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(wallTick);
        handler.removeCallbacks(nowPlayingPoll);
        if (weatherOverlayView != null) {
            weatherOverlayView.stopAnimation();
        }
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
        closeMenu(false);
        finishMove();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCardPrompt();
    }

    /**
     * Android is short of memory while Home sits behind another app: give back what can be remade,
     * so the launcher is less likely to be reclaimed and Home comes back without starting over.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) Backdrop.trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sInstance != null && sInstance.get() == this) {
            sInstance = null;
        }
        if (uploadServer != null) {
            uploadServer.stop();
            uploadServer = null;
        }
        if (nowPlayingListener != null) {
            NowPlayingManager.unregisterListener(nowPlayingListener);
        }
        SoundEffects.release();
        wallDecoder.shutdownNow();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    public void refreshLocationDependentData() {
        updateWeatherAqi();
        updatePrayerTimes();
        WeatherAirQuality.sync(this, this::updateWeatherAqi);
        PrayerTimes.sync(this, this::updatePrayerTimes);
    }

    @Override
    public void onBackPressed() {
        handleBackPress();
    }

    private void handleBackPress() {
        if (openMenu != null) {
            closeMenu(true);
            return;
        }
        if (screensaverActive) {
            dismissScreensaver();
            return;
        }
        if (wallpaperFullscreen) {
            setWallpaperFullscreen(false);
            return;
        }

        if (doubleBackPending) {
            if (singleBackRunnable != null) {
                handler.removeCallbacks(singleBackRunnable);
                singleBackRunnable = null;
            }
            doubleBackPending = false;
            showNightstandScreensaver();
        } else {
            doubleBackPending = true;
            singleBackRunnable = () -> {
                doubleBackPending = false;
                singleBackRunnable = null;
                setWallpaperFullscreen(true);
            };
            handler.postDelayed(singleBackRunnable, DOUBLE_BACK_DELAY_MS);
        }
    }

    private void setWallpaperFullscreen(boolean fullscreen) {
        if (wallpaperFullscreen == fullscreen) return;
        wallpaperFullscreen = fullscreen;
        float alpha = fullscreen ? 0f : 1f;
        int duration = reducedMotion ? 120 : 300;

        if (mainContent != null) {
            mainContent.animate().alpha(alpha).setDuration(duration).start();
        }
        if (heroShelf != null) {
            heroShelf.animate().alpha(alpha).setDuration(duration).start();
        }
        if (islamicShelf != null) {
            islamicShelf.animate().alpha(alpha).setDuration(duration).start();
        }
        if (glass != null) {
            glass.animate().alpha(alpha).setDuration(duration).start();
        }
        if (glider != null) {
            glider.animate().alpha(alpha).setDuration(duration).start();
        }
        if (ambient != null && fullscreen) {
            ambient.animate().alpha(0f).setDuration(duration).start();
        }

        if (wallView != null && placesMode) {
            if (fullscreen) {
                wallView.clearColorFilter();
            } else {
                wallView.setColorFilter(0xFF7A7A7A, android.graphics.PorterDuff.Mode.MULTIPLY);
            }
        }
    }

    // ---- glass highlight -------------------------------------------------------------------------

    /** Reused by every focus move. */
    private final Rect tileRect = new Rect(), showRect = new Rect();

    @Override
    public void onTileFocused(int position, View card) {
        if (!resuming && hasWindowFocus()) SoundEffects.playNav();
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        scheduleAppWallpaper(lastFocusedKey, adapter.labelAt(position));
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            // The rim is drawn inset by pad, right on the zoomed tile, so its corners use the tile's radius as is.
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE);
        }
        glassTargetX = x;
        glassTargetY = y;
        // Exactly the zoomed card, behind it: at rest the app hides it completely, so it never lies
        // over an app and only shows while it travels between places.
        float cardRadius = AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE;
        if (gliderOnPill) {
            // Down from the top menu it travels dark, and turns to glass once it lands, unseen behind
            // the app.
            glider.moveTo(x + pad, y + pad, x + w - pad, y + h - pad, cardRadius);
            glider.onArrive(() -> {
                if (!gliderOnPill) glider.setStyle(Glider.GLASS);
            });
        } else {
            // From app to app it is a pane of glass gliding behind them.
            glider.setStyle(Glider.GLASS);
            glider.moveTo(x + pad, y + pad, x + w - pad, y + h - pad, cardRadius);
        }
        gliderOnPill = false;
        glassShown = true;
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            boolean scroll = Math.abs(dy) > (int) (8 * density);
            if (scroll) {
                if (reducedMotion) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
            showHero(position, scroll ? r.top - dy : r.top);
        } else {
            // Below the dock there is no shelf, but the glow still takes this app's colour, so every
            // tile lights the room the same way the dock does.
            hideShelf();
            showAmbient(adapter.ambientColorAt(position));
            // Scroll just enough so the whole glass (not only the tile) is visible.
            showRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, showRect, reducedMotion);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private boolean isCustomWallpaper() {
        String activeCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Natural");
        if ("Custom".equalsIgnoreCase(activeCat)) return true;
        if (wallpapers != null && !wallpapers.isEmpty()) {
            int idx = getSharedPreferences("wallpaper", MODE_PRIVATE).getInt("index", 0);
            if (idx >= 0 && idx < wallpapers.size()) {
                File f = wallpapers.get(idx);
                if (f != null && f.getAbsolutePath().contains("custom_wallpapers")) return true;
            }
        }
        return false;
    }

    private void showHero(int position, float dockTopInRoot) {
        int color = adapter.ambientColorAt(position);
        heroBottom = dockTopInRoot - HERO_GAP_DP * density;

        if (islamicShelf != null) {
            float iy = heroBottom - islamicShelf.getHeight();
            islamicShelf.setY(iy);
            islamicShelf.animate().cancel();
            if (!heroShown) {
                if (firstFrameHeld || resuming) islamicShelf.setAlpha(1f);
                else islamicShelf.animate().alpha(1f).setDuration(260).start();
            } else {
                islamicShelf.setAlpha(1f);
            }
        }

        // When using custom wallpaper or when description is turned off, disable the description part
        if (isCustomWallpaper() || !isWallpaperDescEnabled()) {
            if (heroShelf != null) {
                heroShelf.animate().cancel();
                heroShelf.setAlpha(0f);
                heroShelf.setVisibility(View.GONE);
            }
            showAmbient(color);
            heroShown = true;
            return;
        }

        if (heroShelf != null) {
            heroShelf.setVisibility(View.VISIBLE);
        }

        // The place the picture is of, when there is one; the app's own name is the fallback for
        // the user's own wallpapers, which carry no title.
        final String label = placeName != null ? placeName : adapter.labelAt(position);
        final String desc = placeName != null ? placeText : "";
        final String where = placeName != null ? placeCountry : "";
        float y = heroBottom - heroShelf.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (reducedMotion) {
            // Snap the shelf instead of sliding and fading it: each of those frames repaints the
            // full-screen panel, and this switch exists for the GPUs that cannot afford it.
            heroShelf.animate().cancel();
            setHeroText(label, where, desc);
            heroShelf.setY(y);
            heroShelf.setTranslationX(0f);
            heroShelf.setAlpha(1f);
            showAmbient(color);
            heroShown = true;
            return;
        }

        if (!heroShown) {
            setHeroText(label, where, desc);
            heroShelf.setY(y);
            heroShelf.setTranslationX(0f);
            // Coming back to Home, the words are simply there with the picture: faded in after the
            // first frame, they arrived a beat behind everything else.
            heroShelf.animate().cancel();
            if (firstFrameHeld || resuming) heroShelf.setAlpha(1f);
            else heroShelf.animate().alpha(1f).setDuration(260).start();
            showAmbient(color);
            heroShown = true;
        } else if (changed) {
            heroShelf.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                setHeroText(label, where, desc);
                heroShelf.setY(y);
                heroShelf.setTranslationX(12 * density);
                heroShelf.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            showAmbient(color);
        } else {
            // Same words, different app: every dock app shares the place's name, so the colour has
            // to follow focus on its own rather than ride along with a change of text.
            heroShelf.setY(y);
            showAmbient(color);
        }
    }

    /**
     * Lines the top-right buttons up with the dock: the Settings circle's right edge sits on the
     * right edge of the last dock card, and Inputs follows on its left. Card width follows the
     * column count and the screen, so the edge is measured rather than written down.
     */
    private void alignTopButtons() {
        // This runs on every layout pass, which during a grid scroll is every frame, so it keeps
        // its views and rectangles rather than looking them up or allocating each time.
        if (topRow == null) {
            topRow = findViewById(R.id.topButtons);
            topSettings = findViewById(R.id.btnSettings);
        }
        if (topRow == null || topSettings.getWidth() == 0) return;
        RecyclerView.ViewHolder last = grid.findViewHolderForAdapterPosition(COLUMNS - 1);
        if (last == null) return;
        View card = last.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() == 0) return;
        // Layout positions, not drawn ones: a focused card is scaled up, and the row's own shift
        // must not be counted twice.
        alignCard.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, alignCard);
        alignButton.set(0, 0, topSettings.getWidth(), topSettings.getHeight());
        root.offsetDescendantRectToMyCoords(topSettings, alignButton);
        float shift = alignCard.right - alignButton.right;
        if (Math.abs(topRow.getTranslationX() - shift) >= 1f) topRow.setTranslationX(shift);
    }

    /**
     * The one focus shape (Glider) goes behind everything it marks; the pills' glass makes way for it
     * as it arrives, and the dock tray paints it over its own glass.
     */
    private void setUpGlider() {
        glider = new Glider(this, reducedMotion);
        // Behind the shelf's text, the pills and the tiles, so it never draws over any of them.
        root.addView(glider, root.indexOfChild(heroShelf), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The glider replaces the glass selection outright.
        glass.setVisibility(View.GONE);
    }

    private boolean isTopPill(View v) {
        int id = v.getId();
        return id == R.id.btnInputs || id == R.id.btnWifi || id == R.id.btnCalendar || id == R.id.btnControl || id == R.id.btnAssistant || id == R.id.btnCandyBox || id == R.id.btnSettings || id == R.id.btnPower;
    }

    /** Sends the focus shape onto a pill: from a tile it glides up and narrows into the pill. */
    private void moveTopHighlight(View pill) {
        if (resuming) return;
        SoundEffects.playNav();
        pill.getLocationInWindow(pillAt);
        glider.getLocationInWindow(rootAt);
        float l = pillAt[0] - rootAt[0], t = pillAt[1] - rootAt[1];
        // The pills do not scroll with the grid, so the shape stops following the grid's scroll.
        glassShown = false;
        // Up to the top menu it is dark at once, and travels that way.
        gliderOnPill = true;
        glider.setStyle(Glider.DARK);
        glider.moveTo(l, t, l + pill.getWidth(), t + pill.getHeight(), pill.getHeight() / 2f);
    }

    /** The shelf's two lines. An empty description takes no room, so the title keeps its place. */
    private void setHeroText(String title, String where, String desc) {
        if (heroName == null || heroWhere == null || heroDesc == null) return;
        // The wallpaper caption is removed: nothing is written over the picture.
        heroName.setVisibility(View.GONE);
        heroWhere.setVisibility(View.GONE);
        heroDesc.setVisibility(View.GONE);
        if (true) return;
        heroName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, titleSizeSp(title));
        heroName.setText(title);
        heroWhere.setText(where);
        heroWhere.setVisibility(where == null || where.isEmpty() ? View.GONE : View.VISIBLE);
        // The description sits further down under a country line; with none, close under the name.
        ViewGroup.MarginLayoutParams descLp = (ViewGroup.MarginLayoutParams) heroDesc.getLayoutParams();
        if (descLp != null) {
            int top = Math.round(((where == null || where.isEmpty()) ? 6f : 9.5f) * density);
            if (descLp.topMargin != top) {
                descLp.topMargin = top;
                heroDesc.setLayoutParams(descLp);
            }
        }
        heroDesc.setText(desc);
        heroDesc.setVisibility(desc == null || desc.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Shouting titles are calmed down: "THE RIBBON HERO" becomes "The Ribbon Hero". A title that
     * already has lower case is left exactly as its studio wrote it, so "LEGO Star Wars" and
     * "WALL·E" keep their capitals instead of being flattened to "Lego" and "Wall·e".
     */
    private static String titleCase(String title) {
        if (title == null || title.isEmpty()) return title;
        for (int i = 0; i < title.length(); i++) {
            if (Character.isLowerCase(title.charAt(i))) return title;
        }
        StringBuilder out = new StringBuilder(title.length());
        boolean start = true;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            // A new word begins after a space or any of the marks a title tends to use.
            start = c == ' ' || c == '-' || c == ':' || c == '.' || c == '\'' || c == '(' || c == '/';
        }
        return out.toString();
    }

    /**
     * The largest size at which the name still fits on one line, up to its share of the width.
     * Names run longer than app names ("Wroclaw Market Square"), and a name cut short tells the
     * viewer nothing, so a long one is set smaller rather than clipped.
     */
    private float titleSizeSp(String title) {
        if (title == null || title.isEmpty() || heroNameMaxWidth <= 0) return HERO_TITLE_MAX_SP;
        android.text.TextPaint paint = new android.text.TextPaint(heroName.getPaint());
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        for (float sp = HERO_TITLE_MAX_SP; sp > HERO_TITLE_MIN_SP; sp -= 2f) {
            paint.setTextSize(sp * scaled);
            if (paint.measureText(title) <= heroNameMaxWidth) return sp;
        }
        return HERO_TITLE_MIN_SP;
    }

    /**
     * Follows the rotation: the shelf names whichever place is on screen. Called after the picture
     * changes, so the words and the picture never disagree.
     */
    private void setPlace(File picture) {
        if ((picture != null && picture.getAbsolutePath().contains("custom_wallpapers")) || !isWallpaperDescEnabled()) {
            placeName = null;
            placeText = "";
            placeCountry = "";
            if (heroShelf != null) {
                heroShelf.animate().cancel();
                heroShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 250).withEndAction(() -> {
                    if (heroShelf != null) heroShelf.setVisibility(View.GONE);
                }).start();
            }
            return;
        }
        if (heroShelf != null && isWallpaperDescEnabled()) {
            heroShelf.setVisibility(View.VISIBLE);
        }
        String[] about = placesMode ? PlaceWallpapers.describe(picture) : OnlineWallpapers.describe(picture);
        String title = about != null ? titleCase(about[0]) : null;
        String overview = about != null ? about[1] : "";
        String country = about != null && about.length > 2 ? about[2] : "";
        if (country != null) {
            country = country.replaceAll("(?i)\\bunsplash\\s*•?\\s*", "").trim();
            if (country.startsWith("Malaysia_")) {
                String st = country.substring("Malaysia_".length());
                if ("WP".equalsIgnoreCase(st)) st = "Wilayah Persekutuan";
                else if ("NegeriSembilan".equalsIgnoreCase(st)) st = "Negeri Sembilan";
                else if ("PulauPinang".equalsIgnoreCase(st)) st = "Pulau Pinang";
                country = st + ", Malaysia";
            }
        }
        if (title != null) {
            title = title.replaceAll("(?i)\\bunsplash\\s*•?\\s*", "").trim();
        }
        if (title == null ? placeName == null : title.equals(placeName)) return;
        placeName = title;
        placeText = overview;
        placeCountry = country;
        // When the shelf is up, the words fade out with the old picture and back in with the new
        // one, inside the picture's own cross-fade: swapped at once, the next place's name sat over
        // the last one's picture while it faded. The layout listener puts the shelf back where it
        // belongs once the new lines have been measured.
        if (heroShown && title != null) {
            final String t = title;
            final String c = country;
            final String o = overview;
            heroShelf.animate().cancel();
            heroShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 250).withEndAction(() -> {
                setHeroText(t, c, o);
                if (heroShown) heroShelf.animate().alpha(1f).setDuration(reducedMotion ? 120 : 400).start();
            }).start();
        }
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture; changing apps only uploads it once.
     */
    private void setAmbient(int color) {
        setAmbient(color, false);
    }

    private FadeTopFrame gridWrap;

    /** Turns the top fade on while any row reaches into its band, with room for a focused tile's zoom. */
    private void updateTopFade() {
        if (gridWrap == null) return;
        float band = 56 * density + 24 * density;
        boolean inBand = false;
        for (int i = 0; i < grid.getChildCount() && !inBand; i++) {
            View child = grid.getChildAt(i);
            inBand = grid.getTop() + child.getTop() + child.getTranslationY() < band;
        }
        gridWrap.setFadeStrength(inBand ? 1f : 0f);
    }

    /**
     * The dock row sits towards the bottom of the screen, leaving room for rows_below_dock grid rows
     * under it; the rest of the grid stays below until you press Down. Sets the grid's top padding
     * for that, and says whether it was already right.
     */
    private boolean parkDock() {
        int h = grid.getHeight();
        if (h <= 0) return false;
        // Item height follows the tile size, which follows the column count (AppAdapter.tileWidthPx).
        int inner = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density)), density);
        int dockRow = itemH + (int) ((6 + 12) * density);   // item height plus DockDecoration offsets
        int want = Math.max(0, h - dockRow - itemH * rowsBelowDockSetting - (int) (16 * density));
        if (grid.getPaddingTop() == want) return true;
        grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
        return false;
    }

    /**
     * Home's first frame waits until Home is whole: tiles bound and laid out, the dock parked at the
     * bottom and the wallpaper in. Drawn as soon as the window was ready, it showed the glass tray
     * floating empty near the top, then the tiles, then the dock dropping into place. This matters
     * most after a long stay in another app, when Android has reclaimed the launcher and Home starts
     * from nothing. The wait is capped, so Home can never hang on a slow picture.
     */
    private void holdFirstFrame() {
        final long start = android.os.SystemClock.uptimeMillis();
        firstFrameHeld = true;
        final boolean expectsWallpaper = !wallpapers.isEmpty() || (config.wallpaper != null && !placesMode) || config.wallpaperOnline;
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // Parked here rather than by the layout listener alone: while frames are held, the
                // second layout pass that listener relies on does not always come.
                boolean parked = parkDock();
                // The place's name and lines come in the same frame as the picture they describe,
                // whenever focus lands in the dock, where the shelf shows.
                boolean whole = adapter.getItemCount() > 0 && grid.getChildCount() > 0 && parked
                        && (wallBitmap != null || !expectsWallpaper) && (heroShown || lastFocused >= COLUMNS);
                if (!whole && android.os.SystemClock.uptimeMillis() - start < FIRST_FRAME_WAIT_MS) {
                    return false;   // skip this frame; the next traversal asks again
                }
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                firstFrameHeld = false;
                android.util.Log.i("TvHome.Perf", "first frame after " + (android.os.SystemClock.uptimeMillis() - start)
                        + " ms" + (whole ? "" : " (gave up waiting: tiles=" + adapter.getItemCount()
                        + " laidOut=" + grid.getChildCount() + " dockParked=" + parked
                        + " wallpaper=" + (wallBitmap != null) + ")"));
                return true;
            }
        });
    }

    /** Tells the glass the glow's colour and strength, so what shows through matches around it. */
    private void syncGlassGlow() {
        Backdrop.setGlow(ambientShape(), glowFrom, ambientColor, glowMix, ambient.getAlpha());
    }

    /** Runs the glass through the same colour blend as the room's TransitionDrawable. */
    private void blendGlassGlow(int from, long ms) {
        if (glowBlend != null) glowBlend.cancel();
        glowFrom = from;
        glowMix = 0f;
        glowBlend = android.animation.ValueAnimator.ofFloat(0f, 1f);
        glowBlend.setDuration(ms);
        glowBlend.addUpdateListener(a -> {
            glowMix = (float) a.getAnimatedValue();
            syncGlassGlow();
        });
        glowBlend.start();
    }

    /** The glow's shape in white, drawn on first use and kept: a tint gives it each app's colour. */
    private Bitmap ambientShape() {
        if (ambientShape != null) return ambientShape;
        int w = 480;
        int h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(
                w * 0.32f, h * 0.42f, w * 0.62f,
                new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(bmp).drawRect(0, 0, w, h, paint);
        ambientShape = bmp;
        return bmp;
    }

    /** With crossFade, the old glow fades into the new one (two small textures, one blend). */
    private void setAmbient(int color, boolean crossFade) {
        if (color == ambientColor && ambient.getBackground() != null) return;
        android.graphics.drawable.Drawable old = ambient.getBackground();
        if (old instanceof android.graphics.drawable.TransitionDrawable) {
            android.graphics.drawable.TransitionDrawable t = (android.graphics.drawable.TransitionDrawable) old;
            old = t.getDrawable(t.getNumberOfLayers() - 1);   // a blend still running: start from where it was heading
        }
        int previous = ambientColor;
        ambientColor = color;
        // Every app's glow is the same shape in a different colour, so the shape is drawn once and
        // each app only tints it. Drawing a fresh bitmap per move cost a CPU fill, half a megabyte
        // and a texture upload every time focus moved, which is what made moving around feel heavy
        // once every tile, not only the dock, tinted the room.
        BitmapDrawable drawable = new BitmapDrawable(getResources(), ambientShape());
        drawable.setGravity(Gravity.FILL);
        drawable.setColorFilter(new android.graphics.PorterDuffColorFilter(
                0xFF000000 | color, android.graphics.PorterDuff.Mode.SRC_IN));
        if (crossFade && old != null) {
            android.graphics.drawable.TransitionDrawable t = new android.graphics.drawable.TransitionDrawable(
                    new android.graphics.drawable.Drawable[]{old, drawable});
            t.setCrossFadeEnabled(true);
            ambient.setBackground(t);
            t.startTransition(900);
            // The glass blends with the room, over the same 450 ms, instead of jumping ahead of it.
            blendGlassGlow(previous, 900);
        } else {
            ambient.setBackground(drawable);
            if (glowBlend != null) glowBlend.cancel();
            glowFrom = color;
            glowMix = 1f;
            syncGlassGlow();
        }
    }

    /** Focus has left the apps altogether: both the shelf and the glow go. */
    private void hideHero() {
        hideShelf();
        if (!ambientShown) return;
        ambientShown = false;
        ambient.animate().alpha(0f).setDuration(reducedMotion ? 140 : 300)
                .setUpdateListener(a -> syncGlassGlow()).start();
    }

    private void hideShelf() {
        if (!heroShown) return;
        heroShown = false;
        if (heroShelf != null) heroShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
        if (islamicShelf != null) islamicShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
    }

    /**
     * Lights the room in this app's colour. From one app to the next it blends straight across,
     * with no dip to plain in between, whether the move is along the dock, down into the grid or
     * back up again.
     */
    private void showAmbient(int color) {
        if (!ambientShown) {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(reducedMotion ? 160 : 600)
                    .setUpdateListener(a -> syncGlassGlow()).start();
            ambientShown = true;
        } else if (color != ambientColor) {
            setAmbient(color, !reducedMotion);
        }
    }

    private void hideGlass() {
        glassShown = false;
    }

    // ---- navigation ------------------------------------------------------------------------------

    // ---- arranging the tiles ---------------------------------------------------------------------

    /** The tile being moved (adapter position), or -1; its card wiggles while it is. */
    private int movingPos = -1;
    private View movingCard;
    private android.animation.ObjectAnimator wiggle;
    /** A key whose release is swallowed: the OK or Back that put a moved tile down must not also open it. */
    private int swallowRelease = -1;
    /**
     * Slides the tiles into their new places while an app is moved. The grid has no item animations
     * otherwise: they would replay on every rebuild and cost frames while scrolling.
     */
    private androidx.recyclerview.widget.DefaultItemAnimator moveSlide;

    /** Holding OK on an app: a glass menu to move it, or to open Android's page for it. */
    @Override
    public boolean onTileHeld(int position, View card) {
        if (openMenu != null || movingPos >= 0) return true;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final String key = adapter.keyAt(position);
        if (adapter.canMove(position)) {
            addInputRow(rows, () -> {
                // No selection gliding back from the menu: it is simply gone, and the app wiggles.
                glider.hideNow();
                gliderOnPill = false;
                closeMenu(true);
            }, R.drawable.ic_move, getString(R.string.tile_move), -1, () -> startMove(key));
        }
        addInputRow(rows, () -> closeMenu(false), R.drawable.ic_info, getString(R.string.tile_app_info), -1,
                () -> adapter.openAppInfo(adapter.positionOf(key)));
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max((int) (190 * density), panel.getMeasuredWidth());
        // Under the app when there is room; over it in the dock, which sits near the bottom.
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        card.getLocationInWindow(menuAt);
        root.getLocationInWindow(menuGliderAt);
        int below = menuAt[1] - menuGliderAt[1] + card.getHeight() + (int) (18 * density) + panel.getMeasuredHeight();
        showMenu(panel, rows, card, width, below > root.getHeight() - (int) (24 * density));
        return true;
    }

    /** Picks the app up: it wiggles, the arrows carry it, and OK or Back puts it down. */
    private void startMove(String key) {
        int pos = adapter.positionOf(key);
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh == null || !adapter.canMove(pos)) return;
        movingPos = pos;
        movingCard = vh.itemView.findViewById(R.id.card);
        movingCard.requestFocus();
        glider.setPaneHidden(true);
        if (moveSlide == null) {
            moveSlide = new androidx.recyclerview.widget.DefaultItemAnimator();
            moveSlide.setSupportsChangeAnimations(false);
            moveSlide.setMoveDuration(reducedMotion ? 140 : 220);
        }
        grid.setItemAnimator(moveSlide);
        float tilt = reducedMotion ? 0.8f : 1.4f;
        wiggle = android.animation.ObjectAnimator.ofFloat(movingCard, View.ROTATION, -tilt, tilt);
        wiggle.setDuration(reducedMotion ? 260 : 140);
        wiggle.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        wiggle.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        wiggle.start();
    }

    /** Puts the moving app down where it is; the order was kept as it moved. */
    private void finishMove() {
        if (movingPos < 0) return;
        if (wiggle != null) wiggle.cancel();
        wiggle = null;
        if (movingCard != null) movingCard.animate().rotation(0f).setDuration(120).start();
        movingCard = null;
        movingPos = -1;
        glider.setPaneHidden(false);
        // Back to no item animations once the last slide has finished.
        RecyclerView.ItemAnimator slide = grid.getItemAnimator();
        if (slide != null) slide.isRunning(() -> {
            if (movingPos < 0 && grid.getItemAnimator() == slide) grid.setItemAnimator(null);
        });
    }

    /** Carries the moving app one step, the other tiles making room, and the selection follows it. */
    private void stepMove(int keyCode) {
        int last = adapter.lastMovable();
        int to = movingPos;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (movingPos % COLUMNS > 0) to = movingPos - 1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (movingPos % COLUMNS < COLUMNS - 1) to = Math.min(movingPos + 1, last);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (movingPos >= COLUMNS) to = movingPos - COLUMNS;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (movingPos / COLUMNS < last / COLUMNS) to = Math.min(movingPos + COLUMNS, last);
                break;
            default:
                return;
        }
        if (!adapter.move(movingPos, to)) return;
        movingPos = to;
        final View card = movingCard;
        final int pos = to;
        // Once the tiles have shifted, glide the selection (and the shelf, and the scroll) to it.
        grid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                grid.removeOnLayoutChangeListener(this);
                grid.post(() -> {
                    if (movingPos == pos && card != null && card.isAttachedToWindow()) onTileFocused(pos, card);
                });
            }
        });
        grid.requestLayout();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        lastUserInteraction = android.os.SystemClock.elapsedRealtime();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        lastUserInteraction = android.os.SystemClock.elapsedRealtime();
        if (screensaverActive) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                dismissScreensaver();
            }
            return true;
        }
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_SETTINGS) {
            if (event.getAction() == KeyEvent.ACTION_UP) showSettingsPopup();
            return true;
        }
        // The remote's own app buttons (Netflix, YouTube, Prime...): launch what was mapped, or show the key's number.
        {
            String mapped = KeyMapReceiver.packageFor(this, code);
            if (mapped != null) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    Intent li = getPackageManager().getLaunchIntentForPackage(mapped);
                    if (li == null) li = getPackageManager().getLeanbackLaunchIntentForPackage(mapped);
                    if (li != null) startActivity(li);
                    else Toast.makeText(this, mapped + " is not installed", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                String kn = KeyEvent.keyCodeToString(code);
                if ((kn != null && !kn.isEmpty() && Character.isDigit(kn.charAt(0))) || (code >= 297 && code <= 304)) {
                    Toast.makeText(this, "Remote key " + code + " (scan " + event.getScanCode() + ")", Toast.LENGTH_LONG).show();
                }
            }
        }
        if (wallpaperFullscreen) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                setWallpaperFullscreen(false);
                if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                    doubleBackPending = true;
                    singleBackRunnable = () -> {
                        doubleBackPending = false;
                        singleBackRunnable = null;
                    };
                    handler.postDelayed(singleBackRunnable, DOUBLE_BACK_DELAY_MS);
                }
            }
            return true;
        }
        if (doubleBackPending && code != KeyEvent.KEYCODE_BACK && code != KeyEvent.KEYCODE_ESCAPE) {
            if (singleBackRunnable != null) {
                handler.removeCallbacks(singleBackRunnable);
                singleBackRunnable = null;
            }
            doubleBackPending = false;
        }
        if (code == swallowRelease && event.getAction() == KeyEvent.ACTION_UP) {
            swallowRelease = -1;
            return true;
        }
        if (code == KeyEvent.KEYCODE_APP_SWITCH) {
            return true; // Live apps disabled per request
        }
        if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_ALL_APPS) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.isLongPress() || (event.getFlags() & KeyEvent.FLAG_LONG_PRESS) != 0 || event.getRepeatCount() > 0) {
                    if (!isHomeLongPressed) {
                        isHomeLongPressed = true;
                        handler.removeCallbacks(longPressHomeRunnable);
                        longPressHomeRunnable.run();
                    }
                    return true;
                }
                if (event.getRepeatCount() == 0) {
                    isHomeLongPressed = false;
                    handler.removeCallbacks(longPressHomeRunnable);
                    handler.postDelayed(longPressHomeRunnable, 450);
                }
                return true;
            }
            if (event.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPressHomeRunnable);
                if (isHomeLongPressed) {
                    isHomeLongPressed = false;
                    return true;
                }
                if (screensaverActive) {
                    dismissScreensaver();
                    return true;
                }
                if (cardPrompt != null) cardPrompt.dismiss();
                if (openPopup != null) openPopup.dismiss();
                closeMenu(false);
                if (movingPos >= 0) finishMove();
                focusTile(0);
                return true;
            }
            return true;
        }
        if (movingPos < 0) {
            if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    handleBackPress();
                }
                return true;
            }
            return super.dispatchKeyEvent(event);
        }
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) stepMove(code);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (down) {
                    finishMove();
                    swallowRelease = code;
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return position % COLUMNS == 0;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                // Straight to whatever lies above: football on the left, prayer times in the middle, Wi-Fi and settings on the right.
                float frac = (position + 0.5f) / COLUMNS;
                if (frac < 0.34f) {
                    if (sportsCard != null && sportsCard.getVisibility() == View.VISIBLE && sportsCard.getChildCount() > 1) {
                        sportsCard.getChildAt(sportsCard.getChildCount() - 1).requestFocus();
                        return true;
                    }
                    if (weatherAirQualityView != null && weatherAirQualityView.getVisibility() == View.VISIBLE) {
                        weatherAirQualityView.requestFocus();
                        return true;
                    }
                } else if (frac < 0.67f) {
                    View pt = findViewById(R.id.prayerTable);
                    if (pt != null && pt.getVisibility() == View.VISIBLE && islamicShelf != null && islamicShelf.getVisibility() == View.VISIBLE) {
                        pt.requestFocus();
                        return true;
                    }
                    if (ayahBlock != null && ayahBlock.getVisibility() == View.VISIBLE) {
                        ayahBlock.requestFocus();
                        return true;
                    }
                } else {
                    View top = btnWifi != null && btnWifi.getVisibility() == View.VISIBLE ? btnWifi : findViewById(R.id.btnSettings);
                    if (top != null) {
                        top.requestFocus();
                        return true;
                    }
                }
                return false;          // dock row: let focus move to the top bar
            }
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        focusTile(target);
        return true;
    }

    /** The tile {@link #focusWhenSettled} focuses once the grid stops scrolling to it. */
    private int pendingFocus;
    private final RecyclerView.OnScrollListener focusWhenSettled = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
            rv.removeOnScrollListener(this);
            RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pendingFocus);
            View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
            if (target != null) target.requestFocus();
        }
    };

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) {
            (btnWifi.getVisibility() == View.VISIBLE ? btnWifi : findViewById(R.id.btnSettings)).requestFocus();
            return;
        }
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles. One listener at most,
        // aimed at the latest target, so asking again mid-scroll cannot pull focus back to an older one.
        pendingFocus = pos;
        grid.removeOnScrollListener(focusWhenSettled);
        grid.addOnScrollListener(focusWhenSettled);
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateShamsiDate() {
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    private void updatePrayerTimes() {
        if (prayerTimesView != null) {
            prayerTimesView.setText(PrayerTimes.getFormatted(this));
        }
        updateIslamicShelf();
        if (screensaverActive) {
            updateNightstandScreensaver();
        }
    }

    private void updateWeatherAqi() {
        HomeWeather.Line last = HomeWeather.last(this);
        if (last != null) {
            showHomeWeather(last);
        } else if (weatherAirQualityView != null) {
            weatherAirQualityView.setText(WeatherAirQuality.getFormatted(this));
        }
        updateHomeWeather();
    }

    private void updateHomeWeather() {
        if (weatherAirQualityView == null || homeWeatherRunning || !HomeWeather.due(this)) return;
        homeWeatherRunning = true;
        Location.Zone zone = Location.chosen(this);
        final double lat = (zone != null) ? zone.lat : 3.1412;
        final double lon = (zone != null) ? zone.lon : 101.6865;
        new Thread(() -> {
            HomeWeather.Line line = HomeWeather.refresh(getApplicationContext(), lat, lon);
            handler.post(() -> {
                homeWeatherRunning = false;
                if (line != null && !isDestroyed()) showHomeWeather(line);
            });
        }, "home-weather").start();
    }

    private void showHomeWeather(HomeWeather.Line line) {
        if (weatherAirQualityView == null) return;
        if (line == null) {
            weatherAirQualityView.setText(WeatherAirQuality.getFormatted(this));
            return;
        }
        String sep = line.weather.isEmpty() || line.api.isEmpty() ? "" : "     ·     ";
        Location.Zone pz = Location.chosen(this);
        String place = (pz != null && pz.town != null) ? pz.town + "     ·     " : "";
        android.text.SpannableString text = new android.text.SpannableString(place + line.weather + sep + line.api);
        if (!line.api.isEmpty()) {
            text.setSpan(new android.text.style.ForegroundColorSpan(line.apiColor), text.length() - line.api.length(),
                    text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        weatherAirQualityView.setText(text);
        if (line.icon != 0) {
            Drawable d = getDrawable(line.icon);
            int px = Math.round(20 * density);
            int yOffset = 0;
            d.setBounds(0, yOffset, px, px + yOffset);
            weatherAirQualityView.setCompoundDrawables(d, null, null, null);
            weatherAirQualityView.setCompoundDrawablePadding(Math.round(8 * density));
        } else {
            weatherAirQualityView.setCompoundDrawables(null, null, null, null);
        }
        updateWeatherOverlay(line);
    }

    public void ensureWeatherOverlayView() {
        if (weatherOverlayView == null && root != null) {
            weatherOverlayView = new WeatherBackgroundView(this);
            weatherOverlayView.setOverlayMode(true);
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
                android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                if (am != null) {
                    am.getMemoryInfo(mi);
                    if (mi.totalMem <= 1610612736L) {
                        weatherOverlayView.setLowSpecMode(true);
                    }
                }
            } catch (Exception ignored) {}

            int idx = (wallView != null) ? root.indexOfChild(wallView) + 1 : 0;
            root.addView(weatherOverlayView, idx, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            weatherOverlayView.setVisibility(View.GONE);
        }
    }

    public void updateWeatherOverlay(HomeWeather.Line line) {
        ensureWeatherOverlayView();
        if (weatherOverlayView == null) return;

        boolean enabled = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("dynamic_weather_overlay", true);
        if (!enabled) {
            weatherOverlayView.setCondition(WeatherBackgroundView.ConditionType.NONE);
            weatherOverlayView.stopAnimation();
            weatherOverlayView.setVisibility(View.GONE);
            return;
        }

        applyWeatherText(weatherOverlayView, weatherSkyText(line));
    }

    /** The sky part of the weather line, lower case: no "next hour" part, or the test word when one is set. */
    private String weatherSkyText(HomeWeather.Line line) {
        String wText = "";
        if (testWeatherOverride != null && !testWeatherOverride.isEmpty()) {
            wText = testWeatherOverride.toLowerCase(Locale.ROOT);
        } else if (line != null && line.weather != null) {
            wText = line.weather.toLowerCase(Locale.ROOT);
            // Only the sky now: the "next hour" part would otherwise start rain that is an hour away.
            int cut = wText.indexOf('\u00B7');
            if (cut > 0) wText = wText.substring(0, cut);
        }
        return wText;
    }

    /** Sets a weather view (home overlay or screensaver sky) from the sky text. */
    private void applyWeatherText(WeatherBackgroundView v, String wText) {
        WeatherBackgroundView.ConditionType cond = WeatherBackgroundView.ConditionType.NONE;
        boolean heavy = false;
        if (wText.contains("thunder") || wText.contains("storm") || wText.contains("ribut") || wText.contains("petir")) {
            cond = WeatherBackgroundView.ConditionType.THUNDERSTORM;
        } else if (wText.contains("heavy") || wText.contains("lebat") || wText.contains("deras")) {
            cond = WeatherBackgroundView.ConditionType.RAIN;
            heavy = true;
        } else if (wText.contains("drizzle") || wText.contains("gerimis")) {
            cond = WeatherBackgroundView.ConditionType.DRIZZLE;
        } else if (wText.contains("rain") || wText.contains("shower") || wText.contains("hujan") || wText.contains("renyai")) {
            cond = WeatherBackgroundView.ConditionType.RAIN;
        } else if (wText.contains("snow")) {
            cond = WeatherBackgroundView.ConditionType.SNOW;
        } else if (wText.contains("haze") || wText.contains("fog") || wText.contains("kabus") || wText.contains("mist")) {
            cond = WeatherBackgroundView.ConditionType.FOG;
        } else if (wText.contains("cloud") || wText.contains("mendung") || wText.contains("overcast")) {
            cond = WeatherBackgroundView.ConditionType.CLOUDY;
        }

        // Day or night: the API's flag from the last fetch, or the clock for a test word.
        boolean day;
        if (wText.contains("night") || wText.contains("malam")) day = false;
        else if (wText.contains("sunny") || wText.contains("hot") || wText.contains("panas")) day = true;
        else if (testWeatherOverride != null && !testWeatherOverride.isEmpty()) {
            int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
            day = h >= 7 && h < 19;
        } else day = HomeWeather.isDay(this);

        // Wind: from the API's km/h, or a test word.
        float wind = 0f;
        if (wText.contains("wind") || wText.contains("angin") || wText.contains("gale") || wText.contains("breez")) {
            wind = 0.8f;
        } else if (testWeatherOverride == null || testWeatherOverride.isEmpty()) {
            float kmh = HomeWeather.windKmh(this);
            wind = kmh < 12f ? 0f : Math.min(1f, (kmh - 8f) / 32f);
        }

        // Hot: a temperature of 33 degrees or more, or a test word.
        boolean hot = wText.contains("hot") || wText.contains("panas");
        int deg = wText.indexOf('\u00B0');
        if (deg > 0) {
            try { hot = hot || Integer.parseInt(wText.substring(0, deg).trim()) >= 33; } catch (Exception ignored) { }
        }

        // Clear skies: sun by day, stars by night; the sky text for it is "clear", "mainly clear", "cerah".
        boolean clearSky = wText.contains("clear") || wText.contains("cerah") || wText.contains("sunny")
                || wText.contains("fair") || wText.contains("hot") || wText.contains("panas")
                || wText.contains("night") || wText.contains("malam") || wText.contains("wind") || wText.contains("angin");
        if (cond == WeatherBackgroundView.ConditionType.NONE && clearSky) {
            cond = day ? WeatherBackgroundView.ConditionType.SUNNY : WeatherBackgroundView.ConditionType.NIGHT;
        }

        v.setWind(wind);
        v.setHot(hot && day);
        v.setHeavyRain(heavy);
        if (cond == WeatherBackgroundView.ConditionType.NONE) {
            v.setCondition(WeatherBackgroundView.ConditionType.NONE);
            v.stopAnimation();
            v.setVisibility(View.GONE);
        } else {
            v.setVisibility(View.VISIBLE);
            v.setCondition(cond);
            v.startAnimation();
        }
    }

    public void setTestWeatherOverride(String condition) {
        if ("reset".equalsIgnoreCase(condition) || "clear".equalsIgnoreCase(condition) || "none".equalsIgnoreCase(condition)) {
            this.testWeatherOverride = null;
        } else {
            this.testWeatherOverride = condition;
        }
        updateWeatherOverlay(HomeWeather.last(this));
    }

    public boolean isIslamicShelfEnabled() {
        return getSharedPreferences("theme_prefs", MODE_PRIVATE).getBoolean("islamic_shelf_enabled", true);
    }

    public void setIslamicShelfEnabled(boolean enabled) {
        getSharedPreferences("theme_prefs", MODE_PRIVATE).edit().putBoolean("islamic_shelf_enabled", enabled).apply();
        if (islamicShelf != null) {
            islamicShelf.setVisibility(enabled ? View.VISIBLE : View.GONE);
            if (enabled) updateIslamicShelf();
        }
    }

    void updateIslamicShelf() {
        if (islamicShelf == null) return;
        if (!isIslamicShelfEnabled()) {
            islamicShelf.setVisibility(View.GONE);
            return;
        }

        if (WeeklyAyah.shown(this)) {
            if (MinbarManager.getDisplayMode(this) == MinbarManager.MODE_MINBAR) {
                MinbarManager.KhutbahItem k = MinbarManager.getKhutbah(this);
                showMinbar(k);
                MinbarManager.refreshIfDue(this, () -> {
                    if (!isDestroyed()) showMinbar(MinbarManager.getKhutbah(this));
                });
            } else {
                WeeklyAyah.Ayah a = WeeklyAyah.stored(this);
                if (a == null) a = WeeklyAyah.last(this);
                showAyah(a);
                updateAyah();
            }
        } else if (ayahBlock != null) {
            ayahBlock.setVisibility(View.GONE);
        }

        Location.Zone currentZone = Location.chosen(this);
        String zoneTitle = (currentZone != null && currentZone.town != null) ? currentZone.town.toUpperCase(Locale.ROOT) : PrayerTimes.getShortZoneName(this);
        // The "WAKTU SOLAT <town> / Hijri date" line above the times is removed.
        if (prayerHeader != null) prayerHeader.setVisibility(View.GONE);

        updatePrayerCountdown();
        PrayerTimes.PrayerItem[] items = PrayerTimes.getPrayerItems(this);
        if (items != null && items.length >= 6) {
            TextView[] labels = {lblSubuh, lblSyuruk, lblZohor, lblAsar, lblMaghrib, lblIsyak};
            TextView[] times = {timeSubuh, timeSyuruk, timeZohor, timeAsar, timeMaghrib, timeIsyak};

            for (int i = 0; i < 6; i++) {
                if (times[i] != null) times[i].setText(items[i].timeStr);
                if (labels[i] != null && labels[i].getParent() instanceof View) {
                    // The next prayer sits in a glass box; the others keep the same padding so nothing shifts.
                    View column = (View) labels[i].getParent();
                    column.setPadding(Math.round(10 * density), Math.round(4 * density), Math.round(10 * density), Math.round(5 * density));
                    if (items[i].isNext) {
                        if (!(column.getBackground() instanceof GlassDrawable)) {
                            column.setBackground(new GlassDrawable(column, 14 * density, density, null, 0.5f));
                            Backdrop.watch(column);
                        }
                    } else if (column.getBackground() != null) {
                        column.setBackground(null);
                    }
                }
                if (labels[i] != null) {
                    labels[i].setText(items[i].name);
                    if (items[i].isNext) {
                        labels[i].setTextColor(0xFFFFB300);
                        labels[i].setTypeface(Typeface.DEFAULT_BOLD);
                        if (times[i] != null) {
                            times[i].setTextColor(0xFFFFB300);
                            times[i].setTypeface(Typeface.DEFAULT_BOLD);
                        }
                    } else {
                        labels[i].setTextColor(0xB3FFFFFF);
                        labels[i].setTypeface(Typeface.DEFAULT);
                        if (times[i] != null) {
                            times[i].setTextColor(0xFFFFFFFF);
                            times[i].setTypeface(Typeface.DEFAULT);
                        }
                    }
                }
            }
        }
    }

    void updateAyah() {
        if (ayahBlock == null || ayahRunning || !WeeklyAyah.shown(this) || WeeklyAyah.stored(this) != null) return;
        if (MinbarManager.getDisplayMode(this) == MinbarManager.MODE_MINBAR) return;
        ayahRunning = true;
        new Thread(() -> {
            WeeklyAyah.Ayah a = WeeklyAyah.refresh(getApplicationContext());
            handler.post(() -> {
                ayahRunning = false;
                if (a != null && !isDestroyed() && MinbarManager.getDisplayMode(MainActivity.this) != MinbarManager.MODE_MINBAR) {
                    showAyah(a);
                }
            });
        }, "weekly-ayah").start();
    }

    void showAyah(WeeklyAyah.Ayah a) {
        if (ayahBlock == null) return;
        if (!WeeklyAyah.shown(this) || (nowPlayingCard != null && nowPlayingCard.getVisibility() == View.VISIBLE)) {
            ayahBlock.setVisibility(View.GONE);
            return;
        }
        if (a == null || a.arabic == null || a.arabic.isEmpty()) {
            ayahBlock.setVisibility(View.GONE);
            return;
        }

        int usable = getResources().getDisplayMetrics().widthPixels - (int) ((55 + 56) * density);
        int islamicMaxWidth = Math.round(usable * 0.38f);
        boolean is4K = (getResources().getDisplayMetrics().widthPixels >= 3840 || getResources().getDisplayMetrics().heightPixels >= 2160);

        final int turn = ZikirList.turn(this);
        if (turn != 0) {
            ZikirList.Zikir z = turn == 1 ? DoaList.current() : ZikirList.current();
            if (ayahLabel != null) {
                ayahLabel.setText(turn == 1 ? "DOA HARIAN  •  " + z.count : "ZIKIR  •  " + z.count);
                ayahLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, is4K ? 12f : 10f);
            }
            if (ayahArabic != null) {
                ayahArabic.setMaxWidth(islamicMaxWidth);
                ayahArabic.setSingleLine(true);
                ayahArabic.setEllipsize(TextUtils.TruncateAt.END);
                ayahArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
                ayahArabic.setGravity(Gravity.END);
                try {
                    ayahArabic.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.amiri_quran), Typeface.NORMAL);
                } catch (Exception ignored) {}
                ayahArabic.setTextColor(0xFFFFC56B);
                ayahArabic.setTextSize(TypedValue.COMPLEX_UNIT_SP, is4K ? 18f : 14.5f);
                ayahArabic.setText(z.arabic);
            }
            if (ayahRumi != null) {
                ayahRumi.setMaxWidth(islamicMaxWidth);
                ayahRumi.setText(z.rumi);
                ayahRumi.setVisibility(View.VISIBLE);
            }
            if (ayahText != null) {
                ayahText.setMaxWidth(islamicMaxWidth);
                ayahText.setText(z.meaning);
                ayahText.setTextSize(TypedValue.COMPLEX_UNIT_SP, is4K ? 12.5f : 10.5f);
                ayahText.setVisibility(View.VISIBLE);
            }
            ayahBlock.setVisibility(View.VISIBLE);
            return;
        }

        if (ayahLabel != null) {
            ayahLabel.setText(AppLanguage.getAyahLabel(this, a.reference));
            ayahLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, is4K ? 12f : 10f);
        }
        if (ayahArabic != null) {
            String text = a.arabic.trim();
            ayahArabic.setMaxWidth(islamicMaxWidth);
            ayahArabic.setSingleLine(true);
            ayahArabic.setMaxLines(1);
            ayahArabic.setEllipsize(TextUtils.TruncateAt.END);
            ayahArabic.setTextDirection(View.TEXT_DIRECTION_RTL);
            ayahArabic.setGravity(Gravity.END);
            try {
                ayahArabic.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.amiri_quran), Typeface.NORMAL);
            } catch (Exception ignored) {}
            ayahArabic.setTextColor(0xFFFFC56B);

            float baseSp = is4K ? 18f : 14.5f;
            android.text.TextPaint paint = new android.text.TextPaint(ayahArabic.getPaint());
            paint.setTextSize(baseSp * getResources().getDisplayMetrics().scaledDensity);
            float textWidth = paint.measureText(text);

            float targetSp = baseSp;
            float maxAllowed = islamicMaxWidth * 0.95f;
            while (textWidth > maxAllowed && targetSp > 11.5f) {
                targetSp -= 0.5f;
                paint.setTextSize(targetSp * getResources().getDisplayMetrics().scaledDensity);
                textWidth = paint.measureText(text);
            }
            ayahArabic.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp);
            ayahArabic.setText(text);
        }
        if (ayahText != null) {
            ayahText.setMaxWidth(islamicMaxWidth);
            if (ayahRumi != null) {
                ayahRumi.setMaxWidth(islamicMaxWidth);
                ayahRumi.setText(a.translit == null ? "" : a.translit);
                ayahRumi.setVisibility(a.translit == null || a.translit.isEmpty() ? View.GONE : View.VISIBLE);
            }
            // Doa harian stays in Malay whatever the app language is.
            String tr = (a.malay != null && !a.malay.isEmpty()) ? a.malay : a.english;
            ayahText.setText(tr != null ? tr.trim() : "");
            ayahText.setTextSize(TypedValue.COMPLEX_UNIT_SP, is4K ? 12.5f : 10.5f);
            ayahText.setVisibility(tr != null && !tr.trim().isEmpty() ? View.VISIBLE : View.GONE);
        }
        ayahBlock.setVisibility(View.VISIBLE);
    }

    void showMinbar(MinbarManager.KhutbahItem k) {
        if (ayahBlock == null) return;
        if (!WeeklyAyah.shown(this) || (nowPlayingCard != null && nowPlayingCard.getVisibility() == View.VISIBLE)) {
            ayahBlock.setVisibility(View.GONE);
            return;
        }
        if (k == null || k.title == null || k.title.isEmpty()) {
            ayahBlock.setVisibility(View.GONE);
            return;
        }

        int usable = getResources().getDisplayMetrics().widthPixels - (int) ((55 + 56) * density);
        int islamicMaxWidth = Math.round(usable * 0.38f);
        boolean is4K = (getResources().getDisplayMetrics().widthPixels >= 3840 || getResources().getDisplayMetrics().heightPixels >= 2160);

        if (ayahLabel != null) {
            String datePart = (k.date != null && !k.date.isEmpty()) ? "  •  " + k.date.toUpperCase(Locale.ROOT) : "";
            ayahLabel.setText("KHUTBAH MINBAR • " + k.authority.toUpperCase(Locale.ROOT) + datePart);
            ayahLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, is4K ? 11.5f : 9.5f);
        }
        if (ayahArabic != null) {
            String text = k.title.trim();
            ayahArabic.setMaxWidth(islamicMaxWidth);
            ayahArabic.setSingleLine(true);
            ayahArabic.setMaxLines(1);
            ayahArabic.setEllipsize(TextUtils.TruncateAt.END);
            ayahArabic.setTextDirection(View.TEXT_DIRECTION_LTR);
            ayahArabic.setGravity(Gravity.END);
            try {
                ayahArabic.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.outfit), Typeface.BOLD);
            } catch (Exception e) {
                ayahArabic.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            }
            ayahArabic.setTextColor(0xFFFFC56B);

            float baseSp = is4K ? 16f : 13.5f;
            android.text.TextPaint paint = new android.text.TextPaint(ayahArabic.getPaint());
            paint.setTextSize(baseSp * getResources().getDisplayMetrics().scaledDensity);
            float textWidth = paint.measureText(text);

            float targetSp = baseSp;
            float maxAllowed = islamicMaxWidth * 0.95f;
            while (textWidth > maxAllowed && targetSp > 11.0f) {
                targetSp -= 0.5f;
                paint.setTextSize(targetSp * getResources().getDisplayMetrics().scaledDensity);
                textWidth = paint.measureText(text);
            }
            ayahArabic.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp);
            ayahArabic.setText(text);
        }
        if (ayahText != null) {
            ayahText.setMaxWidth(islamicMaxWidth);
            if (ayahRumi != null) ayahRumi.setVisibility(View.GONE);
            String summary = (k.subtitle != null && !k.subtitle.isEmpty()) ? k.subtitle + " — " + k.excerpt : k.excerpt;
            ayahText.setText(summary != null ? summary.trim() : "");
            ayahText.setTextSize(TypedValue.COMPLEX_UNIT_SP, is4K ? 12.5f : 10.5f);
            ayahText.setVisibility(summary != null && !summary.trim().isEmpty() ? View.VISIBLE : View.GONE);
        }
        ayahBlock.setVisibility(View.VISIBLE);
    }

    void showIslamicDetailDialog() {
        int mode = MinbarManager.getDisplayMode(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        if (mode == MinbarManager.MODE_MINBAR) {
            MinbarManager.KhutbahItem k = MinbarManager.getKhutbah(this);
            builder.setTitle("" + k.title);
            String message = "Sumber: " + k.authority + (k.date != null && !k.date.isEmpty() ? "  •  " + k.date : "") + "\n\n"
                    + (k.subtitle != null && !k.subtitle.isEmpty() ? "" + k.subtitle + "\n\n" : "")
                    + k.excerpt;
            builder.setMessage(message);
            builder.setPositiveButton("Switch to Daily Doa", (d, w) -> {
                MinbarManager.setDisplayMode(MainActivity.this, MinbarManager.MODE_DOA);
                SoundEffects.playSelect();
                updateIslamicShelf();
                Toast.makeText(MainActivity.this, "Display changed to Daily Doa", Toast.LENGTH_SHORT).show();
            });
        } else {
            int turn = ZikirList.turn(this);
            if (turn == 1 || turn == 2) {
                // The same doa or zikir the home screen is showing, with its Malay meaning.
                ZikirList.Zikir z = turn == 1 ? DoaList.current() : ZikirList.current();
                builder.setTitle((turn == 1 ? "Doa harian  -  " : "Zikir  -  ") + z.count);
                builder.setMessage(z.arabic + "\n\n" + z.rumi + "\n\n" + z.meaning);
            } else {
                WeeklyAyah.Ayah a = WeeklyAyah.stored(this);
                if (a == null) a = WeeklyAyah.last(this);
                builder.setTitle("" + a.reference);
                builder.setMessage(a.arabic + "\n\n" + a.translation(AppLanguage.LANG_MS));
            }
            builder.setPositiveButton("Switch to Minbar Text", (d, w) -> {
                MinbarManager.setDisplayMode(MainActivity.this, MinbarManager.MODE_MINBAR);
                SoundEffects.playSelect();
                updateIslamicShelf();
                Toast.makeText(MainActivity.this, "Display changed to Minbar Text", Toast.LENGTH_SHORT).show();
            });
        }
        builder.setNegativeButton("Close", (d, w) -> d.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    void showDisplayModeChoiceDialog() {
        showDisplayModeChoiceDialog(null);
    }

    void showDisplayModeChoiceDialog(Runnable onModeChanged) {
        final String[] options = {"Ayat Al-Quran", "Doa harian", "Zikir", "Minbar text (Friday sermon)"};
        final boolean[] checked = {
                ZikirList.enabled(this, 0), ZikirList.enabled(this, 1), ZikirList.enabled(this, 2),
                MinbarManager.getDisplayMode(this) == MinbarManager.MODE_MINBAR};
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("Display content");
        builder.setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> {
            checked[which] = isChecked;
            SoundEffects.playSelect();
            if (which < 3) ZikirList.setEnabled(MainActivity.this, which, isChecked);
            else MinbarManager.setDisplayMode(MainActivity.this, isChecked ? MinbarManager.MODE_MINBAR : MinbarManager.MODE_DOA);
            updateIslamicShelf();
            if (onModeChanged != null) onModeChanged.run();
        });
        builder.setNeutralButton("Timer: " + ZikirList.seconds(this) + " s", (dialog, w) -> showIslamicTimerDialog(onModeChanged));
        builder.setPositiveButton("Done", (dialog, w) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    /** How long each ayat, doa or zikir stays on the home screen: from 30 seconds. */
    private void showIslamicTimerDialog(Runnable onModeChanged) {
        final int[] secs = {30, 45, 60, 90, 120, 180};
        String[] names = new String[secs.length];
        int cur = 0;
        for (int i = 0; i < secs.length; i++) {
            names[i] = secs[i] + " seconds";
            if (secs[i] == ZikirList.seconds(this)) cur = i;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("Change every");
        builder.setSingleChoiceItems(names, cur, (dialog, which) -> {
            ZikirList.setSeconds(MainActivity.this, secs[which]);
            SoundEffects.playSelect();
            handler.removeCallbacks(ayahRotate);
            handler.post(ayahRotate);
            dialog.dismiss();
            showDisplayModeChoiceDialog(onModeChanged);
        });
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    private void updateDateAndPeriod() {
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        String period = AppLanguage.getPeriodString(this, hour);

        int day = cal.get(Calendar.DAY_OF_MONTH);
        int month = cal.get(Calendar.MONTH);
        int year = cal.get(Calendar.YEAR);

        String lang = AppLanguage.getLanguage(this);
        String[] months;
        switch (lang) {
            case AppLanguage.LANG_EN:
                months = new String[]{"JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC"};
                break;
            case AppLanguage.LANG_ID:
                months = new String[]{"JAN", "FEB", "MAR", "APR", "MEI", "JUN", "JUL", "AGU", "SEP", "OKT", "NOV", "DES"};
                break;
            case AppLanguage.LANG_MS:
            default:
                months = new String[]{"JAN", "FEB", "MAC", "APR", "MEI", "JUN", "JUL", "OGO", "SEP", "OKT", "NOV", "DIS"};
                break;
        }
        String mStr = (month >= 0 && month < 12) ? months[month] : "";
        String dateStr = day + " " + mStr + " " + year;

        // 24-hour clock: the Hijri date sits beside it where AM/PM used to be.
        if (ampmView != null) {
            try { ampmView.setText(PrayerTimes.getHijriDate(this)); } catch (Exception e) { ampmView.setText(""); }
        }
        if (dateView != null) dateView.setText(dateStr);
        if (greetingView != null) {
            greetingView.setText(hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening");
        }
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        if (btnWifi.getVisibility() == View.GONE) return;   // show_network_pill off
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        btnWifi.setText(text);
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    /** Shows an Apple TV-style frosted glass Wi-Fi control center popup. */
    void showWifiPopup() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = btnWifi;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        String ssid = getString(R.string.wifi);
        String ip = "";
        boolean isConnected = false;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activeNet = cm != null ? cm.getActiveNetwork() : null;
            if (activeNet != null) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(activeNet);
                if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                    isConnected = true;
                }
            }
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wm.isWifiEnabled()) {
                WifiInfo info = wm.getConnectionInfo();
                if (info != null && info.getNetworkId() != -1) {
                    String s = info.getSSID();
                    if (s != null && !s.equals("<unknown ssid>") && !s.isEmpty()) {
                        ssid = s.replaceAll("^\"|\"$", "");
                    }
                    int ipVal = info.getIpAddress();
                    if (ipVal != 0) {
                        ip = String.format(Locale.getDefault(), "%d.%d.%d.%d",
                                (ipVal & 0xff), (ipVal >> 8 & 0xff), (ipVal >> 16 & 0xff), (ipVal >> 24 & 0xff));
                    }
                }
            }
        } catch (Exception ignored) {
        }

        int width = (int) (240 * density);

        // Row 1: Current Wi-Fi status & SSID
        addInputRow(rows, close, R.drawable.ic_wifi, ssid, isConnected ? TvInputManager.INPUT_STATE_CONNECTED : TvInputManager.INPUT_STATE_DISCONNECTED, () -> {
            SoundEffects.playSelect();
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
        });

        addSeparator(rows);

        // Nearby networks (needs location permission to scan)
        try {
            WifiManager wm2 = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 4711);
                addInputRow(rows, close, R.drawable.ic_wifi, "Allow location to list nearby Wi-Fi", -1, () -> {
                    SoundEffects.playSelect();
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 4711);
                });
            } else if (wm2 != null) {
                try { wm2.startScan(); } catch (Exception ignored) {}
                java.util.List<android.net.wifi.ScanResult> found = wm2.getScanResults();
                java.util.LinkedHashMap<String, Integer> best = new java.util.LinkedHashMap<>();
                if (found != null) {
                    for (android.net.wifi.ScanResult r : found) {
                        if (r.SSID == null || r.SSID.isEmpty() || r.SSID.equals(ssid)) continue;
                        Integer prev = best.get(r.SSID);
                        if (prev == null || r.level > prev) best.put(r.SSID, r.level);
                    }
                }
                java.util.List<java.util.Map.Entry<String, Integer>> sorted = new java.util.ArrayList<>(best.entrySet());
                java.util.Collections.sort(sorted, (x, y) -> y.getValue() - x.getValue());
                int shownCount = 0;
                for (java.util.Map.Entry<String, Integer> e : sorted) {
                    if (shownCount++ >= 6) break;
                    int bars = android.net.wifi.WifiManager.calculateSignalLevel(e.getValue(), 4);
                    String label = e.getKey() + "   " + new String[]{"▁", "▂▃", "▂▃▅", "▂▃▅▇"}[Math.max(0, Math.min(3, bars))];
                    addInputRow(rows, close, R.drawable.ic_wifi, label, -1, () -> {
                        SoundEffects.playSelect();
                        open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
                    });
                }
                if (shownCount == 0) {
                    addInputRow(rows, close, R.drawable.ic_wifi, "No other networks found", -1, () -> {});
                }
            }
        } catch (Exception ignored) {
        }
        addSeparator(rows);

        // Row 2: Wi-Fi settings
        addInputRow(rows, close, R.drawable.ic_settings, "Wi-Fi Settings", -1, () -> {
            SoundEffects.playSelect();
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
        });

        // Row 3: Tutup
        addInputRow(rows, close, R.drawable.ic_close, "Close", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(true);
        });

        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    /** Shows the Who's Watching profile selection dialog. */
    public void showCandyBoxPopup() {
        if (openMenu != null) {
            closeMenu(false);
        }
        SoundEffects.playSelect();
        WhoIsWatchingDialog.show(this);
    }

    void updateTopBarUserIcon() {
        if (btnCandyBox instanceof FrameLayout) {
            FrameLayout candy = (FrameLayout) btnCandyBox;
            ProfileManager.UserProfile active = ProfileManager.getActiveProfile(this);
            ProfileAvatarHelper.bindAvatar(candy, active, this);
            boolean isChild = ChildModeManager.isChildModeEnabled(this);
            candy.setContentDescription("Current user: " + active.name + (isChild ? " (Child mode)" : ""));
        }
    }

    public void updateSettingsBadge() {
        runOnUiThread(() -> {
            if (badgeSettingsUpdate != null) {
                boolean hasUpdate = AppUpdateManager.isUpdateAvailable(this);
                badgeSettingsUpdate.setVisibility(hasUpdate ? View.VISIBLE : View.GONE);
            }
        });
    }

    /** Shows the modern floating 2-column Settings modal (Profile, Avatars, Child Mode, Home, etc.). */
    public void showSettingsPopup() {
        if (openMenu != null) {
            closeMenu(false);
        }
        ModernSettingsDialog.show(this);
    }

    void showSoundEffectsDialog() {
        final String[] soundOptions = new String[]{
                "PlayStation 5 (Default Chime)",
                "Nintendo Switch (Click & Snap Pop)",
                "Xbox Series X/S (Warm Harmonic Chime)",
                "Sound Off (Muted)"
        };

        final String[] soundKeys = new String[]{
                SoundEffects.THEME_PS5,
                SoundEffects.THEME_SWITCH,
                SoundEffects.THEME_XBOX,
                SoundEffects.THEME_OFF
        };

        String currentTheme = SoundEffects.getActiveTheme(this);
        int selectedIndex = 0;
        for (int i = 0; i < soundKeys.length; i++) {
            if (soundKeys[i].equalsIgnoreCase(currentTheme)) {
                selectedIndex = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("Choose Sound Effects");
        builder.setSingleChoiceItems(soundOptions, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            String chosenKey = soundKeys[which];
            SoundEffects.setTheme(MainActivity.this, chosenKey);
            String displayName = SoundEffects.getThemeDisplayName(chosenKey);

            if (SoundEffects.THEME_OFF.equalsIgnoreCase(chosenKey)) {
                Toast.makeText(MainActivity.this, "Sound effects off", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Sound effect: " + displayName, Toast.LENGTH_SHORT).show();
                // Play sample of the selected theme
                handler.postDelayed(() -> SoundEffects.playSelect(), 120);
            }
        });
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    void showLanguageDialog() {
        SoundEffects.playSelect();
        final String[] langOptions = new String[]{
                "Bahasa Melayu (Standard)",
                "English",
                "Bahasa Indonesia"
        };
        final String[] langKeys = new String[]{
                AppLanguage.LANG_MS,
                AppLanguage.LANG_EN,
                AppLanguage.LANG_ID
        };

        String currentLang = AppLanguage.getLanguage(this);
        int selectedIndex = 0;
        for (int i = 0; i < langKeys.length; i++) {
            if (langKeys[i].equalsIgnoreCase(currentLang)) {
                selectedIndex = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("" + AppLanguage.strLanguage(this));
        builder.setSingleChoiceItems(langOptions, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            String chosenLang = langKeys[which];
            AppLanguage.setLanguage(MainActivity.this, chosenLang);
            WeeklyAyah.setEnglish(MainActivity.this, AppLanguage.LANG_EN.equals(chosenLang));
            WeeklyAyah.clearCache(MainActivity.this);
            HomeWeather.forget(MainActivity.this);
            showHomeWeather(HomeWeather.last(MainActivity.this));
            updateHomeWeather();
            updateIslamicShelf();
            updateDateAndPeriod();
            Toast.makeText(MainActivity.this, AppLanguage.strLanguageChangedToast(MainActivity.this, chosenLang), Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton(AppLanguage.strClose(this), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    void showAzanSettingsDialog() {
        SoundEffects.playSelect();
        Location.Zone chosenPlace = Location.chosen(this);
        String zoneCode = (chosenPlace != null) ? chosenPlace.code : "WLY01";
        java.util.Calendar cal = java.util.Calendar.getInstance();
        PrayerTimes.Day todayTimes = PrayerTimes.day(this, zoneCode, cal);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        AlertDialog[] dialogHolder = new AlertDialog[1];

        LinearLayout rootContainer = new LinearLayout(this);
        rootContainer.setOrientation(LinearLayout.VERTICAL);
        rootContainer.setPadding((int) (22 * density), (int) (16 * density), (int) (22 * density), (int) (16 * density));

        // Header Title Row
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(AppLanguage.strAzanSettingsHeaderTitle(this));
        titleView.setTextSize(19);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        titleView.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        titleCol.addView(titleView);

        TextView subView = new TextView(this);
        subView.setText(AppLanguage.strAzanSettingsHeaderSub(this));
        subView.setTextSize(12.5f);
        subView.setTextColor(0xD9FFFFFF);
        subView.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        subView.setFontVariationSettings("'wght' 600");
        subView.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        subView.setPadding(0, (int) (2 * density), 0, 0);
        titleCol.addView(subView);

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        rootContainer.addView(headerRow);

        // Gap below header
        View headerDivider = new View(this);
        headerDivider.setBackgroundColor(0x22FFFFFF);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, (int) (1 * density)));
        divLp.topMargin = (int) (8 * density);
        divLp.bottomMargin = (int) (8 * density);
        rootContainer.addView(headerDivider, divLp);

        // Content ScrollView with 2 balanced columns (16:9 widescreen layout)
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(true);

        LinearLayout twoCols = new LinearLayout(this);
        twoCols.setOrientation(LinearLayout.HORIZONTAL);

        // --- LEFT COLUMN: A (Suis Utama) & B (Pilihan Waktu Solat) ---
        LinearLayout leftCol = new LinearLayout(this);
        leftCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f);
        leftLp.rightMargin = (int) (12 * density);
        leftCol.setLayoutParams(leftLp);

        // Bahagian A: Suis Utama
        leftCol.addView(createDialogSectionHeader(AppLanguage.strAzanSectionMaster(this)));
        final TextView masterStatusTag = new TextView(this);
        LinearLayout masterRow = createDialogRow(
                AppLanguage.strAzanMasterTitle(this),
                AppLanguage.strAzanMasterDesc(this),
                masterStatusTag,
                null,
                () -> {
                    boolean nowOn = !Azan.isMasterEnabled(MainActivity.this);
                    Azan.setMasterEnabled(MainActivity.this, nowOn);
                    Azan.schedule(MainActivity.this, zoneCode);
                    SoundEffects.playSelect();
                    updateMasterTag(masterStatusTag, nowOn);
                    Toast.makeText(MainActivity.this, nowOn ? "Popup Azan Diaktifkan" : " Popup Azan Dimatikan", Toast.LENGTH_SHORT).show();
                }
        );
        masterRow.setId(View.generateViewId());
        updateMasterTag(masterStatusTag, Azan.isMasterEnabled(this));
        leftCol.addView(masterRow);

        // Bahagian B: Pilihan Waktu Solat
        leftCol.addView(createDialogSectionHeader(AppLanguage.strAzanSectionPrayers(this)));
        int[] prayers = {0, 2, 3, 4, 5};
        String[] localizedPrayerNames = AppLanguage.getPrayerNames(this);
        String[] prayerIcons = {"", "", "", "", ""};
        java.util.List<LinearLayout> prayerRowsList = new java.util.ArrayList<>();

        for (int i = 0; i < prayers.length; i++) {
            final int pIdx = prayers[i];
            String pName = prayerIcons[i] + localizedPrayerNames[pIdx];
            String pTimeStr = "";
            if (todayTimes != null && pIdx < todayTimes.minutes.length) {
                int min = todayTimes.minutes[pIdx];
                pTimeStr = String.format(Locale.ROOT, " • %02d:%02d", min / 60, min % 60);
            }

            final TextView prayerTag = new TextView(this);
            LinearLayout prayerRow = createDialogRow(
                    pName + pTimeStr,
                    null,
                    prayerTag,
                    null,
                    () -> {
                        boolean current = Azan.on(MainActivity.this, pIdx);
                        Azan.set(MainActivity.this, pIdx, !current);
                        Azan.schedule(MainActivity.this, zoneCode);
                        SoundEffects.playSelect();
                        updatePrayerTag(prayerTag, !current);
                    }
            );
            prayerRow.setId(View.generateViewId());
            prayerRowsList.add(prayerRow);
            updatePrayerTag(prayerTag, Azan.on(this, pIdx));
            leftCol.addView(prayerRow);
        }
        twoCols.addView(leftCol);

        // Subtle vertical divider between columns
        View colDivider = new View(this);
        colDivider.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams colDivLp = new LinearLayout.LayoutParams(Math.max(1, (int) (1 * density)), ViewGroup.LayoutParams.MATCH_PARENT);
        colDivLp.leftMargin = (int) (4 * density);
        colDivLp.rightMargin = (int) (12 * density);
        twoCols.addView(colDivider, colDivLp);

        // --- RIGHT COLUMN: C (Audio), D (Uji Popup), Panduan & Tutup ---
        LinearLayout rightCol = new LinearLayout(this);
        rightCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rightCol.setLayoutParams(rightLp);

        // Bahagian C: Audio
        rightCol.addView(createDialogSectionHeader(AppLanguage.strAzanSectionAudio(this)));
        final TextView soundTag = new TextView(this);
        final TextView[] soundSubHolder = new TextView[1];
        LinearLayout soundRow = createDialogRow(
                AppLanguage.strAzanAudioCurrent(this),
                Azan.soundLabel(Azan.getSoundChoice(this)),
                soundTag,
                soundSubHolder,
                () -> {
                    SoundEffects.playSelect();
                    showAzanSoundChoiceSubDialog(() -> {
                        if (soundSubHolder[0] != null) {
                            soundSubHolder[0].setText(Azan.soundLabel(Azan.getSoundChoice(MainActivity.this)));
                        }
                    });
                }
        );
        soundRow.setId(View.generateViewId());
        masterRow.setNextFocusRightId(soundRow.getId());
        soundRow.setNextFocusLeftId(masterRow.getId());
        for (LinearLayout pr : prayerRowsList) {
            pr.setNextFocusRightId(soundRow.getId());
        }
        soundTag.setText(AppLanguage.strAzanAudioChangeBtn(this));
        soundTag.setTextSize(12.5f);
        soundTag.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        soundTag.setFontVariationSettings("'wght' 700");
        soundTag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, 0xFF38BDF8}));
        rightCol.addView(soundRow);

        // Bahagian D: Ujian Azan
        rightCol.addView(createDialogSectionHeader(AppLanguage.strAzanSectionTest(this)));
        final TextView testTag = new TextView(this);
        LinearLayout testRow = createDialogRow(
                AppLanguage.strAzanTestTitle(this),
                AppLanguage.strAzanTestDesc(this),
                testTag,
                null,
                () -> {
                    SoundEffects.playSelect();
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                    Intent azanIntent = new Intent(MainActivity.this, AzanActivity.class);
                    azanIntent.putExtra("prayer", 4);
                    azanIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(azanIntent);
                }
        );
        testTag.setText(AppLanguage.strAzanTestBtn(this));
        testTag.setTextSize(13);
        testTag.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        testTag.setFontVariationSettings("'wght' 700");
        testTag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, 0xFF38BDF8}));
        rightCol.addView(testRow);

        // Info Card
        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding((int) (12 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(0x12FFFFFF);
        infoBg.setCornerRadius(9 * density);
        infoBg.setStroke(Math.max(1, (int) (1 * density)), 0x22FFFFFF);
        infoCard.setBackground(infoBg);

        TextView infoText = new TextView(this);
        infoText.setText(AppLanguage.strAzanInfoText(this));
        infoText.setTextSize(11f);
        infoText.setTextColor(0xB3FFFFFF);
        infoText.setTypeface(getResources().getFont(R.font.inter), Typeface.NORMAL);
        infoText.setFontVariationSettings("'wght' 500");
        infoText.setLineSpacing(0, 1.25f);
        infoCard.addView(infoText);

        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = (int) (4 * density);
        infoLp.bottomMargin = (int) (5 * density);
        infoCard.setLayoutParams(infoLp);
        rightCol.addView(infoCard);

        // Butang Tutup / Selesai
        final TextView closeTag = new TextView(this);
        LinearLayout closeRow = createDialogRow(
                AppLanguage.strAzanCloseBtn(this),
                null,
                closeTag,
                null,
                () -> {
                    SoundEffects.playSelect();
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                }
        );
        closeTag.setText(AppLanguage.strClose(this).toUpperCase(Locale.ROOT));
        closeTag.setTextSize(12.5f);
        closeTag.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        closeTag.setFontVariationSettings("'wght' 700");
        closeTag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, 0x88FFFFFF}));
        rightCol.addView(closeRow);

        twoCols.addView(rightCol);
        scroll.addView(twoCols);
        rootContainer.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setView(rootContainer);
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        showGlass(dialog);
        styleGlassDialog(dialog);

        // Set true 16:9 popup layout sizing with generous top & bottom margins
        Window window = dialog.getWindow();
        if (window != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int screenH = dm.heightPixels;
            int screenW = dm.widthPixels;

            // Target height: ~70% of screen height in 16:9 (e.g. 756px on 1080p, leaving 162px top & bottom margin)
            int targetH = Math.round(screenH * 0.70f / 9f) * 9;
            int targetW = targetH * 16 / 9;
            if (targetW > (int) (screenW * 0.85f)) {
                targetW = Math.round((screenW * 0.85f) / 16f) * 16;
                targetH = targetW * 9 / 16;
            }
            window.setLayout(targetW, targetH);
            window.setGravity(Gravity.CENTER);
        }
    }

    void showAzanSoundChoiceSubDialog(Runnable onUpdated) {
        final String[] soundKeys = new String[]{
                Azan.SOUND_MAKKAH,
                Azan.SOUND_MADINAH,
                Azan.SOUND_KLCC,
                Azan.SOUND_SINGAPORE,
                Azan.SOUND_INDONESIA,
                Azan.SOUND_CHIME,
                Azan.SOUND_SILENT
        };

        final String[] soundOptions = AppLanguage.strAzanSoundOptions(this);

        String currentSound = Azan.getSoundChoice(this);
        int selectedIndex = 0;
        for (int i = 0; i < soundKeys.length; i++) {
            if (soundKeys[i].equalsIgnoreCase(currentSound)) {
                selectedIndex = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle(AppLanguage.strAzanChooseAudioTitle(this));
        builder.setSingleChoiceItems(soundOptions, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            String chosen = soundKeys[which];
            Azan.setSoundChoice(MainActivity.this, chosen);
            SoundEffects.playSelect();
            Azan.fetch(MainActivity.this, chosen);
            Toast.makeText(MainActivity.this, "Audio Dipilih: " + Azan.soundLabel(chosen), Toast.LENGTH_SHORT).show();
            if (onUpdated != null) onUpdated.run();
        });
        builder.setNegativeButton(AppLanguage.strBack(this), (dialog, which) -> dialog.dismiss());
        AlertDialog d = builder.create();
        showGlass(d);
        styleGlassDialog(d);

        if (d.getWindow() != null) {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            int w = Math.min((int) (540 * density), (int) (dm.widthPixels * 0.75f));
            int h = Math.min((int) (400 * density), (int) (dm.heightPixels * 0.72f));
            d.getWindow().setLayout(w, h);
            d.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private void updateMasterTag(TextView tag, boolean on) {
        tag.setText(on ? "ON" : "OFF");
        tag.setTextSize(13);
        tag.setTypeface(getResources().getFont(R.font.inter), Typeface.NORMAL);
        tag.setFontVariationSettings(on ? "'wght' 700" : "'wght' 600");
        int color = on ? 0xFF38BDF8 : 0x99FFFFFF;
        tag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, color}));
    }

    private void updatePrayerTag(TextView tag, boolean on) {
        tag.setText(on ? "ON" : "OFF");
        tag.setTextSize(13);
        tag.setTypeface(getResources().getFont(R.font.inter), Typeface.NORMAL);
        tag.setFontVariationSettings(on ? "'wght' 700" : "'wght' 600");
        int color = on ? 0xFF38BDF8 : 0x99FFFFFF;
        tag.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, color}));
    }

    private TextView createDialogSectionHeader(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(11.5f);
        tv.setTextColor(0xFF38BDF8);
        tv.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        tv.setFontVariationSettings("'wght' 700");
        tv.setLetterSpacing(0.08f);
        tv.setPadding(0, (int) (6 * density), 0, (int) (3 * density));
        return tv;
    }

    private LinearLayout createDialogRow(String title, String subtitle, TextView tagView, TextView[] subHolderOut, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        boolean hasSub = (subtitle != null && !subtitle.isEmpty());
        int vPad = hasSub ? (int) (8 * density) : (int) (5 * density);
        row.setPadding((int) (14 * density), vPad, (int) (14 * density), vPad);

        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFFFFFFFF);
        on.setCornerRadius(9 * density);
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x1AFFFFFF);
        off.setCornerRadius(9 * density);
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        row.setBackground(bg);

        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setDuplicateParentStateEnabled(true);

        TextView rowTitle = new TextView(this);
        rowTitle.setText(title);
        rowTitle.setTextSize(15);
        rowTitle.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, 0xFFFFFFFF}));
        rowTitle.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        rowTitle.setFontVariationSettings("'wght' 700");
        rowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        rowTitle.setDuplicateParentStateEnabled(true);
        topRow.addView(rowTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (tagView != null) {
            tagView.setDuplicateParentStateEnabled(true);
            topRow.addView(tagView);
        }
        row.addView(topRow);

        final TextView finalRowSub;
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView rowSub = new TextView(this);
            rowSub.setText(subtitle);
            rowSub.setTextSize(12.5f);
            rowSub.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF1E293B, 0xD9FFFFFF}));
            rowSub.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
            rowSub.setFontVariationSettings("'wght' 600");
            rowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            rowSub.setPadding(0, (int) (2 * density), 0, 0);
            rowSub.setDuplicateParentStateEnabled(true);
            row.addView(rowSub);
            finalRowSub = rowSub;
            if (subHolderOut != null) {
                subHolderOut[0] = rowSub;
            }
        } else {
            finalRowSub = null;
        }

        row.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                rowTitle.setShadowLayer(0, 0, 0, 0);
                if (finalRowSub != null) finalRowSub.setShadowLayer(0, 0, 0, 0);
            } else {
                rowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
                if (finalRowSub != null) finalRowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            }
        });

        row.setOnClickListener(v -> {
            if (onClick != null) onClick.run();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = hasSub ? (int) (5 * density) : (int) (3 * density);
        row.setLayoutParams(lp);

        return row;
    }

    /** Power button, far right: restart, power off or sleep. */
    void showPowerMenu() {
        SoundEffects.playSelect();
        final String[] items = {"Restart", "Power off", "Sleep"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("Power");
        builder.setItems(items, (d, which) -> {
            d.dismiss();
            if (which == 0) showRestartDialog();
            else if (which == 1) showTurnOffDialog();
            else HomeKeyService.sleepScreen();
        });
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
        if (dialog.getListView() != null) dialog.getListView().requestFocus();
    }

    void showRestartDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle(AppLanguage.strRestartTitle(this));
        builder.setMessage(AppLanguage.strRestartMsg(this));
        builder.setPositiveButton(AppLanguage.strRestartConfirm(this), (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(MainActivity.this, AppLanguage.strRestartToast(MainActivity.this), Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> HomeKeyService.restartTv(MainActivity.this), 300);
        });
        builder.setNegativeButton(AppLanguage.strCancel(this), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    void showTurnOffDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle(AppLanguage.strTurnOffTitle(this));
        builder.setMessage(AppLanguage.strTurnOffMsg(this));
        builder.setPositiveButton(AppLanguage.strTurnOffConfirm(this), (dialog, which) -> {
            dialog.dismiss();
            HomeKeyService.turnOffTv(MainActivity.this);
        });
        builder.setNegativeButton(AppLanguage.strCancel(this), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    void showCleanTvCacheDialog() {
        SoundEffects.playSelect();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle(AppLanguage.strCleanCacheTitle(this));

        long freeBytes = CacheCleaner.getFreeStorageBytes();
        long totalBytes = CacheCleaner.getTotalStorageBytes();
        int usedPct = CacheCleaner.usedPercent();
        String freeStr = android.text.format.Formatter.formatFileSize(this, freeBytes);
        String totalStr = android.text.format.Formatter.formatFileSize(this, totalBytes);

        String msg = AppLanguage.strCleanCacheMsg(this, freeStr, totalStr, 100 - usedPct, usedPct);
        builder.setMessage(msg);

        builder.setPositiveButton(AppLanguage.strCleanCacheStart(this), (dialog, which) -> {
            dialog.dismiss();
            Toast.makeText(MainActivity.this, AppLanguage.strCleanCacheToast(MainActivity.this), Toast.LENGTH_SHORT).show();

            CacheCleaner.cleanNow(MainActivity.this, (bytesFreed, percentBefore, percentAfter, systemTrimmed) -> {
                SoundEffects.playSelect();
                String freedStr = android.text.format.Formatter.formatFileSize(MainActivity.this, bytesFreed);
                String resultMsg = AppLanguage.strCleanCacheDoneMsg(MainActivity.this, freedStr, bytesFreed > 0);

                AlertDialog resDialog = new AlertDialog.Builder(MainActivity.this, R.style.Theme_TvHome_GlassDialog)
                        .setTitle(AppLanguage.strCleanCacheDoneTitle(MainActivity.this))
                        .setMessage(resultMsg + AppLanguage.strCleanCacheUsageLine(MainActivity.this, percentBefore, percentAfter))
                        .setPositiveButton(AppLanguage.strDone(MainActivity.this), (d, w) -> d.dismiss())
                        .create();
                showGlass(resDialog);
                styleGlassDialog(resDialog);
                Button btnOk = resDialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (btnOk != null) btnOk.requestFocus();
            });
        });
        builder.setNegativeButton(AppLanguage.strCancel(this), (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
        Button btnPos = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (btnPos != null) btnPos.requestFocus();
    }

    String getAppVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "4.0.0";
        }
    }

    void showAutoUpdateDialog() {
        SoundEffects.playSelect();
        View view = getLayoutInflater().inflate(R.layout.dialog_ota_coffee, null);

        TextView tvVersionSubtitle = view.findViewById(R.id.tvVersionSubtitle);
        TextView tvAutoStatus = view.findViewById(R.id.tvAutoStatus);
        TextView btnCheckNow = view.findViewById(R.id.btnCheckNow);
        TextView btnChangeTime = view.findViewById(R.id.btnChangeTime);
        TextView btnToggleAuto = view.findViewById(R.id.btnToggleAuto);
        TextView btnClose = view.findViewById(R.id.btnClose);
        ImageView imgQrCoffee = view.findViewById(R.id.imgQrCoffee);

        if (tvVersionSubtitle != null) {
            tvVersionSubtitle.setText("Glass TV Home v" + getAppVersionName() + " • Enjin Auto-Update (OTA)");
        }

        Runnable updateStatusUi = () -> {
            boolean isAuto = AppUpdateManager.isAutoUpdateEnabled(this);
            String sched = AppUpdateManager.getAutoUpdateTime(this);
            if (tvAutoStatus != null) {
                tvAutoStatus.setText(isAuto ? "Auto-Update: On (" + sched + ")" : " Auto-Update: Dimatikan");
            }
            if (btnToggleAuto != null) {
                btnToggleAuto.setText(isAuto ? "Turn Off Auto-Update" : "Turn On Auto-Update");
            }
        };
        updateStatusUi.run();

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        View.OnFocusChangeListener focusListener = (v, hasFocus) -> {
            if (v instanceof TextView) {
                ((TextView) v).setTextColor(hasFocus ? 0xFF000000 : 0xFFFFFFFF);
            }
            if (hasFocus) SoundEffects.playNav();
        };
        if (btnCheckNow != null) {
            btnCheckNow.setTextColor(0xFF000000);
            btnCheckNow.setOnFocusChangeListener(focusListener);
        }
        if (btnChangeTime != null) {
            btnChangeTime.setTextColor(0xFFFFFFFF);
            btnChangeTime.setOnFocusChangeListener(focusListener);
        }
        if (btnToggleAuto != null) {
            btnToggleAuto.setTextColor(0xFFFFFFFF);
            btnToggleAuto.setOnFocusChangeListener(focusListener);
        }
        if (btnClose != null) {
            btnClose.setTextColor(0xFFFFFFFF);
            btnClose.setOnFocusChangeListener(focusListener);
        }

        if (btnCheckNow != null) {
            btnCheckNow.setOnClickListener(v -> {
                SoundEffects.playSelect();
                dialog.dismiss();
                AppUpdateManager.checkForUpdates(MainActivity.this, true);
            });
        }

        if (btnChangeTime != null) {
            btnChangeTime.setOnClickListener(v -> {
                SoundEffects.playSelect();
                dialog.dismiss();
                showUpdateTimePickerDialog();
            });
        }

        if (btnToggleAuto != null) {
            btnToggleAuto.setOnClickListener(v -> {
                SoundEffects.playSelect();
                boolean isAuto = AppUpdateManager.isAutoUpdateEnabled(this);
                boolean newMode = !isAuto;
                AppUpdateManager.setAutoUpdateEnabled(this, newMode);
                updateStatusUi.run();
                Toast.makeText(this, newMode ? "Auto-Update Dihidupkan (" + AppUpdateManager.getAutoUpdateTime(this) + ")" : " Auto-Update Dimatikan", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                SoundEffects.playSelect();
                dialog.dismiss();
            });
        }

        if (imgQrCoffee != null) {
            imgQrCoffee.setOnClickListener(v -> {
                SoundEffects.playSelect();
                Toast.makeText(this, "Terima kasih atas sokongan belanja kopi! (@imsomangge)", Toast.LENGTH_SHORT).show();
            });
        }

        showGlass(dialog);
        styleGlassDialog(dialog);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            int width = Math.min((int) (680 * density), (int) (getResources().getDisplayMetrics().widthPixels * 0.85f));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }

        if (btnCheckNow != null) btnCheckNow.requestFocus();
    }

    private void showUpdateTimePickerDialog() {
        final String[] times = new String[]{
                "03:30 AM (Best: while you sleep)",
                "04:00 AM (Awal Pagi)",
                "05:00 AM (Subuh)",
                "12:00 PM (Tengah Hari)",
                "02:00 AM (Tengah Malam)"
        };
        final String[] rawTimes = new String[]{
                "03:30 AM", "04:00 AM", "05:00 AM", "12:00 PM", "02:00 AM"
        };
        String current = AppUpdateManager.getAutoUpdateTime(this);
        int selectedIndex = 0;
        for (int i = 0; i < rawTimes.length; i++) {
            if (rawTimes[i].equalsIgnoreCase(current)) {
                selectedIndex = i;
                break;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle(" Choose Daily Auto-Update Time");
        builder.setSingleChoiceItems(times, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            String chosen = rawTimes[which];
            AppUpdateManager.setAutoUpdateTime(MainActivity.this, chosen);
            AppUpdateManager.setAutoUpdateEnabled(MainActivity.this, true);
            Toast.makeText(MainActivity.this, "Auto-update time set to: " + chosen, Toast.LENGTH_SHORT).show();
            showAutoUpdateDialog();
        });
        builder.setNegativeButton("Back", (dialog, which) -> {
            dialog.dismiss();
            showAutoUpdateDialog();
        });
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    private void updateNowPlayingUi(NowPlayingManager.MediaInfo info) {
        if (nowPlayingCard == null || cardTitle == null || cardSubtitle == null) return;

        boolean hasActiveMusic = info != null && info.hasMusic();

        if (!hasActiveMusic) {
            hideNowPlayingCard();
            nowPlayingDisabledByUser = false;
            lastPlayedTrackKey = "";
            return;
        }

        String currentTrackKey = info.packageName + ":" + info.title;
        if (!currentTrackKey.equals(lastPlayedTrackKey)) {
            lastPlayedTrackKey = currentTrackKey;
            nowPlayingDisabledByUser = false; // New song started playing -> show
        }

        if (nowPlayingDisabledByUser) {
            hideNowPlayingCard();
            return;
        }

        if (ayahBlock != null) {
            ayahBlock.setVisibility(View.GONE);
        }

        // Active playback from Spotify or YouTube Music
        cardTitle.setText(info.title.isEmpty() ? "Lagu Dimainkan" : info.title);
        cardTitle.setSelected(true);
        cardSubtitle.setText("Now playing • " + (info.appName.isEmpty() ? "SPOTIFY" : info.appName.toUpperCase(Locale.ROOT)));

        if (info.albumArt != null && cardAlbumArt != null) {
            cardAlbumArt.setImageBitmap(info.albumArt);
            cardAlbumArt.setPadding(0, 0, 0, 0);
        } else if (cardAlbumArt != null) {
            try {
                Drawable appIcon = getPackageManager().getApplicationIcon(info.packageName);
                cardAlbumArt.setImageDrawable(appIcon);
                cardAlbumArt.setPadding(6, 6, 6, 6);
            } catch (Exception e) {
                cardAlbumArt.setImageResource(R.drawable.ic_music);
                cardAlbumArt.setPadding(8, 8, 8, 8);
            }
        }

        if (btnInlinePlayPause != null) {
            btnInlinePlayPause.setImageResource(info.isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
        }

        if (nowPlayingCard.getVisibility() != View.VISIBLE) {
            nowPlayingCard.setVisibility(View.VISIBLE);
            nowPlayingCard.setEnabled(true);
            nowPlayingCard.setAlpha(0f);
            nowPlayingCard.setScaleX(0.9f);
            nowPlayingCard.setScaleY(0.9f);
            nowPlayingCard.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .start();
            if (weatherAirQualityView != null) {
                weatherAirQualityView.setNextFocusDownId(R.id.cardTrackInfo);
            }
        }
    }

    private void disableNowPlaying() {
        nowPlayingDisabledByUser = true;
        NowPlayingManager.pausePlayback();
        hideNowPlayingCard();
    }

    private void hideNowPlayingCard() {
        if (nowPlayingCard == null) return;
        if (nowPlayingCard.getVisibility() != View.GONE) {
            nowPlayingCard.animate()
                    .alpha(0f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(180)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .withEndAction(() -> {
                        nowPlayingCard.setVisibility(View.GONE);
                        nowPlayingCard.setEnabled(false);
                        if (weatherAirQualityView != null) {
                            weatherAirQualityView.setNextFocusDownId(View.NO_ID);
                        }
                        updateIslamicShelf();
                    })
                    .start();
        } else {
            nowPlayingCard.setVisibility(View.GONE);
            nowPlayingCard.setEnabled(false);
            if (weatherAirQualityView != null) {
                weatherAirQualityView.setNextFocusDownId(View.NO_ID);
            }
            updateIslamicShelf();
        }
    }

    private void showNowPlayingDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View panel = getLayoutInflater().inflate(R.layout.dialog_media_hub, root, false);
        dialog.setContentView(panel);

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (480 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.65f);
            w.setFlags(android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        ImageView btnPopupPrev = panel.findViewById(R.id.btnPopupPrev);
        ImageView btnPlayPause = panel.findViewById(R.id.btnPlayPause);
        ImageView btnPopupNext = panel.findViewById(R.id.btnPopupNext);
        TextView btnOpenApp = panel.findViewById(R.id.btnOpenApp);
        TextView btnCloseDialog = panel.findViewById(R.id.btnCloseDialog);

        updatePopupMediaInfo(panel, dialog);

        if (btnPopupPrev != null) {
            btnPopupPrev.setOnClickListener(v -> {
                SoundEffects.playSelect();
                NowPlayingManager.skipToPrevious();
                handler.postDelayed(() -> {
                    NowPlayingManager.updateCurrentMedia(MainActivity.this);
                    updatePopupMediaInfo(panel, dialog);
                }, 300);
            });
        }

        if (btnPlayPause != null) {
            btnPlayPause.setOnClickListener(v -> {
                SoundEffects.playSelect();
                NowPlayingManager.togglePlayPause();
                handler.postDelayed(() -> {
                    NowPlayingManager.updateCurrentMedia(MainActivity.this);
                    updatePopupMediaInfo(panel, dialog);
                }, 300);
            });
        }

        if (btnPopupNext != null) {
            btnPopupNext.setOnClickListener(v -> {
                SoundEffects.playSelect();
                NowPlayingManager.skipToNext();
                handler.postDelayed(() -> {
                    NowPlayingManager.updateCurrentMedia(MainActivity.this);
                    updatePopupMediaInfo(panel, dialog);
                }, 300);
            });
        }

        if (btnOpenApp != null) {
            btnOpenApp.setOnClickListener(v -> {
                SoundEffects.playSelect();
                dialog.dismiss();
                NowPlayingManager.openMediaApp(MainActivity.this);
            });
        }

        if (btnCloseDialog != null) {
            btnCloseDialog.setOnClickListener(v -> {
                SoundEffects.playSelect();
                dialog.dismiss();
                disableNowPlaying();
            });
        }

        View.OnFocusChangeListener btnFocus = (v, hasFocus) -> {
            if (hasFocus) SoundEffects.playNav();
            float scale = hasFocus ? 1.08f : 1.0f;
            v.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(120)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withLayer()
                    .start();
        };

        if (btnPopupPrev != null) btnPopupPrev.setOnFocusChangeListener(btnFocus);
        if (btnPlayPause != null) btnPlayPause.setOnFocusChangeListener(btnFocus);
        if (btnPopupNext != null) btnPopupNext.setOnFocusChangeListener(btnFocus);
        if (btnOpenApp != null) btnOpenApp.setOnFocusChangeListener(btnFocus);
        if (btnCloseDialog != null) btnCloseDialog.setOnFocusChangeListener(btnFocus);

        showGlass(dialog);
        if (btnPlayPause != null) {
            btnPlayPause.requestFocus();
        }
    }

    private void updatePopupMediaInfo(View panel, Dialog dialog) {
        if (panel == null || (dialog != null && !dialog.isShowing())) return;
        NowPlayingManager.MediaInfo info = NowPlayingManager.getCurrentInfo();
        boolean hasMedia = info != null && info.hasMusic();

        ImageView headerIcon = panel.findViewById(R.id.headerIcon);
        TextView badgeSource = panel.findViewById(R.id.badgeSource);
        ImageView imgAlbumArt = panel.findViewById(R.id.imgAlbumArt);
        TextView txtTrackTitle = panel.findViewById(R.id.txtTrackTitle);
        TextView txtTrackArtist = panel.findViewById(R.id.txtTrackArtist);
        TextView txtPlaybackStatus = panel.findViewById(R.id.txtPlaybackStatus);
        ImageView btnPlayPause = panel.findViewById(R.id.btnPlayPause);
        TextView btnOpenApp = panel.findViewById(R.id.btnOpenApp);

        if (hasMedia) {
            if (txtTrackTitle != null) {
                txtTrackTitle.setText(info.title.isEmpty() ? "Lagu Dimainkan" : info.title);
                txtTrackTitle.setSelected(true);
            }
            if (txtTrackArtist != null) {
                txtTrackArtist.setText(info.artist.isEmpty() ? (info.appName.isEmpty() ? "Muzik" : info.appName) : info.artist);
            }
            if (txtPlaybackStatus != null) {
                txtPlaybackStatus.setText(info.isPlaying ? " Now Playing" : "Pause");
                txtPlaybackStatus.setTextColor(info.isPlaying ? Color.parseColor("#1DB954") : Color.parseColor("#FFC107"));
            }

            if (imgAlbumArt != null) {
                if (info.albumArt != null) {
                    imgAlbumArt.setImageBitmap(info.albumArt);
                    imgAlbumArt.setPadding(0, 0, 0, 0);
                } else if (!info.packageName.isEmpty()) {
                    try {
                        Drawable appIcon = getPackageManager().getApplicationIcon(info.packageName);
                        imgAlbumArt.setImageDrawable(appIcon);
                        imgAlbumArt.setPadding(16, 16, 16, 16);
                    } catch (Exception e) {
                        imgAlbumArt.setImageResource(R.drawable.ic_music);
                        imgAlbumArt.setPadding(18, 18, 18, 18);
                    }
                }
            }

            if (badgeSource != null) {
                badgeSource.setText(info.appName.isEmpty() ? "SPOTIFY" : info.appName.toUpperCase(Locale.ROOT));
                if (info.packageName.contains("spotify")) {
                    badgeSource.setTextColor(Color.parseColor("#1DB954"));
                    if (headerIcon != null) headerIcon.setColorFilter(Color.parseColor("#1DB954"));
                } else {
                    badgeSource.setTextColor(Color.parseColor("#FF0033"));
                    if (headerIcon != null) headerIcon.setColorFilter(Color.parseColor("#FF0033"));
                }
            }

            if (btnPlayPause != null) {
                btnPlayPause.setImageResource(info.isPlaying ? R.drawable.ic_pause : R.drawable.ic_play);
            }

            if (btnOpenApp != null) {
                btnOpenApp.setText("Open " + (info.appName.isEmpty() ? "App" : info.appName));
            }
        } else {
            if (txtTrackTitle != null) txtTrackTitle.setText("Nothing Playing");
            if (txtTrackArtist != null) txtTrackArtist.setText("Open Spotify or YouTube Music to start listening");
            if (txtPlaybackStatus != null) {
                txtPlaybackStatus.setText("Not playing");
                txtPlaybackStatus.setTextColor(Color.parseColor("#80FFFFFF"));
            }
            if (btnPlayPause != null) {
                btnPlayPause.setImageResource(R.drawable.ic_play);
            }
        }
    }

    private Dialog qrUploadDialog;
    private WallpaperUploadServer uploadServer;
    private ImageView qrImageView;
    private TextView qrStatusText;
    private TextView qrUrlText;

    void showWallpaperDialog() {
        File customDir = new File(getFilesDir(), "custom_wallpapers");
        File[] customFiles = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
        int customCount = (customFiles != null) ? customFiles.length : 0;

        String currentKey = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Malaysia");

        class WallpaperOption {
            final String key;
            final String title;
            final String subtitle;
            WallpaperOption(String key, String title, String subtitle) {
                this.key = key;
                this.title = title;
                this.subtitle = subtitle;
            }
        }

        String curLang = AppLanguage.getLanguage(this);
        List<WallpaperOption> options = new ArrayList<>();

        // Category 1: Malaysia
        String tMal = AppLanguage.LANG_EN.equals(curLang) ? "Attractions in Malaysia" : (AppLanguage.LANG_ID.equals(curLang) ? "Tempat Wisata Malaysia" : "Tempat Menarik di Malaysia");
        String sMal = AppLanguage.LANG_EN.equals(curLang) ? "16 Iconic 4K Collection (Pexels Ultra HD)" : "16 Koleksi 4K Ikonik (Pexels Ultra HD)";
        options.add(new WallpaperOption("Malaysia", tMal, sMal));

        // Category 2: Natural
        String tNat = AppLanguage.LANG_EN.equals(curLang) ? "Nature & Landscapes" : (AppLanguage.LANG_ID.equals(curLang) ? "Alam Semesta & Lanskap" : "Alam Semula Jadi & Landskap");
        String sNat = AppLanguage.LANG_EN.equals(curLang) ? "Mountains, Forests & Aurora 4K" : (AppLanguage.LANG_ID.equals(curLang) ? "Pegunungan, Hutan & Aurora 4K" : "Pergunungan, Rimba & Aurora 4K");
        options.add(new WallpaperOption("Natural", tNat, sNat));

        // Category 3: Skyscraper
        String tSky = AppLanguage.LANG_EN.equals(curLang) ? "Skyscrapers & Cities" : (AppLanguage.LANG_ID.equals(curLang) ? "Pencakar Langit & Kota" : "Pencakar Langit & Bandar Raya");
        String sSky = AppLanguage.LANG_EN.equals(curLang) ? "Modern Metropolis & Architecture" : (AppLanguage.LANG_ID.equals(curLang) ? "Metropolis Modern & Arsitektur" : "Metropolis Moden & Seni Bina");
        options.add(new WallpaperOption("Skyscraper", tSky, sSky));

        // Category 4: Space
        String tSpc = AppLanguage.LANG_EN.equals(curLang) ? "Space & Cosmos" : (AppLanguage.LANG_ID.equals(curLang) ? "Luar Angkasa & Kosmos" : "Angkasa & Kosmos");
        String sSpc = AppLanguage.LANG_EN.equals(curLang) ? "Galaxies, Nebulae & Stars (Deep Space)" : (AppLanguage.LANG_ID.equals(curLang) ? "Galaksi, Nebula & Bintang (Deep Space)" : "Galaksi, Nebula & Bintang (Deep Space)");
        options.add(new WallpaperOption("Space", tSpc, sSpc));

        // Category 5: Ocean
        String tOcn = AppLanguage.LANG_EN.equals(curLang) ? "Oceans & Coastlines" : (AppLanguage.LANG_ID.equals(curLang) ? "Lautan & Pesisir Pantai" : "Lautan & Pesisir Pantai");
        String sOcn = AppLanguage.LANG_EN.equals(curLang) ? "Crystal Clear Waters, Reefs & Beaches" : (AppLanguage.LANG_ID.equals(curLang) ? "Air Jernih, Terumbu Karang & Pantai" : "Air Jernih, Terumbu Karang & Pantai");
        options.add(new WallpaperOption("Ocean", tOcn, sOcn));

        // Category 6: Minimalist
        String tMin = AppLanguage.LANG_EN.equals(curLang) ? "Dark Minimalist (OLED)" : (AppLanguage.LANG_ID.equals(curLang) ? "Minimalis Gelap (OLED)" : "Minimalis Gelap (OLED)");
        String sMin = AppLanguage.LANG_EN.equals(curLang) ? "16 Unsplash Dark Wallpapers • Perfect for OLED" : (AppLanguage.LANG_ID.equals(curLang) ? "16 Koleksi Unsplash Dark Wallpaper • Khusus Layar OLED" : "16 Unsplash Dark Wallpapers • OLED Special");
        options.add(new WallpaperOption("Minimalist", tMin, sMin));

        // Category 7: Manage Custom
        String tCst = AppLanguage.LANG_EN.equals(curLang) ? "Manage Uploaded Photos" : (AppLanguage.LANG_ID.equals(curLang) ? "Kelola Foto yang Diunggah" : "Manage Uploaded Pictures");
        String sCst = customCount > 0
                ? (AppLanguage.LANG_EN.equals(curLang) ? (customCount + " uploaded photos • QR / Slideshow") : (AppLanguage.LANG_ID.equals(curLang) ? (customCount + " foto diunggah • QR / Slideshow") : (customCount + " foto dimuat naik • QR / Slideshow")))
                : (AppLanguage.LANG_EN.equals(curLang) ? "Upload personal photos via phone" : (AppLanguage.LANG_ID.equals(curLang) ? "Unggah foto pribadi lewat ponsel" : "Upload personal photos from your phone"));
        options.add(new WallpaperOption("MANAGE_CUSTOM", tCst, sCst));

        int curInterval = getWallpaperIntervalMinutes();
        String intervalText = AppLanguage.strWallpaperIntervalText(this, curInterval);

        boolean isShuffle = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("shuffle_mode", true);
        boolean isDescOn = isWallpaperDescEnabled();
        boolean isWeatherFxOn = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("dynamic_weather_overlay", true);

        String tInt = (AppLanguage.LANG_EN.equals(curLang) ? " Shuffle Interval: " : (AppLanguage.LANG_ID.equals(curLang) ? " Interval Shuffle: " : " Selang Masa Shuffle: ")) + intervalText;
        String sInt = AppLanguage.LANG_EN.equals(curLang) ? "Choose wallpaper change frequency (5 min, 10 min, etc.)" : (AppLanguage.LANG_ID.equals(curLang) ? "Pilih frekuensi pergantian wallpaper (5 mnt, 10 mnt, dll)" : "Choose how often the wallpaper changes (5 min, 10 min...)");
        options.add(new WallpaperOption("INTERVAL", tInt, sInt));

        String tShf = AppLanguage.LANG_EN.equals(curLang) ? ("Play Order: " + (isShuffle ? "Random (Shuffle ON)" : "Sequential")) : (AppLanguage.LANG_ID.equals(curLang) ? ("Urutan Tampil: " + (isShuffle ? "Acak (Shuffle ON)" : "Berurutan (Sequential)")) : ("Mod Susunan: " + (isShuffle ? "Rawak (Shuffle ON)" : "Berurutan (Sequential)")));
        String sShf = isShuffle
                ? (AppLanguage.LANG_EN.equals(curLang) ? "Photos picked randomly without repeats" : (AppLanguage.LANG_ID.equals(curLang) ? "Gambar dipilih acak tanpa berulang" : "Gambar dipilih secara rawak tanpa mengulang"))
                : (AppLanguage.LANG_EN.equals(curLang) ? "Photos cycle through catalog order" : (AppLanguage.LANG_ID.equals(curLang) ? "Gambar bertukar sesuai urutan katalog" : "Gambar bertukar mengikut urutan katalog"));
        options.add(new WallpaperOption("TOGGLE_SHUFFLE", tShf, sShf));

        String tDsc = (AppLanguage.LANG_EN.equals(curLang) ? "Image Title & Info: " : (AppLanguage.LANG_ID.equals(curLang) ? "Info & Deskripsi Gambar: " : "Info & Deskripsi Gambar: ")) + (isDescOn ? (AppLanguage.LANG_EN.equals(curLang) ? "ON" : (AppLanguage.LANG_ID.equals(curLang) ? "Aktif (ON)" : "Dihidupkan (ON)")) : (AppLanguage.LANG_EN.equals(curLang) ? "OFF" : (AppLanguage.LANG_ID.equals(curLang) ? "Mati (OFF)" : "Dimatikan (OFF)")));
        String sDsc = isDescOn
                ? (AppLanguage.LANG_EN.equals(curLang) ? "Display title and description on home screen" : (AppLanguage.LANG_ID.equals(curLang) ? "Tampilkan judul dan deskripsi gambar di layar utama" : "Paparkan tajuk dan penerangan gambar di skrin utama"))
                : (AppLanguage.LANG_EN.equals(curLang) ? "Hide text for a clean screen display" : (AppLanguage.LANG_ID.equals(curLang) ? "Sembunyikan teks untuk tampilan layar bersih" : "Sembunyikan teks untuk paparan skrin bersih (Clean Look)"));
        options.add(new WallpaperOption("TOGGLE_DESC", tDsc, sDsc));

        String tWtr = (AppLanguage.LANG_EN.equals(curLang) ? "Dynamic Weather: " : (AppLanguage.LANG_ID.equals(curLang) ? "Cuaca Dinamis: " : "Dynamic Weather: ")) + (isWeatherFxOn ? (AppLanguage.LANG_EN.equals(curLang) ? "ON" : (AppLanguage.LANG_ID.equals(curLang) ? "Aktif (ON)" : "Dihidupkan (ON)")) : (AppLanguage.LANG_EN.equals(curLang) ? "OFF" : (AppLanguage.LANG_ID.equals(curLang) ? "Mati (OFF)" : "Dimatikan (OFF)")));
        String sWtr = isWeatherFxOn
                ? (AppLanguage.LANG_EN.equals(curLang) ? "Rain & lightning animations on wallpaper when raining" : (AppLanguage.LANG_ID.equals(curLang) ? "Animasi rintik hujan & kilat di atas wallpaper saat hujan" : "Animasi rintik hujan & kilat di atas wallpaper bila cuaca hujan"))
                : (AppLanguage.LANG_EN.equals(curLang) ? "Static wallpaper without weather animations" : (AppLanguage.LANG_ID.equals(curLang) ? "Hanya wallpaper statis tanpa animasi cuaca" : "Hanya wallpaper pegun tanpa animasi cuaca"));
        options.add(new WallpaperOption("TOGGLE_WEATHER_OVERLAY", tWtr, sWtr));

        String tNxt = AppLanguage.LANG_EN.equals(curLang) ? " Change Wallpaper Now" : (AppLanguage.LANG_ID.equals(curLang) ? " Ganti Gambar Sekarang" : " Tukar Gambar Sekarang");
        String sNxt = AppLanguage.LANG_EN.equals(curLang) ? "Cycle and display next wallpaper immediately" : (AppLanguage.LANG_ID.equals(curLang) ? "Putar dan tampilkan gambar berikutnya sekarang juga" : "Putar dan paparkan gambar seterusnya serta-merta");
        options.add(new WallpaperOption("NEXT", tNxt, sNxt));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (20 * density), (int) (16 * density), (int) (20 * density), (int) (12 * density));

        // Header Title
        TextView titleView = new TextView(this);
        titleView.setText(AppLanguage.strWallpaperDialogTitle(this));
        titleView.setTextSize(19);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        titleView.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        layout.addView(titleView);

        // Header Subtitle
        TextView subView = new TextView(this);
        subView.setText(AppLanguage.strWallpaperDialogSub(this));
        subView.setTextSize(13.5f);
        subView.setTextColor(0xE6FFFFFF);
        subView.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        subView.setFontVariationSettings("'wght' 600");
        subView.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        subView.setPadding(0, (int) (3 * density), 0, (int) (12 * density));
        layout.addView(subView);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        View[] viewToFocus = new View[1];

        for (int i = 0; i < options.size(); i++) {
            WallpaperOption opt = options.get(i);
            boolean isActive = opt.key.equalsIgnoreCase(currentKey) || (opt.key.equals("Malaysia") && currentKey.startsWith("Malaysia"));

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding((int) (16 * density), (int) (9 * density), (int) (16 * density), (int) (9 * density));

            StateListDrawable bg = new StateListDrawable();
            GradientDrawable on = new GradientDrawable();
            on.setColor(0xFFFFFFFF);
            on.setCornerRadius(10 * density);
            GradientDrawable off = new GradientDrawable();
            off.setColor(isActive ? 0x334A90E2 : 0x1AFFFFFF);
            if (isActive) {
                off.setStroke((int) (1.8f * density), 0xFF38BDF8);
            }
            off.setCornerRadius(10 * density);
            bg.addState(new int[]{android.R.attr.state_focused}, on);
            bg.addState(new int[]{}, off);
            row.setBackground(bg);

            // Row horizontal container for Title + Active tag
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView rowTitle = new TextView(this);
            rowTitle.setText(opt.title);
            rowTitle.setTextSize(16);
            rowTitle.setTextColor(0xFFFFFFFF);
            rowTitle.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
            rowTitle.setFontVariationSettings("'wght' 700");
            rowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
            topRow.addView(rowTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView activeTag = null;
            if (isActive) {
                activeTag = new TextView(this);
                activeTag.setText("" + AppLanguage.strNowBadge(this));
                activeTag.setTextSize(13);
                activeTag.setTextColor(0xFF38BDF8);
                activeTag.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
                activeTag.setFontVariationSettings("'wght' 700");
                topRow.addView(activeTag);
            }
            row.addView(topRow);

            TextView rowSub = new TextView(this);
            rowSub.setText(opt.subtitle);
            rowSub.setTextSize(13);
            rowSub.setTextColor(0xD9FFFFFF);
            rowSub.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
            rowSub.setFontVariationSettings("'wght' 600");
            rowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            rowSub.setPadding(0, (int) (2 * density), 0, 0);
            row.addView(rowSub);

            final TextView finalRowTitle = rowTitle;
            final TextView finalRowSub = rowSub;
            final TextView finalActiveTag = activeTag;
            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    finalRowTitle.setTextColor(0xFF0F172A);
                    finalRowTitle.setShadowLayer(0, 0, 0, 0);
                    finalRowSub.setTextColor(0xFF334155);
                    finalRowSub.setShadowLayer(0, 0, 0, 0);
                    if (finalActiveTag != null) {
                        finalActiveTag.setTextColor(0xFF0284C7);
                        finalActiveTag.setShadowLayer(0, 0, 0, 0);
                    }
                } else {
                    finalRowTitle.setTextColor(0xFFFFFFFF);
                    finalRowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
                    finalRowSub.setTextColor(0xD9FFFFFF);
                    finalRowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
                    if (finalActiveTag != null) {
                        finalActiveTag.setTextColor(0xFF38BDF8);
                        finalActiveTag.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
                    }
                }
            });

            final String chosenKey = opt.key;
            row.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                SoundEffects.playSelect();
                if ("NEXT".equals(chosenKey)) {
                    if (wallpapers != null && !wallpapers.isEmpty()) {
                        showNextWallpaper(true);
                        if (wallpaperIntervalMs > 0 && wallpapers.size() >= 2) {
                            handler.removeCallbacks(wallTick);
                            handler.postDelayed(wallTick, wallpaperIntervalMs);
                        }
                        Toast.makeText(MainActivity.this, "Menukar ke gambar seterusnya...", Toast.LENGTH_SHORT).show();
                    }
                } else if ("INTERVAL".equals(chosenKey)) {
                    showWallpaperIntervalDialog();
                } else if ("TOGGLE_SHUFFLE".equals(chosenKey)) {
                    boolean current = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("shuffle_mode", true);
                    boolean newMode = !current;
                    getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit().putBoolean("shuffle_mode", newMode).apply();
                    Toast.makeText(MainActivity.this, newMode ? "Mod Rawak: Gambar Dipilih Secara Rawak" : " Mod Berurutan: Gambar Dipilih Ikut Urutan", Toast.LENGTH_SHORT).show();
                    showWallpaperDialog();
                } else if ("TOGGLE_DESC".equals(chosenKey)) {
                    boolean current = isWallpaperDescEnabled();
                    boolean newMode = !current;
                    setWallpaperDescEnabled(newMode);
                    if (!newMode) {
                        if (heroShelf != null) {
                            heroShelf.animate().cancel();
                            heroShelf.setAlpha(0f);
                            heroShelf.setVisibility(View.GONE);
                        }
                        Toast.makeText(MainActivity.this, "Deskripsi Wallpaper Dimatikan (Clean Look)", Toast.LENGTH_SHORT).show();
                    } else {
                        if (heroShelf != null && !isCustomWallpaper()) {
                            heroShelf.setVisibility(View.VISIBLE);
                            heroShelf.setAlpha(1f);
                            if (wallpapers != null && !wallpapers.isEmpty()) {
                                int curIdx = getSharedPreferences("wallpaper", MODE_PRIVATE).getInt("index", 0);
                                if (curIdx >= 0 && curIdx < wallpapers.size()) {
                                    setPlace(wallpapers.get(curIdx));
                                }
                            }
                        }
                        Toast.makeText(MainActivity.this, "Deskripsi Wallpaper Dihidupkan (ON)", Toast.LENGTH_SHORT).show();
                    }
                    showWallpaperDialog();
                } else if ("TOGGLE_WEATHER_OVERLAY".equals(chosenKey)) {
                    boolean current = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("dynamic_weather_overlay", true);
                    boolean newMode = !current;
                    getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit().putBoolean("dynamic_weather_overlay", newMode).apply();
                    Toast.makeText(MainActivity.this, newMode ? "Dynamic Weather Overlay Dihidupkan (ON)" : "Dynamic Weather Overlay Dimatikan (OFF)", Toast.LENGTH_SHORT).show();
                    updateWeatherOverlay(HomeWeather.last(MainActivity.this));
                    showWallpaperDialog();
                } else if ("MANAGE_CUSTOM".equals(chosenKey)) {
                    showCustomWallpaperManagerDialog();
                } else {
                    switchWallpaperCategory(chosenKey);
                }
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) (5 * density);
            list.addView(row, rlp);

            if (isActive && viewToFocus[0] == null) {
                viewToFocus[0] = row;
                rowTitle.setTextColor(0xFF0F172A);
                rowTitle.setShadowLayer(0, 0, 0, 0);
                rowSub.setTextColor(0xFF334155);
                rowSub.setShadowLayer(0, 0, 0, 0);
                if (activeTag != null) {
                    activeTag.setTextColor(0xFF0284C7);
                    activeTag.setShadowLayer(0, 0, 0, 0);
                }
            }
        }

        scroll.addView(list);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setView(layout);
        builder.setNegativeButton(AppLanguage.strClose(this), (d, w) -> d.dismiss());
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        showGlass(dialog);
        styleGlassDialog(dialog);

        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (470 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.85f));
            dialog.getWindow().setLayout(w, h);
        }

        if (viewToFocus[0] != null) {
            viewToFocus[0].requestFocus();
        } else if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }

    void showWallpaperIntervalDialog() {
        SoundEffects.playSelect();
        class IntervalOption {
            final int minutes;
            final String title;
            final String subtitle;
            IntervalOption(int minutes, String title, String subtitle) {
                this.minutes = minutes;
                this.title = title;
                this.subtitle = subtitle;
            }
        }

        String curLang = AppLanguage.getLanguage(this);
        List<IntervalOption> options = new ArrayList<>();
        options.add(new IntervalOption(-30, "30 Seconds", "Very fast • a new picture twice a minute"));
        options.add(new IntervalOption(1,
                AppLanguage.LANG_EN.equals(curLang) ? "1 Minute" : (AppLanguage.LANG_ID.equals(curLang) ? "1 Menit" : "1 Minit"),
                AppLanguage.LANG_EN.equals(curLang) ? "Fast • Ideal for demonstration & testing" : (AppLanguage.LANG_ID.equals(curLang) ? "Cepat • Cocok untuk demonstrasi & pengujian" : "Pantas • Sesuai untuk demonstrasi & ujian")));
        options.add(new IntervalOption(5,
                AppLanguage.LANG_EN.equals(curLang) ? "5 Minutes (Recommended)" : (AppLanguage.LANG_ID.equals(curLang) ? "5 Menit (Disarankan)" : "5 Minit (Disyorkan)"),
                AppLanguage.LANG_EN.equals(curLang) ? "Dynamic display, changes fresh every 5 minutes" : (AppLanguage.LANG_ID.equals(curLang) ? "Tampilan dinamis, berganti segar tiap 5 menit" : "Dynamic display, refreshed every 5 minutes")));
        options.add(new IntervalOption(10,
                AppLanguage.LANG_EN.equals(curLang) ? "10 Minutes (Recommended)" : (AppLanguage.LANG_ID.equals(curLang) ? "10 Menit (Disarankan)" : "10 Minit (Disyorkan)"),
                AppLanguage.LANG_EN.equals(curLang) ? "Balanced cadence for relaxed viewing" : (AppLanguage.LANG_ID.equals(curLang) ? "Keseimbangan pas untuk kenyamanan santai" : "Keseimbangan sempurna untuk tontonan santai")));
        options.add(new IntervalOption(15,
                AppLanguage.LANG_EN.equals(curLang) ? " 15 Minutes" : (AppLanguage.LANG_ID.equals(curLang) ? " 15 Menit" : " 15 Minit"),
                AppLanguage.LANG_EN.equals(curLang) ? "Quarter-hour rotation" : (AppLanguage.LANG_ID.equals(curLang) ? "Rotasi santai setiap seperempat jam" : "Pertukaran santai suku jam")));
        options.add(new IntervalOption(30,
                AppLanguage.LANG_EN.equals(curLang) ? " 30 Minutes" : (AppLanguage.LANG_ID.equals(curLang) ? " 30 Menit" : " 30 Minit"),
                AppLanguage.LANG_EN.equals(curLang) ? "Half-hour rotation" : (AppLanguage.LANG_ID.equals(curLang) ? "Rotasi setiap setengah jam" : "Pertukaran perlahan setengah jam")));
        options.add(new IntervalOption(60,
                AppLanguage.LANG_EN.equals(curLang) ? " 1 Hour (60 Minutes)" : (AppLanguage.LANG_ID.equals(curLang) ? " 1 Jam (60 Menit)" : " 1 Jam (60 Minit)"),
                AppLanguage.LANG_EN.equals(curLang) ? "One wallpaper per hour" : (AppLanguage.LANG_ID.equals(curLang) ? "Satu wallpaper setiap jam" : "Satu gambar bagi setiap jam")));
        options.add(new IntervalOption(0,
                AppLanguage.LANG_EN.equals(curLang) ? "Manual Only (Turn Off Shuffle)" : (AppLanguage.LANG_ID.equals(curLang) ? "Manual Saja (Matikan Shuffle)" : "Manual only (shuffle off)"),
                AppLanguage.LANG_EN.equals(curLang) ? "Keep current wallpaper • No automatic change" : (AppLanguage.LANG_ID.equals(curLang) ? "Pertahankan gambar saat ini • Tidak berganti otomatis" : "Keep the current picture • No automatic change")));

        int currentMin = getWallpaperIntervalMinutes();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (20 * density), (int) (16 * density), (int) (20 * density), (int) (12 * density));

        // Header Title
        TextView titleView = new TextView(this);
        titleView.setText(AppLanguage.strWallpaperIntervalTitle(this));
        titleView.setTextSize(19);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        titleView.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        layout.addView(titleView);

        // Header Subtitle
        TextView subView = new TextView(this);
        subView.setText(AppLanguage.strWallpaperIntervalSub(this));
        subView.setTextSize(13.5f);
        subView.setTextColor(0xE6FFFFFF);
        subView.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        subView.setFontVariationSettings("'wght' 600");
        subView.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        subView.setPadding(0, (int) (3 * density), 0, (int) (12 * density));
        layout.addView(subView);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        View[] viewToFocus = new View[1];

        for (int i = 0; i < options.size(); i++) {
            IntervalOption opt = options.get(i);
            boolean isActive = (opt.minutes == currentMin);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding((int) (16 * density), (int) (9 * density), (int) (16 * density), (int) (9 * density));

            StateListDrawable bg = new StateListDrawable();
            GradientDrawable on = new GradientDrawable();
            on.setColor(0xFFFFFFFF);
            on.setCornerRadius(10 * density);
            GradientDrawable off = new GradientDrawable();
            off.setColor(isActive ? 0x334A90E2 : 0x1AFFFFFF);
            if (isActive) {
                off.setStroke((int) (1.8f * density), 0xFF38BDF8);
            }
            off.setCornerRadius(10 * density);
            bg.addState(new int[]{android.R.attr.state_focused}, on);
            bg.addState(new int[]{}, off);
            row.setBackground(bg);

            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView rowTitle = new TextView(this);
            rowTitle.setText(opt.title);
            rowTitle.setTextSize(16);
            rowTitle.setTextColor(0xFFFFFFFF);
            rowTitle.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
            rowTitle.setFontVariationSettings("'wght' 700");
            rowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
            topRow.addView(rowTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView activeTag = null;
            if (isActive) {
                activeTag = new TextView(this);
                activeTag.setText("" + AppLanguage.strNowBadge(this));
                activeTag.setTextSize(13);
                activeTag.setTextColor(0xFF38BDF8);
                activeTag.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
                activeTag.setFontVariationSettings("'wght' 700");
                topRow.addView(activeTag);
            }
            row.addView(topRow);

            TextView rowSub = new TextView(this);
            rowSub.setText(opt.subtitle);
            rowSub.setTextSize(13);
            rowSub.setTextColor(0xD9FFFFFF);
            rowSub.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
            rowSub.setFontVariationSettings("'wght' 600");
            rowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
            rowSub.setPadding(0, (int) (2 * density), 0, 0);
            row.addView(rowSub);

            final TextView finalRowTitle = rowTitle;
            final TextView finalRowSub = rowSub;
            final TextView finalActiveTag = activeTag;
            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    finalRowTitle.setTextColor(0xFF0F172A);
                    finalRowTitle.setShadowLayer(0, 0, 0, 0);
                    finalRowSub.setTextColor(0xFF334155);
                    finalRowSub.setShadowLayer(0, 0, 0, 0);
                    if (finalActiveTag != null) {
                        finalActiveTag.setTextColor(0xFF0284C7);
                        finalActiveTag.setShadowLayer(0, 0, 0, 0);
                    }
                } else {
                    finalRowTitle.setTextColor(0xFFFFFFFF);
                    finalRowTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
                    finalRowSub.setTextColor(0xD9FFFFFF);
                    finalRowSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
                    if (finalActiveTag != null) {
                        finalActiveTag.setTextColor(0xFF38BDF8);
                        finalActiveTag.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
                    }
                }
            });

            final int chosenMin = opt.minutes;
            row.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                SoundEffects.playSelect();
                getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                        .putInt("wallpaper_interval_minutes", chosenMin)
                        .apply();
                wallpaperIntervalMs = getWallpaperIntervalMs();
                handler.removeCallbacks(wallTick);
                if (wallpaperIntervalMs > 0 && wallpapers != null && wallpapers.size() >= 2) {
                    wallShownAt = android.os.SystemClock.elapsedRealtime();
                    handler.postDelayed(wallTick, wallpaperIntervalMs);
                }
                String msg = (chosenMin == 0)
                        ? "Auto change is off (manual)"
                        : "Wallpaper will change every " + (chosenMin == 60 ? "1 hour" : chosenMin + " minutes");
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                showWallpaperDialog();
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) (5 * density);
            list.addView(row, rlp);

            if (isActive && viewToFocus[0] == null) {
                viewToFocus[0] = row;
                rowTitle.setTextColor(0xFF0F172A);
                rowTitle.setShadowLayer(0, 0, 0, 0);
                rowSub.setTextColor(0xFF334155);
                rowSub.setShadowLayer(0, 0, 0, 0);
                if (activeTag != null) {
                    activeTag.setTextColor(0xFF0284C7);
                    activeTag.setShadowLayer(0, 0, 0, 0);
                }
            }
        }

        scroll.addView(list);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setView(layout);
        builder.setNegativeButton(AppLanguage.strBack(this), (d, w) -> {
            d.dismiss();
            showWallpaperDialog();
        });
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        showGlass(dialog);
        styleGlassDialog(dialog);

        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (470 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.85f));
            dialog.getWindow().setLayout(w, h);
        }

        if (viewToFocus[0] != null) {
            viewToFocus[0].requestFocus();
        } else if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }

    public static final int DARK_MODE_OFF = 0;
    public static final int DARK_MODE_AUTO = 1;
    public static final int DARK_MODE_ALWAYS = 2;
    public static final int DARK_MODE_OLED_BLACK = 3;

    public void applyDarkModeFilter() {
        if (wallView == null) return;
        SharedPreferences sp = getSharedPreferences("appearance_prefs", Context.MODE_PRIVATE);
        int mode = sp.getInt("dark_mode_style", DARK_MODE_OFF);

        if (mode == DARK_MODE_OLED_BLACK) {
            wallView.setVisibility(View.GONE);
            if (root != null) root.setBackgroundColor(0xFF000000);
            return;
        }

        wallView.setVisibility(View.VISIBLE);
        boolean isDark = false;
        if (mode == DARK_MODE_ALWAYS) {
            isDark = true;
        } else if (mode == DARK_MODE_AUTO) {
            int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            isDark = (h < 7 || h >= 19);
        }

        if (isDark) {
            wallView.setColorFilter(0xFF4A4A4A, android.graphics.PorterDuff.Mode.MULTIPLY);
        } else if (placesMode) {
            wallView.setColorFilter(0xFF7A7A7A, android.graphics.PorterDuff.Mode.MULTIPLY);
        } else {
            wallView.clearColorFilter();
        }
    }

    void showDarkModeDialog() {
        SoundEffects.playSelect();
        class DarkModeOption {
            final int mode;
            final String title;
            final String subtitle;
            DarkModeOption(int mode, String title, String subtitle) {
                this.mode = mode;
                this.title = title;
                this.subtitle = subtitle;
            }
        }

        String curLang = AppLanguage.getLanguage(this);
        boolean isMalay = AppLanguage.LANG_MS.equals(curLang) || AppLanguage.LANG_ID.equals(curLang);

        List<DarkModeOption> options = new ArrayList<>();
        options.add(new DarkModeOption(DARK_MODE_OFF,
                isMalay ? "Off (original brightness)" : "Off (Default Wallpaper Brightness)",
                isMalay ? "Show the wallpaper in its original colour and brightness, unfiltered" : "Display wallpapers with standard colors without darkening filter"));
        options.add(new DarkModeOption(DARK_MODE_AUTO,
                isMalay ? "Automatik (7:00 PM – 7:00 AM)" : "Automatic (7:00 PM – 7:00 AM)",
                isMalay ? "Menggelapkan wallpaper secara automatik pada waktu malam sahaja" : "Automatically dims wallpapers at night to prevent eye fatigue"));
        options.add(new DarkModeOption(DARK_MODE_ALWAYS,
                isMalay ? "Sentiasa Gelap (Obsidian 50%)" : "Always Dark (Obsidian 50%)",
                isMalay ? "Penapis gelap obsidian redup untuk teks berkontras tinggi dan perlindungan mata" : "Deep obsidian dimming filter for maximum text contrast and comfort"));
        options.add(new DarkModeOption(DARK_MODE_OLED_BLACK,
                isMalay ? " Hitam Pekat OLED (Pure Black)" : " Pure Black OLED (0% Wallpaper)",
                isMalay ? "Turn the wallpaper off completely: 100% black pixels for OLED TVs" : "Disable wallpapers completely for 100% pure black OLED panels"));

        SharedPreferences sp = getSharedPreferences("appearance_prefs", Context.MODE_PRIVATE);
        int currentMode = sp.getInt("dark_mode_style", DARK_MODE_OFF);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (22 * density), (int) (18 * density), (int) (22 * density), (int) (18 * density));

        TextView titleView = new TextView(this);
        titleView.setText("" + (isMalay ? "Mod Gelap & Perlindungan OLED" : "Dark Mode & OLED Dimming"));
        titleView.setTextSize(18);
        titleView.setTextColor(0xFFFFFFFF);
        titleView.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        titleView.setFontVariationSettings("'wght' 700");
        titleView.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        layout.addView(titleView);

        TextView subView = new TextView(this);
        subView.setText(isMalay ? "Choose a homescreen dimming style to suit your living room." : "Choose homescreen dimming mode to match your living room lighting.");
        subView.setTextSize(12.5f);
        subView.setTextColor(0xBBFFFFFF);
        subView.setTypeface(getResources().getFont(R.font.inter), Typeface.NORMAL);
        subView.setFontVariationSettings("'wght' 500");
        subView.setPadding(0, (int) (3 * density), 0, (int) (12 * density));
        layout.addView(subView);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);

        AlertDialog[] dialogHolder = new AlertDialog[1];
        View[] viewToFocus = new View[1];

        for (int i = 0; i < options.size(); i++) {
            DarkModeOption opt = options.get(i);
            boolean isActive = (opt.mode == currentMode);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setFocusable(true);
            row.setClickable(true);
            row.setPadding((int) (16 * density), (int) (10 * density), (int) (16 * density), (int) (10 * density));

            StateListDrawable bg = new StateListDrawable();
            GradientDrawable on = new GradientDrawable();
            on.setColor(0xFFFFFFFF);
            on.setCornerRadius(10 * density);
            GradientDrawable off = new GradientDrawable();
            off.setColor(isActive ? 0x334A90E2 : 0x1AFFFFFF);
            if (isActive) {
                off.setStroke((int) (1.8f * density), 0xFF38BDF8);
            }
            off.setCornerRadius(10 * density);
            bg.addState(new int[]{android.R.attr.state_focused}, on);
            bg.addState(new int[]{}, off);
            row.setBackground(bg);

            LinearLayout headerRow = new LinearLayout(this);
            headerRow.setOrientation(LinearLayout.HORIZONTAL);
            headerRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView rowTitle = new TextView(this);
            rowTitle.setText(opt.title);
            rowTitle.setTextSize(15);
            rowTitle.setTextColor(0xFFFFFFFF);
            rowTitle.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
            rowTitle.setFontVariationSettings("'wght' 700");
            headerRow.addView(rowTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView activeTag = null;
            if (isActive) {
                activeTag = new TextView(this);
                activeTag.setText("" + (isMalay ? "AKTIF" : "ACTIVE"));
                activeTag.setTextSize(11);
                activeTag.setTextColor(0xFF38BDF8);
                activeTag.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
                activeTag.setFontVariationSettings("'wght' 700");
                headerRow.addView(activeTag);
            }
            row.addView(headerRow);

            TextView rowSub = new TextView(this);
            rowSub.setText(opt.subtitle);
            rowSub.setTextSize(12.5f);
            rowSub.setTextColor(0xD9FFFFFF);
            rowSub.setTypeface(getResources().getFont(R.font.inter), Typeface.NORMAL);
            rowSub.setFontVariationSettings("'wght' 500");
            rowSub.setPadding(0, (int) (2 * density), 0, 0);
            row.addView(rowSub);

            final TextView fTitle = rowTitle;
            final TextView fSub = rowSub;
            final TextView fActive = activeTag;
            row.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    fTitle.setTextColor(0xFF0F172A);
                    fTitle.setShadowLayer(0, 0, 0, 0);
                    fSub.setTextColor(0xFF334155);
                    fSub.setShadowLayer(0, 0, 0, 0);
                    if (fActive != null) {
                        fActive.setTextColor(0xFF0284C7);
                        fActive.setShadowLayer(0, 0, 0, 0);
                    }
                } else {
                    fTitle.setTextColor(0xFFFFFFFF);
                    fTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
                    fSub.setTextColor(0xD9FFFFFF);
                    fSub.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
                    if (fActive != null) {
                        fActive.setTextColor(0xFF38BDF8);
                        fActive.setShadowLayer(2 * density, 0, 1 * density, 0xAA000000);
                    }
                }
            });

            final int chosenMode = opt.mode;
            row.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                SoundEffects.playSelect();
                sp.edit().putInt("dark_mode_style", chosenMode).apply();
                applyDarkModeFilter();
                Toast.makeText(MainActivity.this, isMalay ? "Pilihan mod gelap berjaya disimpan" : "Dark mode preference saved", Toast.LENGTH_SHORT).show();
            });

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = (int) (5 * density);
            list.addView(row, rlp);

            if (isActive && viewToFocus[0] == null) {
                viewToFocus[0] = row;
                rowTitle.setTextColor(0xFF0F172A);
                rowTitle.setShadowLayer(0, 0, 0, 0);
                rowSub.setTextColor(0xFF334155);
                rowSub.setShadowLayer(0, 0, 0, 0);
                if (activeTag != null) {
                    activeTag.setTextColor(0xFF0284C7);
                    activeTag.setShadowLayer(0, 0, 0, 0);
                }
            }
        }

        scroll.addView(list);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setView(layout);
        builder.setNegativeButton(AppLanguage.strClose(this), (d, w) -> d.dismiss());
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        showGlass(dialog);
        styleGlassDialog(dialog);

        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (470 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.85f));
            dialog.getWindow().setLayout(w, h);
        }

        if (viewToFocus[0] != null) {
            viewToFocus[0].requestFocus();
        } else if (list.getChildCount() > 0) {
            list.getChildAt(0).requestFocus();
        }
    }

    private void triggerGoogleAssistant() {
        SoundEffects.playSelect();
        // 1. Primary: System Google Assistant (same as Assistant key on remote)
        try {
            Intent intent = new Intent(Intent.ACTION_ASSIST);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 2. Direct Katniss Voice Search (bypasses intent disambiguation chooser)
        try {
            Intent intent = new Intent("android.speech.action.RECOGNIZE_SPEECH");
            intent.setPackage("com.google.android.katniss");
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 3. Voice Command Action
        try {
            Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 4. Web Search Action
        try {
            Intent intent = new Intent("android.speech.action.WEB_SEARCH");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return;
        } catch (Exception ignored) {}

        // 5. System Search Dialog
        try {
            startSearch(null, false, null, true);
            return;
        } catch (Exception ignored) {}

        // 6. Generic Speech Recognizer Fallback
        try {
            Intent intent = new Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Google Assistant", Toast.LENGTH_SHORT).show();
        }
    }

    /** Shows a dialog and turns its panel into the same frosted glass as the rest of the launcher. */
    void showGlass(android.app.Dialog d) {
        if (d == null) return;
        d.show();
        glassify(d);
    }

    void glassify(android.app.Dialog d) {
        try {
            android.view.Window w = d.getWindow();
            if (w == null) return;
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
            w.setDimAmount(0.30f);
            View panel = null;
            int id = getResources().getIdentifier("parentPanel", "id", "android");
            if (id != 0) panel = w.findViewById(id);
            if (panel == null) panel = w.findViewById(android.R.id.content);
            if (panel == null) return;
            final float r = 26 * density;
            final View target = panel;
            target.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{new GlassDrawable(target, r, density, null, 0.6f), GlassPage.veil(r, 0x8C0B1220)}));
            Backdrop.watch(target);
            target.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View v, android.graphics.Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
                }
            });
            target.setClipToOutline(true);
        } catch (Throwable ignored) {}
    }

    void styleGlassDialog(AlertDialog dialog) {
        if (dialog == null) return;
        try {
            Typeface outfit = getResources().getFont(R.font.inter);
            TextView titleView = dialog.findViewById(android.R.id.title);
            if (titleView != null) {
                titleView.setTypeface(outfit, Typeface.BOLD);
                titleView.setFontVariationSettings("'wght' 700");
                titleView.setTextSize(20f);
                titleView.setTextColor(0xFFFFFFFF);
                titleView.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
            }
            TextView msgView = dialog.findViewById(android.R.id.message);
            if (msgView != null) {
                msgView.setTypeface(outfit, Typeface.BOLD);
                msgView.setFontVariationSettings("'wght' 600");
                msgView.setTextSize(15f);
                msgView.setTextColor(0xF0FFFFFF);
                msgView.setLineSpacing(0, 1.35f);
                msgView.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
            }
            Button btnPos = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (btnPos != null) {
                btnPos.setTypeface(outfit, Typeface.BOLD);
                btnPos.setFontVariationSettings("'wght' 700");
                btnPos.setTextSize(15f);
                btnPos.setTextColor(0xFFFFFFFF);
            }
            Button btnNeg = dialog.getButton(DialogInterface.BUTTON_NEGATIVE);
            if (btnNeg != null) {
                btnNeg.setTypeface(outfit, Typeface.BOLD);
                btnNeg.setFontVariationSettings("'wght' 650");
                btnNeg.setTextSize(15f);
                btnNeg.setTextColor(0xCCFFFFFF);
            }
            Button btnNeu = dialog.getButton(DialogInterface.BUTTON_NEUTRAL);
            if (btnNeu != null) {
                btnNeu.setTypeface(outfit, Typeface.BOLD);
                btnNeu.setFontVariationSettings("'wght' 650");
                btnNeu.setTextSize(15f);
                btnNeu.setTextColor(0xCCFFFFFF);
            }
            ListView listView = dialog.getListView();
            if (listView != null) {
                listView.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        if (child instanceof TextView) {
                            TextView tv = (TextView) child;
                            tv.setTypeface(outfit, Typeface.BOLD);
                            tv.setFontVariationSettings("'wght' 650");
                            tv.setTextSize(15.5f);
                            tv.setTextColor(0xFFFFFFFF);
                            tv.setShadowLayer(3 * density, 0, 1 * density, 0xAA000000);
                            tv.setPadding((int) (18 * density), (int) (12 * density), (int) (18 * density), (int) (12 * density));
                        }
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {}
                });
            }
        } catch (Exception ignored) {}
    }

    private void switchWallpaperCategory(String category) {
        getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit().putString("active_category", category).apply();
        String displayLabel;
        if ("Malaysia".equalsIgnoreCase(category)) {
            displayLabel = "Tempat Menarik di Malaysia";
        } else if ("Minimalist".equalsIgnoreCase(category)) {
            displayLabel = "Minimalis Gelap (OLED)";
        } else {
            displayLabel = category;
        }
        Toast.makeText(this, "Kategori: " + displayLabel, Toast.LENGTH_SHORT).show();

        if ("Custom".equalsIgnoreCase(category)) {
            placesMode = false;
            if (heroShelf != null) {
                heroShelf.animate().cancel();
                heroShelf.setAlpha(0f);
                heroShelf.setVisibility(View.GONE);
            }
            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
                getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
                showNextWallpaper(true);
                startWallpaperRotation();
                return;
            }
        } else {
            placesMode = false;
            if (heroShelf != null) {
                if (isWallpaperDescEnabled()) {
                    heroShelf.setVisibility(View.VISIBLE);
                } else {
                    heroShelf.setVisibility(View.GONE);
                    heroShelf.setAlpha(0f);
                }
            }
        }

        List<File> matching = OnlineWallpapers.listByCategory(this, category);
        if (!matching.isEmpty()) {
            wallpapers = matching;
            showNextWallpaper(true);
            startWallpaperRotation();
        } else {
            Toast.makeText(this, "Memuat turun 4K Wallpaper " + displayLabel + " (Pexels & Unsplash)...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                boolean downloaded = OnlineWallpapers.syncCategory(getApplicationContext(), category);
                handler.post(() -> {
                    if (isDestroyed()) return;
                    List<File> fresh = OnlineWallpapers.listByCategory(MainActivity.this, category);
                    if (!fresh.isEmpty()) {
                        wallpapers = fresh;
                        showNextWallpaper(true);
                        startWallpaperRotation();
                    }
                });
            }, "wall-cat-sync").start();
        }
    }

    void showGeminiAssistantDialog() {
        SoundEffects.playSelect();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding((int) (26 * density), (int) (20 * density), (int) (26 * density), (int) (20 * density));

        // Header Title
        TextView title = new TextView(this);
        title.setText("Gemini AI Assistant");
        title.setTextSize(22);
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        title.setFontVariationSettings("'wght' 700");
        title.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        title.setPadding(0, 0, 0, (int) (4 * density));
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Bantuan Pintar Built-In Glass TV Home");
        sub.setTextSize(14f);
        sub.setTextColor(0xE6FFFFFF);
        sub.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        sub.setFontVariationSettings("'wght' 600");
        sub.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        sub.setPadding(0, 0, 0, (int) (14 * density));
        layout.addView(sub);

        // Status Card: Contextual AI Overview
        LinearLayout statusCard = new LinearLayout(this);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0x24FFFFFF);
        cardBg.setCornerRadius(14 * density);
        cardBg.setStroke((int) (1.8f * density), 0x8064D2FF);
        statusCard.setBackground(cardBg);

        Location.Zone zone = Location.chosen(this);
        String zoneTown = (zone != null) ? zone.town : "Kuala Lumpur";
        String nextPrayer = PrayerTimes.getNextPrayerSummary(this);
        HomeWeather.Line wx = HomeWeather.last(this);
        String wxStr = (wx != null && !wx.weather.isEmpty()) ? (wx.weather + (wx.api.isEmpty() ? "" : " • " + wx.api)) : "Live weather on";

        TextView statusText = new TextView(this);
        statusText.setText("Ringkasan AI Sistem:\n"
                + "• Solat Seterusnya: " + nextPrayer + " (" + zoneTown + ")\n"
                + "• Cuaca & JAS IPU: " + wxStr + "\n"
                + "• Memori & TV: Sistem Beroperasi Pantas & Bersih");
        statusText.setTextSize(14.5f);
        statusText.setTextColor(0xFFFFFFFF);
        statusText.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        statusText.setFontVariationSettings("'wght' 600");
        statusText.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        statusText.setLineSpacing(0, 1.3f);
        statusCard.addView(statusText);
        layout.addView(statusCard);

        TextView actionsTitle = new TextView(this);
        actionsTitle.setText("Tindakan Pintar:");
        actionsTitle.setTextSize(14.5f);
        actionsTitle.setTextColor(0xE6FFFFFF);
        actionsTitle.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        actionsTitle.setFontVariationSettings("'wght' 700");
        actionsTitle.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        actionsTitle.setPadding(0, (int) (14 * density), 0, (int) (8 * density));
        layout.addView(actionsTitle);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout actionList = new LinearLayout(this);
        actionList.setOrientation(LinearLayout.VERTICAL);

        AlertDialog[] dialogHolder = new AlertDialog[1];

        // Action 1: Smart Boost
        addAiActionItem(actionList, "Smart Boost TV (Bebas Memori & Bersih Cache)", () -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            SoundEffects.playSelect();
            smartBoostTv();
        });

        // Action 2: Cuaca & Kualiti Udara
        addAiActionItem(actionList, "Detailed Weather & Air Quality", () -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            SoundEffects.playSelect();
            showAiWeatherSummaryDialog(zoneTown, wxStr);
        });

        // Action 3: Waktu Solat & Refleksi Doa
        addAiActionItem(actionList, "Prayer Times & Today's Doa", () -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            SoundEffects.playSelect();
            showAiIslamicDialog(zoneTown);
        });

        // Action 4: Tips & Bantuan Kawalan TV
        addAiActionItem(actionList, "Tips & Panduan Kawalan Pintar TV", () -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            SoundEffects.playSelect();
            showAiTipsDialog();
        });

        scroll.addView(actionList);
        layout.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        builder.setView(layout);
        builder.setNegativeButton("Close", (d, w) -> d.dismiss());
        AlertDialog dialog = builder.create();
        dialogHolder[0] = dialog;
        showGlass(dialog);
        styleGlassDialog(dialog);

        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (480 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.75f));
            dialog.getWindow().setLayout(w, h);
        }
        if (actionList.getChildCount() > 0) {
            actionList.getChildAt(0).requestFocus();
        }
    }

    private void addAiActionItem(LinearLayout container, String title, Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(title);
        btn.setTextSize(15.5f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        btn.setFontVariationSettings("'wght' 650");
        btn.setShadowLayer(3 * density, 0, 1 * density, 0xAA000000);
        btn.setPadding((int) (16 * density), (int) (12 * density), (int) (16 * density), (int) (12 * density));
        btn.setFocusable(true);
        btn.setClickable(true);

        StateListDrawable bg = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setColor(0xFFFFFFFF);
        on.setCornerRadius(12 * density);
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x1AFFFFFF);
        off.setCornerRadius(12 * density);
        bg.addState(new int[]{android.R.attr.state_focused}, on);
        bg.addState(new int[]{}, off);
        btn.setBackground(bg);
        btn.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF000000, 0xFFFFFFFF}));

        btn.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (6 * density);
        container.addView(btn, lp);
    }

    private void smartBoostTv() {
        new Thread(() -> {
            try {
                File cache = getCacheDir();
                if (cache != null && cache.isDirectory()) {
                    File[] files = cache.listFiles();
                    if (files != null) for (File f : files) f.delete();
                }
            } catch (Exception ignored) {}
            CacheCleaner.maybeRun(getApplicationContext(), 1);
            System.gc();
            handler.post(() -> Toast.makeText(this, "TV memory and cache optimised!", Toast.LENGTH_SHORT).show());
        }, "gemini-boost").start();
    }

    private void showAiTipsDialog() {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog)
                .setTitle("Tips Pintar Glass TV")
                .setMessage("• Long-press HOME (500ms) to open the Settings popup from any app or the home screen.\n\n"
                        + "• Tekan sekali butang HOME untuk kembali ke paparan utama.\n\n"
                        + "• Koleksi Wallpaper 4K dijana of Pexels & Unsplash mengikut kategori.\n\n"
                        + "• Azan & prayer times follow the JAKIM Malaysia zone.\n\n"
                        + "• Gunakan Gemini AI untuk membersihkan cache dan menyegarkan TV anda secara pantas.")
                .setPositiveButton("Faham", null)
                .create();
        showGlass(dialog);
        styleGlassDialog(dialog);
        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (430 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.68f));
            dialog.getWindow().setLayout(w, h);
        }
    }

    private void showAiWeatherSummaryDialog(String town, String wxStr) {
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog)
                .setTitle("Analisis Cuaca & Jerebu Pintar")
                .setMessage("Location: " + town + "\n\n"
                        + "Status Terkini: " + wxStr + "\n\n"
                        + "ℹ Data cuaca disegerakkan of Open-Meteo dan bacaan Indeks Pencemar Udara (IPU) rasmi stesen Jabatan Alam Sekitar Malaysia (APIMS DOE).\n\n"
                        + "Air quality is fine today.")
                .setPositiveButton("OK", null)
                .create();
        showGlass(dialog);
        styleGlassDialog(dialog);
        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (430 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.68f));
            dialog.getWindow().setLayout(w, h);
        }
    }

    private void showAiIslamicDialog(String town) {
        WeeklyAyah.Ayah ayah = WeeklyAyah.last(this);
        String ayahTxt = (ayah != null) ? (ayah.arabic + "\n\n\"" + ayah.malay + "\"\n(" + ayah.reference + ")") : "No daily doa.";
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog)
                .setTitle("Prayer Times & Today's Doa")
                .setMessage("JAKIM zone: " + town + "  •  " + PrayerTimes.getHijriDate() + "\n\n"
                        + "Solat Seterusnya: " + PrayerTimes.getNextPrayerSummary(this) + "\n\n"
                        + "Featured Verse / Doa Harian:\n" + ayahTxt)
                .setPositiveButton("Close", null)
                .create();
        showGlass(dialog);
        styleGlassDialog(dialog);
        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (440 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.70f));
            dialog.getWindow().setLayout(w, h);
        }
    }

    void showWallpaperUploadQrDialog() {
        if (uploadServer != null) {
            uploadServer.stop();
            uploadServer = null;
        }

        String localIp = WallpaperUploadServer.getLocalIpAddress();
        WallpaperUploadServer.UploadListener listener = createUploadListener();
        uploadServer = new WallpaperUploadServer(this, 8080, listener);
        boolean started = uploadServer.start();

        if (!started) {
            Toast.makeText(this, "Error: could not start the local upload server.", Toast.LENGTH_LONG).show();
            return;
        }

        int port = uploadServer.getPort();
        String uploadUrl = "http://" + localIp + ":" + port;
        Bitmap qrBmp = QrBitmapUtil.createQrBitmap(uploadUrl, Math.round(260 * density));

        LinearLayout rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        int padH = Math.round(36 * density);
        int padV = Math.round(28 * density);
        rootLayout.setPadding(padH, padV, padH, padV);
        rootLayout.setBackgroundColor(0xF0131722); // Elegant dark slate

        // Title
        TextView title = new TextView(this);
        title.setText("Upload Wallpaper From Phone");
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        title.setFontVariationSettings("'wght' 700");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        title.setShadowLayer(4 * density, 0, 1 * density, 0xDD000000);
        title.setGravity(Gravity.CENTER);
        rootLayout.addView(title);

        // Subtitle instructions
        TextView sub = new TextView(this);
        sub.setText("Sambungkan telefon pada Wi-Fi yang sama, kemudian imbas kod QR di bawah:");
        sub.setTextColor(0xFFE2E8F0);
        sub.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        sub.setFontVariationSettings("'wght' 600");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        sub.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Math.round(6 * density), 0, Math.round(18 * density));
        rootLayout.addView(sub);

        // QR Code Container (Crisp White Card with subtle margin)
        FrameLayout qrContainer = new FrameLayout(this);
        qrContainer.setBackgroundColor(0xFFFFFFFF);
        int qrPad = Math.round(12 * density);
        qrContainer.setPadding(qrPad, qrPad, qrPad, qrPad);
        LinearLayout.LayoutParams qrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qrLp.gravity = Gravity.CENTER_HORIZONTAL;
        qrContainer.setLayoutParams(qrLp);

        qrImageView = new ImageView(this);
        if (qrBmp != null) {
            qrImageView.setImageBitmap(qrBmp);
        }
        int qrDim = Math.round(220 * density);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(qrDim, qrDim);
        imgLp.gravity = Gravity.CENTER;
        qrImageView.setLayoutParams(imgLp);
        qrContainer.addView(qrImageView);
        rootLayout.addView(qrContainer);

        // Web URL pill
        qrUrlText = new TextView(this);
        qrUrlText.setText("" + uploadUrl);
        qrUrlText.setTextColor(0xFF38BDF8);
        qrUrlText.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        qrUrlText.setFontVariationSettings("'wght' 700");
        qrUrlText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        qrUrlText.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);
        qrUrlText.setGravity(Gravity.CENTER);
        qrUrlText.setPadding(0, Math.round(14 * density), 0, Math.round(4 * density));
        rootLayout.addView(qrUrlText);

        // Status text
        qrStatusText = new TextView(this);
        qrStatusText.setText("Menunggu imbasan kamera atau fail dari telefon...");
        qrStatusText.setTextColor(0xFFF1F5F9);
        qrStatusText.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        qrStatusText.setFontVariationSettings("'wght' 600");
        qrStatusText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        qrStatusText.setShadowLayer(3 * density, 0, 1 * density, 0xAA000000);
        qrStatusText.setGravity(Gravity.CENTER);
        qrStatusText.setPadding(0, 0, 0, Math.round(18 * density));
        rootLayout.addView(qrStatusText);

        // Close button
        Button btnClose = new Button(this);
        btnClose.setText("Close");
        btnClose.setTextColor(0xFFFFFFFF);
        btnClose.setBackgroundResource(R.drawable.pill_bg);
        btnClose.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        btnClose.setFontVariationSettings("'wght' 700");
        btnClose.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnClose.setFocusable(true);
        btnClose.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                Math.round(180 * density), Math.round(42 * density));
        btnLp.gravity = Gravity.CENTER_HORIZONTAL;
        btnClose.setLayoutParams(btnLp);
        rootLayout.addView(btnClose);

        // Watermark credit
        TextView credit = new TextView(this);
        credit.setText("Powered by @imsomangge");
        credit.setTextColor(0xFFCBD5E1);
        credit.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        credit.setFontVariationSettings("'wght' 600");
        credit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, Math.round(14 * density), 0, 0);
        rootLayout.addView(credit);

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setView(rootLayout);
        qrUploadDialog = builder.create();

        btnClose.setOnClickListener(v -> qrUploadDialog.dismiss());

        qrUploadDialog.setOnDismissListener(dialog -> {
            if (uploadServer != null) {
                uploadServer.stop();
                uploadServer = null;
            }
            qrUploadDialog = null;
        });

        showGlass(qrUploadDialog);
        btnClose.requestFocus();
    }

    private WallpaperUploadServer.UploadListener createUploadListener() {
        return new WallpaperUploadServer.UploadListener() {
            @Override
            public void onUploaded(File imageFile) {
                onCustomWallpaperUploaded(imageFile);
            }

            @Override
            public void onDeleted(File file) {
                onCustomWallpaperDeleted(file);
            }

            @Override
            public void onSelected(File file) {
                onCustomWallpaperSelected(file);
            }

            @Override
            public void onSlideshowMode() {
                onCustomWallpaperSlideshow();
            }

            @Override
            public void onCleared() {
                onCustomWallpaperCleared();
            }
        };
    }

    private void onCustomWallpaperUploaded(File file) {
        runOnUiThread(() -> {
            SoundEffects.playSelect();
            Toast.makeText(this, "Wallpaper berjaya dimuat naik dari telefon!", Toast.LENGTH_LONG).show();

            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putString("active_category", "Custom")
                    .putBoolean("custom_single_mode", false)
                    .remove("custom_single_file")
                    .apply();

            placesMode = false;
            if (heroShelf != null) {
                heroShelf.animate().cancel();
                heroShelf.setAlpha(0f);
                heroShelf.setVisibility(View.GONE);
            }
            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
            } else {
                wallpapers = Collections.singletonList(file);
            }

            getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
            showNextWallpaper(true);

            if (qrStatusText != null) {
                qrStatusText.setText("Received & shown on TV!");
                qrStatusText.setTextColor(0xFF4ADE80);
            }
            if (qrUrlText != null) {
                qrUrlText.setText("Active wallpaper: " + file.getName());
                qrUrlText.setTextColor(0xFF4ADE80);
            }
            if (qrImageView != null) {
                try {
                    Bitmap thumb = decodeWallpaper(file);
                    if (thumb != null) {
                        qrImageView.setImageBitmap(thumb);
                        qrImageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    }
                } catch (Exception ignored) {}
            }

            handler.postDelayed(() -> {
                if (qrUploadDialog != null && qrUploadDialog.isShowing()) {
                    qrUploadDialog.dismiss();
                }
            }, 3000);
        });
    }

    private void onCustomWallpaperSelected(File file) {
        runOnUiThread(() -> {
            SoundEffects.playSelect();
            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putString("active_category", "Custom")
                    .putBoolean("custom_single_mode", true)
                    .putString("custom_single_file", file.getName())
                    .apply();

            placesMode = false;
            if (heroShelf != null) {
                heroShelf.animate().cancel();
                heroShelf.setAlpha(0f);
                heroShelf.setVisibility(View.GONE);
            }
            wallpapers = Collections.singletonList(file);
            getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
            showNextWallpaper(true);
            Toast.makeText(this, "Pinned wallpaper: " + file.getName(), Toast.LENGTH_SHORT).show();
        });
    }

    private void onCustomWallpaperSlideshow() {
        runOnUiThread(() -> {
            SoundEffects.playSelect();
            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putString("active_category", "Custom")
                    .putBoolean("custom_single_mode", false)
                    .remove("custom_single_file")
                    .apply();

            placesMode = false;
            if (heroShelf != null) {
                heroShelf.animate().cancel();
                heroShelf.setAlpha(0f);
                heroShelf.setVisibility(View.GONE);
            }

            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            if (files != null && files.length > 0) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                wallpapers = new ArrayList<>(Arrays.asList(files));
                getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
                showNextWallpaper(true);
                startWallpaperRotation();
                Toast.makeText(this, "Slideshow of all wallpapers on (" + files.length + " foto)", Toast.LENGTH_SHORT).show();
            } else {
                switchWallpaperCategory("Natural");
            }
        });
    }

    private void onCustomWallpaperDeleted(File file) {
        runOnUiThread(() -> {
            String singleFile = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("custom_single_file", null);
            if (singleFile != null && singleFile.equals(file.getName())) {
                getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                        .putBoolean("custom_single_mode", false)
                        .remove("custom_single_file")
                        .apply();
            }

            File customDir = new File(getFilesDir(), "custom_wallpapers");
            File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
            String activeCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Natural");

            if ("Custom".equalsIgnoreCase(activeCat)) {
                if (files != null && files.length > 0) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    wallpapers = new ArrayList<>(Arrays.asList(files));
                    getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("index", -1).apply();
                    showNextWallpaper(true);
                    Toast.makeText(this, "Picture deleted. Left: " + files.length + " foto.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "All pictures deleted. Switched to Natural.", Toast.LENGTH_SHORT).show();
                    switchWallpaperCategory("Natural");
                }
            } else {
                Toast.makeText(this, "Picture deleted from storage.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onCustomWallpaperCleared() {
        runOnUiThread(() -> {
            getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).edit()
                    .putBoolean("custom_single_mode", false)
                    .remove("custom_single_file")
                    .apply();

            String activeCat = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("active_category", "Natural");
            if ("Custom".equalsIgnoreCase(activeCat)) {
                switchWallpaperCategory("Natural");
            }
            Toast.makeText(this, "All uploaded pictures cleared.", Toast.LENGTH_SHORT).show();
        });
    }

    void showCustomWallpaperManagerDialog() {
        File customDir = new File(getFilesDir(), "custom_wallpapers");
        File[] files = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
        if (files == null || files.length == 0) {
            AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog)
                    .setTitle("Manage Uploaded Pictures")
                    .setMessage("No personal pictures found on the TV. Upload pictures from your phone using the QR code.")
                    .setPositiveButton("Open QR Code", (d, w) -> showWallpaperUploadQrDialog())
                    .setNegativeButton("Back", (d, w) -> showWallpaperDialog())
                    .create();
            showGlass(dialog);
            styleGlassDialog(dialog);
            return;
        }

        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        boolean isSingleMode = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getBoolean("custom_single_mode", false);
        String singleFile = getSharedPreferences("wallpaper_prefs", MODE_PRIVATE).getString("custom_single_file", "");

        List<String> items = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        // Quick Mode Actions at top
        items.add("Upload New Photo (Phone QR Code)");
        actions.add(this::showWallpaperUploadQrDialog);

        items.add("Play All Pictures (Slideshow)");
        actions.add(this::onCustomWallpaperSlideshow);

        items.add("Delete All Pictures (Clear)");
        actions.add(() -> {
            AlertDialog dialog = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog)
                    .setTitle("Delete All Pictures?")
                    .setMessage("Adakah anda pasti ingin memadam semua " + files.length + " gambar yang dimuat naik?")
                    .setPositiveButton("Yes, Delete All", (d, w) -> {
                        for (File f : files) f.delete();
                        onCustomWallpaperCleared();
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
            showGlass(dialog);
            styleGlassDialog(dialog);
        });

        SimpleDateFormat sdf = new SimpleDateFormat("d MMM, HH:mm", Locale.getDefault());
        for (File f : files) {
            double kb = f.length() / 1024.0;
            String sizeStr = kb > 1024 ? String.format(Locale.ROOT, "%.1f MB", kb / 1024.0) : String.format(Locale.ROOT, "%.0f KB", kb);
            boolean isCurrent = isSingleMode && f.getName().equals(singleFile);
            String prefix = isCurrent ? "[Kekal] " : "";
            items.add(prefix + f.getName() + " (" + sizeStr + " • " + sdf.format(new Date(f.lastModified())) + ")");
            actions.add(() -> showSingleCustomActionDialog(f));
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("TV Picture Manager (" + files.length + " Foto)");
        builder.setItems(items.toArray(new String[0]), (dialog, which) -> {
            if (which >= 0 && which < actions.size()) {
                actions.get(which).run();
            }
        });
        builder.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
        if (dialog.getWindow() != null) {
            int w = Math.min((int) (520 * density), getResources().getDisplayMetrics().widthPixels - (int) (120 * density));
            int h = Math.min((int) (440 * density), (int) (getResources().getDisplayMetrics().heightPixels * 0.70f));
            dialog.getWindow().setLayout(w, h);
        }
    }

    private void showSingleCustomActionDialog(File file) {
        String[] actions = {
                "Use as Only Wallpaper (Pin This Image)",
                "Delete This Picture",
                "Keep Only This Picture & Delete the Rest",
                "Back"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("" + file.getName());
        builder.setItems(actions, (dialog, which) -> {
            switch (which) {
                case 0: // Lock single
                    onCustomWallpaperSelected(file);
                    break;
                case 1: // Delete this
                    file.delete();
                    onCustomWallpaperDeleted(file);
                    break;
                case 2: // Keep this, delete others
                    File customDir = new File(getFilesDir(), "custom_wallpapers");
                    File[] allFiles = customDir.listFiles((dir, name) -> name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg"));
                    if (allFiles != null) {
                        for (File f : allFiles) {
                            if (!f.getName().equals(file.getName())) {
                                f.delete();
                            }
                        }
                    }
                    onCustomWallpaperSelected(file);
                    Toast.makeText(this, "Other pictures deleted. This one is kept.", Toast.LENGTH_SHORT).show();
                    break;
                case 3: // Back
                    showCustomWallpaperManagerDialog();
                    break;
            }
        });
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    private void showHazeLocationDialog() {
        startActivity(new Intent(this, LocationActivity.class));
    }

    private void showBraviaLocationDialog() {
        startActivity(new Intent(this, LocationActivity.class));
    }

    private void showAsiaCountryDialog() {
        final List<String> regions = AppLocationManager.getAvailableRegions();
        String activeRegion = AppLocationManager.getActiveRegion(this);
        int selectedIndex = 0;
        CharSequence[] items = new CharSequence[regions.size()];
        for (int i = 0; i < regions.size(); i++) {
            String r = regions.get(i);
            items[i] = r;
            if (r.equalsIgnoreCase(activeRegion)) {
                selectedIndex = i;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("Choose Country / Region");
        builder.setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            String chosenRegion = regions.get(which);
            AppLocationManager.setActiveRegion(MainActivity.this, chosenRegion);
            Toast.makeText(MainActivity.this, "Region dipilih: " + chosenRegion, Toast.LENGTH_SHORT).show();
            showStationListDialog(chosenRegion);
        });
        builder.setNegativeButton("Back", (dialog, which) -> {
            dialog.dismiss();
            showHazeLocationDialog();
        });
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    private void showStationListDialog(String region) {
        final List<AppLocationManager.Station> stations = AppLocationManager.getStationsForRegion(region);
        String currentId = AppLocationManager.getStationId(this);
        int selectedIndex = 0;
        CharSequence[] items = new CharSequence[stations.size()];
        for (int i = 0; i < stations.size(); i++) {
            AppLocationManager.Station s = stations.get(i);
            items[i] = s.getDisplayName() + "\n" + s.place;
            if (s.id.equalsIgnoreCase(currentId)) {
                selectedIndex = i;
            }
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_TvHome_GlassDialog);
        builder.setTitle("Choose City / Station (" + region + ")");
        builder.setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
            dialog.dismiss();
            AppLocationManager.Station chosen = stations.get(which);
            AppLocationManager.setStation(MainActivity.this, chosen);
            Toast.makeText(MainActivity.this, "Location: " + chosen.name + " (" + chosen.country + ")", Toast.LENGTH_SHORT).show();
            refreshLocationDependentData();
        });
        builder.setNegativeButton("Back", (dialog, which) -> {
            dialog.dismiss();
            showHazeLocationDialog();
        });
        AlertDialog dialog = builder.create();
        showGlass(dialog);
        styleGlassDialog(dialog);
    }

    private void showPrayerZoneDialog() {
        startActivity(new Intent(this, LocationActivity.class));
    }

    /** Shows an Apple TV-style frosted glass Putrajaya prayer timetable popup. */
    private void showPrayerPopup() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = prayerTimesView != null ? prayerTimesView : findViewById(R.id.leftBlock);
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        // Countdown to the next prayer
        addInputRow(rows, close, R.drawable.ic_azan, "" + PrayerTimes.getNextCountdown(this), -1, () -> {});

        // Header: Active Prayer Zone
        addInputRow(rows, close, R.drawable.ic_settings, "Prayer times " + PrayerTimes.getShortZoneName(this) + " (Change Zone)", TvInputManager.INPUT_STATE_CONNECTED, () -> {
            SoundEffects.playSelect();
            closeMenu(false);
            startActivity(new Intent(MainActivity.this, LocationActivity.class));
        });

        // Date row: Islamic Hijri
        addInputRow(rows, close, R.drawable.ic_input, PrayerTimes.getHijriDate(), -1, () -> {});

        addSeparator(rows);

        // Prayer times list
        java.util.List<PrayerTimes.ScheduleItem> schedule = PrayerTimes.getFullSchedule(this);
        for (PrayerTimes.ScheduleItem item : schedule) {
            addPrayerRow(rows, close, item.name, item.timeFormatted, item.isNext);
        }

        addSeparator(rows);

        // Tetapan Azan shortcut
        addInputRow(rows, close, R.drawable.ic_azan, "Azan Settings & Options", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(false);
            showAzanSettingsDialog();
        });

        // Tutup
        addInputRow(rows, close, R.drawable.ic_close, "Close", -1, () -> {
            SoundEffects.playSelect();
            closeMenu(true);
        });

        int width = (int) (270 * density);
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    private void addPrayerRow(LinearLayout rows, Runnable close, String name, String time, boolean isNext) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(R.drawable.ic_input);
        TextView title = row.findViewById(R.id.title);
        title.setText(name + (isNext ? "  ·  Next" : ""));
        title.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        title.setFontVariationSettings(isNext ? "'wght' 700" : "'wght' 650");
        title.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);

        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        status.setText(time);
        status.setVisibility(View.VISIBLE);
        status.setTypeface(getResources().getFont(R.font.inter), Typeface.BOLD);
        status.setFontVariationSettings(isNext ? "'wght' 700" : "'wght' 600");
        status.setShadowLayer(3 * density, 0, 1 * density, 0xCC000000);

        if (isNext) {
            dot.setVisibility(View.VISIBLE);
        } else {
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            SoundEffects.playSelect();
            close.run();
        });
        rows.addView(row);
    }

    private void checkInactivity() {
        if (!hasWindowFocus() || isFinishing() || isDestroyed()) return;
        long idleMs = android.os.SystemClock.elapsedRealtime() - lastUserInteraction;
        if (idleMs >= TV_SLEEP_TIMEOUT_MS) {
            turnOffTv();
        } else if (idleMs >= SCREENSAVER_TIMEOUT_MS && !screensaverActive) {
            showNightstandScreensaver();
        } else if (screensaverActive && screensaverView != null) {
            updateNightstandScreensaver();
        }
    }

    void showNightstandScreensaver() {
        if (screensaverActive || isFinishing() || isDestroyed()) return;
        if (wallpaperFullscreen) {
            setWallpaperFullscreen(false);
        }
        screensaverActive = true;
        if (screensaverView == null) {
            screensaverView = getLayoutInflater().inflate(R.layout.view_nightstand, root, false);
            screensaverView.setOnClickListener(v -> dismissScreensaver());
            root.addView(screensaverView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        updateNightstandScreensaver();
        applyScreensaverPhoto();
        applySaverSky();

        screensaverView.setVisibility(View.VISIBLE);
        screensaverView.setAlpha(0f);
        screensaverView.animate().alpha(1f).setDuration(500).start();
        android.util.Log.i("TvHome", "Nightstand screensaver activated");
    }

    /** Screensaver options (Settings, StandBy): what to show on it. */
    boolean saverOpt(String key, boolean def) {
        return getSharedPreferences("screensaver_prefs", MODE_PRIVATE).getBoolean(key, def);
    }

    void setSaverOpt(String key, boolean on) {
        getSharedPreferences("screensaver_prefs", MODE_PRIVATE).edit().putBoolean(key, on).apply();
    }

    /** Today's events on the switched-on calendars, up to three lines. */
    private String saverAgendaText() {
        try {
            java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kuala_Lumpur"));
            List<CalendarData.Item> list = CalendarData.items(this, today, today);
            if (list.isEmpty()) return "No events today";
            StringBuilder sb = new StringBuilder("Today");
            int shown = 0;
            for (CalendarData.Item it : list) {
                if (shown == 3) break;
                sb.append('\n');
                if (!it.time.isEmpty()) sb.append(it.time).append("  ");
                sb.append(it.title);
                shown++;
            }
            if (list.size() > shown) sb.append("\n+").append(list.size() - shown).append(" more");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** The animated sky behind the screensaver clock, when switched on. */
    private void applySaverSky() {
        if (!(screensaverView instanceof FrameLayout)) return;
        FrameLayout fl = (FrameLayout) screensaverView;
        WeatherBackgroundView sky = fl.findViewWithTag("saverSky");
        if (!saverOpt("weather", false)) {
            if (sky != null) { sky.stopAnimation(); sky.setVisibility(View.GONE); }
            return;
        }
        if (sky == null) {
            sky = new WeatherBackgroundView(this);
            sky.setTag("saverSky");
            sky.setOverlayMode(false);
            fl.addView(sky, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        applyWeatherText(sky, weatherSkyText(HomeWeather.last(this)));
        if (sky.getCondition() == WeatherBackgroundView.ConditionType.NONE) sky.setVisibility(View.GONE);
    }

    /** Screensaver option: the owner's own uploaded photos behind the clock (set in Settings, StandBy). */
    boolean screensaverPhotos() {
        return getSharedPreferences("screensaver_prefs", MODE_PRIVATE).getBoolean("photos", false);
    }

    void setScreensaverPhotos(boolean on) {
        getSharedPreferences("screensaver_prefs", MODE_PRIVATE).edit().putBoolean("photos", on).apply();
    }

    private void applyScreensaverPhoto() {
        if (!(screensaverView instanceof FrameLayout)) return;
        FrameLayout fl = (FrameLayout) screensaverView;
        android.widget.ImageView pic = fl.findViewWithTag("saverPhoto");
        File[] files = new File(getFilesDir(), "custom_wallpapers").listFiles();
        if (!screensaverPhotos() || saverOpt("weather", false) || files == null || files.length == 0) {
            if (pic != null) pic.setVisibility(View.GONE);
            return;
        }
        final File f = files[new java.util.Random().nextInt(files.length)];
        if (pic == null) {
            pic = new android.widget.ImageView(this);
            pic.setTag("saverPhoto");
            pic.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            pic.setColorFilter(0xFF8A8A8A, android.graphics.PorterDuff.Mode.MULTIPLY);
            fl.addView(pic, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        pic.setVisibility(View.VISIBLE);
        final android.widget.ImageView target = pic;
        wallDecoder.execute(() -> {
            final android.graphics.Bitmap bmp = decodeWallpaper(f);
            handler.post(() -> { if (bmp != null && !isDestroyed()) target.setImageBitmap(bmp); });
        });
    }

    private void updateNightstandScreensaver() {
        if (screensaverView == null) return;
        TextView hijri = screensaverView.findViewById(R.id.nightstandHijri);
        if (hijri != null) hijri.setText("" + PrayerTimes.getHijriDate());

        TextView prayer = screensaverView.findViewById(R.id.nightstandPrayer);
        if (prayer != null) prayer.setText("" + PrayerTimes.getNextPrayerSummary(this));

        // Center locked strictly with no movement
        View content = screensaverView.findViewById(R.id.nightstandContent);
        if (content != null) {
            content.animate().cancel();
            content.setTranslationX(0f);
            content.setTranslationY(0f);
        }

        // iPhone StandBy Wind Down Mode: 10:00 PM (22:00) to 6:00 AM (06:00) -> Red Monochrome
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        boolean isNightMode = saverOpt("red", true) && (hour >= 22 || hour < 6);

        TextView clock = screensaverView.findViewById(R.id.nightstandClock);
        TextView ampm = screensaverView.findViewById(R.id.nightstandAmPm);
        TextView date = screensaverView.findViewById(R.id.nightstandDate);
        TextView hint = screensaverView.findViewById(R.id.nightstandHint);
        TextView wline = screensaverView.findViewById(R.id.nightstandWeather);
        TextView agenda = screensaverView.findViewById(R.id.nightstandAgenda);

        // What the owner ticked in Settings > StandBy.
        if (clock != null) clock.setVisibility(saverOpt("clock", true) ? View.VISIBLE : View.GONE);
        if (ampm != null) ampm.setVisibility(saverOpt("clock", true) ? View.VISIBLE : View.GONE);
        if (date != null) date.setVisibility(saverOpt("date", true) ? View.VISIBLE : View.GONE);
        if (hijri != null) hijri.setVisibility(saverOpt("hijri", true) ? View.VISIBLE : View.GONE);
        if (prayer != null) prayer.setVisibility(saverOpt("prayer", true) ? View.VISIBLE : View.GONE);
        if (wline != null) {
            String w = "";
            if (saverOpt("temp", false)) {
                HomeWeather.Line ln = HomeWeather.last(this);
                if (ln != null && ln.weather != null) {
                    w = ln.weather;
                    int cut = w.indexOf('\u00B7');
                    if (cut > 0) w = w.substring(0, cut).trim();
                }
            }
            wline.setText(w);
            wline.setVisibility(w.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (agenda != null) {
            String t = saverOpt("agenda", false) ? saverAgendaText() : "";
            agenda.setText(t);
            agenda.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
        }

        if (isNightMode) {
            int red = 0xFFE0762F;
            if (clock != null) clock.setTextColor(red);
            if (ampm != null) ampm.setTextColor(0xCCE0762F);
            if (date != null) date.setTextColor(0xE6E0762F);
            if (hijri != null) hijri.setTextColor(0xE6E0762F);
            if (prayer != null) {
                prayer.setTextColor(red);
                android.graphics.drawable.GradientDrawable redPill = new android.graphics.drawable.GradientDrawable();
                redPill.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
                redPill.setColor(0x26E0762F);
                redPill.setStroke((int) (1 * density), 0x59E0762F);
                redPill.setCornerRadius(21 * density);
                prayer.setBackground(redPill);
            }
            if (hint != null) hint.setTextColor(0x55E0762F);
            if (wline != null) wline.setTextColor(0xE6E0762F);
            if (agenda != null) agenda.setTextColor(0xE6E0762F);
        } else {
            if (wline != null) wline.setTextColor(0xE6F5E1BC);
            if (agenda != null) agenda.setTextColor(0xE6F5E1BC);
            if (clock != null) clock.setTextColor(0xFFFFC66B);
            if (ampm != null) ampm.setTextColor(0xCCFFC66B);
            if (date != null) date.setTextColor(0xE6F5E1BC);
            if (hijri != null) hijri.setTextColor(0xD9E8B87A);
            if (prayer != null) {
                prayer.setTextColor(0xFFFFC66B);
                prayer.setBackgroundResource(R.drawable.pill_amber);
            }
            if (hint != null) hint.setTextColor(0x4DFFC66B);
        }
    }

    private void dismissScreensaver() {
        if (!screensaverActive || screensaverView == null) return;
        screensaverActive = false;
        lastUserInteraction = android.os.SystemClock.elapsedRealtime();
        screensaverView.animate().alpha(0f).setDuration(350).withEndAction(() -> {
            if (!screensaverActive && screensaverView != null) {
                screensaverView.setVisibility(View.GONE);
                if (screensaverView instanceof FrameLayout) {
                    WeatherBackgroundView sky = ((FrameLayout) screensaverView).findViewWithTag("saverSky");
                    if (sky != null) sky.stopAnimation();
                }
            }
        }).start();
        android.util.Log.i("TvHome", "Nightstand screensaver dismissed");
    }

    private void turnOffTv() {
        android.util.Log.i("TvHome", "TV idle: 10m screensaver + 2h timeout reached, putting TV to sleep");
        boolean ok = HomeKeyService.sleepScreen();
        if (!ok) {
            try {
                Runtime.getRuntime().exec("input keyevent 223");
            } catch (Exception e) {
                android.util.Log.w("TvHome", "Failed to send sleep keyevent: " + e);
            }
        }
    }

    private void ensureSystemScreensaverAndSleepSettings() {
        new Thread(() -> {
            try {
                android.content.ContentResolver cr = getContentResolver();
                // 10 minutes idle -> screensaver (600,000 ms)
                android.provider.Settings.System.putInt(cr, "screen_off_timeout", 600000);
                // 2 hours after screensaver -> TV off (10m + 2h = 7,800,000 ms)
                android.provider.Settings.Secure.putInt(cr, "sleep_timeout", 7800000);
                android.provider.Settings.Secure.putInt(cr, "screensaver_enabled", 1);
                android.provider.Settings.Secure.putString(cr, "screensaver_components", "com.gathin.tvhome/.ClockDream");

                // Auto-enable HomeKeyService for system actions (restart, sleep, force-stop)
                String enabledServices = android.provider.Settings.Secure.getString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                String myService = getPackageName() + "/" + HomeKeyService.class.getName();
                if (enabledServices == null || !enabledServices.contains(myService)) {
                    String newServices = enabledServices == null || enabledServices.isEmpty() ? myService : enabledServices + ":" + myService;
                    android.provider.Settings.Secure.putString(cr, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices);
                    android.provider.Settings.Secure.putInt(cr, android.provider.Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                }

                // Auto-enable MediaNotificationListener for Now Playing media widget
                String enabledListeners = android.provider.Settings.Secure.getString(cr, "enabled_notification_listeners");
                String myListener = getPackageName() + "/" + MediaNotificationListener.class.getName();
                if (enabledListeners == null || !enabledListeners.contains(myListener)) {
                    String newListeners = enabledListeners == null || enabledListeners.isEmpty() ? myListener : enabledListeners + ":" + myListener;
                    try {
                        android.provider.Settings.Secure.putString(cr, "enabled_notification_listeners", newListeners);
                    } catch (Exception ignored) {}
                }
                try {
                    Runtime.getRuntime().exec(new String[]{"cmd", "notification", "allow_listener", myListener, "0"});
                } catch (Exception ignored) {}
            } catch (Exception ignored) {
            }
        }, "system-timeout-sync").start();
    }


    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged.
     */
    private Intent systemIntent(String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(getPackageManager()) != null) return i;
        return new Intent(standardAction);   // nothing found: let open() show the can't-open toast
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        SoundEffects.playSelect();
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** The glass menu while one is open (Inputs, or an app's), and what it opened from. */
    private View openMenu;
    private View menuAnchor;
    private final int[] menuAt = new int[2], menuGliderAt = new int[2];

    /**
     * Menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. It is drawn in
     * Home's own window rather than a popup window of its own: a new window takes about a quarter of
     * a second to reach the screen on this TV, so the selection could not glide out of the pill into
     * it. Here Home's own focus shape glides from the pill to the first row, the way it glides into
     * the dock's tray, and the menu's glass paints it over itself.
     */
    private void showInputs() {
        if (openMenu != null) return;
        SoundEffects.playSelect();
        View anchor = btnInputs;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, close, hdmi ? R.drawable.ic_hdmi : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, close, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null && config.showLiveTv) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, close, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    /**
     * Shows a glass menu in Home's own window, under {@code anchor} or, with {@code above}, over it:
     * Home's focus shape glides from the anchor to the first row, the menu's glass paints it over
     * itself, and the glass and rows fade in around it. Up from the first row (Down from the last when
     * the menu is above), or Back, closes it and the shape glides back to the anchor.
     */
    private void showMenu(FrameLayout panel, LinearLayout rows, View anchor, int width, boolean above) {
        GlassDrawable glass = new GlassDrawable(panel, 20 * density, density, glider, 0.5f, true);
        panel.setBackground(glass);
        Backdrop.watch(panel);
        glider.addCompanion(panel);
        anchor.getLocationInWindow(menuAt);
        root.getLocationInWindow(menuGliderAt);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.max(0, Math.min(menuAt[0] - menuGliderAt[0], root.getWidth() - width));
        int gap = (int) (10 * density);
        if (above) {
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            lp.topMargin = Math.max(0, menuAt[1] - menuGliderAt[1] - gap - panel.getMeasuredHeight());
        } else {
            lp.topMargin = menuAt[1] - menuGliderAt[1] + anchor.getHeight() + gap;
        }
        root.addView(panel, lp);
        openMenu = panel;
        menuAnchor = anchor;

        // Focus stays in the menu until it closes. Leaving it towards the anchor closes it, and the
        // selection glides back into what it opened from.
        View first = null, last = null;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) continue;
            if (first == null) first = row;
            last = row;
            row.setOnFocusChangeListener((v, has) -> {
                if (has) glideToMenuRow(panel, rows, v);
            });
        }
        final View top = first, bottom = last;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) continue;
            row.setOnKeyListener((v, keyCode, event) -> {
                boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (v != top) return false;
                        if (down && !above) closeMenu(true);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (v != bottom) return false;
                        if (down && above) closeMenu(true);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return true;
                    case KeyEvent.KEYCODE_BACK:
                        if (down) closeMenu(true);
                        return true;
                    default:
                        return false;
                }
            });
        }

        // The glass and the rows fade in around the selection as it arrives.
        glass.setAlpha(0);
        rows.setAlpha(0f);
        final float slide = (above ? 8 : -8) * density;
        rows.setTranslationY(slide);
        android.animation.ValueAnimator in = android.animation.ValueAnimator.ofFloat(0f, 1f);
        in.setDuration(220);
        in.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        in.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            glass.setAlpha(Math.round(255 * f));
            rows.setAlpha(f);
            rows.setTranslationY(slide * (1f - f));
        });
        in.start();
        if (top != null) {
            top.requestFocus();
            panel.post(() -> {
                if (openMenu == panel && top != null) {
                    top.requestFocus();
                }
            });
        }
    }

    /**
     * Glides Home's focus shape onto a row of the Inputs menu. From layout positions, not on-screen
     * ones: the rows slide into place as the menu opens, and the shape must land where the row ends
     * up, not where it was when focus arrived. The first row takes focus before the menu has been
     * laid out, so then it waits for the layout.
     */
    private void glideToMenuRow(View panel, View rows, View row) {
        SoundEffects.playNav();
        if (!panel.isLaidOut() || row.getHeight() == 0) {
            panel.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    panel.removeOnLayoutChangeListener(this);
                    // Still inside the layout pass here, where the panel does not count as laid
                    // out yet: glide once the pass is over.
                    panel.post(() -> {
                        if (row.hasFocus()) glideToMenuRow(panel, rows, row);
                    });
                }
            });
            return;
        }
        float l = panel.getLeft() + rows.getLeft() + row.getLeft() - glider.getLeft();
        float t = panel.getTop() + rows.getTop() + row.getTop() - glider.getTop();
        gliderOnPill = true;
        glider.setStyle(Glider.DARK);
        glider.moveTo(l, t, l + row.getWidth(), t + row.getHeight(), 13 * density);
    }

    /** Closes the open menu; with {@code toAnchor}, focus and the selection go back to what it opened from. */
    private void closeMenu(boolean toAnchor) {
        View panel = openMenu;
        if (panel == null) return;
        SoundEffects.playNav();
        openMenu = null;
        if (toAnchor && menuAnchor != null) menuAnchor.requestFocus();
        menuAnchor = null;
        panel.animate().alpha(0f).setDuration(reducedMotion ? 80 : 150).withEndAction(() -> {
            glider.removeCompanion(panel);
            root.removeView(panel);
        }).start();
    }

    private boolean inMenu(View v) {
        for (View p = v; p != null && openMenu != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == openMenu) return true;
        }
        return false;
    }

    private PopupWindow glassPopup(View panel, int width) {
        // The same glass as the pills and the tray: what is behind it, softened, with a shining edge.
        panel.setBackground(new GlassDrawable(panel, 20 * density, density, null, 0.5f));
        Backdrop.watch(panel);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, Runnable close, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            close.run();
            action.run();
        });
        rows.addView(row);
    }

    // ---- module cards ----------------------------------------------------------------------------

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        showGlass(dialog);
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }
}

package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/**
 * The azan, as a floating glass card at the top of the screen over whatever was on (the app or Home stays
 * in view behind it, blurred through the card where the TV can blur). Video and music pause because the
 * azan takes the sound (audio focus). When it ends, or on Back, it closes and the app
 * underneath is where it was.
 */
public class AzanActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private float density;
    private TextView hint;
    /** The card stays up this long after the sound ends, so "Already prayed" / "Remind me" can be pressed. */
    private static final long AFTER_MS = 90_000L;
    private int prayerIdx;
    /** True once the card has been on screen: until then a stop is the screensaver going, not the owner. */
    private boolean shown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Over the screensaver too: turn the screen on and come in front of it.
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        density = getResources().getDisplayMetrics().density;
        int prayer = getIntent().getIntExtra("prayer", 4);
        Typeface outfit = getResources().getFont(R.font.inter);
        Typeface amiri = getResources().getFont(R.font.amiri_quran);

        // A floating card at the top of the screen; the app or Home behind it stays in view.
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.y = dp(40);
        w.setAttributes(lp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(30), dp(20), dp(34), dp(20));
        GradientDrawable glass = new GradientDrawable();
        // Safe default dark glass background tint: readable over bright content even without window blur
        glass.setColor(0xE0141C2C);
        glass.setCornerRadius(dp(28));
        glass.setStroke(Math.max(1, dp(1.5f)), 0x59FFFFFF);
        card.setBackground(glass);
        card.setClipToOutline(true);
        card.setMinimumWidth(dp(640));
        card.setElevation(dp(16));

        TextView takbir = text("ٱللَّهُ أَكْبَرُ", amiri, 40, 0xFFF5D489, 400);
        card.addView(takbir);
        View line = new View(this);
        line.setBackgroundColor(0x33FFFFFF);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Math.max(1, dp(1)), dp(70));
        llp.leftMargin = dp(26);
        llp.rightMargin = dp(26);
        card.addView(line, llp);

        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        String town = Location.prefs(this).getString("now_town", "");
        String azanWord = AppLanguage.strAzanPopupTitle(this);
        TextView label = text(town.isEmpty() ? azanWord : azanWord + "  ·  " + town.toUpperCase(java.util.Locale.ROOT), outfit, 12, 0xB3FFFFFF, 600);
        label.setLetterSpacing(0.22f);
        label.setGravity(Gravity.START);
        words.addView(label);
        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.BOTTOM);
        nameRow.setPadding(0, dp(2), 0, dp(2));

        String[] pNames = AppLanguage.getPrayerNames(this);
        int validPrayerIdx = Math.max(0, Math.min(prayer, pNames.length - 1));
        TextView name = text(pNames[validPrayerIdx], outfit, 28, 0xFFFFFFFF, 500);
        name.setSingleLine(true);
        nameRow.addView(name);
        TextView time = text(when, outfit, 20, 0xCCFFFFFF, 300);
        time.setSingleLine(true);
        time.setPadding(dp(14), 0, 0, dp(3));
        nameRow.addView(time);
        words.addView(nameRow);
        hint = text(AppLanguage.strAzanPopupHint(this), outfit, 12, 0x99FFFFFF, 400);
        hint.setGravity(Gravity.START);
        words.addView(hint);
        prayerIdx = validPrayerIdx;
        final boolean reminder = getIntent().getBooleanExtra("reminder", false);
        if (reminder) {
            label.setText("REMINDER");
            hint.setText("Have you prayed " + pNames[validPrayerIdx] + "?");
        }
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(12), 0, 0);
        TextView done = button("Already prayed", outfit, true);
        done.setOnClickListener(v -> {
            Azan.cancelReminder(this);
            finish();
        });
        TextView later = button("Remind in 10 minutes", outfit, false);
        later.setOnClickListener(v -> {
            Azan.remindLater(this, prayerIdx, 10);
            android.widget.Toast.makeText(this, "I will remind you in 10 minutes", android.widget.Toast.LENGTH_SHORT).show();
            finish();
        });
        done.setGravity(Gravity.CENTER);
        later.setGravity(Gravity.CENTER);
        buttons.addView(done, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, -2, 1.3f);
        blp.leftMargin = dp(10);
        buttons.addView(later, blp);
        words.addView(buttons);
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.setFocusable(false);
        done.post(done::requestFocus);

        // CRITICAL FIX FOR ISSUE #5:
        // Must call setContentView(card) FIRST so the Window creates and attaches its DecorView.
        // On Android 14 (Chromecast Sabrina, etc.), calling setBackgroundBlurRadius before
        // setContentView dereferences a null DecorView inside PhoneWindow and throws NullPointerException.
        setContentView(card);

        if (Build.VERSION.SDK_INT >= 31) {
            try {
                WindowManager wm = getSystemService(WindowManager.class);
                if (wm != null && wm.isCrossWindowBlurEnabled() && w.getDecorView() != null) {
                    w.setBackgroundBlurRadius(dp(30));
                    glass.setColor(0xB3101826);
                }
            } catch (Throwable t) {
                // Graceful fallback: keep solid frosted glass tint without crashing
                android.util.Log.w("TvHome.Azan", "Window blur unavailable or failed: " + t.getMessage());
            }
        }

        if (reminder) {
            handler.postDelayed(this::finish, AFTER_MS);
            return;
        }
        String soundChoice = Azan.getSoundChoice(this);
        if (Azan.SOUND_SILENT.equals(soundChoice)) {
            hint.setText(AppLanguage.strAzanPopupSilentHint(this));
            handler.postDelayed(this::finish, AFTER_MS);
        } else if (Azan.SOUND_CHIME.equals(soundChoice)) {
            hint.setText(AppLanguage.strAzanPopupChimeHint(this));
            playChime();
        } else {
            File file = Azan.recording(this, soundChoice);
            if (file == null) {
                hint.setText(AppLanguage.strAzanPopupDownloadingHint(this, Azan.soundLabel(soundChoice)));
                Azan.fetch(this, soundChoice);
                playChime();
                handler.postDelayed(this::finish, AFTER_MS);
                return;
            }
            play(file, soundChoice);
        }
    }

    /** A pill button: white when it is the one to press first, glass otherwise; lights up when selected. */
    private TextView button(String label, Typeface font, boolean primary) {
        final TextView b = text(label, font, 14, primary ? 0xFF000000 : 0xFFFFFFFF, 600);
        b.setSingleLine(true);
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        b.setFocusable(true);
        b.setClickable(true);
        setButtonLook(b, primary, false);
        b.setOnFocusChangeListener((v, has) -> setButtonLook(b, primary, has));
        return b;
    }

    private void setButtonLook(TextView b, boolean primary, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(20));
        g.setColor(focused ? 0xFFFFFFFF : primary ? 0xE6FFFFFF : 0x33FFFFFF);
        g.setStroke(Math.max(1, dp(1)), focused ? 0xFFFFFFFF : 0x66FFFFFF);
        b.setTextColor(focused || primary ? 0xFF000000 : 0xFFFFFFFF);
        b.setBackground(g);
    }

    private void playChime() {
        audio = getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        audio.requestAudioFocus(focus);
        try {
            player = MediaPlayer.create(this, R.raw.ps5_select);
            if (player != null) {
                player.setOnCompletionListener(mp -> handler.postDelayed(this::finish, AFTER_MS));
                player.start();
            } else {
                handler.postDelayed(this::finish, AFTER_MS);
            }
        } catch (Exception e) {
            handler.postDelayed(this::finish, AFTER_MS);
        }
    }

    private void play(File file, String soundChoice) {
        audio = getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        // Transient focus: Spotify and video players pause now and carry on when the azan is over.
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        audio.requestAudioFocus(focus);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(attrs);
            player.setDataSource(file.getPath());
            player.setOnCompletionListener(mp -> handler.postDelayed(this::finish, AFTER_MS));
            player.setOnErrorListener((mp, what, extra) -> {
                finish();
                return true;
            });
            player.prepare();
            if (Azan.SOUND_KLCC.equals(soundChoice) && file.getName().contains("klcc")) {
                player.seekTo(Azan.KLCC_START_MS);
            }
            player.start();
        } catch (Exception e) {
            finish();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
                || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        shown = true;
    }

    /**
     * Left in any way (Home, another app): the azan stops with it. Not before it has been shown, though:
     * started while the screensaver was ending, it is stopped once on the way in, and must keep playing.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (shown && !isChangingConfigurations()) finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) {
            player.release();
            player = null;
        }
        if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
    }

    private TextView text(String s, Typeface font, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(font);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        if (font != null) t.setFontVariationSettings("'wght' " + weight);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}

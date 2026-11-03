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
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;

/**
 * The azan card: a wide panel along the bottom of the screen over whatever was on, with the prayer's
 * name large on the left, the two answers (prayed / remind me) on the right and a thin line underneath
 * that fills as the azan plays. The chosen azan plays whole or, if the owner picked the short version,
 * fades out after a minute. Video and music pause because the azan takes the sound (audio focus).
 * Back or OK closes it and the app underneath is where it was.
 */
public class AzanActivity extends Activity {
    private static final int AMBER = 0xFFFFC66B;
    private static final int CREAM = 0xFFF5E1BC;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private float density;
    private TextView hint;
    private ProgressBar progress;
    /** The card stays up this long after the sound ends, so "Prayed" / "Remind me" can be pressed. */
    private static final long AFTER_MS = 90_000L;
    private int prayerIdx;
    /** True once the card has been on screen: until then a stop is the screensaver going, not the owner. */
    private boolean shown;
    private int startAt;
    private int playLength;

    private final Runnable tickProgress = new Runnable() {
        @Override
        public void run() {
            if (player == null || progress == null) return;
            try {
                int pos = player.getCurrentPosition() - startAt;
                progress.setProgress(Math.max(0, Math.min(playLength, pos)));
                handler.postDelayed(this, 400);
            } catch (Exception ignored) { }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Over the screensaver too: turn the screen on and come in front of it.
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        density = getResources().getDisplayMetrics().density;
        int prayer = getIntent().getIntExtra("prayer", 4);
        Typeface body = getResources().getFont(R.font.instrument_sans);
        Typeface display = getResources().getFont(R.font.outfit);

        // A panel along the bottom; the app or Home behind it stays in view.
        Window w = getWindow();
        w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        w.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams lp = w.getAttributes();
        lp.y = dp(36);
        w.setAttributes(lp);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF01A1410);
        bg.setCornerRadius(dp(10));
        bg.setStroke(Math.max(1, dp(1)), 0x55FFC66B);
        panel.setBackground(bg);
        panel.setClipToOutline(true);
        panel.setElevation(dp(14));
        panel.setMinimumWidth(dp(760));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(28), dp(20), dp(24), dp(16));

        // Left: what time it is for.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        String town = Location.prefs(this).getString("now_town", "");
        String head = AppLanguage.strAzanPopupTitle(this);
        TextView label = text(town.isEmpty() ? head : head + "  ·  " + town.toUpperCase(java.util.Locale.ROOT), body, 12, AMBER);
        label.setLetterSpacing(0.24f);
        left.addView(label);

        String[] pNames = AppLanguage.getPrayerNames(this);
        int validPrayerIdx = Math.max(0, Math.min(prayer, pNames.length - 1));
        prayerIdx = validPrayerIdx;
        TextView name = text(pNames[validPrayerIdx], display, 46, CREAM);
        name.setSingleLine(true);
        left.addView(name);

        String when = DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date());
        TextView time = text(when, body, 18, 0xB3F5E1BC);
        left.addView(time);
        row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Right: the two answers, stacked.
        final boolean reminder = getIntent().getBooleanExtra("reminder", false);
        LinearLayout answers = new LinearLayout(this);
        answers.setOrientation(LinearLayout.VERTICAL);
        TextView done = button("I have prayed", body, true);
        done.setOnClickListener(v -> {
            Azan.cancelReminder(this);
            finish();
        });
        TextView later = button("Remind me in 10 min", body, false);
        later.setOnClickListener(v -> {
            Azan.remindLater(this, prayerIdx, 10);
            android.widget.Toast.makeText(this, "I will remind you in 10 minutes", android.widget.Toast.LENGTH_SHORT).show();
            finish();
        });
        answers.addView(done, new LinearLayout.LayoutParams(dp(210), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(210), ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        answers.addView(later, llp);
        row.addView(answers);
        panel.addView(row);

        // Under it: the line that fills as the azan plays, and a short hint.
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(1);
        progress.setProgress(0);
        progress.setProgressDrawable(progressLook());
        panel.addView(progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        hint = text(AppLanguage.strAzanPopupHint(this), body, 12, 0x99F5E1BC);
        hint.setPadding(dp(28), dp(8), dp(28), dp(10));
        hint.setGravity(Gravity.START);
        panel.addView(hint);

        if (reminder) {
            label.setText("REMINDER");
            hint.setText("Have you prayed " + pNames[validPrayerIdx] + "?");
        }
        panel.setFocusable(false);
        done.post(done::requestFocus);

        // setContentView first, so the window has its decor view before any blur is asked for
        // (on some Android 14 boxes the other order throws a NullPointerException).
        setContentView(panel);

        if (Build.VERSION.SDK_INT >= 31) {
            try {
                WindowManager wm = getSystemService(WindowManager.class);
                if (wm != null && wm.isCrossWindowBlurEnabled() && w.getDecorView() != null) {
                    w.setBackgroundBlurRadius(dp(26));
                    bg.setColor(0xCC1A1410);
                }
            } catch (Throwable t) {
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

    private android.graphics.drawable.Drawable progressLook() {
        GradientDrawable track = new GradientDrawable();
        track.setColor(0x22FFC66B);
        GradientDrawable fill = new GradientDrawable();
        fill.setColor(AMBER);
        android.graphics.drawable.ClipDrawable clip = new android.graphics.drawable.ClipDrawable(
                fill, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL);
        android.graphics.drawable.LayerDrawable layers = new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{track, clip});
        layers.setId(0, android.R.id.background);
        layers.setId(1, android.R.id.progress);
        return layers;
    }

    /** A squared button: amber outline, filled amber when it has focus. */
    private TextView button(String label, Typeface font, boolean primary) {
        final TextView b = text(label, font, 14, CREAM);
        b.setSingleLine(true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(16), dp(10), dp(16), dp(10));
        b.setFocusable(true);
        b.setClickable(true);
        setButtonLook(b, primary, false);
        b.setOnFocusChangeListener((v, has) -> setButtonLook(b, primary, has));
        return b;
    }

    private void setButtonLook(TextView b, boolean primary, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(6));
        g.setColor(focused ? AMBER : primary ? 0x33FFC66B : 0x00000000);
        g.setStroke(Math.max(1, dp(1)), focused ? AMBER : 0x88FFC66B);
        b.setTextColor(focused ? 0xFF1A1410 : CREAM);
        b.setBackground(g);
    }

    private void takeFocus(int contentType, int gain) {
        audio = getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(contentType)
                .build();
        focus = new AudioFocusRequest.Builder(gain)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(change -> { })
                .build();
        audio.requestAudioFocus(focus);
    }

    private void playChime() {
        takeFocus(AudioAttributes.CONTENT_TYPE_SONIFICATION, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        try {
            player = MediaPlayer.create(this, R.raw.azan_chime);
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
        // Transient focus: Spotify and video players pause now and carry on when the azan is over.
        takeFocus(AudioAttributes.CONTENT_TYPE_SPEECH, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(file.getPath());
            player.setOnCompletionListener(mp -> handler.postDelayed(this::finish, AFTER_MS));
            player.setOnErrorListener((mp, what, extra) -> {
                finish();
                return true;
            });
            player.prepare();
            startAt = (Azan.SOUND_KLCC.equals(soundChoice) && file.getName().contains("klcc")) ? Azan.KLCC_START_MS : 0;
            if (startAt > 0) player.seekTo(startAt);
            int whole = Math.max(1, player.getDuration() - startAt);
            final boolean shortVersion = Azan.isShort(this);
            playLength = shortVersion ? Math.min(whole, Azan.SHORT_MS) : whole;
            progress.setMax(playLength);
            player.start();
            handler.post(tickProgress);
            if (shortVersion && whole > Azan.SHORT_MS) {
                handler.postDelayed(this::fadeOut, Azan.SHORT_MS - 3000);
            }
        } catch (Exception e) {
            finish();
        }
    }

    /** The short version: three seconds of fading, then the card waits for an answer as usual. */
    private void fadeOut() {
        final int steps = 12;
        for (int i = 1; i <= steps; i++) {
            final float vol = 1f - i / (float) steps;
            handler.postDelayed(() -> {
                if (player != null) {
                    try { player.setVolume(vol, vol); } catch (Exception ignored) { }
                }
            }, i * 250L);
        }
        handler.postDelayed(() -> {
            if (player != null) {
                try { player.stop(); } catch (Exception ignored) { }
                player.release();
                player = null;
            }
            if (progress != null) progress.setProgress(progress.getMax());
            if (audio != null && focus != null) audio.abandonAudioFocusRequest(focus);
            handler.postDelayed(this::finish, AFTER_MS);
        }, steps * 250L + 100);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) {
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

    private TextView text(String s, Typeface font, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        if (font != null) t.setTypeface(font);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}

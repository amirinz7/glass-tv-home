package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Teaches Home what a remote's own app button does:
 * adb shell am broadcast -a com.gathin.tvhome.MAP_KEY -p com.gathin.tvhome --ei code 1234 --es pkg com.netflix.ninja
 * An empty pkg forgets the key. The key code is shown in a message when such a button is pressed.
 */
public class KeyMapReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !"com.gathin.tvhome.MAP_KEY".equals(intent.getAction())) return;
        int code = intent.getIntExtra("code", -1);
        if (code < 0) return;
        String pkg = intent.getStringExtra("pkg");
        android.content.SharedPreferences.Editor ed = ctx.getSharedPreferences("remote_keys", Context.MODE_PRIVATE).edit();
        if (pkg == null || pkg.trim().isEmpty()) ed.remove("k" + code); else ed.putString("k" + code, pkg.trim());
        ed.apply();
    }

    /** This TCL remote's own app buttons (key numbers the TV reported), used unless a key was mapped on purpose. */
    private static String builtIn(int code) {
        switch (code) {
            case 4062: return "com.netflix.ninja";
            case 4065: return "com.google.android.youtube.tv";
            case 4099: return "com.amazon.amazonvideo.livingroom";
            default: return null;
        }
    }

    static String packageFor(Context ctx, int code) {
        android.content.SharedPreferences p = ctx.getSharedPreferences("remote_keys", Context.MODE_PRIVATE);
        if (p.contains("k" + code)) {
            String v = p.getString("k" + code, null);
            return v == null || v.isEmpty() ? null : v;
        }
        return builtIn(code);
    }
}

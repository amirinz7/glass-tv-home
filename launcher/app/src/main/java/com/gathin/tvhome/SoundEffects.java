package com.gathin.tvhome;

import android.content.Context;

/**
 * The home screen makes no clicking or chiming sounds of its own. The methods stay, as no-ops,
 * so every screen that used to call them keeps compiling.
 */
final class SoundEffects {
    private SoundEffects() {
    }

    static void init(Context context) { }

    static void release() { }

    static void playNav() { }

    static void playSelect() { }
}

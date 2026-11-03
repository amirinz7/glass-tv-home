package com.gathin.tvhome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Links or unlinks the owner's Google Calendar from a computer (typing a long address with the remote is slow):
 * adb shell am broadcast -a com.gathin.tvhome.SET_CALENDAR -p com.gathin.tvhome --es url "https://calendar.google.com/calendar/ical/.../basic.ics"
 * adb shell am broadcast -a com.gathin.tvhome.SET_CALENDAR -p com.gathin.tvhome --es url ""    (unlink)
 */
public class CalendarLinkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!"com.gathin.tvhome.SET_CALENDAR".equals(intent.getAction())) return;
        if (intent.hasExtra("url")) {
            boolean ok = CalendarData.setMyUrl(ctx, intent.getStringExtra("url"));
            android.util.Log.i("TvHome.Calendar", "SET_CALENDAR " + (ok ? "saved" : "rejected"));
            if (ok) CalendarData.refresh(ctx, true, null);
        }
        if (intent.hasExtra("on")) CalendarData.setMyOn(ctx, intent.getBooleanExtra("on", true));
    }
}

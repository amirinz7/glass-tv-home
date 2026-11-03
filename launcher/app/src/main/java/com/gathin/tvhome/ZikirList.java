package com.gathin.tvhome;

/** Zikir shown in turn with the daily doa above the prayer times. Text stays in Malay on purpose. */
final class ZikirList {
    static final class Zikir {
        final String arabic, meaning, count, rumi;
        Zikir(String arabic, String rumi, String meaning, String count) {
            this.arabic = arabic; this.rumi = rumi; this.meaning = meaning; this.count = count;
        }
    }

    static final Zikir[] ALL = {
            new Zikir("سُبْحَانَ اللَّهِ", "Subhanallah", "Maha Suci Allah", "33x"),
            new Zikir("الْحَمْدُ لِلَّهِ", "Alhamdulillah", "Segala puji bagi Allah", "33x"),
            new Zikir("اللَّهُ أَكْبَرُ", "Allahu Akbar", "Allah Maha Besar", "33x"),
            new Zikir("لَا إِلَٰهَ إِلَّا اللَّهُ", "La ilaha illallah", "Tiada Tuhan melainkan Allah", "100x"),
            new Zikir("أَسْتَغْفِرُ اللَّهَ", "Astaghfirullah", "Aku memohon ampun kepada Allah", "100x"),
            new Zikir("سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "Subhanallahi wa bihamdih", "Maha Suci Allah dan segala puji bagi-Nya", "100x"),
            new Zikir("سُبْحَانَ اللَّهِ الْعَظِيمِ", "Subhanallahil 'azim", "Maha Suci Allah Yang Maha Agung", "100x"),
            new Zikir("لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "La haula wa la quwwata illa billah", "Tiada daya dan kekuatan melainkan dengan Allah", "100x"),
            new Zikir("اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ", "Allahumma salli 'ala Muhammad", "Ya Allah, berselawatlah ke atas Nabi Muhammad", "100x"),
            new Zikir("حَسْبُنَا اللَّهُ وَنِعْمَ الْوَكِيلُ", "Hasbunallahu wa ni'mal wakil", "Cukuplah Allah bagi kami, Dialah sebaik-baik pelindung", "7x"),
            new Zikir("رَبِّ اغْفِرْ لِي وَتُبْ عَلَيَّ", "Rabbighfir li wa tub 'alayya", "Tuhanku, ampunilah aku dan terimalah taubatku", "100x"),
            new Zikir("يَا حَيُّ يَا قَيُّومُ", "Ya Hayyu ya Qayyum", "Wahai Yang Maha Hidup, wahai Yang Maha Berdiri Sendiri", "33x"),
            new Zikir("لَا إِلَٰهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ", "La ilaha illa anta subhanaka inni kuntu minazh zhalimin", "Tiada Tuhan melainkan Engkau, Maha Suci Engkau, sesungguhnya aku termasuk orang yang zalim", "7x"),
            new Zikir("اللَّهُمَّ أَعِنِّي عَلَى ذِكْرِكَ وَشُكْرِكَ وَحُسْنِ عِبَادَتِكَ", "Allahumma a'inni 'ala zikrika wa syukrika wa husni 'ibadatik", "Ya Allah, bantulah aku untuk mengingati-Mu, bersyukur kepada-Mu dan beribadah dengan baik kepada-Mu", "1x"),
    };

    private static android.content.SharedPreferences prefs(android.content.Context c) {
        return c.getSharedPreferences("islamic_rotation", android.content.Context.MODE_PRIVATE);
    }

    /** Which of ayat (0), doa harian (1) and zikir (2) take part in the box; all three until chosen otherwise. */
    static boolean enabled(android.content.Context c, int kind) {
        String[] keys = {"ayat", "doa", "zikir"};
        return prefs(c).getBoolean(keys[kind], true);
    }

    static void setEnabled(android.content.Context c, int kind, boolean on) {
        String[] keys = {"ayat", "doa", "zikir"};
        prefs(c).edit().putBoolean(keys[kind], on).apply();
    }

    /** Seconds each one stays: 30 and up. */
    static int seconds(android.content.Context c) {
        return Math.max(30, prefs(c).getInt("secs", 30));
    }

    static void setSeconds(android.content.Context c, int secs) {
        prefs(c).edit().putInt("secs", Math.max(30, secs)).apply();
    }

    static long intervalMs(android.content.Context c) {
        return seconds(c) * 1000L;
    }

    /** The one on show now: 0 = ayat, 1 = daily doa, 2 = zikir, moving on every few seconds among those chosen. */
    static int turn(android.content.Context c) {
        int[] on = new int[3];
        int n = 0;
        for (int k = 0; k < 3; k++) if (enabled(c, k)) on[n++] = k;
        if (n == 0) return 0;
        long slot = System.currentTimeMillis() / intervalMs(c);
        return on[(int) (slot % n)];
    }

    static Zikir current() {
        long slot = System.currentTimeMillis() / 90_000L;
        return ALL[(int) (slot % ALL.length)];
    }

    private ZikirList() {}
}

package com.gathin.tvhome;

/** Everyday doa, shown between the ayat and the zikir. Arabic, rumi and a Malay meaning; the name of the doa is in the label. */
final class DoaList {
    static final ZikirList.Zikir[] ALL = {
            new ZikirList.Zikir("اللَّهُمَّ بَارِكْ لَنَا فِيمَا رَزَقْتَنَا وَقِنَا عَذَابَ النَّارِ", "Allahumma barik lana fima razaqtana wa qina 'azaban nar", "Ya Allah, berkatilah rezeki yang Engkau berikan kepada kami dan peliharalah kami daripada azab neraka", "Sebelum makan"),
            new ZikirList.Zikir("الْحَمْدُ لِلَّهِ الَّذِي أَطْعَمَنَا وَسَقَانَا وَجَعَلَنَا مُسْلِمِينَ", "Alhamdulillahilladzi ath'amana wa saqana wa ja'alana muslimin", "Segala puji bagi Allah yang memberi kami makan dan minum serta menjadikan kami orang Islam", "Selepas makan"),
            new ZikirList.Zikir("الْحَمْدُ لِلَّهِ الَّذِي أَحْيَانَا بَعْدَ مَا أَمَاتَنَا وَإِلَيْهِ النُّشُورُ", "Alhamdulillahilladzi ahyana ba'da ma amatana wa ilaihin nusyur", "Segala puji bagi Allah yang menghidupkan kami setelah mematikan kami, dan kepada-Nya kami dibangkitkan", "Bangun tidur"),
            new ZikirList.Zikir("بِاسْمِكَ اللَّهُمَّ أَحْيَا وَأَمُوتُ", "Bismikallahumma ahya wa amut", "Dengan nama-Mu ya Allah aku hidup dan aku mati", "Sebelum tidur"),
            new ZikirList.Zikir("اللَّهُمَّ افْتَحْ لِي أَبْوَابَ رَحْمَتِكَ", "Allahummaftah li abwaba rahmatik", "Ya Allah, bukakanlah untukku pintu-pintu rahmat-Mu", "Masuk masjid"),
            new ZikirList.Zikir("اللَّهُمَّ إِنِّي أَسْأَلُكَ مِنْ فَضْلِكَ", "Allahumma inni as-aluka min fadhlik", "Ya Allah, sesungguhnya aku memohon kurnia-Mu", "Keluar masjid"),
            new ZikirList.Zikir("بِسْمِ اللَّهِ تَوَكَّلْتُ عَلَى اللَّهِ لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "Bismillah tawakkaltu 'alallah la haula wa la quwwata illa billah", "Dengan nama Allah, aku berserah kepada Allah, tiada daya dan kekuatan melainkan dengan Allah", "Keluar rumah"),
            new ZikirList.Zikir("سُبْحَانَ الَّذِي سَخَّرَ لَنَا هَذَا وَمَا كُنَّا لَهُ مُقْرِنِينَ وَإِنَّا إِلَى رَبِّنَا لَمُنْقَلِبُونَ", "Subhanalladzi sakhkhara lana hadza wa ma kunna lahu muqrinin wa inna ila rabbina lamunqalibun", "Maha Suci Allah yang menundukkan kenderaan ini untuk kami, sedang kami tidak mampu menguasainya, dan kepada Tuhan kami kami akan kembali", "Menaiki kenderaan"),
            new ZikirList.Zikir("رَبِّ اغْفِرْ لِي وَلِوَالِدَيَّ وَارْحَمْهُمَا كَمَا رَبَّيَانِي صَغِيرًا", "Rabbighfir li wa liwalidayya warhamhuma kama rabbayani saghira", "Tuhanku, ampunilah aku dan kedua ibu bapaku, dan kasihanilah mereka sebagaimana mereka mendidikku semasa kecil", "Untuk ibu bapa"),
            new ZikirList.Zikir("رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ", "Rabbana atina fid-dunya hasanah wa fil-akhirati hasanah wa qina 'azaban nar", "Wahai Tuhan kami, berilah kami kebaikan di dunia dan kebaikan di akhirat, dan peliharalah kami daripada azab neraka", "Kebaikan dunia dan akhirat"),
            new ZikirList.Zikir("اللَّهُمَّ إِنِّي أَعُوذُ بِكَ مِنَ الْخُبُثِ وَالْخَبَائِثِ", "Allahumma inni a'udzu bika minal khubutsi wal khaba-its", "Ya Allah, aku berlindung dengan-Mu daripada syaitan lelaki dan perempuan", "Masuk tandas"),
            new ZikirList.Zikir("غُفْرَانَكَ", "Ghufranaka", "Aku memohon keampunan-Mu", "Keluar tandas"),
    };

    static ZikirList.Zikir current() {
        return ALL[(int) ((System.currentTimeMillis() / 90_000L) % ALL.length)];
    }

    private DoaList() {}
}

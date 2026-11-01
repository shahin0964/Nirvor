package com.nirvor.app.islamic

/** A dhikr for the digital tasbih: Arabic, how it is read in Bangla, its meaning and a usual count. */
class Dhikr(val key: String, val arabic: String, val bn: String, val meaning: String, val target: Int)

object Dhikrs {
    val presets = listOf(
        Dhikr("subhanallah", "سُبْحَانَ اللَّهِ", "সুবহানাল্লাহ", "আল্লাহ পবিত্র", 33),
        Dhikr("alhamdulillah", "الْحَمْدُ لِلَّهِ", "আলহামদুলিল্লাহ", "সব প্রশংসা আল্লাহর", 33),
        Dhikr("allahuakbar", "اللَّهُ أَكْبَرُ", "আল্লাহু আকবার", "আল্লাহ সবচেয়ে বড়", 34),
        Dhikr("tahlil", "لَا إِلَهَ إِلَّا اللَّهُ", "লা ইলাহা ইল্লাল্লাহ", "আল্লাহ ছাড়া কোনো উপাস্য নেই", 100),
        Dhikr("istighfar", "أَسْتَغْفِرُ اللَّهَ", "আস্তাগফিরুল্লাহ", "আমি আল্লাহর কাছে ক্ষমা চাই", 100),
        Dhikr("subhanallahi_wabihamdihi", "سُبْحَانَ اللَّهِ وَبِحَمْدِهِ", "সুবহানাল্লাহি ওয়া বিহামদিহি", "আল্লাহ পবিত্র, সব প্রশংসা তাঁর", 100),
        Dhikr("hawqala", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللَّهِ", "লা হাওলা ওয়া লা কুওয়াতা ইল্লা বিল্লাহ", "আল্লাহর সাহায্য ছাড়া কোনো শক্তি ও সামর্থ্য নেই", 100),
        Dhikr("salawat", "اللَّهُمَّ صَلِّ عَلَى مُحَمَّدٍ", "আল্লাহুম্মা সাল্লি আলা মুহাম্মাদ", "হে আল্লাহ, মুহাম্মাদ ﷺ-এর ওপর রহমত বর্ষণ করুন", 100),
    )

    /** After each prayer: 33 SubhanAllah, 33 Alhamdulillah, 34 Allahu Akbar (Sahih Muslim). */
    val afterPrayer = listOf(presets[0] to 33, presets[1] to 33, presets[2] to 34)

    const val AFTER_PRAYER_KEY = "after_prayer"

    fun of(key: String): Dhikr? = presets.firstOrNull { it.key == key }
}

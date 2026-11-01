package com.nirvor.app.tools

/** One unit: [toBase] = how many base units one of this unit is. */
class UnitDef(val name: String, val symbol: String, val toBase: Double)

class UnitCategory(
    val emoji: String,
    val title: String,
    val units: List<UnitDef>,
    val note: String? = null,
    /** Temperature is not a simple factor. */
    val temperature: Boolean = false,
)

object Units {
    val categories: List<UnitCategory> = listOf(
        UnitCategory(
            "🏡", "জমি / ক্ষেত্রফল",
            listOf(
                UnitDef("শতাংশ (ডেসিমাল)", "শতক", 435.6),
                UnitDef("কাঠা", "কাঠা", 720.0),
                UnitDef("বিঘা", "বিঘা", 14_400.0),
                UnitDef("একর", "একর", 43_560.0),
                UnitDef("ছটাক (জমি)", "ছটাক", 45.0),
                UnitDef("বর্গফুট", "ft²", 1.0),
                UnitDef("বর্গমিটার", "m²", 10.763910416709722),
                UnitDef("বর্গগজ", "yd²", 9.0),
                UnitDef("বর্গইঞ্চি", "in²", 1.0 / 144.0),
                UnitDef("হেক্টর", "ha", 107_639.10416709722),
                UnitDef("বর্গকিলোমিটার", "km²", 10_763_910.416709722),
            ),
            note = "সরকারি মাপ: ১ শতাংশ = ৪৩৫.৬ বর্গফুট, ১ কাঠা = ৭২০ বর্গফুট, ১ বিঘা = ২০ কাঠা = ৩৩ শতাংশ। এলাকাভেদে কাঠা-বিঘা আলাদা হতে পারে।",
        ),
        UnitCategory(
            "📏", "দৈর্ঘ্য",
            listOf(
                UnitDef("মিলিমিটার", "mm", 0.001),
                UnitDef("সেন্টিমিটার", "cm", 0.01),
                UnitDef("মিটার", "m", 1.0),
                UnitDef("কিলোমিটার", "km", 1000.0),
                UnitDef("ইঞ্চি", "in", 0.0254),
                UnitDef("ফুট", "ft", 0.3048),
                UnitDef("গজ", "yd", 0.9144),
                UnitDef("হাত (১৮ ইঞ্চি)", "হাত", 0.4572),
                UnitDef("মাইল", "mi", 1609.344),
                UnitDef("নটিক্যাল মাইল", "nmi", 1852.0),
            ),
        ),
        UnitCategory(
            "⚖️", "ওজন",
            listOf(
                UnitDef("গ্রাম", "g", 1.0),
                UnitDef("কিলোগ্রাম", "kg", 1000.0),
                UnitDef("মিলিগ্রাম", "mg", 0.001),
                UnitDef("টন", "t", 1_000_000.0),
                UnitDef("মণ (৪০ সের)", "মণ", 37_324.17),
                UnitDef("সের", "সের", 933.10425),
                UnitDef("ছটাক (ওজন)", "ছটাক", 58.31901),
                UnitDef("ভরি / তোলা", "ভরি", 11.6638),
                UnitDef("আনা (সোনা)", "আনা", 11.6638 / 16.0),
                UnitDef("রতি", "রতি", 11.6638 / 96.0),
                UnitDef("পাউন্ড", "lb", 453.59237),
                UnitDef("আউন্স", "oz", 28.349523125),
            ),
            note = "১ মণ = ৪০ সের, ১ সের = ১৬ ছটাক = ৮০ তোলা। সোনা: ১ ভরি = ১৬ আনা, ১ আনা = ৬ রতি।",
        ),
        UnitCategory(
            "🌡️", "তাপমাত্রা",
            listOf(
                UnitDef("সেলসিয়াস", "°C", 1.0),
                UnitDef("ফারেনহাইট", "°F", 1.0),
                UnitDef("কেলভিন", "K", 1.0),
            ),
            temperature = true,
        ),
        UnitCategory(
            "🧴", "তরল / আয়তন",
            listOf(
                UnitDef("মিলিলিটার", "ml", 0.001),
                UnitDef("লিটার", "L", 1.0),
                UnitDef("চা-চামচ", "tsp", 0.005),
                UnitDef("টেবিল-চামচ", "tbsp", 0.015),
                UnitDef("কাপ", "cup", 0.24),
                UnitDef("গ্যালন (US)", "gal", 3.785411784),
                UnitDef("গ্যালন (UK)", "gal UK", 4.54609),
                UnitDef("ঘনমিটার", "m³", 1000.0),
                UnitDef("ঘনফুট", "ft³", 28.316846592),
                UnitDef("ফ্লুইড আউন্স", "fl oz", 0.0295735295625),
            ),
        ),
        UnitCategory(
            "🚗", "গতি",
            listOf(
                UnitDef("কিলোমিটার/ঘণ্টা", "km/h", 1.0),
                UnitDef("মিটার/সেকেন্ড", "m/s", 3.6),
                UnitDef("মাইল/ঘণ্টা", "mph", 1.609344),
                UnitDef("নট", "knot", 1.852),
                UnitDef("ফুট/সেকেন্ড", "ft/s", 1.09728),
            ),
        ),
        UnitCategory(
            "⏳", "সময়",
            listOf(
                UnitDef("মিলিসেকেন্ড", "ms", 0.001),
                UnitDef("সেকেন্ড", "s", 1.0),
                UnitDef("মিনিট", "min", 60.0),
                UnitDef("ঘণ্টা", "h", 3600.0),
                UnitDef("দিন", "দিন", 86_400.0),
                UnitDef("সপ্তাহ", "সপ্তাহ", 604_800.0),
                UnitDef("মাস (গড়ে)", "মাস", 2_629_746.0),
                UnitDef("বছর (গড়ে)", "বছর", 31_556_952.0),
            ),
        ),
        UnitCategory(
            "💾", "ডেটা",
            listOf(
                UnitDef("বিট", "bit", 0.125),
                UnitDef("বাইট", "B", 1.0),
                UnitDef("কিলোবাইট", "KB", 1e3),
                UnitDef("মেগাবাইট", "MB", 1e6),
                UnitDef("গিগাবাইট", "GB", 1e9),
                UnitDef("টেরাবাইট", "TB", 1e12),
                UnitDef("কিবিবাইট", "KiB", 1024.0),
                UnitDef("মেবিবাইট", "MiB", 1_048_576.0),
                UnitDef("গিবিবাইট", "GiB", 1_073_741_824.0),
            ),
            note = "ফোন ও মেমোরি কার্ডে সাধারণত ১ GB = ১০০,০০,০০,০০০ বাইট ধরা হয়।",
        ),
    )

    fun convert(category: UnitCategory, value: Double, from: Int, to: Int): Double {
        if (!category.temperature) {
            return value * category.units[from].toBase / category.units[to].toBase
        }
        val c = when (from) {
            1 -> (value - 32.0) * 5.0 / 9.0
            2 -> value - 273.15
            else -> value
        }
        return when (to) {
            1 -> c * 9.0 / 5.0 + 32.0
            2 -> c + 273.15
            else -> c
        }
    }
}

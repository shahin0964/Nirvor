package com.nirvor.app.ui.kids

data class KidVideo(
    val id: String,
    val title: String,
    val category: String,
    val youtubeId: String,
    val channel: String,
    val duration: String,
    val color: Long,
)

data class KidLetter(
    val char: String,
    val word: String,
    val meaning: String,
    val emoji: String,
    val group: String, // "স্বরবর্ণ", "ব্যঞ্জনবর্ণ", "English", "সংখ্যা"
)

object KidsRepository {

    val categories = listOf("সবগুলো", "কার্টুন", "ইসলামিক", "ছড়া ও গান", "শিক্ষণীয়")

    val videos = listOf(
        // কার্টুন (মীনা, সিসিমপুর, গোপাল ভাঁড়)
        KidVideo(
            id = "v1",
            title = "মীনা কার্টুন - স্কুলে যাওয়া (Meena Cartoon)",
            category = "কার্টুন",
            youtubeId = "zD3Q9b9c9Q8", // Meena Bengali
            channel = "UNICEF Bangladesh",
            duration = "১৩:২০",
            color = 0xFFFF6584,
        ),
        KidVideo(
            id = "v2",
            title = "সিসিমপুর - টুকটুকি ও হালুমের গল্প",
            category = "কার্টুন",
            youtubeId = "f5hJ_0xW-X8",
            channel = "Sisimpur",
            duration = "১০:১৫",
            color = 0xFF4ADE80,
        ),
        KidVideo(
            id = "v3",
            title = "গোপাল ভাঁড় - মজার বুদ্ধির গল্প",
            category = "কার্টুন",
            youtubeId = "8qJz3_L4kF8",
            channel = "Sony AATH",
            duration = "১১:৪৫",
            color = 0xFFFBBF24,
        ),
        KidVideo(
            id = "v4",
            title = "মীনা কার্টুন - স্বাস্থ্য ও পুষ্টি",
            category = "কার্টুন",
            youtubeId = "X2pQnO0YkFw",
            channel = "UNICEF",
            duration = "১২:৩০",
            color = 0xFF38BDF8,
        ),

        // ইসলামিক
        KidVideo(
            id = "v5",
            title = "নবীজির গল্প - হযরত ইউনুস (আঃ) ও মাছের পেট",
            category = "ইসলামিক",
            youtubeId = "J7-5jB0Z5vA",
            channel = "Islamic Kids",
            duration = "০৮:৪৫",
            color = 0xFF10B981,
        ),
        KidVideo(
            id = "v6",
            title = "ছোটদের প্রয়োজনীয় ৫টি ইসলামিক দোয়া ও নাশিদ",
            category = "ইসলামিক",
            youtubeId = "G4x8K7L2P0Q",
            channel = "Muslim Kids World",
            duration = "০৬:২০",
            color = 0xFF059669,
        ),
        KidVideo(
            id = "v7",
            title = "নবীজির গল্প - হযরত নূহ (আঃ) এর কিশতি",
            category = "ইসলামিক",
            youtubeId = "K9z1P0L8mQ4",
            channel = "Islamic Animation",
            duration = "০৯:১০",
            color = 0xFF0D9488,
        ),

        // ছড়া ও গান
        KidVideo(
            id = "v8",
            title = "খোকন খোকন ডাক পাড়ি ও প্রিয় বাংলা ছড়া",
            category = "ছড়া ও গান",
            youtubeId = "L0p8M1N9z2X",
            channel = "Bengali Rhymes",
            duration = "০৫:৩০",
            color = 0xFFA855F7,
        ),
        KidVideo(
            id = "v9",
            title = "আয় আয় চাঁদ মামা টিপ দিয়ে যা",
            category = "ছড়া ও গান",
            youtubeId = "M7q0P9L1x3Z",
            channel = "Kids Rhymes",
            duration = "০৪:১৫",
            color = 0xFFEC4899,
        ),
        KidVideo(
            id = "v10",
            title = "Twinkle Twinkle Little Star & ABC Song",
            category = "ছড়া ও গান",
            youtubeId = "yCjJyiqpAuU",
            channel = "Super Simple Songs",
            duration = "০৬:০০",
            color = 0xFF6366F1,
        ),

        // শিক্ষণীয়
        KidVideo(
            id = "v11",
            title = "রং চেনার সহজ উপায় ও ম্যাজিক এনিমেশন",
            category = "শিক্ষণীয়",
            youtubeId = "T0p8L9M1N2x",
            channel = "Fun Learning BD",
            duration = "০৭:৪০",
            color = 0xFFF97316,
        ),
        KidVideo(
            id = "v12",
            title = "বন্য প্রাণীদের মজার জগত ও তাদের ডাক",
            category = "শিক্ষণীয়",
            youtubeId = "R3x9P1L0m8K",
            channel = "Animal Planet Kids",
            duration = "০৮:১৫",
            color = 0xFF14B8A6,
        ),
    )

    // বাংলা স্বরবর্ণ (১১টি)
    val swarabarna = listOf(
        KidLetter("অ", "অলি", "অলি ওড়ে ফুলে ফুলে", "🐝", "স্বরবর্ণ"),
        KidLetter("আ", "আম", "আমটি আমি খাবো পেড়ে", "🥭", "স্বরবর্ণ"),
        KidLetter("ই", "ইলিশ", "ইলিশ ভাজা খেতে মজা", "🐟", "স্বরবর্ণ"),
        KidLetter("ঈ", "ঈগল", "ঈগল পাখি আকাশে ওড়ে", "🦅", "স্বরবর্ণ"),
        KidLetter("উ", "উট", "উট চলেছে মরু দেশে", "🐪", "স্বরবর্ণ"),
        KidLetter("ঊ", "ঊষা", "ঊষা হাসে পুব আকাশে", "🌅", "স্বরবর্ণ"),
        KidLetter("ঋ", "ঋতু", "ঋতু বৈচিত্র্যের বাংলাদেশ", "🌸", "স্বরবর্ণ"),
        KidLetter("এ", "একতারা", "একতারা বাজে বাউলের গানে", "🪕", "স্বরবর্ণ"),
        KidLetter("ঐ", "ঐরাবত", "ঐরাবত চলে হেলেদুলে", "🐘", "স্বরবর্ণ"),
        KidLetter("ও", "ওল", "ওল খেলে ধরে গলা", "🥔", "স্বরবর্ণ"),
        KidLetter("ঔ", "ঔষধ", "ঔষধ খেলে সারে রোগ", "💊", "স্বরবর্ণ"),
    )

    // বাংলা ব্যঞ্জনবর্ণ (শীর্ষ জনপ্রিয়)
    val byanjanbarna = listOf(
        KidLetter("ক", "কলম", "কলম দিয়ে লিখি আমি", "🖊️", "ব্যঞ্জনবর্ণ"),
        KidLetter("খ", "খরগোশ", "খরগোশ ছানা লাফিয়ে চলে", "🐇", "ব্যঞ্জনবর্ণ"),
        KidLetter("গ", "গরু", "গরু দেয় মিষ্টি দুধ", "🐄", "ব্যঞ্জনবর্ণ"),
        KidLetter("ঘ", "ঘোড়া", "ঘোড়া ছোটে টগবগিয়ে", "🐎", "ব্যঞ্জনবর্ণ"),
        KidLetter("ঙ", "ব্যাঙ", "ব্যাঙ ডাকে ঘ্যাঙর ঘ্যাঙ", "🐸", "ব্যঞ্জনবর্ণ"),
        KidLetter("চ", "চাঁদ", "চাঁদ মামা হাসে রাতে", "🌙", "ব্যঞ্জনবর্ণ"),
        KidLetter("ছ", "ছাতা", "ছাতা লাগে বৃষ্টি হলে", "☂️", "ব্যঞ্জনবর্ণ"),
        KidLetter("জ", "জাহাজ", "জাহাজ ভাসে সাগর জলে", "🚢", "ব্যঞ্জনবর্ণ"),
        KidLetter("ঝ", "ঝড়", "ঝড়ের দিনে আম কুড়াই", "🌪️", "ব্যঞ্জনবর্ণ"),
        KidLetter("ট", "টিয়া", "টিয়া পাখির রক্তিম ঠোঁট", "🦜", "ব্যঞ্জনবর্ণ"),
        KidLetter("ঠ", "ঠাকুমা", "ঠাকুমা শোনান মজার গল্প", "👵", "ব্যঞ্জনবর্ণ"),
        KidLetter("ড", "ডালিম", "ডালিম গাছে থোকা থোকা ফল", "🍎", "ব্যঞ্জনবর্ণ"),
        KidLetter("ঢ", "ঢোল", "ঢোল বাজে আনন্দের তালে", "🥁", "ব্যঞ্জনবর্ণ"),
        KidLetter("ত", "তিমি", "তিমি নাচে সাগর মাঝে", "🐋", "ব্যঞ্জনবর্ণ"),
        KidLetter("থ", "থালা", "থালা ভরা মিষ্টি খাবার", "🍽️", "ব্যঞ্জনবর্ণ"),
        KidLetter("দ", "দোয়েল", "দোয়েল আমাদের জাতীয় পাখি", "🐦", "ব্যঞ্জনবর্ণ"),
        KidLetter("ধ", "ধান", "ধানের শীষে শিশির কণা", "🌾", "ব্যঞ্জনবর্ণ"),
        KidLetter("ন", "নৌকা", "নৌকা ভাসে নদীর বুকে", "⛵", "ব্যঞ্জনবর্ণ"),
        KidLetter("প", "পাখি", "পাখিরা গায় মিষ্টি গান", "🕊️", "ব্যঞ্জনবর্ণ"),
        KidLetter("ফ", "ফুল", "ফুলের গন্ধে মন ভরে যায়", "🌺", "ব্যঞ্জনবর্ণ"),
        KidLetter("ব", "বাঘ", "সুন্দরবনের রয়েল বেঙ্গল বাঘ", "🐅", "ব্যঞ্জনবর্ণ"),
        KidLetter("ভ", "ভালুক", "ভালুক নাচে বনের মাঝে", "🐻", "ব্যঞ্জনবর্ণ"),
        KidLetter("ম", "ময়ূর", "ময়ূর নাচে পেখম তুলে", "🦚", "ব্যঞ্জনবর্ণ"),
        KidLetter("য", "যাঁতা", "যাঁতা ঘোরে তালে তালে", "⚙️", "ব্যঞ্জনবর্ণ"),
        KidLetter("র", "রথ", "রথ চলে মেলায় মেলায়", "🎪", "ব্যঞ্জনবর্ণ"),
        KidLetter("ল", "লাটিম", "লাটিম ঘোরে বনবন করে", "🪀", "ব্যঞ্জনবর্ণ"),
        KidLetter("শ", "শাপলা", "শাপলা আমাদের জাতীয় ফুল", "🪷", "ব্যঞ্জনবর্ণ"),
        KidLetter("স", "সিংহ", "সিংহ বনের প্রতাপশালী রাজা", "🦁", "ব্যঞ্জনবর্ণ"),
        KidLetter("হ", "হাতি", "হাতির পিঠে চড়তে মজা", "🐘", "ব্যঞ্জনবর্ণ"),
    )

    // English Alphabet
    val englishAlphabets = listOf(
        KidLetter("A", "Apple", "A for Apple", "🍎", "English"),
        KidLetter("B", "Ball", "B for Ball", "⚽", "English"),
        KidLetter("C", "Cat", "C for Cat", "🐱", "English"),
        KidLetter("D", "Dog", "D for Dog", "🐶", "English"),
        KidLetter("E", "Elephant", "E for Elephant", "🐘", "English"),
        KidLetter("F", "Fish", "F for Fish", "🐠", "English"),
        KidLetter("G", "Grape", "G for Grape", "🍇", "English"),
        KidLetter("H", "Hat", "H for Hat", "🎩", "English"),
        KidLetter("I", "Ice-cream", "I for Ice-cream", "🍦", "English"),
        KidLetter("J", "Juice", "J for Juice", "🧃", "English"),
        KidLetter("K", "Kite", "K for Kite", "🪁", "English"),
        KidLetter("L", "Lion", "L for Lion", "🦁", "English"),
        KidLetter("M", "Monkey", "M for Monkey", "🐒", "English"),
        KidLetter("N", "Nest", "N for Nest", "🪺", "English"),
        KidLetter("O", "Orange", "O for Orange", "🍊", "English"),
        KidLetter("P", "Parrot", "P for Parrot", "🦜", "English"),
        KidLetter("Q", "Queen", "Q for Queen", "👑", "English"),
        KidLetter("R", "Rainbow", "R for Rainbow", "🌈", "English"),
        KidLetter("S", "Sun", "S for Sun", "☀️", "English"),
        KidLetter("T", "Tiger", "T for Tiger", "🐯", "English"),
        KidLetter("U", "Umbrella", "U for Umbrella", "☂️", "English"),
        KidLetter("V", "Van", "V for Van", "🚐", "English"),
        KidLetter("W", "Watch", "W for Watch", "⌚", "English"),
        KidLetter("X", "Xylophone", "X for Xylophone", "🎼", "English"),
        KidLetter("Y", "Yo-yo", "Y for Yo-yo", "🪀", "English"),
        KidLetter("Z", "Zebra", "Z for Zebra", "🦓", "English"),
    )

    // বাংলা ও ইংরেজি সংখ্যা
    val numbers = listOf(
        KidLetter("১", "এক", "একটি সূর্য", "☀️", "সংখ্যা"),
        KidLetter("২", "দুই", "দুইটি তারা", "⭐⭐", "সংখ্যা"),
        KidLetter("৩", "তিন", "তিনটি ফুল", "🌸🌸🌸", "সংখ্যা"),
        KidLetter("৪", "চার", "চারটি পাখি", "🐦🐦🐦🐦", "সংখ্যা"),
        KidLetter("৫", "পাঁচ", "পাঁচটি ফল", "🍎🍎🍎🍎🍎", "সংখ্যা"),
        KidLetter("৬", "ছয়", "ছয়টি মাছ", "🐟🐟🐟🐟🐟🐟", "সংখ্যা"),
        KidLetter("৭", "সাত", "সাতটি বেলুন", "🎈🎈🎈🎈🎈🎈🎈", "সংখ্যা"),
        KidLetter("৮", "আট", "আটটি বল", "⚽⚽⚽⚽⚽⚽⚽⚽", "সংখ্যা"),
        KidLetter("৯", "নয়", "নয়টি আম", "🥭🥭🥭🥭🥭🥭🥭🥭🥭", "সংখ্যা"),
        KidLetter("১০", "দশ", "দশটি হাততালি", "👏 দশটি আনন্দ", "সংখ্যা"),
    )
}

package com.nirvor.app.tools

/** Groups on the Tool Box page, in the order the user listed them. */
enum class ToolSection(val emoji: String, val title: String, val sub: String, val color: Long) {
    Sensor("🟢", "সেন্সরের টুল", "ক্যামেরা লাগবে না", 0xFF0BA765),
    Camera("🔵", "ক্যামেরা ও AR", "দেখে, মেপে, পয়েন্ট বসিয়ে কাজ", 0xFF168FFB),
    Software("🟠", "হিসাবের টুল", "ক্যালকুলেটর ও কনভার্টার", 0xFFF76B15),
    Office("🔵", "অফিস", "PDF, Word, Excel", 0xFF6E56CF),
}

/**
 * Every tool of the Tool Box. [ready] = false: comes in a later update (shown with "শীঘ্রই").
 */
enum class ToolId(
    val emoji: String,
    val title: String,
    val bn: String,
    val section: ToolSection,
    val ready: Boolean,
) {
    Level("🫧", "3D Spirit Level", "লেভেল ও পানির লেভেল", ToolSection.Sensor, true),
    Compass("🧭", "Compass", "কম্পাস ও কিবলা", ToolSection.Sensor, true),
    Angle("📐", "Digital Angle", "কোণ মাপা", ToolSection.Sensor, true),
    Tilt("📳", "Tilt Meter", "কতটা হেলে আছে", ToolSection.Sensor, true),
    Stopwatch("⏱️", "Stopwatch", "স্টপওয়াচ", ToolSection.Sensor, true),
    Flashlight("🔦", "Flashlight", "টর্চ ও SOS", ToolSection.Sensor, true),
    SoundMeter("🔊", "Sound Meter", "শব্দ মাপা (dB)", ToolSection.Sensor, true),
    LightMeter("💡", "Light Meter", "আলো মাপা (lux)", ToolSection.Sensor, true),
    Ruler("📏", "Screen Ruler", "স্ক্রিনে স্কেল", ToolSection.Sensor, true),

    ArDistance("📏", "Distance Measure", "দূরত্ব মাপা", ToolSection.Camera, true),
    ArAngle("📐", "Angle Measure", "ক্যামেরায় কোণ", ToolSection.Camera, true),
    ArArea("📐", "Area Measure", "জায়গার মাপ", ToolSection.Camera, true),
    ArHeight("📏", "Height Measure", "উচ্চতা মাপা", ToolSection.Camera, true),
    ArPoints("📍", "Point-to-Point", "পয়েন্ট থেকে পয়েন্ট", ToolSection.Camera, true),
    ArWall("🧱", "Surface / Wall", "দেয়াল ও মেঝে", ToolSection.Camera, true),
    Magnifier("🔍", "Magnifier", "বড় করে দেখা", ToolSection.Camera, true),
    ColorDetector("🎨", "Color Detector", "রং চেনা", ToolSection.Camera, true),
    DocScanner("📄", "Document Scanner", "ডকুমেন্ট স্ক্যান", ToolSection.Camera, true),
    Ocr("🔤", "OCR", "ছবি থেকে লেখা", ToolSection.Camera, true),
    Receipt("🧾", "Receipt Scanner", "রসিদ থেকে খরচ", ToolSection.Camera, true),
    QrCode("🔳", "QR / Barcode", "স্ক্যান ও তৈরি", ToolSection.Camera, true),
    Objects("📦", "Object Detection", "জিনিস চেনা", ToolSection.Camera, true),

    Calculator("🧮", "Calculator", "অ্যাডভান্সড ক্যালকুলেটর", ToolSection.Software, true),
    Units("🔁", "Unit Converter", "একক রূপান্তর", ToolSection.Software, true),
    Bmi("⚖️", "BMI", "ওজন ঠিক আছে কিনা", ToolSection.Software, true),
    Age("🎂", "Age", "বয়স হিসাব", ToolSection.Software, true),
    Percent("💯", "Percentage", "শতকরা হিসাব", ToolSection.Software, true),
    Discount("🏷️", "Discount", "ছাড়ের পর দাম", ToolSection.Software, true),
    Emi("🏦", "EMI", "লোনের কিস্তি হিসাব", ToolSection.Software, true),

    Word("📝", "Office Word", "নতুন ডকুমেন্ট বানান", ToolSection.Office, true),
    PdfToWord("📄", "PDF → Word", "PDF থেকে Word", ToolSection.Office, true),
    PdfToExcel("📊", "PDF → Excel", "PDF থেকে Excel", ToolSection.Office, true),
    ExcelToPdf("📑", "Excel → PDF", "Excel থেকে PDF", ToolSection.Office, true),
    WordToPdf("📃", "Word → PDF", "Word থেকে PDF", ToolSection.Office, true),
    ImageToPdf("🖼️", "Image → PDF", "ছবি থেকে PDF", ToolSection.Office, true),
    PdfMergeSplit("🗂️", "PDF Merge / Split", "জোড়া ও ভাগ", ToolSection.Office, true);

    companion object {
        fun of(name: String): ToolId? = entries.firstOrNull { it.name == name }
    }
}

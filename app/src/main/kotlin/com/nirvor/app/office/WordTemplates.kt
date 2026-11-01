package com.nirvor.app.office

/**
 * Starting layouts for new documents. They only hold the usual headings and dotted blanks to
 * fill in — no names, numbers or sample text.
 */
object WordTemplates {
    class Template(val emoji: String, val name: String, val build: () -> Doc)

    private const val DOTS = "........................................"

    private fun p(text: String, align: Align = Align.Left, bold: Boolean = false) = Para(listOf(Run(text, bold = bold)), align = align)
    private fun h(text: String, kind: ParaKind, align: Align = Align.Left) = Para(listOf(Run(text)), kind, align)
    private val blank = Para(emptyList())
    private fun bullets(n: Int) = List(n) { Para(emptyList(), list = ListKind.Bullet) }
    private fun table(header: List<String>, rows: Int) = Table(
        listOf(header.map { Cell(listOf(p(it))) }) + List(rows) { header.map { Cell(listOf(blank)) } },
        header = true,
    )

    val all = listOf(
        Template("📝", "ফাঁকা পাতা") { Doc(listOf(blank)) },
        Template("✉️", "দরখাস্ত") {
            Doc(
                listOf(
                    p("তারিখ: ....../....../.........."),
                    blank,
                    p("বরাবর,"),
                    p(DOTS),
                    p(DOTS),
                    p(DOTS),
                    blank,
                    p("বিষয়: $DOTS", bold = true),
                    blank,
                    p("জনাব,"),
                    p("বিনীত নিবেদন এই যে, $DOTS", align = Align.Justify),
                    blank,
                    p("অতএব, $DOTS", align = Align.Justify),
                    blank,
                    p("বিনীত নিবেদক,"),
                    blank,
                    p(DOTS),
                    p("নাম: $DOTS"),
                    p("ঠিকানা: $DOTS"),
                    p("মোবাইল: $DOTS"),
                )
            )
        },
        Template("💌", "চিঠি") {
            Doc(
                listOf(
                    p("....../....../..........", Align.Right),
                    p(DOTS, Align.Right),
                    blank,
                    p("প্রিয় $DOTS,"),
                    blank,
                    p(DOTS, align = Align.Justify),
                    blank,
                    p("ইতি,"),
                    p(DOTS),
                )
            )
        },
        Template("👤", "সিভি / বায়োডাটা") {
            Doc(
                listOf(
                    h("নাম", ParaKind.Title),
                    p("📞 $DOTS   ✉️ $DOTS"),
                    p("📍 $DOTS"),
                    Rule,
                    h("ক্যারিয়ারের লক্ষ্য", ParaKind.H2),
                    p(DOTS, align = Align.Justify),
                    h("শিক্ষাগত যোগ্যতা", ParaKind.H2),
                    table(listOf("পরীক্ষা / ডিগ্রি", "প্রতিষ্ঠান", "সাল", "ফলাফল"), 3),
                    h("কাজের অভিজ্ঞতা", ParaKind.H2),
                    h("পদ — প্রতিষ্ঠান", ParaKind.H3),
                    p("সময়: $DOTS"),
                ) + bullets(2) + listOf(
                    h("দক্ষতা", ParaKind.H2),
                ) + bullets(3) + listOf(
                    h("ভাষা", ParaKind.H2),
                ) + bullets(2) + listOf(
                    h("ব্যক্তিগত তথ্য", ParaKind.H2),
                    Table(
                        listOf("বাবার নাম", "মায়ের নাম", "জন্ম তারিখ", "জাতীয়তা", "স্থায়ী ঠিকানা")
                            .map { k -> listOf(Cell(listOf(p(k, bold = true))), Cell(listOf(blank))) },
                    ),
                    h("রেফারেন্স", ParaKind.H2),
                    p(DOTS),
                )
            )
        },
        Template("📢", "নোটিশ") {
            Doc(
                listOf(
                    h(DOTS, ParaKind.H2, Align.Center),
                    p(DOTS, Align.Center),
                    Rule,
                    p("স্মারক নং: ..................", Align.Left),
                    p("তারিখ: ....../....../..........", Align.Right),
                    h("নোটিশ", ParaKind.H1, Align.Center),
                    p("বিষয়: $DOTS", bold = true),
                    blank,
                    p("এতদ্বারা সংশ্লিষ্ট সকলের অবগতির জন্য জানানো যাচ্ছে যে, $DOTS", align = Align.Justify),
                    blank,
                    blank,
                    p(DOTS, Align.Right),
                    p("(স্বাক্ষর ও পদবি)", Align.Right),
                )
            )
        },
        Template("🗒️", "মিটিং নোট") {
            Doc(
                listOf(
                    h("মিটিং নোট", ParaKind.H1),
                    p("তারিখ ও সময়: $DOTS"),
                    p("স্থান: $DOTS"),
                    p("উপস্থিত: $DOTS"),
                    h("আলোচনার বিষয়", ParaKind.H2),
                ) + bullets(3) + listOf(
                    h("সিদ্ধান্ত", ParaKind.H2),
                    Para(emptyList(), list = ListKind.Number),
                    Para(emptyList(), list = ListKind.Number),
                    h("পরের কাজ", ParaKind.H2),
                    table(listOf("কাজ", "দায়িত্বে", "শেষ তারিখ"), 3),
                )
            )
        },
    )
}

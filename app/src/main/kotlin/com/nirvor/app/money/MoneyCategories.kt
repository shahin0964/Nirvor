package com.nirvor.app.money

import com.nirvor.app.data.MoneyCategoryEntity

enum class MoneyType(val key: String) {
    Expense("expense"),
    Income("income");

    companion object {
        fun of(key: String): MoneyType = if (key == Income.key) Income else Expense
    }
}

/**
 * A money category with its emoji. Built-in ones have a fixed word as [key]; the ones the user
 * makes are stored in the database and use "c<row id>" as key ([customId] is that row id).
 */
data class MoneyCategory(
    val key: String,
    val label: String,
    val emoji: String,
    val color: Long,
    val type: MoneyType,
    val customId: Long = 0L,
) {
    val custom: Boolean get() = customId != 0L
}

fun MoneyCategoryEntity.toCategory(): MoneyCategory =
    MoneyCategory(MoneyCategories.customKey(id), name, emoji, color, MoneyType.of(type), id)

object MoneyCategories {
    /** Where entries go when their category is deleted. */
    const val OTHER_EXPENSE = "other"
    const val OTHER_INCOME = "other_income"
    const val SALARY = "salary"
    /** Where paid কিস্তি go in Money (💳 ঋণ ও কিস্তি). */
    const val LOAN = "loan"

    fun customKey(id: Long) = "c$id"

    // "food", "transport" and "other" are the keys used since v1.0, so old entries keep their category.
    val expenseDefaults: List<MoneyCategory> = listOf(
        MoneyCategory("food", "খাবার", "🍔", 0xFF0AA35C, MoneyType.Expense),
        MoneyCategory("snacks", "চা-নাস্তা", "☕", 0xFFB7791F, MoneyType.Expense),
        MoneyCategory("transport", "যাতায়াত", "🚌", 0xFF168FFB, MoneyType.Expense),
        MoneyCategory("bazar", "বাজার", "🛒", 0xFF0E9F8E, MoneyType.Expense),
        MoneyCategory("rent", "বাসা ভাড়া", "🏠", 0xFF4F5BD5, MoneyType.Expense),
        MoneyCategory("bills", "বিদ্যুৎ-গ্যাস বিল", "💡", 0xFFE89B0C, MoneyType.Expense),
        MoneyCategory("mobile", "মোবাইল ও ইন্টারনেট", "📱", 0xFF0EA5C6, MoneyType.Expense),
        MoneyCategory("health", "চিকিৎসা ও ওষুধ", "💊", 0xFFE5484D, MoneyType.Expense),
        MoneyCategory("education", "পড়াশোনা", "📚", 0xFF6E56CF, MoneyType.Expense),
        MoneyCategory("shopping", "কেনাকাটা", "🛍️", 0xFFE0428E, MoneyType.Expense),
        MoneyCategory("fun", "বিনোদন ও ঘোরা", "🎬", 0xFFF76B15, MoneyType.Expense),
        MoneyCategory("family", "পরিবার", "👪", 0xFF9C6B3E, MoneyType.Expense),
        MoneyCategory(LOAN, "ঋণ ও কিস্তি", "💳", 0xFF5B6B82, MoneyType.Expense),
        MoneyCategory("gift", "দান ও উপহার", "🎁", 0xFFCF3F7A, MoneyType.Expense),
        MoneyCategory(OTHER_EXPENSE, "অন্যান্য", "📦", 0xFF8A43DB, MoneyType.Expense),
    )

    val incomeDefaults: List<MoneyCategory> = listOf(
        MoneyCategory(SALARY, "বেতন", "💼", 0xFF0BA765, MoneyType.Income),
        MoneyCategory("business", "ব্যবসা", "🏪", 0xFF168FFB, MoneyType.Income),
        MoneyCategory("freelance", "ফ্রিল্যান্স", "💻", 0xFF6E56CF, MoneyType.Income),
        MoneyCategory("bonus", "বোনাস", "🎉", 0xFFF76B15, MoneyType.Income),
        MoneyCategory("tuition", "টিউশনি", "📖", 0xFF0E9F8E, MoneyType.Income),
        MoneyCategory("rent_income", "ভাড়া থেকে আয়", "🏘️", 0xFF4F5BD5, MoneyType.Income),
        MoneyCategory("gift_income", "উপহার পাওয়া", "🎁", 0xFFCF3F7A, MoneyType.Income),
        MoneyCategory(OTHER_INCOME, "অন্যান্য আয়", "💵", 0xFF5B6B82, MoneyType.Income),
    )

    fun defaults(type: MoneyType): List<MoneyCategory> =
        if (type == MoneyType.Income) incomeDefaults else expenseDefaults

    /** Built-in categories followed by the user's own (of the same type), "other" kept last. */
    fun list(type: MoneyType, custom: List<MoneyCategory>): List<MoneyCategory> {
        val base = defaults(type)
        val other = base.last()
        return base.dropLast(1) + custom.filter { it.type == type } + other
    }

    /** The category for a stored key; unknown keys (e.g. a deleted category) show as "other". */
    fun of(type: MoneyType, key: String, custom: List<MoneyCategory>): MoneyCategory =
        defaults(type).firstOrNull { it.key == key }
            ?: custom.firstOrNull { it.key == key && it.type == type }
            ?: defaults(type).last()

    /** Colours offered for a new category. */
    val palette: List<Long> = listOf(
        0xFF0BA765, 0xFF168FFB, 0xFFF76B15, 0xFF8A43DB, 0xFFE5484D, 0xFF0E9F8E,
        0xFFE0428E, 0xFFE89B0C, 0xFF4F5BD5, 0xFF0EA5C6, 0xFF9C6B3E, 0xFF5B6B82,
    )

    /**
     * Emojis offered when making a category. Only emojis from Emoji 5.0 or older, so they show
     * on every supported phone (Android 8+); any other emoji can still be typed in.
     */
    val emojis: List<String> = listOf(
        "🍔", "🍕", "🍚", "🍛", "🍜", "🍗", "🐟", "🍎", "🥛", "☕", "🍵", "🍰",
        "🛒", "🚌", "🚗", "🚕", "🏍️", "🚲", "🚆", "✈️", "⛽", "🏠", "💡", "🔥",
        "💧", "🔧", "🛋️", "📱", "💻", "📶", "📺", "🎮", "💊", "🏥", "💪", "📚",
        "✏️", "🎓", "👕", "👟", "👜", "💄", "💍", "🛍️", "🎬", "🎵", "⚽", "🏏",
        "🏖️", "🎉", "🎁", "👪", "👶", "🐶", "🐱", "💐", "🕌", "🤲", "💰", "💵",
        "💳", "🏦", "💼", "📈", "🏪", "📦", "⭐", "❤️", "📌", "✂️", "🧒", "🌙",
    )
}

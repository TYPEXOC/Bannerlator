package com.winlator.star.i18n

/**
 * Runtime translator for the Compose UI.
 *
 * Bannerlator's Compose screens embed English literals directly in `Text(...)` calls instead of
 * referencing string resources, so resource-level localisation (`values-zh-rCN`) never reaches
 * them. [Cn.t] closes that gap: every string that flows into the shared [Text] wrapper is looked
 * up in [CN_TABLE] by exact value whenever the app language is Simplified Chinese. Strings that
 * are not in the table (dynamic text, user data, proper nouns) fall through unchanged, which keeps
 * the table safe to grow one screen at a time.
 */
object Cn {
    /** Returns the Simplified-Chinese rendering of [en], or [en] itself when untranslated. */
    fun t(en: String): String {
        if (!AppLanguage.isChinese) return en
        return CN_TABLE[en] ?: en
    }
}

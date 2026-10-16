package com.winlator.star.i18n

import android.content.res.Resources
import java.util.Locale

/**
 * Runtime translator for the Compose UI.
 *
 * Bannerlator's Compose screens embed English literals directly in `Text(...)` calls instead of
 * referencing string resources, so resource-level localisation (`values-zh-rCN`, `values-ja`, …)
 * never reaches them. Every string that flows into the shared [Text] wrapper is looked up here by
 * exact value; whatever is missing falls through unchanged, so a language's table can grow one
 * screen at a time without touching call sites.
 *
 * Multi-language by design: one table per language, keyed by the same tag [AppLanguage] stores.
 * Adding a language = add its table file + one line in [TABLES] + one entry in
 * [AppLanguage.options] (+ its `values-<tag>` resources for the XML side). No screen code changes.
 */
object I18n {

    /** Tables by [AppLanguage] tag. Add the entry here when a new table file lands. */
    private val TABLES: Map<String, Map<String, String>> = mapOf(
        AppLanguage.CHINESE to CN_TABLE,
        // AppLanguage.CHINESE_TW to CN_TABLE_TW,  // 繁體中文 — uncomment with the table file
        // AppLanguage.JAPANESE to JA_TABLE,       // 日本語   — uncomment with the table file
    )

    @Volatile
    private var table: Map<String, String> = emptyMap()

    /** Selects the active table for [tag]; called by [AppLanguage] on init and on every change. */
    fun setLanguage(tag: String) {
        table = resolve(tag)
    }

    /**
     * "System default" follows the DEVICE's own language: if it matches a table we ship, that
     * table activates — otherwise English, exactly like a language we have no table for.
     * Uses the process's effective locale, which reflects a system-level per-app language
     * (Android 13+) when one is set, otherwise the device language. [AppLanguage.refresh]
     * re-runs this when an activity is (re)created, so it stays correct after language changes.
     */
    private fun resolve(tag: String): Map<String, String> {
        if (tag != AppLanguage.SYSTEM) return TABLES[tag] ?: emptyMap()
        val lang = try {
            Locale.getDefault().language
        } catch (t: Throwable) {
            Resources.getSystem().configuration.locales[0].language
        }
        if (lang.isNullOrEmpty()) return emptyMap()
        return TABLES.entries
            .firstOrNull { (key, _) -> key.equals(lang, true) || key.startsWith("$lang-", true) }
            ?.value ?: emptyMap()
    }

    /** Returns the current language's rendering of [en], or [en] itself when untranslated. */
    fun t(en: String): String {
        val m = table
        if (m.isEmpty()) return en
        return m[en] ?: en
    }
}

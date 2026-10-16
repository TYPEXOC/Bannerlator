package com.winlator.star.i18n

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * App-wide UI language, selectable in Settings › Appearance and independent of the device locale.
 *
 * The choice is persisted in its own SharedPreferences file and applied through
 * [AppCompatDelegate.setApplicationLocales], so every AppCompat activity picks up the selected
 * locale (and the OS recreates the running ones when it changes). On Android 13+ the same value is
 * mirrored into the system's per-app language settings through the manifest's `localeConfig`.
 *
 * The Compose side reads [isChinese] through [Cn]: most of Bannerlator's Compose text is embedded
 * as literals rather than string resources, so [Cn.t] translates those by value instead of by
 * resource id.
 */
object AppLanguage {
    const val SYSTEM = "system"
    const val ENGLISH = "en"
    const val CHINESE = "zh-CN"

    /** A language offered in Settings › Appearance; labels are shown in their own language. */
    data class LanguageOption(val tag: String, val label: String)

    /** The languages listed in Settings › Appearance, in display order.
     *  Adding a language = one entry here + its table in [I18n] + its `values-<tag>` resources. */
    val options: List<LanguageOption> = listOf(
        LanguageOption(SYSTEM, "System default"),
        LanguageOption(ENGLISH, "English"),
        LanguageOption(CHINESE, "简体中文"),
    )

    private const val TAG = "AppLanguage"
    private const val PREFS = "winlator_language"
    private const val KEY_LANGUAGE = "app_language"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var selected: String = SYSTEM

    /** Fast lookup for the Compose translation table; refreshed by [init] and [set]. */
    @Volatile
    var isChinese: Boolean = false
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        selected = normalize(p.getString(KEY_LANGUAGE, SYSTEM) ?: SYSTEM)
        isChinese = selected == CHINESE
        I18n.setLanguage(selected)
    }

    fun get(context: Context): String {
        init(context)
        return selected
    }

    /** Persist a new choice and apply it. AppCompat recreates the running activities itself. */
    fun set(context: Context, tag: String) {
        init(context)
        val v = normalize(tag)
        val changed = v != selected
        prefs?.edit()?.putString(KEY_LANGUAGE, v)?.apply()
        selected = v
        isChinese = v == CHINESE
        I18n.setLanguage(v)
        if (changed) applyLocales()
    }

    /** Called once from the Application before any Activity exists; idempotent. */
    fun applyAtStartup(context: Context) {
        init(context)
        applyLocales()
    }

    /** Re-resolves the active translation table for the CURRENT effective locale. Called when an
     *  activity is (re)created, so "System default" follows the device (or a system-level per-app
     *  language) even right after a language change recreated the UI. Cheap and idempotent. */
    fun refresh(context: Context) {
        init(context)
        I18n.setLanguage(selected)
    }

    private fun normalize(tag: String): String = when {
        tag.equals(CHINESE, true) || tag.equals("zh", true) || tag.startsWith("zh-", true) -> CHINESE
        tag.equals(ENGLISH, true) || tag.startsWith("en-", true) -> ENGLISH
        else -> SYSTEM
    }

    private fun applyLocales() {
        try {
            val want = if (selected == SYSTEM) null else selected
            val now = AppCompatDelegate.getApplicationLocales()
            val nowTag = if (now.isEmpty) null else now.toLanguageTags()
            if (want != nowTag) {
                AppCompatDelegate.setApplicationLocales(
                    if (want == null) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(want)
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "applyLocales failed", t)
        }
    }
}

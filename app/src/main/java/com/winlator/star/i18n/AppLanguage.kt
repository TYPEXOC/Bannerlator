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
        if (changed) applyLocales()
    }

    /** Called once from the Application before any Activity exists; idempotent. */
    fun applyAtStartup(context: Context) {
        init(context)
        applyLocales()
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

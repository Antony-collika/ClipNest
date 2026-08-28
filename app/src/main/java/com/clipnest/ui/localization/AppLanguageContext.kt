package com.clipnest.ui.localization

import android.content.Context
import android.content.res.Configuration
import com.clipnest.data.local.AppLanguage
import java.util.Locale

fun Context.withAppLanguage(language: AppLanguage): Context {
    val locale = when (language) {
        AppLanguage.ENGLISH -> Locale.ENGLISH
        AppLanguage.VIETNAMESE -> Locale.forLanguageTag("vi")
    }
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}

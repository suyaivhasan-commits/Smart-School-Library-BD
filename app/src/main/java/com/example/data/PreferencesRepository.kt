package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.i18n.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "smart_school_library_prefs")

enum class ThemeMode(val code: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        fun fromCode(code: String?): ThemeMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}

data class UserPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: AppLanguage = AppLanguage.BN,
    val onboardingCompleted: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val isLoaded: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("app_language")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.applicationContext.appDataStore.data.map { prefs ->
        UserPreferencesState(
            themeMode = ThemeMode.fromCode(prefs[Keys.THEME_MODE]),
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE]),
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
            isLoaded = true
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.applicationContext.appDataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.code
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.applicationContext.appDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.applicationContext.appDataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.applicationContext.appDataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled
        }
    }
}

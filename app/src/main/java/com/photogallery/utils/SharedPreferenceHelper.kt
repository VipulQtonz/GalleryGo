package com.photogallery.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SharedPreferenceHelper
private constructor(
    context: Context, prefName: String,
    mode: Int
) {
    private val sharedPref: SharedPreferences = context.getSharedPreferences(prefName, mode)

    fun getString(key: String, defaultValue: String): String? {
        return sharedPref.getString(key, defaultValue)
    }

    fun getInt(key: String?, defaultValue: Int): Int {
        return sharedPref.getInt(key, defaultValue)
    }

    fun clearKey(key: String?) {
        sharedPref.edit { remove(key) }
    }

    fun getBoolean(key: String?, defaultValue: Boolean): Boolean {
        return try {
            sharedPref.getBoolean(key, defaultValue)
        } catch (_: Exception) {
            false
        }
    }

    fun setString(key: String?, value: String?) {
        sharedPref.edit {
            putString(key, value)
        }
    }

    fun putBoolean(key: String?, defaultValue: Boolean): Int {
        sharedPref.edit {
            putBoolean(key, defaultValue)
        }
        return 0
    }

    fun putInt(key: String?, defaultValue: Int): Int {
        sharedPref.edit {
            putInt(key, defaultValue)
        }
        return 0
    }

    fun putString(key: String?, defaultValue: String?): Int {
        sharedPref.edit {
            putString(key, defaultValue)
        }
        return 0
    }

    fun putLong(key: String?, value: Long): Int {
        sharedPref.edit {
            putLong(key, value)
        }
        return 0
    }

    fun getLong(key: String?, defaultValue: Long): Long {
        return sharedPref.getLong(key, defaultValue)
    }

    fun getStringSet(key: String, defaultValue: Set<String> = emptySet()): Set<String> {
        return sharedPref.getStringSet(key, defaultValue) ?: defaultValue
    }

    fun putStringSet(key: String, value: Set<String>): Int {
        sharedPref.edit {
            putStringSet(key, value)
        }
        return 0
    }

    companion object {
        private const val PREF_NAME: String = "photo_gallery_pref"
        private const val MODE_PRIVATE = 0
        internal const val ALBUMS_KEY = "albums_key"
        const val PREF_THEME = "app_theme"

        const val RATE_US = "RATE_US"
        const val IS_NOTIFICATION_ON = "is_notification_on"
        const val PREF_APP_COLOR = "pref_app_color"

        fun getInstance(context: Context): SharedPreferenceHelper {
            return SharedPreferenceHelper(context, PREF_NAME, MODE_PRIVATE)
        }
    }
}

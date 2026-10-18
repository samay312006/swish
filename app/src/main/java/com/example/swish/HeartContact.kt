package com.example.swish

import android.content.Context

/**
 * The phone number pre-filled in the dialer when a "heart" gesture is drawn.
 * Stored on the device only (SharedPreferences), never sent anywhere.
 */
object HeartContact {
    private const val PREFS = "swish_settings"
    private const val KEY_NUMBER = "heart_contact_number"

    /** The saved number, or null if the user hasn't set one yet. */
    fun get(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NUMBER, null)
            ?.takeIf { it.isNotBlank() }

    fun set(context: Context, number: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NUMBER, number.trim())
            .apply()
    }
}

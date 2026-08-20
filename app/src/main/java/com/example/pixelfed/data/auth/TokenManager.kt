package com.example.pixelfed.data.auth

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("pixelfed_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_INSTANCE_URL = "instance_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_CLIENT_SECRET = "client_secret"
    }

    var instanceUrl: String?
        get() = prefs.getString(KEY_INSTANCE_URL, null)
        set(value) = prefs.edit().putString(KEY_INSTANCE_URL, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var clientId: String?
        get() = prefs.getString(KEY_CLIENT_ID, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_ID, value).apply()

    var clientSecret: String?
        get() = prefs.getString(KEY_CLIENT_SECRET, null)
        set(value) = prefs.edit().putString(KEY_CLIENT_SECRET, value).apply()

    fun isLoggedIn(): Boolean {
        return !accessToken.isNullOrBlank() && !instanceUrl.isNullOrBlank()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}

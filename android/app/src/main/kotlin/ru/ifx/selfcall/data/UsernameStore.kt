package ru.ifx.selfcall.data

import android.content.Context

class UsernameStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("selfcall", Context.MODE_PRIVATE)

    var username: String
        get() = prefs.getString(KEY, "").orEmpty()
        set(value) { prefs.edit().putString(KEY, value).apply() }

    fun clear() { prefs.edit().remove(KEY).apply() }

    companion object { private const val KEY = "username" }
}

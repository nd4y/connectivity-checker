package com.connectivity.checker.settings

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var vmUrl: String
        get() = prefs.getString("vm_url", "") ?: ""
        set(v) = prefs.edit().putString("vm_url", v).apply()

    var vmUsername: String
        get() = prefs.getString("vm_username", "") ?: ""
        set(v) = prefs.edit().putString("vm_username", v).apply()

    var vmPassword: String
        get() = prefs.getString("vm_password", "") ?: ""
        set(v) = prefs.edit().putString("vm_password", v).apply()

    val isVmConfigured: Boolean get() = vmUrl.isNotBlank()

    var savedChecksYaml: String
        get() = prefs.getString("saved_checks_yaml", "") ?: ""
        set(v) = prefs.edit().putString("saved_checks_yaml", v).apply()
}

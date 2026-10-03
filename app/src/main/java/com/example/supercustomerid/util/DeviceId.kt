package com.example.supercustomerid.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

object DeviceId {
    @SuppressLint("HardwareIds")
    fun get(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?: "unknown-device"
    }
}

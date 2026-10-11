package com.spotlin.messenger.util

import android.util.Log
import com.spotlin.messenger.BuildConfig

object SpotlinLogger {
    private const val TAG = "SpotlinDebug"

    fun d(tag: String = TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, "[Spotlin-DEBUG] $message")
        }
    }

    fun i(tag: String = TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, "[Spotlin-INFO] $message")
        }
    }

    fun w(tag: String = TAG, message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(tag, "[Spotlin-WARN] $message")
        }
    }

    fun e(tag: String = TAG, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e(tag, "[Spotlin-ERROR] $message", throwable)
            } else {
                Log.e(tag, "[Spotlin-ERROR] $message")
            }
        }
    }
}

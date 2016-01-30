package com.hotpodata.blockelganger

import android.content.Context

/**
 * Created by jdrotos on 1/30/16.
 */
object SettingsMaster {

    val APP_PREFS = "APP_PREFS"
    val STORAGE_KEY_LAUNCH_COUNT = "STORAGE_KEY_LAUNCH_COUNT"
    val STORAGE_KEY_LAST_SEEN_VERSION = "STORAGE_KEY_LAST_SEEN_VERSION"
    val STORAGE_KEY_AUTO_SIGN_IN = "STORAGE_KEY_AUTO_SIGN_IN"

    var context: Context? = null

    var launchCount: Int
        set(launches: Int) {
            context?.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)?.edit()?.let {
                it.putInt(STORAGE_KEY_LAUNCH_COUNT, launches);
                it.commit()
            }
        }
        get() {
            return context?.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)?.let {
                it.getInt(STORAGE_KEY_LAUNCH_COUNT, 0)
            } ?: 0
        }

    var lastSeenVersionCode: Int
        set(launches: Int) {
            context?.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)?.edit()?.let {
                it.putInt(STORAGE_KEY_LAST_SEEN_VERSION, launches);
                it.commit()
            }
        }
        get() {
            return context?.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)?.let {
                it.getInt(STORAGE_KEY_LAST_SEEN_VERSION, 0)
            } ?: 0
        }

    var autoStartSignInFlow: Boolean
        set(signInOnStart: Boolean) {
            context?.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)?.edit()?.let {
                it.putBoolean(STORAGE_KEY_AUTO_SIGN_IN, signInOnStart);
                it.commit()
            }
        }
        get() {
            return context?.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)?.let {
                it.getBoolean(STORAGE_KEY_AUTO_SIGN_IN, false)
            } ?: false
        }
}
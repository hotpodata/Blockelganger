package com.hotpodata.blockelganger

import android.support.multidex.MultiDexApplication
import timber.log.Timber

/**
 * Created by jdrotos on 1/4/16.
 */
class BlockelgangerApplication : MultiDexApplication() {
    public override fun onCreate() {
        super.onCreate()
        if (BuildConfig.LOGGING_ENABLED) {
            Timber.plant(Timber.DebugTree())
        }
        //Required
        SettingsMaster.context = this
    }
}

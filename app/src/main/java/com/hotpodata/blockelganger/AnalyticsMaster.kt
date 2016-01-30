package com.hotpodata.blockelganger

import android.content.Context
import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.Tracker
import com.hotpodata.common.interfaces.IAnalyticsProvider

/**
 * Created by jdrotos on 11/18/15.
 */
object AnalyticsMaster : IAnalyticsProvider{

    //SCREENS
    val SCREEN_SINGLE_PLAYER = "SinglePlayerGameScreen"
    val SCREEN_LANDING = "LandingScreen"

    //CATEGORIES
    val CATEGORY_ACTION = "Action"

    //ACTIONS
    val ACTION_START_GAME = "StartGame"
    val ACTION_GAME_OVER = "GameOver"
    val ACTION_PAUSE = "Pause"
    val ACTION_RESUME = "Resume"
    val ACTION_START_OVER = "StartOver"
    val ACTION_HELP = "Help"
    val ACTION_UPDATE_FRAG = "UpdateFragment"
    val ACTION_OPEN_DRAWER = "OpenDrawer"
    val ACTION_SIGN_IN = "SignInClicked"
    val ACTION_ACHIEVEMENTS = "AchievementsClicked"
    val ACTION_LEADERBOARD = "LeaderBoardClicked"
    val ACTION_LEVEL_COMPLETE = "LevelComplete"
    val ACTION_EARLY_SMASH = "EarlySmash"

    //Labels
    val LABEL_LEVEL = "Level"
    val LABEL_LAUNCH_COUNT = "LaunchCount"
    val LABEL_SECONDS_REMAINING = "SecondsRemaining"

    private var tracker: Tracker? = null
    public override fun getTracker(context: Context): Tracker {
        val t = tracker ?:
                GoogleAnalytics.getInstance(context).newTracker(R.xml.global_tracker).apply {
                    enableExceptionReporting(true)
                    enableAdvertisingIdCollection(true)
                }
        tracker = t
        return t
    }
}
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
    val SCREEN_GAME = "BlockelGangerGame"

    //CATEGORIES
    val CATEGORY_ACTION = "Action"

    //ACTIONS
    val ACTION_START_GAME = "StartGame"
    val ACTION_GAME_OVER = "GameOver"
    val ACTION_PAUSE = "Pause"
    val ACTION_RESUME = "Resume"
    val ACTION_START_OVER = "StartOver"
    val ACTION_HELP = "Help"
    val ACTION_OPEN_DRAWER = "OpenDrawer"
    val ACTION_SIGN_IN = "SignInClicked"
    val ACTION_ACHIEVEMENTS = "AchievementsClicked"
    val ACTION_LEADERBOARD = "LeaderBoardClicked"
    val ACTION_LEVEL_COMPLETE = "LevelComplete"

    val ACTION_RATE_APP = "Rate_App"
    val ACTION_CONTACT = "Contact"
    val ACTION_WEBSITE = "Website"
    val ACTION_TWITTER = "Twitter"
    val ACTION_GITHUB = "GitHub"

    val ACTION_FILECAT = "FileCat"
    val ACTION_REDCHAIN = "RedChain"
    val ACTION_WIKICAT = "WikiCat"
    val ACTION_BACONMASHER = "BaconMasher"

    //Labels
    val LABEL_LEVEL = "Level"
    val LABEL_LAUNCH_COUNT = "LaunchCount"

    private var tracker: Tracker? = null
    public override fun getTracker(context: Context): Tracker {
        val t = tracker ?:
                GoogleAnalytics.getInstance(context).newTracker(R.xml.global_tracker).apply {
                    enableExceptionReporting(true)
                    enableAdvertisingIdCollection(true)
                    enableAutoActivityTracking(true)
                }
        tracker = t
        return t
    }
}
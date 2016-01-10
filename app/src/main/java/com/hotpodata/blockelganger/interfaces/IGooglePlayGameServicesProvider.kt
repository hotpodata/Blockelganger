package com.hotpodata.blockelganger.interfaces

/**
 * Created by jdrotos on 1/6/16.
 */
interface IGooglePlayGameServicesProvider {
    fun isLoggedIn(): Boolean
    fun login()
    fun logout()
    fun showLeaderBoard()
    fun showAchievements()
}
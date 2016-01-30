package com.hotpodata.blockelganger.activity

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.MenuItem
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.games.Games
import com.hotpodata.blockelganger.AnalyticsMaster
import com.hotpodata.blockelganger.R
import com.hotpodata.blockelganger.SettingsMaster
import com.hotpodata.blockelganger.adapter.BlockelgangerSideBarAdapter
import com.hotpodata.blockelganger.adapter.StartupAdapter
import com.hotpodata.blockelganger.fragment.DialogHowToPlayFragment
import com.hotpodata.blockelganger.fragment.DialogUpdateFragment
import com.hotpodata.blockelganger.interfaces.IGooglePlayGameServicesProvider
import com.hotpodata.blockelganger.utils.BaseGameUtils
import com.hotpodata.common.activity.ChameleonActivity
import com.hotpodata.common.view.SizeAwareFrameLayout
import kotlinx.android.synthetic.main.activity_game.*
import timber.log.Timber

class GameActivity : ChameleonActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, IGooglePlayGameServicesProvider {

    val REQUEST_LEADERBOARD = 1
    val REQUEST_ACHIEVEMENTS = 2
    val RC_SIGN_IN = 9001


    val FTAG_HOW_TO_PLAY = "FTAG_HOW_TO_PLAY"
    val FTAG_UPDATES = "FTAG_UPDATES"

    var sideBarAdapter: BlockelgangerSideBarAdapter? = null
    var startupAdapter: StartupAdapter? = null
    var drawerToggle: ActionBarDrawerToggle? = null

    //Sign in stuff
    var resolvingConnectionFailure = false
    var signInClicked = false;
    var _googleApiClient: GoogleApiClient? = null
    val googleApiClient: GoogleApiClient
        get() {
            if (_googleApiClient == null) {
                _googleApiClient = GoogleApiClient.Builder(this)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .addApi(Games.API)
                        .addScope(Games.SCOPE_GAMES)
                        .build();
            }
            return _googleApiClient!!
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        drawerToggle = object : ActionBarDrawerToggle(this, drawer_layout, R.string.drawer_open, R.string.drawer_closed) {
            override fun onDrawerOpened(drawerView: View?) {
                try {
                    AnalyticsMaster.getTracker(this@GameActivity).send(HitBuilders.EventBuilder()
                            .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                            .setAction(AnalyticsMaster.ACTION_OPEN_DRAWER)
                            .build());
                } catch(ex: Exception) {
                    Timber.e(ex, "Analytics Exception");
                }
            }

            override fun onDrawerClosed(drawerView: View?) {
            }
        }
        drawer_layout.setDrawerListener(drawerToggle)
        sidebar_drawer_btn.setOnClickListener {
            if (drawer_layout.isDrawerOpen(left_drawer)) {
                drawer_layout.closeDrawer(left_drawer)
            } else {
                drawer_layout.openDrawer(left_drawer)
            }
        }

        setUpLeftDrawer()
        setUpStartUpAdapter()

        body_container.sizeChangeListener = object : SizeAwareFrameLayout.ISizeChangeListener {
            override fun onSizeChange(w: Int, h: Int, oldw: Int, oldh: Int) {
                if (h != oldh) {
                    startupAdapter?.spacerHeight = 2 * h / 3
                }
            }
        }


        start_options_recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            var dyInterpolatr = DecelerateInterpolator()
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                //man_in_mirror.scaleX = (man_in_mirror.scaleX + )
                var scaleChange = dy / (body_container.height / 2f)
                man_in_mirror.scaleY = Math.max(1f, Math.min(1.2f, man_in_mirror.scaleY + scaleChange))
                man_in_mirror.scaleX = Math.max(1f, Math.min(1.2f, man_in_mirror.scaleX + scaleChange))
                man_in_mirror.translationY = (man_in_mirror.translationY - dy / 2f)
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
            }
        })

    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle?.syncState()
    }

    override fun onStart() {
        super.onStart()
        if (SettingsMaster.autoStartSignInFlow) {
            googleApiClient.connect();
        }
    }

    override fun onResume() {
        super.onResume()
        AnalyticsMaster.getTracker(this).setScreenName(AnalyticsMaster.SCREEN_LANDING);
        AnalyticsMaster.getTracker(this).send(HitBuilders.ScreenViewBuilder().build());
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        googleApiClient.disconnect()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        super.onActivityResult(requestCode, resultCode, intent)
        if (requestCode == RC_SIGN_IN) {
            signInClicked = false;
            resolvingConnectionFailure = false;
            if (resultCode == RESULT_OK) {
                googleApiClient.connect();
            } else {
                BaseGameUtils.showActivityResultError(this,
                        requestCode, resultCode, R.string.signin_error);
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle?.onConfigurationChanged(newConfig)
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(left_drawer) ?: false ) {
            drawer_layout.closeDrawers()
        } else {
            super.onBackPressed()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return drawerToggle?.onOptionsItemSelected(item) ?: false
    }


    /**
     * Sets up the left drawer adapter and adds it to the recyclerview
     */
    fun setUpLeftDrawer() {
        if (sideBarAdapter == null) {
            sideBarAdapter = with(BlockelgangerSideBarAdapter(this, this)) {
                setAccentColor(android.support.v4.content.ContextCompat.getColor(this@GameActivity, R.color.colorPrimary))
                this
            }
            left_drawer.adapter = sideBarAdapter
            left_drawer.layoutManager = LinearLayoutManager(this)
        }
    }

    fun setUpStartUpAdapter() {
        if (startupAdapter == null) {
            startupAdapter = with(StartupAdapter(this)) {
                googleServicesProvider = this@GameActivity
                rebuildRowSet()
                this
            }
            start_options_recycler.adapter = startupAdapter
            start_options_recycler.layoutManager = LinearLayoutManager(this)
        }
    }


    fun showHowToPlayDialog() {
        var frag: DialogHowToPlayFragment? = supportFragmentManager.findFragmentByTag(FTAG_HOW_TO_PLAY) as DialogHowToPlayFragment?
        if (frag == null) {
            frag = DialogHowToPlayFragment()
        }
        if (!frag.isAdded) {
            frag.show(supportFragmentManager, FTAG_HOW_TO_PLAY)
        }

        try {
            AnalyticsMaster.getTracker(this).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_HELP)
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }
    }

    fun showUpdatesDialog(backToVersion: Int) {
        var frag: DialogUpdateFragment? = supportFragmentManager.findFragmentByTag(FTAG_UPDATES) as DialogUpdateFragment?
        if (frag == null) {
            frag = DialogUpdateFragment(backToVersion)
        }
        if (!frag.isAdded) {
            frag.show(supportFragmentManager, FTAG_UPDATES)
        }

        try {
            AnalyticsMaster.getTracker(this).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_UPDATE_FRAG)
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }
    }


    /**
     * IGoolgePlayGameServicesProvider
     */

    override fun isLoggedIn(): Boolean {
        return googleApiClient.isConnected
    }

    override fun login() {
        signInClicked = true
        SettingsMaster.autoStartSignInFlow = true
        googleApiClient.connect();
        try {
            AnalyticsMaster.getTracker(this@GameActivity).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_SIGN_IN)
                    .setLabel(AnalyticsMaster.LABEL_LAUNCH_COUNT)
                    .setValue(SettingsMaster.launchCount.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }
    }

    override fun logout() {
        signInClicked = false
        SettingsMaster.autoStartSignInFlow = false
        if (isLoggedIn()) {
            Games.signOut(googleApiClient)
            googleApiClient.disconnect()
            sideBarAdapter?.rebuildRowSet()
        }
    }

    override fun showLeaderBoard() {
        if (isLoggedIn()) {
            startActivityForResult(Games.Leaderboards.getLeaderboardIntent(googleApiClient,
                    getString(R.string.leaderboard_scores)), REQUEST_LEADERBOARD);
        } else {
            Toast.makeText(this, R.string.you_must_be_signed_in, Toast.LENGTH_SHORT).show()
        }
        try {
            AnalyticsMaster.getTracker(this@GameActivity).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_LEADERBOARD)
                    .setLabel(AnalyticsMaster.LABEL_LAUNCH_COUNT)
                    .setValue(SettingsMaster.launchCount.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }
    }

    override fun showAchievements() {
        if (isLoggedIn()) {
            startActivityForResult(Games.Achievements.getAchievementsIntent(googleApiClient),
                    REQUEST_ACHIEVEMENTS);
        } else {
            Toast.makeText(this, R.string.you_must_be_signed_in, Toast.LENGTH_SHORT).show()
        }
        try {
            AnalyticsMaster.getTracker(this@GameActivity).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_ACHIEVEMENTS)
                    .setLabel(AnalyticsMaster.LABEL_LAUNCH_COUNT)
                    .setValue(SettingsMaster.launchCount.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }
    }


    /**
     * SIGN IN STUFF
     */

    override fun onConnected(connectionHint: Bundle?) {
        Timber.d("SignIn - onConnected")
        sideBarAdapter?.rebuildRowSet()
    }

    override fun onConnectionSuspended(p0: Int) {
        Timber.d("SignIn - onConnectionSuspended")
        googleApiClient.connect()
    }

    override fun onConnectionFailed(result: ConnectionResult?) {
        Timber.d("SignIn - onConnectionFailed")
        if (resolvingConnectionFailure) {
            // already resolving
            return
        }

        // if the sign-in button was clicked or if auto sign-in is enabled,
        // launch the sign-in flow
        if (signInClicked || SettingsMaster.autoStartSignInFlow) {
            SettingsMaster.autoStartSignInFlow = false
            signInClicked = false
            resolvingConnectionFailure = true

            // Attempt to resolve the connection failure using BaseGameUtils.
            // The R.string.signin_other_error value should reference a generic
            // error string in your strings.xml file, such as "There was
            // an issue with sign-in, please try again later."
            if (!BaseGameUtils.resolveConnectionFailure(this,
                    googleApiClient, result,
                    RC_SIGN_IN, getString(R.string.sign_in_failed))) {
                resolvingConnectionFailure = false
            }
        }
    }


}

package com.hotpodata.blockelganger.activity

import android.animation.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.games.Games
import com.hotpodata.blockelganger.AnalyticsMaster
import com.hotpodata.blockelganger.BuildConfig
import com.hotpodata.blockelganger.R
import com.hotpodata.blockelganger.adapter.BlockelgangerSideBarAdapter
import com.hotpodata.blockelganger.fragment.DialogHowToPlayFragment
import com.hotpodata.blockelganger.helpers.ColorBlockDrawer
import com.hotpodata.blockelganger.helpers.GameGridHelper
import com.hotpodata.blockelganger.helpers.GameHelper
import com.hotpodata.blockelganger.helpers.GridTouchListener
import com.hotpodata.blockelganger.interfaces.IGooglePlayGameServicesProvider
import com.hotpodata.blockelganger.utils.BaseGameUtils
import com.hotpodata.blocklib.Grid
import com.hotpodata.common.activity.ChameleonActivity
import com.hotpodata.common.utils.HashUtils
import com.hotpodata.common.view.SizeAwareFrameLayout
import kotlinx.android.synthetic.main.activity_game.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

class GameActivity : ChameleonActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, IGooglePlayGameServicesProvider, GridTouchListener.ITouchCoordinator {

    val REQUEST_LEADERBOARD = 1
    val REQUEST_ACHIEVEMENTS = 2
    val RC_SIGN_IN = 9001
    val STORAGE_KEY_AUTO_SIGN_IN = "STORAGE_KEY_AUTO_SIGN_IN"
    val STORAGE_KEY_LAUNCH_COUNT = "STORAGE_KEY_LAUNCH_COUNT"
    val FTAG_HOW_TO_PLAY = "FTAG_HOW_TO_PLAY"

    var points = 0
        set(pts: Int) {
            field = pts
            sidebar_points_tv.text = "" + pts
        }

    var level = 0
        set(lvl: Int) {
            field = lvl
            sidebar_level_tv.text = "" + lvl
        }

    val chapter: GameHelper.Chapter
        get() {
            return GameHelper.chapterForLevel(level)
        }


    var topGrid = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_top.grid = grd
        }

    var btmGrid = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_btm.grid = grd
        }

    var rightGrid = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_right.grid = grd
        }

    var gangerGrid = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_blockelganger.grid = grd
        }

    var subTicker: Subscription? = null
    var spentTicks = 0L
    var random = Random()
    var actionAnimator: Animator? = null
    var countDownAnimator: Animator? = null
    var gridHelpTextAnim: Animator? = null

    var sideBarAdapter: BlockelgangerSideBarAdapter? = null
    var drawerToggle: ActionBarDrawerToggle? = null

    var touchedInTick = false
    var noTouchStreak = 0

    var activityResumed = false


    var paused = false
        set(pause: Boolean) {
            field = pause
            updateGameStateVisibilities()
        }

    var gameover = false
        set(gOver: Boolean) {
            //Submit the score on gameover
            if (isLoggedIn() && !field && gOver) {
                Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_scores), points.toLong())
            }
            field = gOver
            gamestarted = false
            updateGameStateVisibilities()
        }

    var gamestarted = false
        set(started: Boolean) {
            field = started
            updateGameStateVisibilities()
        }

    //Launch stats
    var launchCount: Int
        set(launches: Int) {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            with(sharedPref.edit()) {
                putInt(STORAGE_KEY_LAUNCH_COUNT, launches);
                commit()
            }
        }
        get() {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            return sharedPref.getInt(STORAGE_KEY_LAUNCH_COUNT, 0)
        }

    //Sign in stuff
    var resolvingConnectionFailure = false
    var autoStartSignInFlow: Boolean
        set(signInOnStart: Boolean) {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            with(sharedPref.edit()) {
                putBoolean(STORAGE_KEY_AUTO_SIGN_IN, signInOnStart);
                commit()
            }
        }
        get() {
            var sharedPref = getPreferences(Context.MODE_PRIVATE);
            return sharedPref.getBoolean(STORAGE_KEY_AUTO_SIGN_IN, false)
        }
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

    //ads
    var interstitialAd: InterstitialAd? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        //set up sidebar buttons
        sidebar_drawer_btn.setOnClickListener {
            drawer_layout.openDrawer(left_drawer)
        }
        sidebar_play_btn.setOnClickListener {
            actionResumeGame()
        }
        sidebar_pause_btn.setOnClickListener {
            actionPauseGame()
        }
        sidebar_help_btn.setOnClickListener {
            showHowToPlayDialog()
        }

        //Setup our click actions
        play_btn.setOnClickListener {
            actionStartGame()
        }
        stopped_start_over_btn.setOnClickListener {
            actionStartOver()
        }
        stopped_continue_btn.setOnClickListener {
            actionResumeGame()
        }
        stopped_leader_board_btn.setOnClickListener {
            showLeaderBoard()
        }
        stopped_sign_in_button.setOnClickListener {
            login()
        }

        //Set up the drawer
        setUpLeftDrawer()
        drawerToggle = object : ActionBarDrawerToggle(this, drawer_layout, R.string.drawer_open, R.string.drawer_closed) {
            override fun onDrawerOpened(drawerView: View?) {
                if (gamestarted && !gameover) {
                    actionPauseGame()
                }
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                try {
                    AnalyticsMaster.getTracker(this@GameActivity).send(HitBuilders.EventBuilder()
                            .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                            .setAction(AnalyticsMaster.ACTION_OPEN_DRAWER)
                            .setLabel(AnalyticsMaster.LABEL_LEVEL)
                            .setValue(level.toLong())
                            .build());
                } catch(ex: Exception) {
                    Timber.e(ex, "Analytics Exception");
                }
            }

            override fun onDrawerClosed(drawerView: View?) {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }
        drawer_layout.setDrawerListener(drawerToggle)
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);


        //Set our gridviews
        gridbinderview_top.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.top_grid))
        gridbinderview_btm.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.btm_grid))
        gridbinderview_right.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.right_grid))
        gridbinderview_blockelganger.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.ganger_grid))
        gridbinderview_top.setOnTouchListener(GridTouchListener(this, object : GridTouchListener.IGridChangedListener {
            override fun onGridChanged(grid: Grid) {
                topGrid = grid
            }
        }))
        gridbinderview_btm.setOnTouchListener(GridTouchListener(this, object : GridTouchListener.IGridChangedListener {
            override fun onGridChanged(grid: Grid) {
                btmGrid = grid
            }
        }))
        gridbinderview_right.setOnTouchListener(GridTouchListener(this, object : GridTouchListener.IGridChangedListener {
            override fun onGridChanged(grid: Grid) {
                rightGrid = grid
            }
        }))



        //Set up ads..
        var ad = InterstitialAd(this);
        ad.setAdUnitId(getString(R.string.interstitial_add_unit_id))
        ad.adListener = object : AdListener() {
            override fun onAdClosed() {
                super.onAdClosed()
                requestNewInterstitial()
            }
        }
        interstitialAd = ad
        requestNewInterstitial()

        //Accounting
        if (savedInstanceState == null) {
            launchCount++
            if (launchCount <= 1) {
                showHowToPlayDialog()
            }
        }

        //Reset game state
        grid_container.sizeChangeListener = object : SizeAwareFrameLayout.ISizeChangeListener {
            override fun onSizeChange(w: Int, h: Int, oldw: Int, oldh: Int) {
                actionResetGame()
            }

        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle?.syncState()
    }

    override fun onStart() {
        super.onStart()
        if (autoStartSignInFlow) {
            googleApiClient.connect();
        }
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
        AnalyticsMaster.getTracker(this).setScreenName(AnalyticsMaster.SCREEN_GAME);
        AnalyticsMaster.getTracker(this).send(HitBuilders.ScreenViewBuilder().build());
    }

    override fun onPause() {
        super.onPause()
        if (gamestarted) {
            paused = true
        }
        activityResumed = false
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


    /**
     * Updates some of the pause/play/gameover/start visibilites and menu items
     */
    fun updateGameStateVisibilities() {
        var gameoverVis = gameover
        var pauseVis = !gameover && paused && gamestarted
        if (gameoverVis || pauseVis) {
            if (gameoverVis) {
                stopped_msg_tv.text = getString(R.string.game_over)
                stopped_continue_btn.visibility = View.GONE
                stopped_spacer.layoutParams.height = resources.getDimensionPixelSize(R.dimen.grid_height_with_margins)
                stopped_spacer.layoutParams = stopped_spacer.layoutParams
                stopped_container.setBackgroundColor(Color.TRANSPARENT)
                stopped_msg_tv.setTextColor(resources.getColor(R.color.white))
                stopped_tip_tv.setTextColor(resources.getColor(R.color.white))
                stopped_sign_in_blurb.setTextColor(resources.getColor(R.color.white))
            } else if (pauseVis) {
                stopped_msg_tv.text = getString(R.string.paused)
                stopped_continue_btn.visibility = View.VISIBLE
                stopped_spacer.layoutParams.height = 0
                stopped_spacer.layoutParams = stopped_spacer.layoutParams
                stopped_container.setBackgroundColor(resources.getColor(R.color.overlay_shade))
                stopped_msg_tv.setTextColor(resources.getColor(R.color.overlay_text))
                stopped_tip_tv.setTextColor(resources.getColor(R.color.overlay_text))
                stopped_sign_in_blurb.setTextColor(resources.getColor(R.color.overlay_text))
            }
            if (googleApiClient.isConnected) {
                stopped_signed_in_container.visibility = View.VISIBLE
                stopped_sign_in_container.visibility = View.GONE
            } else {
                stopped_signed_in_container.visibility = View.GONE
                stopped_sign_in_container.visibility = View.VISIBLE
            }
            stopped_container.visibility = View.VISIBLE
        } else {
            stopped_container.visibility = View.INVISIBLE
        }

        var startVis = !gamestarted && !gameover
        start_container.visibility = if (startVis) View.VISIBLE else View.INVISIBLE
        //supportInvalidateOptionsMenu()

        sidebar_play_btn.visibility = if (paused && !gameover && gamestarted) View.VISIBLE else View.GONE
        sidebar_pause_btn.visibility = if (!paused && !gameover && gamestarted) View.VISIBLE else View.GONE
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

    /**
     * Animate from the game over state, to the game started state
     */
    fun actionRestartGame() {
        var reset = genStartOverAnim()
        reset.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                actionResetGame()
            }
        })
        reset.start()
    }

    /**
     * If we are mid game: startover
     * If we are gameover: reset
     */
    fun actionStartOver() {
        try {
            AnalyticsMaster.getTracker(this).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_START_OVER)
                    .setLabel(AnalyticsMaster.LABEL_LEVEL)
                    .setValue(level.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }

        if (gameover) {
            actionRestartGame()
        } else {
            actionResetGame()
            actionStartGame()
        }
    }


    /**
     * This starts the game
     */
    fun actionStartGame() {
        try {
            AnalyticsMaster.getTracker(this).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_START_GAME)
                    .setLabel(AnalyticsMaster.LABEL_LAUNCH_COUNT)
                    .setValue(launchCount.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }


        var infoOutAnim = genHideInfoAnim(true)
        var startSmashAnim = genSmashAnim()
        var startAnim = AnimatorSet()
        startAnim.playSequentially(infoOutAnim, startSmashAnim)
        startAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                gamestarted = true
            }
        })
        actionAnimator = startAnim
        startAnim.start()


    }

    fun getColorForChapter(chapter: GameHelper.Chapter): Int {
        return when (chapter) {
            GameHelper.Chapter.ONE -> resources.getColor(R.color.chapter_one_color)
            GameHelper.Chapter.TWO -> resources.getColor(R.color.chapter_two_color)
            GameHelper.Chapter.THREE -> resources.getColor(R.color.chapter_three_color)
        }
    }

    /**
     * This resets the game
     */
    fun actionResetGame() {
        unsubscribeFromTicker()
        if (actionAnimator?.isRunning ?: false) {
            actionAnimator?.cancel()
        }
        if (countDownAnimator?.isRunning ?: false) {
            countDownAnimator?.cancel()
        }
        setGridHelpTextShowing(false)

        if (points > 0 && isLoggedIn()) {
            //Submit on reset, for the quitters
            Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_scores), points.toLong())
        }

        level = 0
        points = 0
        touchedInTick = false
        noTouchStreak = 0
        spentTicks = 0L
        gameover = false
        paused = false
        gamestarted = false

        setLayoutForChapter(chapter)
        setColor(getColorForChapter(chapter), true)

        topGrid = GameGridHelper.genFullGrid(1, 1, true)
        btmGrid = GameGridHelper.genFullGrid(1, 1, true)
        rightGrid = GameGridHelper.genFullGrid(1, 1, true)
        gangerGrid = GameGridHelper.genFullGrid(1, 1, true)

        grid_container.scaleX = 1f
        grid_container.scaleY = 1f
        stopped_container.scaleX = 1f
        stopped_container.scaleY = 1f
        gridbinderview_top.translationY = 0f
        gridbinderview_btm.translationY = 0f
        gridbinderview_btm.visibility = View.GONE
        gridbinderview_blockelganger.translationY = getDefaultGangerTransYForChapter(chapter)
        gridbinderview_blockelganger.translationX = getDefaultGangerTransXForChapter(chapter)
        stopped_container.visibility = View.INVISIBLE
    }

    /**
     * Pause the game
     */
    fun actionPauseGame() {
        if (!paused) {
            unsubscribeFromTicker()
            paused = true
            if (actionAnimator?.isRunning ?: false) {
                actionAnimator?.pause()
            }
            if (countDownAnimator?.isRunning ?: false) {
                countDownAnimator?.pause()
            }
            if (gridHelpTextAnim?.isStarted ?: false) {
                gridHelpTextAnim?.pause()
            }


            try {
                AnalyticsMaster.getTracker(this).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_PAUSE)
                        .setLabel(AnalyticsMaster.LABEL_LEVEL)
                        .setValue(level.toLong())
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }
        }
    }

    /**
     * Resume a paused game
     */
    fun actionResumeGame() {
        if (paused) {
            paused = false
            if (actionAnimator?.isPaused ?: false) {
                actionAnimator?.resume()//This resubscribes at the end
            } else {
                if (countDownAnimator?.isPaused ?: false) {
                    countDownAnimator?.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            subscribeToTicker(spentTicks.toInt())
                        }
                    })
                    countDownAnimator?.resume()


                } else {
                    subscribeToTicker(spentTicks.toInt())
                }

                if (gridHelpTextAnim?.isPaused ?: false) {
                    gridHelpTextAnim?.resume()
                }
            }

            try {
                AnalyticsMaster.getTracker(this).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_RESUME)
                        .setLabel(AnalyticsMaster.LABEL_LEVEL)
                        .setValue(level.toLong())
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }
        }
    }

    /**
     * This is effectively the game loop, it does count downs and what not
     */
    fun subscribeToTicker(spentSeconds: Int = 0) {
        unsubscribeFromTicker()

        var seconds = GameHelper.secondForLevel(level)
        spentTicks = spentSeconds.toLong()
        subTicker = Observable.interval(1, TimeUnit.SECONDS).startWith(-1L)
                .filter({ l -> allowGameActions() })//So we don't do anything
                .take(1 + seconds - spentTicks.toInt())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            l ->
                            if (spentTicks < seconds) {
                                var tickPoints = 0
                                if (!touchedInTick) {
                                    noTouchStreak += 1
                                    for (i in 0..noTouchStreak) {
                                        tickPoints += i * 10
                                    }
                                }
                                touchedInTick = false
                                points += tickPoints

                                if (tickPoints > 0) {
                                    countdown_points.text = getString(R.string.countdown_points_template, tickPoints)
                                } else {
                                    countdown_points.text = ""
                                }
                                countdown_tv.text = "" + (seconds - spentTicks)
                                var anim = genCountDownOutAnim(resources.getColor(R.color.countdown_flash_color))
                                countDownAnimator = anim
                                anim.start()
                            }
                            spentTicks++
                        }
                        ,
                        {
                            ex ->
                            Timber.e(ex, "Fail!")
                        },
                        {
                            var anim = genSmashAnim()
                            actionAnimator = anim
                            anim.start()
                        }

                )

    }

    /**
     * Stop the game loop
     */
    fun unsubscribeFromTicker() {
        subTicker?.let { if (!it.isUnsubscribed) it.unsubscribe() }
    }

    /**
     * Should we allow the user to perform actions?
     */
    fun allowGameActions(): Boolean {
        if (!gamestarted || paused || actionAnimator?.isRunning() ?: false) {
            return false
        }
        return true
    }

    /**
     * Init the grids according to the level and bind them to the views
     */
    fun initGridsForLevel(lvl: Int) {
        topGrid = GameGridHelper.genGridForLevel(lvl)
        btmGrid = GameGridHelper.genGridForLevel(lvl)
        rightGrid = GameGridHelper.genGridForLevel(lvl).rotate(false)
        gangerGrid = GameGridHelper.genGangerForLevel(lvl)
        setLayoutForChapter(chapter)

    }


    /**
     * Animate away from the gameover state
     */
    fun genStartOverAnim(): Animator {
        var gameScaleX = ObjectAnimator.ofFloat(grid_container, "scaleX", 0.5f, 1f)
        var gameScaleY = ObjectAnimator.ofFloat(grid_container, "scaleY", 0.5f, 1f)
        var gameOverExpandX = ObjectAnimator.ofFloat(stopped_container, "scaleX", 1f, 10f)
        var gameOverExpandY = ObjectAnimator.ofFloat(stopped_container, "scaleY", 1f, 10f)
        var resetZoomsAnim = AnimatorSet()
        resetZoomsAnim.playTogether(gameScaleX, gameScaleY, gameOverExpandX, gameOverExpandY)
        resetZoomsAnim.interpolator = DecelerateInterpolator()
        resetZoomsAnim.setDuration(650)
        resetZoomsAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                stopped_container.visibility = View.INVISIBLE
                stopped_container.scaleX = 1f
                stopped_container.scaleY = 1f
            }
        })

        var topMove = ObjectAnimator.ofFloat(gridbinderview_top, "translationY", gridbinderview_top.translationY, 0f)
        var gangerYMove = ObjectAnimator.ofFloat(gridbinderview_blockelganger, "translationY", gridbinderview_blockelganger.translationY, getDefaultGangerTransYForChapter(GameHelper.Chapter.ONE))
        var gangerXMove = ObjectAnimator.ofFloat(gridbinderview_blockelganger, "translationX", gridbinderview_blockelganger.translationX, getDefaultGangerTransXForChapter(GameHelper.Chapter.ONE))
        var gangerMove = AnimatorSet()
        gangerMove.playTogether(gangerXMove, gangerYMove)
        var resetTranslationsAnim = AnimatorSet()
        if (chapter == GameHelper.Chapter.TWO) {
            var btmMove = ObjectAnimator.ofFloat(gridbinderview_btm, "translationY", gridbinderview_btm.translationY, 0f)
            resetTranslationsAnim.playTogether(topMove, gangerMove, btmMove)
        } else {
            resetTranslationsAnim.playTogether(topMove, gangerMove)
        }
        resetTranslationsAnim.interpolator = AccelerateInterpolator()
        resetTranslationsAnim.setDuration(400)

        var combinedAnim = AnimatorSet()
        combinedAnim.playSequentially(resetZoomsAnim, resetTranslationsAnim, genHideInfoAnim(false))
        return combinedAnim
    }

    fun genCollideAnim(combined: Grid): Animator {
        var animMoves = AnimatorSet()
        if (chapter == GameHelper.Chapter.ONE || chapter == GameHelper.Chapter.TWO) {
            //Vertical smash animations
            var topAndGangComb = if (chapter == GameHelper.Chapter.ONE) combined else GameGridHelper.combineShapesVert(topGrid, gangerGrid)
            var singleBlockHeight = gridbinderview_top.getSubGridPosition(Grid(1, 1), 0, 0).height()
            var combinedShapeHeight = singleBlockHeight * combined.height
            var combinedShapeTop = grid_container.height / 2f - combinedShapeHeight / 2f
            var combinedShapeBottom = combinedShapeTop + combinedShapeHeight
            var topTransY = combinedShapeTop - gridbinderview_top.top
            var btmTransY = combinedShapeBottom - gridbinderview_btm.bottom
            var gangerTransY = combinedShapeTop + singleBlockHeight * (topAndGangComb.height - gangerGrid.height) - gridbinderview_blockelganger.top
            var topMove = ObjectAnimator.ofFloat(gridbinderview_top, "translationY", 0f, topTransY)
            var gangerMove = ObjectAnimator.ofFloat(gridbinderview_blockelganger, "translationY", getDefaultGangerTransYForChapter(chapter), gangerTransY)
            if (chapter == GameHelper.Chapter.TWO) {
                var btmMove = ObjectAnimator.ofFloat(gridbinderview_btm, "translationY", 0f, btmTransY)
                animMoves.playTogether(topMove, gangerMove, btmMove)
            } else {
                animMoves.playTogether(topMove, gangerMove)
            }
        } else if (chapter == GameHelper.Chapter.THREE) {
            var singleBlockWidth = gridbinderview_right.getSubGridPosition(Grid(1, 1), 0, 0).width()
            var combinedShapeWidth = singleBlockWidth * combined.width
            var combinedShapeLeft = grid_container.width / 2f - combinedShapeWidth / 2f
            var gangerTransX = combinedShapeLeft - gridbinderview_blockelganger.left
            var rightTransX = (combinedShapeLeft + combinedShapeWidth) - gridbinderview_right.right
            var gangerMove = ObjectAnimator.ofFloat(gridbinderview_blockelganger, "translationX", getDefaultGangerTransXForChapter(chapter), gangerTransX)
            var rightMove = ObjectAnimator.ofFloat(gridbinderview_right, "translationX", 0f, rightTransX)
            animMoves.playTogether(gangerMove, rightMove)
        }
        animMoves.interpolator = AccelerateInterpolator()
        animMoves.setDuration(350)
        return animMoves
    }

    fun genCollideToGameOverAnim(): Animator {
        try {
            AnalyticsMaster.getTracker(this).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_GAME_OVER)
                    .setLabel(AnalyticsMaster.LABEL_LEVEL)
                    .setValue(level.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }

        var gameOverAnimator = AnimatorSet()
        var gameScaleX = ObjectAnimator.ofFloat(grid_container, "scaleX", 1f, 0.5f)
        var gameScaleY = ObjectAnimator.ofFloat(grid_container, "scaleY", 1f, 0.5f)
        var scaleDownBoardsAnim = AnimatorSet()
        scaleDownBoardsAnim.playTogether(gameScaleX, gameScaleY)
        scaleDownBoardsAnim.interpolator = AccelerateInterpolator()
        scaleDownBoardsAnim.setDuration(450)

        var gameOverShrinkX = ObjectAnimator.ofFloat(stopped_container, "scaleX", 10f, 1f)
        var gameOverShrinkY = ObjectAnimator.ofFloat(stopped_container, "scaleY", 10f, 1f)
        var enterGameOver = AnimatorSet()
        enterGameOver.playTogether(gameOverShrinkX, gameOverShrinkY)
        enterGameOver.interpolator = AccelerateInterpolator()
        enterGameOver.setDuration(1000L)
        enterGameOver.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                gameover = true
            }

            override fun onAnimationEnd(animation: Animator?) {
                //Show an ad on gameover (randomly if the user is logged in)
                if (!isLoggedIn() || random.nextBoolean()) {
                    stopped_container.postDelayed(Runnable {
                        if (activityResumed && interstitialAd?.isLoaded ?: false) {
                            if (gamestarted) {
                                actionPauseGame()
                            }
                            interstitialAd?.show()
                        }
                    }, 650)
                } else {
                    Toast.makeText(this@GameActivity, R.string.thanks_for_signin_in_skip_ad_blurb, Toast.LENGTH_SHORT).show()
                }
            }
        })
        gameOverAnimator.playSequentially(scaleDownBoardsAnim, enterGameOver)
        return gameOverAnimator
    }

    fun genCollideToBlankAnim(): Animator {
        var endScale = 0.2f
        var gridsZoomX = ObjectAnimator.ofFloat(grid_container, "scaleX", 1f, endScale)
        var gridsZoomY = ObjectAnimator.ofFloat(grid_container, "scaleY", 1f, endScale)
        var gridsAlpha = ObjectAnimator.ofFloat(grid_container, "alpha", 1f, 0f)
        var animGridsOut = AnimatorSet()
        animGridsOut.playTogether(gridsZoomX, gridsZoomY, gridsAlpha)
        animGridsOut.interpolator = AccelerateInterpolator()
        animGridsOut.setDuration(450)
        return animGridsOut
    }

    fun genBlankToLevelAnim(lvl: Int): Animator {
        var animReenter = AnimatorSet()

        var chap = GameHelper.chapterForLevel(lvl)
        var gangerReturn = when (chap) {
            GameHelper.Chapter.ONE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger, "translationY", grid_container.height - gridbinderview_blockelganger.top.toFloat(), getDefaultGangerTransYForChapter(GameHelper.chapterForLevel(lvl)))
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger.translationX = getDefaultGangerTransXForChapter(chap)
                    }
                })
                anim
            }
            GameHelper.Chapter.TWO -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger, "translationX", grid_container.width.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger.translationY = getDefaultGangerTransYForChapter(chap)
                    }
                })
                anim
            }
            GameHelper.Chapter.THREE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger, "translationY", grid_container.height.toFloat(), getDefaultGangerTransYForChapter(GameHelper.chapterForLevel(lvl)))
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger.translationX = getDefaultGangerTransXForChapter(chap)
                    }
                })
                anim
            }
        }

        var playablePieceReturn = when (chap) {
            GameHelper.Chapter.ONE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_top, "translationY", -gridbinderview_top.bottom.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_top.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.TWO -> {
                var topAnim = ObjectAnimator.ofFloat(gridbinderview_top, "translationY", -gridbinderview_top.bottom.toFloat(), 0f)
                var btmAnim = ObjectAnimator.ofFloat(gridbinderview_btm, "translationY", grid_container.height - gridbinderview_btm.top.toFloat(), 0f)
                var anim = AnimatorSet()
                anim.playTogether(topAnim, btmAnim)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_top.translationX = 0f
                        gridbinderview_btm.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.THREE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_right, "translationY", -grid_container.height.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_right.translationX = 0f
                    }
                })
                anim
            }
        }

        if (chapter != chap) {
            var chapterColorAnim = genSetColorAnimator(getColorForChapter(chap))
            animReenter.playTogether(playablePieceReturn, gangerReturn, chapterColorAnim)
        } else {
            animReenter.playTogether(playablePieceReturn, gangerReturn)
        }

        animReenter.interpolator = DecelerateInterpolator()
        animReenter.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                grid_container.scaleX = 1f
                grid_container.scaleY = 1f
                grid_container.alpha = 1f

                if (isLoggedIn()) {
                    if (level == 1) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_new_kid_on_the_block));
                    } else if (level == 5) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_block_head));
                    } else if (level == 7) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_knock_their_blocks_off));
                    } else if (level == 9) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_block_buster));
                    }
                    if (noTouchStreak > 3) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_beat_the_block_clock));
                    }
                }

                try {
                    AnalyticsMaster.getTracker(this@GameActivity).send(HitBuilders.EventBuilder()
                            .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                            .setAction(AnalyticsMaster.ACTION_LEVEL_COMPLETE)
                            .setLabel(AnalyticsMaster.LABEL_LEVEL)
                            .setValue(level.toLong())
                            .build());
                } catch(ex: Exception) {
                    Timber.e(ex, "Analytics Exception");
                }

                level++
                points += 100 * level
                noTouchStreak = 0
                initGridsForLevel(level)
            }

            override fun onAnimationEnd(animation: Animator?) {
                subscribeToTicker()
                if (level == 1) {
                    setGridHelpTextShowing(true)
                }

            }
        })
        return animReenter
    }

    /**
     * This builds a nice animation for the two sides to crash together.
     */
    fun genSmashAnim(): Animator {
        var combined = when (chapter) {
            GameHelper.Chapter.ONE -> GameGridHelper.combineShapesVert(topGrid, gangerGrid)
            GameHelper.Chapter.TWO -> {
                GameGridHelper.combineShapesVert(GameGridHelper.combineShapesVert(topGrid, gangerGrid), btmGrid)
            }
            GameHelper.Chapter.THREE -> {
                GameGridHelper.combineShapesHoriz(gangerGrid, rightGrid)
            }
        }

        //This is the return animator
        var animators = ArrayList<Animator>()
        animators.add(genCollideAnim(combined))
        if (GameGridHelper.combinedShapeIsGameOver(combined)) {
            animators.add(genCollideToGameOverAnim())
        } else {
            animators.add(genCollideToBlankAnim())
            animators.add(genBlankToLevelAnim(level + 1))
        }
        var animCombined = AnimatorSet()
        animCombined.playSequentially(animators)
        animCombined.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                setGridHelpTextShowing(false)
            }
        })
        return animCombined
    }

    fun setLayoutForChapter(chap: GameHelper.Chapter) {
        when (chap) {
            GameHelper.Chapter.ONE -> {
                var gangerParams = gridbinderview_blockelganger.layoutParams
                if (gangerParams is FrameLayout.LayoutParams) {
                    gangerParams.height = resources.getDimensionPixelSize(R.dimen.grid_height)
                    gangerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    gridbinderview_blockelganger.layoutParams = gangerParams
                }
                gridbinderview_top.visibility = View.VISIBLE
                gridbinderview_btm.visibility = View.INVISIBLE
                gridbinderview_right.visibility = View.INVISIBLE
            }
            GameHelper.Chapter.TWO -> {
                var gangerParams = gridbinderview_blockelganger.layoutParams
                if (gangerParams is FrameLayout.LayoutParams) {
                    gangerParams.height = (gridbinderview_top.getSubGridPosition(Grid(1, 1), 0, 0).height() * gangerGrid.height).toInt()
                    gangerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    gridbinderview_blockelganger.layoutParams = gangerParams
                }
                gridbinderview_top.visibility = View.VISIBLE
                gridbinderview_btm.visibility = View.VISIBLE
                gridbinderview_right.visibility = View.INVISIBLE
            }
            GameHelper.Chapter.THREE -> {
                var gangerParams = gridbinderview_blockelganger.layoutParams
                if (gangerParams is FrameLayout.LayoutParams) {
                    gangerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    gangerParams.width = resources.getDimensionPixelSize(R.dimen.grid_height)
                    gridbinderview_blockelganger.layoutParams = gangerParams
                }
                gridbinderview_top.visibility = View.INVISIBLE
                gridbinderview_btm.visibility = View.INVISIBLE
                gridbinderview_right.visibility = View.VISIBLE
            }
        }
    }

    fun getDefaultGangerTransYForChapter(chap: GameHelper.Chapter): Float {
        return when (chap) {
            GameHelper.Chapter.ONE -> {
                //Ganger towards bottom
                var top = grid_container.height / 2f - resources.getDimensionPixelSize(R.dimen.grid_height) / 2f
                grid_container.height - resources.getDimensionPixelSize(R.dimen.keyline_one).toFloat() - resources.getDimensionPixelSize(R.dimen.grid_height) - top
            }
            else -> 0f
        }
    }

    fun getDefaultGangerTransXForChapter(chap: GameHelper.Chapter): Float {
        return when (chap) {
            GameHelper.Chapter.THREE -> {
                //Ganger towards left
                resources.getDimensionPixelSize(R.dimen.keyline_one) - gridbinderview_blockelganger.left.toFloat()
            }
            else -> 0f
        }
    }

    /**
     * Generate the animator that is used to flash the count down clock for the level
     */
    fun genCountDownOutAnim(bgColor: Int): Animator {
        //We flash the background to make things seem more intense
        //Use an ArgbEvaluator to support older versions of android (instead of ValueAnimator.ofArgb())
        var argb = ArgbEvaluator()
        var startColor: Int = bgColor
        var endColor: Int = Color.TRANSPARENT
        var bgAnim = ValueAnimator.ofFloat(0f, 1f)
        bgAnim.addUpdateListener {
            countdown_flash_container.setBackgroundColor(argb.evaluate(it.animatedValue as Float, startColor, endColor) as Int)
        }

        //Scale and fade the current count down number
        var endScale = 0.2f
        var countdownZoomX = ObjectAnimator.ofFloat(countdown_inner_container, "scaleX", 1f, endScale)
        var countdownZoomY = ObjectAnimator.ofFloat(countdown_inner_container, "scaleY", 1f, endScale)
        var countdownAlpha = ObjectAnimator.ofFloat(countdown_inner_container, "alpha", 1f, 0f)
        var animCountdownOut = AnimatorSet()
        animCountdownOut.playTogether(countdownZoomX, countdownZoomY, countdownAlpha, bgAnim)
        animCountdownOut.interpolator = AccelerateInterpolator()
        animCountdownOut.setDuration(700)
        animCountdownOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                countdown_container.visibility = View.VISIBLE
            }

            override fun onAnimationCancel(animation: Animator?) {
                done()
            }

            override fun onAnimationEnd(animation: Animator?) {
                done()
            }

            fun done() {
                countdown_container.visibility = View.INVISIBLE
            }
        })
        return animCountdownOut
    }

    /**
     * Generate an animator that makes the play button fly away
     */
    fun genHideInfoAnim(forward: Boolean): Animator {
        //Play btn anims

        var startScale = if (forward) 1f else 0.1f
        var endScale = if (forward) 0.1f else 1f
        var startAlpha = if (forward) 1f else 0f
        var endAlpha = if (forward) 0f else 1f
        var playScaleX = ObjectAnimator.ofFloat(play_btn, "scaleX", startScale, endScale)
        var playScaleY = ObjectAnimator.ofFloat(play_btn, "scaleY", startScale, endScale)
        var playAlpha = ObjectAnimator.ofFloat(play_btn, "alpha", startAlpha, endAlpha)
        var playBtnOut = AnimatorSet()
        playBtnOut.playTogether(playScaleX, playScaleY, playAlpha)

        var anim = AnimatorSet()
        anim.playTogether(playBtnOut)
        anim.setDuration(250)
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                if (!forward) {
                    play_btn.scaleX = startScale
                    play_btn.scaleY = startScale
                    play_btn.alpha = startAlpha
                    start_container.visibility = View.VISIBLE
                }
            }

            override fun onAnimationEnd(animation: Animator?) {
                if (forward) {
                    start_container.visibility = View.INVISIBLE
                    play_btn.scaleX = 1f
                    play_btn.scaleY = 1f
                    play_btn.alpha = 1f
                }
            }
        })
        return anim
    }


    /**
     * This sets the visibility of the help text, and wiggles it if it's showing
     */
    fun setGridHelpTextShowing(showing: Boolean, delay: Long = 0) {
        if (showing) {
            gridHelpTextAnim?.cancel()

            var alpha = ObjectAnimator.ofFloat(grid_help_text, "rotation", 0f, -3f, 0f, 3f, 0f)
            alpha.interpolator = OvershootInterpolator()
            alpha.setDuration(650)
            alpha.startDelay = delay
            alpha.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    grid_help_text.visibility = View.VISIBLE
                }

                override fun onAnimationEnd(animation: Animator?) {
                    if (gridHelpTextAnim != null) {
                        gridHelpTextAnim = null
                        setGridHelpTextShowing(true, 1000)
                    }
                }

                override fun onAnimationCancel(animation: Animator?) {
                    gridHelpTextAnim = null
                    grid_help_text.visibility = View.GONE
                }
            })
            gridHelpTextAnim = alpha
            alpha.start()
        } else {
            gridHelpTextAnim?.cancel()
            gridHelpTextAnim = null
            grid_help_text.visibility = View.GONE
        }
    }

    fun showHowToPlayDialog() {
        if (gamestarted && !gameover) {
            actionPauseGame()
        }
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
                    .setLabel(AnalyticsMaster.LABEL_LEVEL)
                    .setValue(level.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }
    }


    /**
     * ITouchCoordinator
     */

    override fun onGridTouched() {
        touchedInTick = true
        noTouchStreak = 0
        setGridHelpTextShowing(false)
    }

    override fun allowGridTouch(): Boolean {
        return allowGameActions()
    }


    /**
     * IGoolgePlayGameServicesProvider
     */

    override fun isLoggedIn(): Boolean {
        return googleApiClient.isConnected
    }

    override fun login() {
        signInClicked = true
        autoStartSignInFlow = true
        googleApiClient.connect();
        try {
            AnalyticsMaster.getTracker(this@GameActivity).send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                    .setAction(AnalyticsMaster.ACTION_SIGN_IN)
                    .setLabel(AnalyticsMaster.LABEL_LAUNCH_COUNT)
                    .setValue(launchCount.toLong())
                    .build());
        } catch(ex: Exception) {
            Timber.e(ex, "Analytics Exception");
        }
    }

    override fun logout() {
        signInClicked = false
        autoStartSignInFlow = false
        if (isLoggedIn()) {
            Games.signOut(googleApiClient)
            googleApiClient.disconnect()
            sideBarAdapter?.rebuildRowSet()
        }
        updateGameStateVisibilities()
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
                    .setValue(launchCount.toLong())
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
                    .setValue(launchCount.toLong())
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
        if (gameover && points > 0) {
            Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_scores), points.toLong())
        }
        updateGameStateVisibilities()
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
        if (signInClicked || autoStartSignInFlow) {
            autoStartSignInFlow = false
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

    /*
    AD STUFF
     */

    private fun requestNewInterstitial() {
        var adRequest = with(AdRequest.Builder()) {
            if (BuildConfig.IS_DEBUG_BUILD) {
                addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                var andId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
                var hash = HashUtils.md5(andId).toUpperCase()
                timber.log.Timber.d("Adding test device. hash:" + hash)
                addTestDevice(hash)
            }
            build()
        }
        interstitialAd?.loadAd(adRequest);
    }

    override fun onColorUpdated(color: Int) {
        body_container.setBackgroundColor(color)
    }

    override fun onColorFinalized(color: Int) {
        body_container.setBackgroundColor(color)
        sideBarAdapter?.setAccentColor(color)
    }

}

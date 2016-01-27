package com.hotpodata.blockelganger.activity

import android.animation.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Bundle
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.widget.LinearLayoutManager
import android.view.Gravity
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
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
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


    var gridOne = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_one.grid = grd
        }

    var gridTwo = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_two.grid = grd
        }

    var gangerOneGrid = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_blockelganger_one.grid = grd
        }

    var gangerTwoGrid = GameGridHelper.genFullGrid(1, 1, true)
        set(grd: Grid) {
            field = grd
            gridbinderview_blockelganger_two.grid = grd
        }

    var subTicker: Subscription? = null
    var spentTicks = 0L
    var random = Random()
    var actionAnimator: Animator? = null
    var countDownAnimator: Animator? = null
    var touchHintAnims = HashMap<View, Animator>()

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
        gridbinderview_one.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.top_grid))
        gridbinderview_two.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.btm_grid))
        gridbinderview_animation_coordinator.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.ganger_grid))
        gridbinderview_blockelganger_one.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.ganger_grid))
        gridbinderview_blockelganger_two.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.ganger_grid))
        gridbinderview_one.setOnTouchListener(GridTouchListener(this, object : GridTouchListener.IGridChangedListener {
            override fun onGridChanged(grid: Grid) {
                gridOne = grid
            }
        }))
        gridbinderview_two.setOnTouchListener(GridTouchListener(this, object : GridTouchListener.IGridChangedListener {
            override fun onGridChanged(grid: Grid) {
                gridTwo = grid
            }
        }))
        gridbinderview_blockelganger_one.setOnClickListener {
            if (allowGridTouch(gridbinderview_blockelganger_one)) {
                actionEarlySmash()
            }
        }
        gridbinderview_blockelganger_two.setOnClickListener {
            if (allowGridTouch(gridbinderview_blockelganger_two)) {
                actionEarlySmash()
            }
        }


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
                if (w != oldw) {
                    resetGameState()
                }
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
                stopped_container.translationX = 0f
            } else if (pauseVis) {
                stopped_msg_tv.text = getString(R.string.paused)
                stopped_continue_btn.visibility = View.VISIBLE
                stopped_spacer.layoutParams.height = 0
                stopped_spacer.layoutParams = stopped_spacer.layoutParams
                stopped_container.setBackgroundColor(getColorForChapter(chapter))
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
    fun actionResetGame() {
        var toBlankAnim = genGameOverToBlank()
        toBlankAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                resetGameState()
            }
        })
        var blankToLevelAnim = genBlankToLevelAnim(0)
        var showInfoAnim = genHideInfoAnim(false)
        var backToStartStateAnim = AnimatorSet()
        backToStartStateAnim.playTogether(blankToLevelAnim, showInfoAnim)

        var resetAnim = AnimatorSet()
        resetAnim.playSequentially(toBlankAnim, backToStartStateAnim)
        resetAnim.start()
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
        actionResetGame()
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
            GameHelper.Chapter.FOUR -> resources.getColor(R.color.chapter_four_color)
        }
    }

    /**
     * This resets the game
     */
    fun resetGameState() {
        unsubscribeFromTicker()
        if (actionAnimator?.isRunning ?: false) {
            actionAnimator?.cancel()
        }
        if (countDownAnimator?.isRunning ?: false) {
            countDownAnimator?.cancel()
        }
        setTouchHintShowing(touch_hint_top, false)
        setTouchHintShowing(touch_hint_btm, false)
        setTouchHintShowing(touch_hint_right, false)

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

        initGridsForLevel(level)
        setColor(getColorForChapter(chapter), true)

        gridOne = GameGridHelper.genFullGrid(1, 1, true)
        gridTwo = GameGridHelper.genFullGrid(1, 1, true)
        gangerOneGrid = GameGridHelper.genFullGrid(1, 1, true)
        gangerTwoGrid = GameGridHelper.genFullGrid(1, 1, true)

        unScale(grid_container)
        unScale(stopped_container)

        unTranslate(gridbinderview_one)
        unTranslate(gridbinderview_two)
        unTranslate(gridbinderview_blockelganger_one)
        unTranslate(gridbinderview_blockelganger_two)

        stopped_container.visibility = View.INVISIBLE
    }

    fun unTranslate(v: View) {
        v.translationX = 0f
        v.translationY = 0f
    }

    fun unScale(v: View) {
        v.scaleX = 1f
        v.scaleY = 1f
    }

    /**
     * Smasn the pieces before the countdown ends
     */
    fun actionEarlySmash() {
        unsubscribeFromTicker()
        countDownAnimator?.cancel()

        var tickPoints = level * 100
        while ((GameHelper.secondsForLevel(level) - spentTicks).toInt() > 0) {
            noTouchStreak++
            for (i in 0..noTouchStreak) {
                tickPoints += i * 10
            }
            spentTicks++
        }
        points += tickPoints
        countdown_points.text = if (tickPoints > 0) getString(R.string.countdown_points_template, tickPoints) else ""
        countdown_tv.text = "" + 0

        var countdownAnim = genCountDownOutAnim(Color.WHITE)
        countDownAnimator = countdownAnim
        countdownAnim.start()

        var anim = genSmashAnim()
        actionAnimator = anim
        anim.start()
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
            for (touchHintAnim in touchHintAnims.values) {
                if (touchHintAnim?.isStarted ?: false) {
                    touchHintAnim?.pause()
                }
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

                for (touchHintAnim in touchHintAnims.values) {
                    if (touchHintAnim?.isPaused ?: false) {
                        touchHintAnim?.resume()
                    }
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

        var seconds = GameHelper.secondsForLevel(level)
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
        var chap = GameHelper.chapterForLevel(lvl)

        if (chap == GameHelper.Chapter.FOUR) {
            //TODO: MOVE MORE OF THIS LOGIC TO GAMEGRIDHELPER
            gridOne = GameGridHelper.genFullGrid(GameHelper.gridBreadthForLevel(lvl), GameHelper.gridDepthForLevel(lvl), true)
            gridTwo = GameGridHelper.genFullGrid(GameHelper.gridBreadthForLevel(lvl), GameHelper.gridDepthForLevel(lvl), true)

            var top = GameGridHelper.generateOpenTopGangerGrid(GameHelper.gangerBreadthForLevel(lvl), gridOne.height, true)
            var btm = GameGridHelper.generateOpenBottomGangerGrid(GameHelper.gangerBreadthForLevel(lvl), gridTwo.height, true)
            btm = GridHelper.copyGridPortion(btm, 0, 1, btm.width, btm.height)
            gangerOneGrid = GameGridHelper.combineShapesVert(top, btm)

            var workingGangerGridTwo = GameGridHelper.generateOpenTopGangerGrid(gangerOneGrid.height + 2, gangerOneGrid.width / 2, true).rotate(false)
            for (i in workingGangerGridTwo.width downTo  1) {
                GridHelper.subtractGrid(workingGangerGridTwo, gangerOneGrid, i, (workingGangerGridTwo.height - gangerOneGrid.height) / 2)
            }
            gangerTwoGrid = workingGangerGridTwo
        } else {
            gridOne = when (chap) {
                GameHelper.Chapter.THREE -> GameGridHelper.genGridForLevel(lvl).rotate(false)
                else -> GameGridHelper.genGridForLevel(lvl)
            }

            gridTwo = when (chap) {
                GameHelper.Chapter.TWO -> GameGridHelper.genGridForLevel(lvl)
                else -> GameGridHelper.genFullGrid(1, 1, true)
            }
            gangerOneGrid = GameGridHelper.genGangerForLevel(lvl)
            gangerTwoGrid = Grid(1, 1)
        }

        //We set this behind the views sometimes
        gridbinderview_animation_coordinator.visibility = View.INVISIBLE

        var w = grid_container.width
        var h = grid_container.height
        var usableWidth = w - 2 * resources.getDimensionPixelSize(R.dimen.keyline_one)
        var usableHeight = h - 2 * resources.getDimensionPixelSize(R.dimen.keyline_one)

        //var thirdHeight = usableHeight / 3f
        var fifthHeight = usableHeight / 5f
        var thirdWidth = usableWidth / 3f

        when (chap) {
            GameHelper.Chapter.ONE -> {
                var topParams = gridbinderview_one.layoutParams
                if (topParams is FrameLayout.LayoutParams) {
                    topParams.height = fifthHeight.toInt()
                    topParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    topParams.gravity = Gravity.TOP
                    gridbinderview_one.layoutParams = topParams
                    touch_hint_top.layoutParams = FrameLayout.LayoutParams(topParams)
                }

                var gangerParams = gridbinderview_blockelganger_one.layoutParams
                if (gangerParams is FrameLayout.LayoutParams) {
                    gangerParams.height = fifthHeight.toInt()
                    gangerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    gangerParams.gravity = Gravity.BOTTOM
                    gridbinderview_blockelganger_one.layoutParams = gangerParams
                }

                gridbinderview_animation_coordinator.layoutParams?.let {
                    it.height = fifthHeight.toInt() * 2
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    gridbinderview_animation_coordinator.layoutParams = it
                }

                gridbinderview_one.visibility = View.VISIBLE
                gridbinderview_two.visibility = View.INVISIBLE
                gridbinderview_blockelganger_one.visibility = View.VISIBLE
                gridbinderview_blockelganger_two.visibility = View.INVISIBLE
            }
            GameHelper.Chapter.TWO -> {
                var topParams = gridbinderview_one.layoutParams
                if (topParams is FrameLayout.LayoutParams) {
                    topParams.height = fifthHeight.toInt()
                    topParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    topParams.gravity = Gravity.TOP
                    gridbinderview_one.layoutParams = topParams
                    touch_hint_top.layoutParams = FrameLayout.LayoutParams(topParams)
                }

                var btmParams = gridbinderview_two.layoutParams
                if (btmParams is FrameLayout.LayoutParams) {
                    btmParams.height = fifthHeight.toInt()
                    btmParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    btmParams.gravity = Gravity.BOTTOM
                    gridbinderview_two.layoutParams = btmParams
                    touch_hint_btm.layoutParams = FrameLayout.LayoutParams(btmParams)
                }

                var gangerHeight = (gridbinderview_one.getSubGridPosition(Grid(1, 1), 0, 0).height() * gangerOneGrid.height).toInt()
                var gangerParams = gridbinderview_blockelganger_one.layoutParams
                if (gangerParams is FrameLayout.LayoutParams) {
                    gangerParams.height = gangerHeight
                    gangerParams.width = ViewGroup.LayoutParams.MATCH_PARENT
                    gangerParams.gravity = Gravity.CENTER_VERTICAL
                    gridbinderview_blockelganger_one.layoutParams = gangerParams
                }

                gridbinderview_animation_coordinator.layoutParams?.let {
                    it.height = fifthHeight.toInt() * 2 + gangerHeight
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    gridbinderview_animation_coordinator.layoutParams = it
                }

                gridbinderview_one.visibility = View.VISIBLE
                gridbinderview_two.visibility = View.VISIBLE
                gridbinderview_blockelganger_one.visibility = View.VISIBLE
                gridbinderview_blockelganger_two.visibility = View.INVISIBLE
            }
            GameHelper.Chapter.THREE -> {
                var rightParams = gridbinderview_one.layoutParams
                if (rightParams is FrameLayout.LayoutParams) {
                    rightParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    rightParams.width = thirdWidth.toInt()
                    rightParams.gravity = Gravity.RIGHT
                    gridbinderview_one.layoutParams = rightParams
                    touch_hint_right.layoutParams = FrameLayout.LayoutParams(rightParams)
                }

                var gangerParams = gridbinderview_blockelganger_one.layoutParams
                if (gangerParams is FrameLayout.LayoutParams) {
                    gangerParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    gangerParams.width = thirdWidth.toInt()
                    gangerParams.gravity = Gravity.LEFT
                    gridbinderview_blockelganger_one.layoutParams = gangerParams
                }

                gridbinderview_animation_coordinator.layoutParams?.let {
                    it.height = ViewGroup.LayoutParams.MATCH_PARENT
                    it.width = thirdWidth.toInt() * 2
                    gridbinderview_animation_coordinator.layoutParams = it
                }

                gridbinderview_one.visibility = View.VISIBLE
                gridbinderview_two.visibility = View.INVISIBLE
                gridbinderview_blockelganger_one.visibility = View.VISIBLE
                gridbinderview_blockelganger_two.visibility = View.INVISIBLE
            }
            GameHelper.Chapter.FOUR -> {
                var totalBlocksW = gridOne.width + 2 + gangerTwoGrid.width
                var totalBlocksH = gridOne.height + 2 + gangerOneGrid.height + 2 + gridTwo.height

                var blockW = grid_container.width / totalBlocksW.toFloat()
                var blockH = grid_container.height / totalBlocksH.toFloat()


                var topParams = gridbinderview_one.layoutParams
                if (topParams is FrameLayout.LayoutParams) {
                    topParams.height = (gridOne.height * blockH).toInt()
                    topParams.width = (gridOne.width * blockW).toInt()
                    topParams.gravity = Gravity.TOP or Gravity.RIGHT
                    gridbinderview_one.layoutParams = topParams
                    touch_hint_top.layoutParams = FrameLayout.LayoutParams(topParams)
                }

                var btmParams = gridbinderview_two.layoutParams
                if (btmParams is FrameLayout.LayoutParams) {
                    btmParams.height = (gridTwo.height * blockH).toInt()
                    btmParams.width = (gridTwo.width * blockW).toInt()
                    btmParams.gravity = Gravity.BOTTOM or Gravity.RIGHT
                    gridbinderview_two.layoutParams = btmParams
                    touch_hint_btm.layoutParams = FrameLayout.LayoutParams(btmParams)
                }

                var gangerParams = gridbinderview_blockelganger_one.layoutParams
                if (gangerParams is FrameLayout.LayoutParams) {
                    gangerParams.height = (gangerOneGrid.height * blockH).toInt()
                    gangerParams.width = (gangerOneGrid.width * blockW).toInt()
                    gangerParams.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                    gridbinderview_blockelganger_one.layoutParams = gangerParams
                }

                var gangerLeftParams = gridbinderview_blockelganger_two.layoutParams
                if (gangerLeftParams is FrameLayout.LayoutParams) {
                    gangerLeftParams.height = (gangerTwoGrid.height * blockH).toInt()
                    gangerLeftParams.width = (gangerTwoGrid.width * blockW).toInt()
                    gangerLeftParams.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                    gridbinderview_blockelganger_two.layoutParams = gangerLeftParams
                }

                //We set the layout to match the total of all of our gridviews, so it can fit all of them, and the grids should be the same measured size
                gridbinderview_animation_coordinator.layoutParams?.let {
                    it.height = (gridOne.height + gridTwo.height + gangerOneGrid.height) * blockH.toInt()
                    it.width = (gangerOneGrid.width + gangerTwoGrid.width) * blockW.toInt()
                    gridbinderview_animation_coordinator.layoutParams = it
                }

                gridbinderview_one.visibility = View.VISIBLE
                gridbinderview_two.visibility = View.VISIBLE
                gridbinderview_blockelganger_one.visibility = View.VISIBLE
                gridbinderview_blockelganger_two.visibility = View.VISIBLE
            }
        }
    }


    /**
     * Animate away from the gameover state
     */
    fun genGameOverToBlank(): Animator {
        var gameOverExpandX = ObjectAnimator.ofFloat(stopped_container, "scaleX", 1f, 10f)
        var gameOverExpandY = ObjectAnimator.ofFloat(stopped_container, "scaleY", 1f, 10f)
        var gameOverAnim = AnimatorSet()
        gameOverAnim.playTogether(gameOverExpandX, gameOverExpandY)
        gameOverAnim.interpolator = DecelerateInterpolator()
        gameOverAnim.setDuration(650)
        gameOverAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                stopped_container.visibility = View.INVISIBLE
                stopped_container.scaleX = 1f
                stopped_container.scaleY = 1f
            }
        })

        var gameOverToBlank = AnimatorSet()
        gameOverToBlank.playTogether(genCollideToBlankAnim(), gameOverAnim)
        return gameOverToBlank
    }

    fun findPositionInGridBinder(outerGrid: Grid, targetGrid: Grid, gridBinderView: GridBinderView): RectF? {
        return GridHelper.findInGrid(outerGrid, targetGrid)?.let {
            var vPadding = GridHelper.numEmptyRowsTopBtm(targetGrid)
            var hPadding = GridHelper.numEmptyColsLeftRight(targetGrid)
            var xOffset = it.left - hPadding.first
            var yOffset = it.top - vPadding.first
            gridBinderView.getSubGridPosition(targetGrid, xOffset, yOffset)
        }
    }

    fun genCollideAnim(combined: Grid): Animator {
        var animMoves = AnimatorSet()
        var coordinatorGrid = when (chapter) {
            GameHelper.Chapter.FOUR -> Grid(gangerOneGrid.width + gangerTwoGrid.width, gridOne.height + gridTwo.height + gangerOneGrid.height)
            GameHelper.Chapter.THREE -> Grid(gangerOneGrid.width + gridOne.width, gangerOneGrid.height)
            GameHelper.Chapter.TWO -> Grid(gridOne.width, gridOne.height + gridTwo.height + gangerOneGrid.height)
            GameHelper.Chapter.ONE -> Grid(gridOne.width, gridOne.height + gangerOneGrid.height)
        }

        GridHelper.addGrid(coordinatorGrid, combined, (coordinatorGrid.width - combined.width) / 2, (coordinatorGrid.height - combined.height) / 2)
        gridbinderview_animation_coordinator.grid = coordinatorGrid

        if (BuildConfig.IS_DEBUG_BUILD) {
            var combPrintStr = coordinatorGrid.getPrintString(" - ", { x ->
                when (x) {
                    gridOne -> " 1 "
                    gridTwo -> " 2 "
                    gangerOneGrid -> " A "
                    gangerTwoGrid -> " B "
                    else -> " ? "
                }
            })
            Timber.d("coordinatorGrid:\n" + combPrintStr)
        }


        var verticalAnimators = ArrayList<Animator>()
        var horizontalAnimators = ArrayList<Animator>()
        findPositionInGridBinder(coordinatorGrid, gridOne, gridbinderview_animation_coordinator)?.let {
            //it is now the coorinates for gridOne inside the coordinator
            var transX = gridbinderview_animation_coordinator.left + it.left - gridbinderview_one.left
            var transY = gridbinderview_animation_coordinator.top + it.top - gridbinderview_one.top
            if (transX != 0f) {
                horizontalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_one, "translationX", 0f, transX))
            }
            if (transY != 0f) {
                verticalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_one, "translationY", 0f, transY))
            }
        }
        findPositionInGridBinder(coordinatorGrid, gridTwo, gridbinderview_animation_coordinator)?.let {
            //it is now the coorinates for gridTwo inside the coordinator
            var transX = gridbinderview_animation_coordinator.left + it.left - gridbinderview_two.left
            var transY = gridbinderview_animation_coordinator.top + it.top - gridbinderview_two.top
            if (transX != 0f) {
                horizontalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_two, "translationX", 0f, transX))
            }
            if (transY != 0f) {
                verticalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_two, "translationY", 0f, transY))
            }
        }
        findPositionInGridBinder(coordinatorGrid, gangerOneGrid, gridbinderview_animation_coordinator)?.let {
            //it is now the coorinates for gangerOneGrid inside the coordinator
            var transX = gridbinderview_animation_coordinator.left + it.left - gridbinderview_blockelganger_one.left
            var transY = gridbinderview_animation_coordinator.top + it.top - gridbinderview_blockelganger_one.top
            if (transX != 0f) {
                horizontalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationX", 0f, transX))
            }
            if (transY != 0f) {
                verticalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationY", 0f, transY))
            }
        }
        findPositionInGridBinder(coordinatorGrid, gangerTwoGrid, gridbinderview_animation_coordinator)?.let {
            //it is now the coorinates for gangerTwoGrid inside the coordinator
            var transX = gridbinderview_animation_coordinator.left + it.left - gridbinderview_blockelganger_two.left
            var transY = gridbinderview_animation_coordinator.top + it.top - gridbinderview_blockelganger_two.top
            if (transX != 0f) {
                horizontalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_blockelganger_two, "translationX", 0f, transX))
            }
            if (transY != 0f) {
                verticalAnimators.add(ObjectAnimator.ofFloat(gridbinderview_blockelganger_two, "translationY", 0f, transY))
            }
        }

        var vertAnim = AnimatorSet()
        vertAnim.playTogether(verticalAnimators)
        var horizAnim = AnimatorSet()
        horizAnim.playTogether(horizontalAnimators)


        if (verticalAnimators.size > 0 && horizontalAnimators.size > 0) {
            animMoves.playSequentially(vertAnim, horizAnim)
        } else {
            animMoves.playTogether(vertAnim, horizAnim)
        }
        animMoves.setDuration(350L)
        animMoves.interpolator = AccelerateInterpolator()
        animMoves.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                gridbinderview_animation_coordinator.visibility = View.VISIBLE
                gridbinderview_one.visibility = View.INVISIBLE
                gridbinderview_two.visibility = View.INVISIBLE
                gridbinderview_blockelganger_one.visibility = View.INVISIBLE
                gridbinderview_blockelganger_two.visibility = View.INVISIBLE
            }
        })
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

        var endGameScale = if (chapter == GameHelper.Chapter.THREE || chapter == GameHelper.Chapter.FOUR) 0.25f else 0.5f
        var gameOverAnimator = AnimatorSet()
        var gameScaleX = ObjectAnimator.ofFloat(grid_container, "scaleX", 1f, endGameScale)
        var gameScaleY = ObjectAnimator.ofFloat(grid_container, "scaleY", 1f, endGameScale)
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
                if (!BuildConfig.IS_PRO) {
                    if (BuildConfig.IS_DEBUG_BUILD || (!isLoggedIn() || random.nextBoolean())) {
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
            }
        })
        gameOverAnimator.playSequentially(scaleDownBoardsAnim, enterGameOver)
        return gameOverAnimator
    }

    fun genCollideToBlankAnim(): Animator {
        var endScale = 0.2f
        var gridsZoomX = ObjectAnimator.ofFloat(grid_container, "scaleX", grid_container.scaleX, endScale)
        var gridsZoomY = ObjectAnimator.ofFloat(grid_container, "scaleY", grid_container.scaleY, endScale)
        var gridsAlpha = ObjectAnimator.ofFloat(grid_container, "alpha", grid_container.alpha, 0f)
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
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationY", grid_container.height - gridbinderview_blockelganger_one.top.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.TWO -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationX", grid_container.width.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationY = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.THREE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationY", grid_container.height.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.FOUR -> {
                var gangerOne = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationX", grid_container.width.toFloat(), 0f)
                var gangerTwo = ObjectAnimator.ofFloat(gridbinderview_blockelganger_two, "translationY", grid_container.height.toFloat(), 0f)
                var anim = AnimatorSet()
                anim.playTogether(gangerOne, gangerTwo)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationY = 0f
                        gridbinderview_blockelganger_two.translationX = 0f
                        //unTranslate(gridbinderview_blockelganger_one)
                        //unTranslate(gridbinderview_blockelganger_two)
                    }
                })
                anim
            }
        }

        var playablePieceReturn = when (chap) {
            GameHelper.Chapter.ONE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_one, "translationY", -gridbinderview_one.bottom.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_one.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.TWO -> {
                var topAnim = ObjectAnimator.ofFloat(gridbinderview_one, "translationY", -gridbinderview_one.bottom.toFloat(), 0f)
                var btmAnim = ObjectAnimator.ofFloat(gridbinderview_two, "translationY", grid_container.height - gridbinderview_two.top.toFloat(), 0f)
                var anim = AnimatorSet()
                anim.playTogether(topAnim, btmAnim)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_one.translationX = 0f
                        gridbinderview_two.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.THREE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_one, "translationY", -grid_container.height.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_one.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.FOUR -> {
                var topAnim = ObjectAnimator.ofFloat(gridbinderview_one, "translationY", -gridbinderview_one.bottom.toFloat(), 0f)
                var btmAnim = ObjectAnimator.ofFloat(gridbinderview_two, "translationY", grid_container.height - gridbinderview_two.top.toFloat(), 0f)
                var anim = AnimatorSet()
                anim.playTogether(topAnim, btmAnim)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_one.translationX = 0f
                        gridbinderview_two.translationX = 0f
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
                initGridsForLevel(level)
            }

            override fun onAnimationEnd(animation: Animator?) {
                if (!gameover && (level == 1 || GameHelper.levelIsChapterStart(level))) {
                    when (chap) {
                        GameHelper.Chapter.FOUR -> {
                            setTouchHintShowing(touch_hint_top, true)
                            setTouchHintShowing(touch_hint_btm, true)
                        }
                        GameHelper.Chapter.THREE -> {
                            setTouchHintShowing(touch_hint_right, true)
                        }
                        GameHelper.Chapter.TWO -> {
                            setTouchHintShowing(touch_hint_top, true)
                            setTouchHintShowing(touch_hint_btm, true)
                        }
                        GameHelper.Chapter.ONE -> {
                            setTouchHintShowing(touch_hint_top, true)
                        }
                    }
                }
            }
        })
        return animReenter
    }

    /**
     * This builds a nice animation for the two sides to crash together.
     */
    fun genSmashAnim(): Animator {
        //We copy our grids and mask them so the cells point to the grids they represent
        var gridTop = GridHelper.maskGrid(gridOne, gridOne)
        var gridBtm = GridHelper.maskGrid(gridTwo, gridTwo)
        var gangCenter = GridHelper.maskGrid(gangerOneGrid, gangerOneGrid)
        var gangLeft = GridHelper.maskGrid(gangerTwoGrid, gangerTwoGrid)

        //Combine our grids
        var combined = when (chapter) {
            GameHelper.Chapter.ONE -> GameGridHelper.combineShapesVert(gridTop, gangCenter)
            GameHelper.Chapter.TWO -> {
                GameGridHelper.combineShapesVert(GameGridHelper.combineShapesVert(gridTop, gangCenter), gridBtm)
            }
            GameHelper.Chapter.THREE -> {
                GameGridHelper.combineShapesHoriz(gangCenter, gridTop)
            }
            GameHelper.Chapter.FOUR -> {
                //The left ganger's height is the center ganger's height + 2
                var gangerVertPadding = GridHelper.numEmptyRowsTopBtm(gangCenter)
                var gangerAndTop = GameGridHelper.combineShapesVert(gridTop, gangCenter)
                var gangerAndTopAndBtm = GameGridHelper.combineShapesVert(gangerAndTop, gridBtm)

                //Calculate the offset of the left ganger relative to the combined shape
                var leftGangerTopOffset = gangerAndTop.height - (gangCenter.height - gangerVertPadding.first - gangerVertPadding.second) - 1

                var gangerAndTopAndBtmAdjusted = Grid(gangerAndTopAndBtm.width, Math.max(gangerAndTopAndBtm.height, gangLeft.height) + Math.abs(leftGangerTopOffset))
                var leftGangerAdjusted = Grid(gangLeft.width, gangerAndTopAndBtmAdjusted.height)

                if (leftGangerTopOffset > 0) {
                    GridHelper.addGrid(gangerAndTopAndBtmAdjusted, gangerAndTopAndBtm, 0, 0)
                    GridHelper.addGrid(leftGangerAdjusted, gangLeft, 0, leftGangerTopOffset)
                } else if (leftGangerTopOffset < 0) {
                    GridHelper.addGrid(gangerAndTopAndBtmAdjusted, gangerAndTopAndBtm, 0, Math.abs(leftGangerTopOffset))
                    GridHelper.addGrid(leftGangerAdjusted, gangLeft, 0, 0)
                } else {
                    GridHelper.addGrid(gangerAndTopAndBtmAdjusted, gangerAndTopAndBtm, 0, 0)
                    GridHelper.addGrid(leftGangerAdjusted, gangLeft, 0, 0)
                }
                GameGridHelper.combineShapesHoriz(leftGangerAdjusted, gangerAndTopAndBtmAdjusted)
            }
        }
        combined = GridHelper.trim(combined)

        //This is the return animator
        var animators = ArrayList<Animator>()
        var collideAnim = genCollideAnim(combined)
        animators.add(collideAnim)


        if (GameGridHelper.combinedShapeIsGameOver(combined)) {
            animators.add(genCollideToGameOverAnim())
        } else {
            //If we aren't gameovering, update scores etc right after collision
            collideAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    level++
                    points += 100 * level
                }
            })
            animators.add(genCollideToBlankAnim())
            animators.add(genBlankToLevelAnim(level + 1))
        }

        var animCombined = AnimatorSet()
        animCombined.playSequentially(animators)
        animCombined.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                setTouchHintShowing(touch_hint_top, false)
                setTouchHintShowing(touch_hint_btm, false)
                setTouchHintShowing(touch_hint_right, false)
            }

            override fun onAnimationEnd(animation: Animator?) {
                if (isLoggedIn()) {
                    if (level == 2) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_new_kid_on_the_block));
                    } else if (level == 6) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_block_head));
                    } else if (level == 8) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_knock_their_blocks_off));
                    } else if (level == 10) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_block_buster));
                    }

                    if (noTouchStreak > 3) {
                        Games.Achievements.unlock(googleApiClient, getString(R.string.achievement_beat_the_block_clock));
                    }
                }

                noTouchStreak = 0

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

                subscribeToTicker()
            }
        })
        return animCombined
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

    fun setTouchHintShowing(hintView: View, showing: Boolean, delay: Long = 0) {
        if (showing) {
            var wiggle = ObjectAnimator.ofFloat(hintView, "rotation", 0f, -5f, 0f, 5f, 0f)
            wiggle.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    hintView.visibility = View.VISIBLE
                }

                override fun onAnimationCancel(animation: Animator?) {
                    hintView.visibility = View.INVISIBLE
                    touchHintAnims.remove(hintView)
                }

                override fun onAnimationEnd(animation: Animator?) {
                    if (touchHintAnims.containsKey(hintView)) {
                        touchHintAnims.remove(hintView)
                        setTouchHintShowing(hintView, true, 1000)
                    }
                }
            })
            wiggle.interpolator = OvershootInterpolator()
            wiggle.setDuration(650)
            wiggle.startDelay = delay
            touchHintAnims.put(hintView, wiggle)
            wiggle.start()
        } else {
            touchHintAnims.get(hintView)?.cancel()
            touchHintAnims.remove(hintView)
            hintView.visibility = View.INVISIBLE
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

    override fun onGridTouched(view: View) {
        touchedInTick = true
        noTouchStreak = 0

        if (view == gridbinderview_one) {
            if (chapter == GameHelper.Chapter.THREE) {
                setTouchHintShowing(touch_hint_right, false)
            } else {
                setTouchHintShowing(touch_hint_top, false)
            }
        }
        if (view == gridbinderview_two) {
            setTouchHintShowing(touch_hint_btm, false)
        }
    }

    override fun allowGridTouch(view: View): Boolean {
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
        if (!BuildConfig.IS_PRO) {
            interstitialAd?.loadAd(adRequest);
        }
    }

    override fun onColorUpdated(color: Int) {
        body_container.setBackgroundColor(color)
    }

    override fun onColorFinalized(color: Int) {
        body_container.setBackgroundColor(color)
        sideBarAdapter?.setAccentColor(color)
        touch_hint_top.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        touch_hint_btm.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        touch_hint_right.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        stopped_continue_btn.setTextColor(color)
        stopped_start_over_btn.setTextColor(color)
        stopped_leader_board_btn.setTextColor(color)
    }

}

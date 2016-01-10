package com.hotpodata.blockelganger.activity

import android.animation.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v4.widget.DrawerLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.InterstitialAd
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.games.Games
import com.hotpodata.blockelganger.BuildConfig
import com.hotpodata.blockelganger.R
import com.hotpodata.blockelganger.adapter.SideBarAdapter
import com.hotpodata.blockelganger.helpers.ColorBlockDrawer
import com.hotpodata.blockelganger.interfaces.IGooglePlayGameServicesProvider
import com.hotpodata.blockelganger.utils.BaseGameUtils
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
import kotlinx.android.synthetic.main.activity_game.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

class GameActivity : AppCompatActivity(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, IGooglePlayGameServicesProvider {
    val REQUEST_LEADERBOARD = 1
    val RC_SIGN_IN = 9001
    val STORAGE_KEY_AUTO_SIGN_IN = "STORAGE_KEY_AUTO_SIGN_IN"

    var topGrid = initFullGrid(1, 1)
    var bottomGrid = initFullGrid(1, 1)

    var touchCoords: Pair<Int, Int>? = null
    var touchModeIsAdd = false

    var subTicker: Subscription? = null
    var spentTicks = 0L
    var random = Random()
    var actionAnimator: Animator? = null
    var countDownAnimator: Animator? = null
    var gridHelpTextAnim: Animator? = null

    var sideBarAdapter: SideBarAdapter? = null
    var drawerToggle: ActionBarDrawerToggle? = null

    var touchedInTick = false
    var noTouchStreak = 0

    var activityResumed = false

    var points = 0
        set(pts: Int) {
            field = pts
            supportActionBar?.subtitle = getString(R.string.points_template, pts)
        }

    var level = 0
        set(lvl: Int) {
            field = lvl
            supportActionBar?.title = getString(R.string.level_template, lvl)
        }

    var paused = false
        set(pause: Boolean) {
            field = pause
            updateGameStateVisibilities()
        }

    var gameover = false
        set(gOver: Boolean) {
            //Submit the score on gameover
            if (isLoggedIn() && !field && gOver) {
                Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_alltimehighscores_id), points.toLong())
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

        //Set up the actionbar
        setSupportActionBar(toolbar);
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setShowHideAnimationEnabled(true)


        //Set up the drawer
        setUpLeftDrawer()
        drawerToggle = object : ActionBarDrawerToggle(this, drawer_layout, R.string.drawer_open, R.string.drawer_closed) {
            override fun onDrawerOpened(drawerView: View?) {
                if (gamestarted && !gameover) {
                    actionPauseGame()
                }
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            }

            override fun onDrawerClosed(drawerView: View?) {
                drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }
        drawer_layout.setDrawerListener(drawerToggle)
        drawer_layout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);


        //Set up how to draw the blocks
        gridbinderview_top.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.top_grid))
        gridbinderview_bottom.blockDrawer = ColorBlockDrawer(resources.getColor(R.color.btm_grid))

        //Set up the touch listener for the top grid
        gridbinderview_top.setOnTouchListener {
            view, motionEvent ->
            if (allowGameActions()) {
                touchedInTick = true
                noTouchStreak = 0

                setGridHelpTextShowing(false)
                var gridView = view as GridBinderView
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        //Figure out where the touch happened and if this is to be an additive or subractive touch event
                        var gridCoords = gridView.getGridCoords(motionEvent.x, motionEvent.y)
                        if (topGridCoordsValid(gridCoords)) {
                            touchCoords = gridCoords
                            touchModeIsAdd = !topGridCoordsFilled(gridCoords)
                            if (touchModeIsAdd) {
                                addCoords(gridCoords)
                            } else {
                                subtractCoords(gridCoords)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        touchCoords = null
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        touchCoords = null
                    }
                    MotionEvent.ACTION_MOVE -> {
                        //If we've moved, we either add or subtract depending on the down event
                        var gridCoords = gridView.getGridCoords(motionEvent.x, motionEvent.y)
                        if (topGridCoordsValid(gridCoords)) {
                            if (touchCoords == null) {
                                touchCoords = gridCoords
                                touchModeIsAdd = !topGridCoordsFilled(gridCoords)
                            }
                            if (touchModeIsAdd) {
                                addCoords(gridCoords)
                            } else {
                                subtractCoords(gridCoords)
                            }
                        }
                    }
                }
                true
            } else {
                false
            }
        }

        //Setup our click actions
        play_btn.setOnClickListener {
            actionStartGame()
        }
        stopped_start_over_btn.setOnClickListener {
            actionResetGame()
            actionStartGame()
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

        actionResetGame()

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
    }

    override fun onResume() {
        super.onResume()
        activityResumed = true
    }

    override fun onPause() {
        super.onPause()
        if (gamestarted) {
            paused = true
        }
        activityResumed = false
    }

    override fun onStart() {
        super.onStart()
        if (autoStartSignInFlow) {
            googleApiClient.connect();
        }
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.game_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.findItem(R.id.play)?.let {
            it.icon.setColorFilter(resources.getColor(R.color.white), PorterDuff.Mode.SRC_ATOP)
            it.setEnabled(paused && !gameover && gamestarted)
            it.setVisible(paused && !gameover && gamestarted)
        }
        menu?.findItem(R.id.pause)?.let {
            it.icon.setColorFilter(resources.getColor(R.color.white), PorterDuff.Mode.SRC_ATOP)
            it.setEnabled(!paused && !gameover && gamestarted)
            it.setVisible(!paused && !gameover && gamestarted)
        }
        return super.onPrepareOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (drawerToggle?.onOptionsItemSelected(item) ?: false) {
            return true
        }
        item?.let {
            if (when (item.itemId) {
                R.id.play -> {
                    actionResumeGame()
                    true
                }
                R.id.pause -> {
                    actionPauseGame()
                    true
                }
                else -> false
            }) {
                Timber.d("RETURNING TRUE, BECAUSE SYNTAX!")
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle?.syncState()
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
        supportInvalidateOptionsMenu()
    }

    /**
     * Sets up the left drawer adapter and adds it to the recyclerview
     */
    fun setUpLeftDrawer() {
        if (sideBarAdapter == null) {
            sideBarAdapter = with(SideBarAdapter(this, this)) {
                setAccentColor(android.support.v4.content.ContextCompat.getColor(this@GameActivity, R.color.colorPrimary))
                this
            }
            left_drawer.adapter = sideBarAdapter
            left_drawer.layoutManager = LinearLayoutManager(this)
        }
    }


    /**
     * This starts the game
     */
    fun actionStartGame() {
        var infoOutAnim = genHideInfoAnim()
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
            Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_alltimehighscores_id), points.toLong())
        }

        grid_container.scaleX = 1f
        grid_container.scaleY = 1f
        gridbinderview_top.translationY = 0f
        gridbinderview_bottom.translationY = 0f

        level = 0
        points = 0
        touchedInTick = false
        noTouchStreak = 0
        spentTicks = 0L
        gameover = false
        paused = false
        gamestarted = false
        topGrid = initFullGrid(1, 1)
        bottomGrid = initFullGrid(1, 1)

        //Set up grids
        gridbinderview_top.grid = topGrid
        gridbinderview_bottom.grid = bottomGrid
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
        }
    }

    /**
     * This is effectively the game loop, it does count downs and what not
     */
    fun subscribeToTicker(spentSeconds: Int = 0) {
        unsubscribeFromTicker()

        var seconds = secondForLevel(level)
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
     * Set coords filled, return true if change was made
     */
    fun addCoords(coords: Pair<Int, Int>): Boolean {
        if (topGridCoordsValid(coords)) {
            if (topGrid.at(coords.first, coords.second) == null) {
                topGrid.put(coords.first, coords.second, true)
                gridbinderview_top.grid = topGrid
                return true
            }
        }
        return false
    }

    /**
     * Set coords empty, return true if change was made
     */
    fun subtractCoords(coords: Pair<Int, Int>): Boolean {
        if (topGridCoordsValid(coords)) {
            if (topGrid.at(coords.first, coords.second) != null) {
                topGrid.put(coords.first, coords.second, null)
                gridbinderview_top.grid = topGrid
                return true
            }
        }
        return false
    }

    /**
     * Check if the coordinates at the given top grid are filled
     */
    fun topGridCoordsFilled(coords: Pair<Int, Int>): Boolean {
        return topGridCoordsValid(coords) && topGrid.at(coords.first, coords.second) != null
    }

    /**
     * Check validity of coords in the top grid
     */
    fun topGridCoordsValid(coords: Pair<Int, Int>): Boolean {
        return coords.first >= 0 && coords.first < topGrid.width && coords.second >= 0 && coords.second < topGrid.height
    }

    /**
     * Init the grids according to the level and bind them to the views
     */
    fun initGridsForLevel(lvl: Int) {
        topGrid = initFullGrid(gridWidthForLevel(lvl), gridHeightForLevel(lvl))
        bottomGrid = initBottomGrid(gridWidthForLevel(lvl), gridHeightForLevel(lvl))
        gridbinderview_top.grid = topGrid
        gridbinderview_bottom.grid = bottomGrid
    }

    /**
     * Generate a grid to use for the bottom in the starting position
     */
    fun initBottomGrid(width: Int, height: Int): Grid {
        var grid = Grid(width, height)

        while ((width > 1 && grid.rowFull(0)) || grid.rowEmpty(0)) {
            for (i in grid.slots.indices) {
                val dip = random.nextInt(grid.slots[i].size)
                Timber.d("Dip:" + dip)
                for (j in grid.slots[i].indices) {
                    if (j >= dip ) {
                        grid.put(i, j, true)
                    } else {
                        grid.put(i, j, null)
                    }
                }
            }
        }
        return grid
    }

    /**
     * Generate a grid to use for the top in the starting position
     */
    fun initFullGrid(width: Int, height: Int): Grid {
        var grid = Grid(width, height)
        for (i in 0..width - 1) {
            for (j in 0..height - 1) {
                grid.put(i, j, true)
            }
        }
        return grid
    }

    /**
     * Use this function to smash together two grids into one
     */
    fun combineShapes(topG: Grid, btmG: Grid): Grid {
        var yOffset = 0
        var workingBoard = Grid(topG.width, topG.height * 2)
        GridHelper.addGrid(workingBoard, topG, 0, 0)
        while (GridHelper.gridInBounds(workingBoard, btmG, 0, workingBoard.height - btmG.height - (yOffset + 1)) && !GridHelper.gridsCollide(workingBoard, btmG, 0, workingBoard.height - btmG.height - (yOffset + 1))) {
            yOffset++
        }
        GridHelper.addGrid(workingBoard, btmG, 0, workingBoard.height - btmG.height - yOffset)

        //This is the shape after the things have been smashed together
        var combinedShape = GridHelper.copyGridPortion(workingBoard, 0, 0, workingBoard.width, workingBoard.height - yOffset)
        return combinedShape
    }


    /**
     * This checks if a shape is solid.
     * When we smash our top and bottom grid together our shape should be solid, so gameover if not solid.
     */
    fun combinedShapeIsGameOver(grid: Grid): Boolean {
        var gameOver = false
        for (i in 0..grid.width - 1) {
            if (!grid.colFull(i)) {
                gameOver = true
                break
            }
        }
        return gameOver
    }

    /**
     * Figure out the y translations for the top and bottom grids respectively
     */
    fun getCombinedShapeGridAnimTranslations(combinedShape: Grid, singleSideHeight: Int): Pair<Float, Float> {
        var centerBoard = Grid(combinedShape.width, singleSideHeight * 2)
        gridbinderview_center.grid = centerBoard//We set this up so we can correctly calculate positions
        var shapePos = gridbinderview_center.getSubGridPosition(combinedShape, 0, (centerBoard.height - combinedShape.height) / 2)
        var topTransY = gridbinderview_center.top + shapePos.top - gridbinderview_top.top
        var btmTransY = gridbinderview_center.top + shapePos.bottom - gridbinderview_bottom.bottom
        return Pair(topTransY, btmTransY)
    }


    /**
     * This builds a nice animation for the two sides to crash together.
     */
    fun genSmashAnim(): Animator {
        var tCopy = GridHelper.copyGrid(topGrid)
        var bCopy = GridHelper.copyGrid(bottomGrid)
        var combined = combineShapes(tCopy, bCopy)
        var trans = getCombinedShapeGridAnimTranslations(combined, tCopy.height)
        var gOver = combinedShapeIsGameOver(combined)

        var topMove = ObjectAnimator.ofFloat(gridbinderview_top, "translationY", 0f, trans.first)
        var btmMove = ObjectAnimator.ofFloat(gridbinderview_bottom, "translationY", 0f, trans.second)
        var animMoves = AnimatorSet()
        animMoves.playTogether(topMove, btmMove)
        animMoves.interpolator = AccelerateInterpolator()
        animMoves.setDuration(350)

        //This is the return animator
        var animCombined = AnimatorSet()
        if (gOver) {
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
                    gameover = gOver
                }

                override fun onAnimationEnd(animation: Animator?) {
                    //Show an ad on gameover
                    stopped_container.postDelayed(Runnable {
                        if (activityResumed && interstitialAd?.isLoaded ?: false) {
                            if (gamestarted) {
                                actionPauseGame()
                            }
                            interstitialAd?.show()
                        }
                    }, 1000)

                }
            })
            animCombined.playSequentially(animMoves, scaleDownBoardsAnim, enterGameOver)
        } else {
            var endScale = 0.2f
            var gridsZoomX = ObjectAnimator.ofFloat(grid_container, "scaleX", 1f, endScale)
            var gridsZoomY = ObjectAnimator.ofFloat(grid_container, "scaleY", 1f, endScale)
            var gridsAlpha = ObjectAnimator.ofFloat(grid_container, "alpha", 1f, 0f)
            var animGridsOut = AnimatorSet()
            animGridsOut.playTogether(gridsZoomX, gridsZoomY, gridsAlpha)
            animGridsOut.interpolator = AccelerateInterpolator()
            animGridsOut.setDuration(700)


            var topReturn = ObjectAnimator.ofFloat(gridbinderview_top, "translationY", -gridbinderview_top.height.toFloat(), 0f)
            var btmReturn = ObjectAnimator.ofFloat(gridbinderview_bottom, "translationY", gridbinderview_bottom.height.toFloat(), 0f)
            var animReenter = AnimatorSet()
            animReenter.playTogether(topReturn, btmReturn)
            animReenter.interpolator = DecelerateInterpolator()
            animReenter.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator?) {
                    grid_container.scaleX = 1f
                    grid_container.scaleY = 1f
                    grid_container.alpha = 1f


                    level++
                    points += 100 * level
                    initGridsForLevel(level)
                }

                override fun onAnimationEnd(animation: Animator?) {
                    subscribeToTicker()
                    if (level == 1) {
                        setGridHelpTextShowing(true)
                    }
                }
            })
            animGridsOut.setDuration(450)
            animCombined.playSequentially(animMoves, animGridsOut, animReenter)
        }
        animCombined.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                setGridHelpTextShowing(false)
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
            countdown_container.setBackgroundColor(argb.evaluate(it.animatedValue as Float, startColor, endColor) as Int)
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
    fun genHideInfoAnim(): Animator {
        //Play btn anims
        var endScale = 0.1f
        var playScaleX = ObjectAnimator.ofFloat(play_btn, "scaleX", 1f, endScale)
        var playScaleY = ObjectAnimator.ofFloat(play_btn, "scaleY", 1f, endScale)
        var playAlpha = ObjectAnimator.ofFloat(play_btn, "alpha", 1f, 0f)
        var playBtnOut = AnimatorSet()
        playBtnOut.playTogether(playScaleX, playScaleY, playAlpha)

        var anim = AnimatorSet()
        anim.playTogether(playBtnOut)
        anim.setDuration(250)
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                start_container.visibility = View.INVISIBLE
                play_btn.scaleX = 1f
                play_btn.scaleY = 1f
                play_btn.alpha = 1f
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

    /**
     * How tall should our grids be given the arg level
     */
    fun gridHeightForLevel(lvl: Int): Int {
        return (lvl + 1) / 2 + 1
    }

    /**
     * How wide should our grids be given the arg level
     */
    fun gridWidthForLevel(lvl: Int): Int {
        return 4 + (lvl - 1) * 2
    }

    /**
     * How much time in seconds should we have to solve the puzzle given the arg level
     */
    fun secondForLevel(lvl: Int): Int {
        return lvl * 2 + 1
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
                    getString(R.string.leaderboard_alltimehighscores_id)), REQUEST_LEADERBOARD);
        } else {
            Toast.makeText(this, R.string.you_must_be_signed_in, Toast.LENGTH_SHORT).show()
        }
    }


    /**
     * SIGN IN STUFF
     */

    override fun onConnected(connectionHint: Bundle?) {
        Timber.d("SignIn - onConnected")
        if (gameover && points > 0) {
            Games.Leaderboards.submitScore(googleApiClient, getString(R.string.leaderboard_alltimehighscores_id), points.toLong())
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
                var hash = md5(andId).toUpperCase()
                timber.log.Timber.d("Adding test device. hash:" + hash)
                addTestDevice(hash)
            }
            build()
        }
        interstitialAd?.loadAd(adRequest);
    }

    private fun md5(s: String): String {
        try {
            var digest = MessageDigest.getInstance("MD5")
            digest.update(s.toByteArray())
            var messageDigest = digest.digest()

            var hexString = StringBuffer()
            for (i in messageDigest.indices) {
                var h = Integer.toHexString(0xFF and messageDigest[i].toInt())
                while (h.length < 2)
                    h = "0" + h
                hexString.append(h)
            }
            return hexString.toString()
        } catch(ex: Exception) {
            Timber.e(ex, "Fail in md5");
        }
        return ""
    }
}

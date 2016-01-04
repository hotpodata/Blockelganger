package com.hotpodata.blockelganger.activity

import android.animation.*
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.hotpodata.blockelganger.R
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
import kotlinx.android.synthetic.main.activity_game.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

class GameActivity : AppCompatActivity() {

    var topGrid = initFullGrid(1, 1)
    var bottomGrid = initFullGrid(1, 1)

    var touchCoords: Pair<Int, Int>? = null
    var touchModeIsAdd = false

    var subTicker: Subscription? = null
    var random = Random()
    var actionAnimator: Animator? = null

    var level = 0
        set(lvl: Int) {
            field = lvl
            supportActionBar?.subtitle = getString(R.string.level_template, lvl)
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        //Set up how to draw the blocks
        val topColor = getResources().getColor(R.color.top_grid)
        val btmColor = getResources().getColor(R.color.btm_grid)
        gridbinderview_top.blockDrawer = object : GridBinderView.IBlockDrawer {
            override fun drawBlock(canvas: Canvas, data: Any) {
                canvas.drawColor(topColor)
            }
        }
        gridbinderview_bottom.blockDrawer = object : GridBinderView.IBlockDrawer {
            override fun drawBlock(canvas: Canvas, data: Any) {
                canvas.drawColor(btmColor)
            }
        }

        //Set up grids
        gridbinderview_top.grid = topGrid
        gridbinderview_bottom.grid = bottomGrid

        //Set up the touch listener for the top grid
        gridbinderview_top.setOnTouchListener {
            view, motionEvent ->
            if (allowGameActions()) {
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

        //This is just a temporary way to start/restart the game until we get real controls hooked up
        play_btn.setOnClickListener {
            unsubscribeFromTicker()
            level = 0

            var infoOutAnim = genHideInfoAnim()
            var startSmashAnim = genSmashAnim()
            var startAnim = AnimatorSet()
            startAnim.playSequentially(infoOutAnim, startSmashAnim)
            startAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator?) {
                    subscribeToTicker()
                }
            })
            actionAnimator = startAnim
            startAnim.start()
        }

    }


    /**
     * This is effectively the game loop, it does count downs and what not
     */
    fun subscribeToTicker() {
        unsubscribeFromTicker()

        var seconds = secondForLevel(level)
        subTicker = Observable.interval(1, TimeUnit.SECONDS)
                .filter({ l -> allowGameActions() })//So we don't do anything
                .take(seconds + 1)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            l ->
                            if (l < seconds) {
                                countdown_tv.text = "" + (seconds - l)
                                var anim = genCountDownOutAnim(resources.getColor(R.color.countdown_flash_color))
                                anim.start()
                            }
                        }
                        ,
                        {
                            ex ->
                            Timber.e(ex, "Fail!")
                        },
                        {

                            var anim = genSmashAnim()
                            anim.addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator?) {
                                    subscribeToTicker()
                                }
                            })
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
        if (actionAnimator?.isRunning() ?: false) {
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
        var minFill = -1
        var maxFill = -1
        while (minFill == maxFill) {
            //This is so we dont end up with flat shapes
            minFill = -1
            maxFill = -1
            for (i in 0..width - 1) {
                var fill = random.nextInt(height)
                if (minFill < 0 || maxFill < 0) {
                    minFill = fill
                    maxFill = fill
                } else {
                    if (fill > maxFill) {
                        maxFill = fill
                    }
                    if (fill < minFill) {
                        minFill = fill
                    }
                }
                for (j in height - 1 downTo fill) {
                    Timber.d("filling j:" + j + " height-1:" + (height - 1) + " fill:" + fill + " minFill:" + minFill + " maxFill:" + maxFill)
                    grid.put(i, j, true)
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
     * This method returns the required topShape translation and bottomShape translation in a pair
     */
    fun smashGrids(): Pair<Float, Float> {
        var btmG = GridHelper.copyGrid(bottomGrid)
        var topG = GridHelper.copyGrid(topGrid)

        var yOffset = 0
        var workingBoard = Grid(topG.width, topG.height * 2)
        GridHelper.addGrid(workingBoard, topG, 0, 0)
        while (!GridHelper.gridsCollide(workingBoard, btmG, 0, workingBoard.height - btmG.height - (yOffset + 1)) && yOffset < workingBoard.height) {
            yOffset++
        }
        GridHelper.addGrid(workingBoard, btmG, 0, workingBoard.height - btmG.height - yOffset)

        //This is the shape after the things have been smashed together
        var combinedShape = GridHelper.copyGridPortion(workingBoard, 0, 0, workingBoard.width, workingBoard.height - yOffset)

        //We add our new board so that we can easily get coordinates for smashing things...
        var centerBoard = Grid(topG.width, topG.height * 2)
        GridHelper.addGrid(centerBoard, combinedShape, 0, yOffset / 2)
        gridbinderview_center.grid = centerBoard

        var topShapePos = gridbinderview_center.getSubGridPosition(topG, 0, yOffset / 2)
        var btmShapePos = gridbinderview_center.getSubGridPosition(btmG, 0, workingBoard.height - btmG.height - yOffset)

        var topTransY = gridbinderview_center.top + topShapePos.top - gridbinderview_top.top
        var btmTransY = gridbinderview_center.top + btmShapePos.top - gridbinderview_bottom.top

        return Pair(topTransY, btmTransY)
    }

    /**
     * This builds a nice animation for the two sides to crash together.
     */
    fun genSmashAnim(): Animator {
        var trans = smashGrids()

        var topMove = ObjectAnimator.ofFloat(gridbinderview_top, "translationY", 0f, trans.first)
        var btmMove = ObjectAnimator.ofFloat(gridbinderview_bottom, "translationY", 0f, trans.second)
        var animMoves = AnimatorSet()
        animMoves.playTogether(topMove, btmMove)
        animMoves.interpolator = AccelerateInterpolator()
        animMoves.setDuration(350)

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

                //Dimens should come from levels
                level++
                initGridsForLevel(level)
            }
        })
        animGridsOut.setDuration(450)

        var animCombined = AnimatorSet()
        animCombined.playSequentially(animMoves, animGridsOut, animReenter)
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
        var countdownZoomX = ObjectAnimator.ofFloat(countdown_tv, "scaleX", 1f, endScale)
        var countdownZoomY = ObjectAnimator.ofFloat(countdown_tv, "scaleY", 1f, endScale)
        var countdownAlpha = ObjectAnimator.ofFloat(countdown_tv, "alpha", 1f, 0f)
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

    fun genHideInfoAnim(): Animator {
        //Text anims
        var infoTopY = ObjectAnimator.ofFloat(info_top, "translationY", 0f, -info_top.height.toFloat())
        var infoBtmY = ObjectAnimator.ofFloat(info_bottom, "translationY", 0f, info_bottom.height.toFloat())

        //Play btn anims
        var endScale = 0.1f
        var playScaleX = ObjectAnimator.ofFloat(play_btn, "scaleX", 1f, endScale)
        var playScaleY = ObjectAnimator.ofFloat(play_btn, "scaleY", 1f, endScale)
        var playAlpha = ObjectAnimator.ofFloat(play_btn, "alpha", 1f, 0f)
        var playBtnOut = AnimatorSet()
        playBtnOut.playTogether(playScaleX, playScaleY, playAlpha)

        var anim = AnimatorSet()
        anim.playTogether(infoTopY, infoBtmY, playBtnOut)
        anim.setDuration(450)
        return anim
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

}

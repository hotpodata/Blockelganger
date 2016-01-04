package com.hotpodata.blockelganger.activity

import android.animation.*
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
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

    var topGrid = Grid(4, 2)
    var bottomGrid = Grid(4, 2)

    var touchCoords: Pair<Int, Int>? = null
    var touchModeIsAdd = false

    var subTicker: Subscription? = null
    var random = Random()
    var actionAnimator: Animator? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        topGrid = initTopGrid(4, 2)
        bottomGrid = initBottomGrid(4, 2)

        gridbinderview_top.grid = topGrid
        gridbinderview_bottom.grid = bottomGrid
        gridbinderview_bottom.blockDrawer = object : GridBinderView.IBlockDrawer {
            override fun drawBlock(canvas: Canvas, data: Any) {
                canvas.drawColor(Color.BLUE)
            }
        }

        gridbinderview_top.setOnTouchListener {
            view, motionEvent ->
            var gridView = view as GridBinderView
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
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
        }

        gridbinderview_bottom.setOnClickListener {
            subscribeToTicker()
        }

    }


    fun subscribeToTicker() {
        unsubscribeFromTicker()

        var seconds = 3

        subTicker = Observable.interval(1, TimeUnit.SECONDS)
                .filter({ l -> allowGameActions() })//So we don't do anything
                .take(seconds + 1)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        {
                            l ->
                            if (l < seconds) {
                                countdown_tv.text = "" + (seconds - l)
                                var anim = genCountDownOutAnim(Color.YELLOW)
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
                            anim.start()

                        }

                )

    }

    fun unsubscribeFromTicker() {
        subTicker?.let { if (!it.isUnsubscribed) it.unsubscribe() }
    }

    fun allowGameActions(): Boolean {
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

    fun initTopGrid(width: Int, height: Int): Grid {
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
                topGrid = initTopGrid(4, 2)
                gridbinderview_top.grid = topGrid
                bottomGrid = initBottomGrid(4, 2)
                gridbinderview_bottom.grid = bottomGrid
            }
        })
        animGridsOut.setDuration(450)

        var animCombined = AnimatorSet()
        animCombined.playSequentially(animMoves, animGridsOut, animReenter)
        return animCombined
    }

    fun genCountDownOutAnim(bgColor: Int): Animator {

        var argb = ArgbEvaluator()
        var startColor : Int = bgColor
        var endColor : Int = Color.TRANSPARENT
        var bgAnim = ValueAnimator.ofFloat(0f,1f)
        bgAnim.addUpdateListener {
            countdown_container.setBackgroundColor(argb.evaluate(it.animatedValue as Float, startColor,endColor) as Int)
        }

        var endScale = 0.2f
        var countdownZoomX = ObjectAnimator.ofFloat(countdown_tv, "scaleX", 1f, endScale)
        var countdownZoomY = ObjectAnimator.ofFloat(countdown_tv, "scaleY", 1f, endScale)
        var countdownAlpha = ObjectAnimator.ofFloat(countdown_tv, "alpha", 1f, 0f)
        var animCountdownOut = AnimatorSet()
        animCountdownOut.playTogether(countdownZoomX, countdownZoomY, countdownAlpha, bgAnim)
        animCountdownOut.interpolator = AccelerateInterpolator()
        animCountdownOut.setDuration(700)
        return animCountdownOut
    }
}

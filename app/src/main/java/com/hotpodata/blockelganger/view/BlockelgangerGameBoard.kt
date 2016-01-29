package com.hotpodata.blockelganger.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.Context
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import com.hotpodata.blockelganger.BuildConfig
import com.hotpodata.blockelganger.R
import com.hotpodata.blockelganger.helpers.ColorBlockDrawer
import com.hotpodata.blockelganger.helpers.GameGridHelper
import com.hotpodata.blockelganger.helpers.GameHelper
import com.hotpodata.blockelganger.helpers.GridTouchListener
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
import com.hotpodata.common.view.SizeAwareFrameLayout
import kotlinx.android.synthetic.main.blockelganger_game_board.view.*
import timber.log.Timber
import java.util.*

/**
 * Created by jdrotos on 1/29/16.
 *
 * This is the class that manages positioning and sizing the various grids.
 * It also handles animating between board states
 *
 */
class BlockelgangerGameBoard : SizeAwareFrameLayout, GridTouchListener.ITouchCoordinator {

    interface IGangerTouchListener {
        fun onGangerTouched()
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

    private var chapter: GameHelper.Chapter = GameHelper.Chapter.ONE
    var touchHintAnims = HashMap<View, Animator>()

    var parentTouchCoordinator: GridTouchListener.ITouchCoordinator? = null
    var gangerTouchListener: IGangerTouchListener? = null

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init(context)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context)
    }

    fun init(context: Context) {
        var inflater = LayoutInflater.from(context)
        inflater.inflate(R.layout.blockelganger_game_board, this, true);

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
            gangerTouchListener?.onGangerTouched()
        }
        gridbinderview_blockelganger_two.setOnClickListener {
            gangerTouchListener?.onGangerTouched()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        prepareGridViews()
    }

    /**
     * GridTouchListener.ITouchCoordinator
     */
    override fun onGridTouched(view: View) {
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

        parentTouchCoordinator?.onGridTouched(view)
    }

    override fun allowGridTouch(view: View): Boolean {
        return parentTouchCoordinator?.allowGridTouch(view) ?: false
    }

    /**
     * SET DATA FOR A GIVEN CHAPTER
     */

    fun chapterOne(playerGrid: Grid, gangerGrid: Grid) {
        chapter = GameHelper.Chapter.ONE
        gridOne = playerGrid
        gridTwo = Grid(1, 1)
        gangerOneGrid = gangerGrid
        gangerTwoGrid = Grid(1, 1)
        prepareGridViews()
    }

    fun chapterTwo(playerGridTop: Grid, playerGridBtm: Grid, gangerGrid: Grid) {
        chapter = GameHelper.Chapter.TWO
        gridOne = playerGridTop
        gridTwo = playerGridBtm
        gangerOneGrid = gangerGrid
        gangerTwoGrid = Grid(1, 1)
        prepareGridViews()
    }

    fun chapterThree(playerGrid: Grid, gangerGrid: Grid) {
        chapter = GameHelper.Chapter.THREE
        gridOne = playerGrid
        gridTwo = Grid(1, 1)
        gangerOneGrid = gangerGrid
        gangerTwoGrid = Grid(1, 1)
        prepareGridViews()
    }

    fun chapterFour(playerGridTop: Grid, playerGridBtm: Grid, gangerGridCenter: Grid, gangerGridLeft: Grid) {
        chapter = GameHelper.Chapter.FOUR
        gridOne = playerGridTop
        gridTwo = playerGridBtm
        gangerOneGrid = gangerGridCenter
        gangerTwoGrid = gangerGridLeft
        prepareGridViews()
    }

    /**
     *
     */
    fun setAccentColor(color: Int) {
        touch_hint_top.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        touch_hint_btm.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        touch_hint_right.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
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

    fun genBoardsCollideAnim(combined: Grid): Animator {
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

    fun genBoardsShrinkAnim(): Animator {
        var endGameScale = if (chapter == GameHelper.Chapter.THREE || chapter == GameHelper.Chapter.FOUR) 0.25f else 0.5f
        var gameScaleX = ObjectAnimator.ofFloat(grid_container, "scaleX", 1f, endGameScale)
        var gameScaleY = ObjectAnimator.ofFloat(grid_container, "scaleY", 1f, endGameScale)
        var scaleDownBoardsAnim = AnimatorSet()
        scaleDownBoardsAnim.playTogether(gameScaleX, gameScaleY)
        scaleDownBoardsAnim.interpolator = AccelerateInterpolator()
        scaleDownBoardsAnim.setDuration(450)
        return scaleDownBoardsAnim
    }

    fun genBoardsHideAnim(): Animator {
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

    fun genBoardsEnterAnim(chap: GameHelper.Chapter): Animator {
        var animReenter = AnimatorSet()
        var gangerReturn = when (chap) {
            GameHelper.Chapter.ONE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationY", height - gridbinderview_blockelganger_one.top.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.TWO -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationX", width.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationY = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.THREE -> {
                var anim = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationY", height.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.FOUR -> {
                var gangerOne = ObjectAnimator.ofFloat(gridbinderview_blockelganger_one, "translationX", width.toFloat(), 0f)
                var gangerTwo = ObjectAnimator.ofFloat(gridbinderview_blockelganger_two, "translationY", height.toFloat(), 0f)
                var anim = AnimatorSet()
                anim.playTogether(gangerOne, gangerTwo)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_blockelganger_one.translationY = 0f
                        gridbinderview_blockelganger_two.translationX = 0f
                    }
                })
                anim
            }
        }

        var playablePieceReturn = when (chapter) {
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
                var btmAnim = ObjectAnimator.ofFloat(gridbinderview_two, "translationY", height - gridbinderview_two.top.toFloat(), 0f)
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
                var anim = ObjectAnimator.ofFloat(gridbinderview_one, "translationY", -height.toFloat(), 0f)
                anim.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        gridbinderview_one.translationX = 0f
                    }
                })
                anim
            }
            GameHelper.Chapter.FOUR -> {
                var topAnim = ObjectAnimator.ofFloat(gridbinderview_one, "translationY", -gridbinderview_one.bottom.toFloat(), 0f)
                var btmAnim = ObjectAnimator.ofFloat(gridbinderview_two, "translationY", height - gridbinderview_two.top.toFloat(), 0f)
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

        animReenter.playTogether(playablePieceReturn, gangerReturn)
        animReenter.interpolator = DecelerateInterpolator()
        animReenter.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                grid_container.scaleX = 1f
                grid_container.scaleY = 1f
                grid_container.alpha = 1f
                prepareGridViews()
            }
        })
        return animReenter
    }


    fun pauseAllTouchHintAnims() {
        for (anim in touchHintAnims.values) {
            anim.pause()
        }
    }

    fun resumePausedTouchHintAnims() {
        for (anim in touchHintAnims.values) {
            anim.resume()
        }
    }

    fun setTouchHintsShowing(showing: Boolean) {
        if (showing) {
            when (chapter) {
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
        } else {
            setTouchHintShowing(touch_hint_top, false)
            setTouchHintShowing(touch_hint_btm, false)
            setTouchHintShowing(touch_hint_right, false)
        }
    }


    private fun setTouchHintShowing(hintView: View, showing: Boolean, delay: Long = 0) {
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

    fun resetAnimationChanges() {
        var views = ArrayList<View>()
        views.add(grid_container)
        for (i in 0..grid_container.childCount - 1) {
            views.add(grid_container.getChildAt(i))
        }
        for (v in views) {
            v.translationX = 0f
            v.translationY = 0f
            v.scaleX = 1f
            v.scaleY = 1f
            v.alpha = 1f
        }
    }

    fun prepareGridViews() {
        gridbinderview_animation_coordinator.visibility = View.INVISIBLE

        //var thirdHeight = usableHeight / 3f
        var fifthHeight = height / 5f
        var thirdWidth = width / 3f

        when (chapter) {
            GameHelper.Chapter.ONE -> {
                (gridbinderview_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = fifthHeight.toInt()
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    it.gravity = Gravity.TOP
                    gridbinderview_one.layoutParams = it
                    touch_hint_top.layoutParams = FrameLayout.LayoutParams(it)
                }

                (gridbinderview_blockelganger_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = fifthHeight.toInt()
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    it.gravity = Gravity.BOTTOM
                    gridbinderview_blockelganger_one.layoutParams = it
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
                (gridbinderview_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = fifthHeight.toInt()
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    it.gravity = Gravity.TOP
                    gridbinderview_one.layoutParams = it
                    touch_hint_top.layoutParams = FrameLayout.LayoutParams(it)
                }
                (gridbinderview_two.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = fifthHeight.toInt()
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    it.gravity = Gravity.BOTTOM
                    gridbinderview_two.layoutParams = it
                    touch_hint_btm.layoutParams = FrameLayout.LayoutParams(it)
                }

                var gangerHeight = (gridbinderview_one.getSubGridPosition(Grid(1, 1), 0, 0).height() * gangerOneGrid.height).toInt()
                (gridbinderview_blockelganger_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = gangerHeight
                    it.width = ViewGroup.LayoutParams.MATCH_PARENT
                    it.gravity = Gravity.CENTER_VERTICAL
                    gridbinderview_blockelganger_one.layoutParams = it
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
                (gridbinderview_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = ViewGroup.LayoutParams.MATCH_PARENT
                    it.width = thirdWidth.toInt()
                    it.gravity = Gravity.RIGHT
                    gridbinderview_one.layoutParams = it
                    touch_hint_right.layoutParams = FrameLayout.LayoutParams(it)
                }

                (gridbinderview_blockelganger_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = ViewGroup.LayoutParams.MATCH_PARENT
                    it.width = thirdWidth.toInt()
                    it.gravity = Gravity.LEFT
                    gridbinderview_blockelganger_one.layoutParams = it
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

                var blockW = width / totalBlocksW.toFloat()
                var blockH = height / totalBlocksH.toFloat()

                (gridbinderview_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = (gridOne.height * blockH).toInt()
                    it.width = (gridOne.width * blockW).toInt()
                    it.gravity = Gravity.TOP or Gravity.RIGHT
                    gridbinderview_one.layoutParams = it
                    touch_hint_top.layoutParams = FrameLayout.LayoutParams(it)
                }

                (gridbinderview_two.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = (gridTwo.height * blockH).toInt()
                    it.width = (gridTwo.width * blockW).toInt()
                    it.gravity = Gravity.BOTTOM or Gravity.RIGHT
                    gridbinderview_two.layoutParams = it
                    touch_hint_btm.layoutParams = FrameLayout.LayoutParams(it)
                }

                (gridbinderview_blockelganger_one.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = (gangerOneGrid.height * blockH).toInt()
                    it.width = (gangerOneGrid.width * blockW).toInt()
                    it.gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
                    gridbinderview_blockelganger_one.layoutParams = it
                }

                (gridbinderview_blockelganger_two.layoutParams as? FrameLayout.LayoutParams)?.let {
                    it.height = (gangerTwoGrid.height * blockH).toInt()
                    it.width = (gangerTwoGrid.width * blockW).toInt()
                    it.gravity = Gravity.CENTER_VERTICAL or Gravity.LEFT
                    gridbinderview_blockelganger_two.layoutParams = it
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


    fun combineGrids(): Grid {
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
        return combined
    }

}
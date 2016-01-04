package com.hotpodata.blockelganger.activity

import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import com.hotpodata.blockelganger.R
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import com.hotpodata.blocklib.view.GridBinderView
import kotlinx.android.synthetic.main.activity_game.*
import java.util.*

class GameActivity : AppCompatActivity() {

    var topGrid = Grid(4, 2)
    var bottomGrid = Grid(4, 2)

    var touchCoords: Pair<Int, Int>? = null
    var touchModeIsAdd = false

    var random = Random()


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
            var trans = smashGrids()
            gridbinderview_top.animate().translationY(trans.first).start()
            gridbinderview_bottom.animate().translationY(trans.second).start()
        }

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
        for (i in 0..width - 1) {
            var fill = random.nextInt(height)
            for (j in height - 1 downTo fill) {
                grid.put(i, j, true)
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
    fun smashGrids():Pair<Float,Float>{
        var btmG = GridHelper.copyGrid(bottomGrid)
        var topG = GridHelper.copyGrid(topGrid)

        var yOffset = 0
        var workingBoard = Grid(topG.width, topG.height * 2)
        GridHelper.addGrid(workingBoard,topG,0,0)
        while(!GridHelper.gridsCollide(workingBoard,btmG,0,workingBoard.height - btmG.height - (yOffset + 1)) && yOffset < workingBoard.height){
            yOffset++
        }
        GridHelper.addGrid(workingBoard, btmG,0,workingBoard.height - btmG.height - yOffset)

        //This is the shape after the things have been smashed together
        var combinedShape = GridHelper.copyGridPortion(workingBoard,0,0,workingBoard.width,workingBoard.height - yOffset)

        //We add our new board so that we can easily get coordinates for smashing things...
        var centerBoard = Grid(topG.width, topG.height * 2)
        GridHelper.addGrid(centerBoard,combinedShape,0,yOffset/2)
        gridbinderview_center.grid = centerBoard

        var topShapePos = gridbinderview_center.getSubGridPosition(topG,0,yOffset/2)
        var btmShapePos = gridbinderview_center.getSubGridPosition(btmG,0,workingBoard.height - btmG.height - yOffset)

        var topTransY = gridbinderview_center.top + topShapePos.top - gridbinderview_top.top
        var btmTransY = gridbinderview_center.top + btmShapePos.top - gridbinderview_bottom.top

        return Pair(topTransY,btmTransY)
    }
}

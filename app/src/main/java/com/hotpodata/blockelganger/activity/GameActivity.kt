package com.hotpodata.blockelganger.activity

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import com.hotpodata.blockelganger.R
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.view.GridBinderView
import kotlinx.android.synthetic.main.activity_game.*

class GameActivity : AppCompatActivity() {

    var topGrid = Grid(5, 3)
    var bottomGrid = Grid(5, 3)

    var touchCoords: Pair<Int, Int>? = null
    var touchModeIsAdd = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        gridbinderview_top.grid = topGrid
        gridbinderview_bottom.grid = bottomGrid

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
}

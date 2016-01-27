package com.hotpodata.blockelganger.helpers

import android.view.MotionEvent
import android.view.View
import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.view.GridBinderView

/**
 * Created by jdrotos on 1/21/16.
 */
class GridTouchListener(val touchCoordinator: ITouchCoordinator, val gridChangeListener: IGridChangedListener) : View.OnTouchListener {

    public interface ITouchCoordinator {
        fun onGridTouched(view: View)
        fun allowGridTouch(view: View): Boolean
    }

    public interface IGridChangedListener {
        fun onGridChanged(grid: Grid)
    }

    var touchCoords: Pair<Int, Int>? = null
    var touchModeIsAdd = false

    /**
     * Set coords filled, return true if change was made
     */
    fun addCoords(grid: Grid, coords: Pair<Int, Int>): Boolean {
        if (gridCoordsValid(grid, coords)) {
            if (grid.at(coords.first, coords.second) == null) {
                grid.put(coords.first, coords.second, true)
                gridChangeListener.onGridChanged(grid)
                return true
            }
        }
        return false
    }

    /**
     * Set coords empty, return true if change was made
     */
    fun subtractCoords(grid: Grid, coords: Pair<Int, Int>): Boolean {
        if (gridCoordsValid(grid, coords)) {
            if (grid.at(coords.first, coords.second) != null) {
                grid.put(coords.first, coords.second, null)
                gridChangeListener.onGridChanged(grid)
                return true
            }
        }
        return false
    }

    /**
     * Check if the coordinates at the given top grid are filled
     */
    fun gridCoordsFilled(grid: Grid, coords: Pair<Int, Int>): Boolean {
        return gridCoordsValid(grid, coords) && grid.at(coords.first, coords.second) != null
    }

    /**
     * Check validity of coords in the top grid
     */
    fun gridCoordsValid(grid: Grid, coords: Pair<Int, Int>): Boolean {
        return coords.first >= 0 && coords.first < grid.width && coords.second >= 0 && coords.second < grid.height
    }

    /**
     * The actual touch handling
     */
    override fun onTouch(gridView: View?, motionEvent: MotionEvent?): Boolean {
        if (gridView != null && touchCoordinator.allowGridTouch(gridView) && motionEvent != null && gridView is GridBinderView) {
            var grid = gridView.grid
            if (grid != null) {
                touchCoordinator.onGridTouched(gridView)
                when (motionEvent.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        //Figure out where the touch happened and if this is to be an additive or subractive touch event
                        var gridCoords = gridView.getGridCoords(motionEvent.x, motionEvent.y)
                        if (gridCoordsValid(grid, gridCoords)) {
                            touchCoords = gridCoords
                            touchModeIsAdd = !gridCoordsFilled(grid, gridCoords)
                            if (touchModeIsAdd) {
                                addCoords(grid, gridCoords)
                            } else {
                                subtractCoords(grid, gridCoords)
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
                        if (gridCoordsValid(grid, gridCoords)) {
                            if (touchCoords == null) {
                                touchCoords = gridCoords
                                touchModeIsAdd = !gridCoordsFilled(grid, gridCoords)
                            }
                            if (touchModeIsAdd) {
                                addCoords(grid, gridCoords)
                            } else {
                                subtractCoords(grid, gridCoords)
                            }
                        }
                    }
                }
                return true
            }
        }
        return false
    }
}
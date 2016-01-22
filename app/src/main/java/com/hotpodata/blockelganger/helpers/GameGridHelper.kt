package com.hotpodata.blockelganger.helpers

import com.hotpodata.blocklib.Grid
import com.hotpodata.blocklib.GridHelper
import timber.log.Timber
import java.util.*

/**
 * Created by jdrotos on 1/21/16.
 */
object GameGridHelper {

    var random = Random()

    /**
     * Build a base grid for a playable block given the level
     */
    fun genGridForLevel(lvl: Int): Grid {
        return genFullGrid(GameHelper.gridWidthForLevel(lvl), GameHelper.gridHeightForLevel(lvl), true)
    }

    /**
     * Build a blockelganger block for the given level
     */
    fun genGangerForLevel(lvl: Int): Grid {
        if (GameHelper.chapterForLevel(lvl) == GameHelper.Chapter.TWO) {
            var tHeight = GameHelper.gangerHeightForLevel(lvl) / 2 + 1
            var bHeight = GameHelper.gangerHeightForLevel(lvl) / 2 + 1
            var top = generateOpenTopGangerGrid(GameHelper.gangerWidthForLevel(lvl), tHeight, true)
            var btm = generateOpenBottomGangerGrid(GameHelper.gangerWidthForLevel(lvl), bHeight,true)
            btm = GridHelper.copyGridPortion(btm,0,1,btm.width,btm.height)
            return combineShapes(top,btm)
        } else {
            return generateOpenTopGangerGrid(GameHelper.gangerWidthForLevel(lvl), GameHelper.gangerHeightForLevel(lvl), true)
        }
    }


    /**
     * Generate a new grid of specified size filled with fillData
     */
    fun genFullGrid(width: Int, height: Int, fillData: Any?): Grid {
        var grid = Grid(width, height)
        for (i in 0..width - 1) {
            for (j in 0..height - 1) {
                grid.put(i, j, fillData)
            }
        }
        return grid
    }

    /**
     * Generate a jagged grid where the contours are on top
     */
    fun generateOpenTopGangerGrid(width: Int, height: Int, filledVal: Any?): Grid {
        var grid = Grid(width, height)
        while ((width > 1 && grid.rowFull(0)) || grid.rowEmpty(0)) {
            for (i in grid.slots.indices) {
                val dip = random.nextInt(grid.slots[i].size)
                Timber.d("Dip:" + dip)
                for (j in grid.slots[i].indices) {
                    if (j >= dip ) {
                        grid.put(i, j, filledVal)
                    } else {
                        grid.put(i, j, null)
                    }
                }
            }
        }
        return grid
    }

    /**
     * Generate a jagged grid where the contours are on top
     */
    fun generateOpenBottomGangerGrid(width: Int, height: Int, filledVal: Any?): Grid {
        return generateOpenTopGangerGrid(width, height, filledVal).rotate(true).rotate(true)
    }


    /**
     * Use this function to smash together two grids into one
     */
    fun combineShapes(topG: Grid, btmG: Grid): Grid {
        var yOffset = 0
        var workingBoard = Grid(topG.width, topG.height + btmG.height)
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
}
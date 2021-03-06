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
        if (lvl <= 0) {
            return GameGridHelper.genFullGrid(1, 1, true)
        } else {
            return genFullGrid(GameHelper.gridBreadthForLevel(lvl), GameHelper.gridDepthForLevel(lvl), true)
        }
    }

    /**
     * Build a blockelganger block for the given level
     */
    fun genGangerForLevel(lvl: Int): Grid {
        if (lvl <= 0) {
            return GameGridHelper.genFullGrid(1, 1, true)
        } else {
            return when (GameHelper.chapterForLevel(lvl)) {
                GameHelper.Chapter.FOUR -> {
                    var top = GameGridHelper.generateOpenTopGangerGrid(GameHelper.gangerBreadthForLevel(lvl), GameHelper.gridDepthForLevel(lvl), true)
                    var btm = GameGridHelper.generateOpenBottomGangerGrid(GameHelper.gangerBreadthForLevel(lvl), GameHelper.gridDepthForLevel(lvl), true)
                    btm = GridHelper.copyGridPortion(btm, 0, 1, btm.width, btm.height)
                    GameGridHelper.combineShapesVert(top, btm)
                }
                GameHelper.Chapter.THREE -> {
                    return generateOpenTopGangerGrid(GameHelper.gangerBreadthForLevel(lvl), GameHelper.gangerDepthForLevel(lvl), true).rotate(false)
                }
                GameHelper.Chapter.TWO -> {
                    var tHeight = GameHelper.gangerDepthForLevel(lvl) / 2 + 1
                    var bHeight = GameHelper.gangerDepthForLevel(lvl) / 2 + 1
                    var top = generateOpenTopGangerGrid(GameHelper.gangerBreadthForLevel(lvl), tHeight, true)
                    var btm = generateOpenBottomGangerGrid(GameHelper.gangerBreadthForLevel(lvl), bHeight, true)
                    btm = GridHelper.copyGridPortion(btm, 0, 1, btm.width, btm.height)
                    combineShapesVert(top, btm)
                }
                GameHelper.Chapter.ONE -> {
                    return generateOpenTopGangerGrid(GameHelper.gangerBreadthForLevel(lvl), GameHelper.gangerDepthForLevel(lvl), true)
                }
            }
        }
    }

    fun genGangerTwoForLevel(lvl: Int, gangerOneGrid: Grid): Grid {
        if (GameHelper.chapterForLevel(lvl) == GameHelper.Chapter.FOUR) {
            var workingGangerGridTwo = GameGridHelper.generateOpenTopGangerGrid(gangerOneGrid.height + 2, gangerOneGrid.width / 2, true).rotate(false)
            for (i in workingGangerGridTwo.width downTo  1) {
                GridHelper.subtractGrid(workingGangerGridTwo, gangerOneGrid, i, (workingGangerGridTwo.height - gangerOneGrid.height) / 2)
            }
            return workingGangerGridTwo
        } else {
            return Grid(1, 1)
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
    fun combineShapesVert(topG: Grid, btmG: Grid): Grid {
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
     * Use this function to smash together two grids into one
     */
    fun combineShapesHoriz(leftG: Grid, rightG: Grid): Grid {
        var xOffset = 0
        var workingBoard = Grid(leftG.width + rightG.width, leftG.height)
        GridHelper.addGrid(workingBoard, leftG, 0, 0)
        while (GridHelper.gridInBounds(workingBoard, rightG, workingBoard.width - rightG.width - (xOffset + 1), 0) && !GridHelper.gridsCollide(workingBoard, rightG, workingBoard.width - rightG.width - (xOffset + 1), 0)) {
            xOffset++
        }
        GridHelper.addGrid(workingBoard, rightG, workingBoard.width - rightG.width - xOffset, 0)
        //This is the shape after the things have been smashed together
        var combinedShape = GridHelper.copyGridPortion(workingBoard, 0, 0, workingBoard.width - xOffset, workingBoard.height)
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
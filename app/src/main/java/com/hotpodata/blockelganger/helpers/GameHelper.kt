package com.hotpodata.blockelganger.helpers

import android.animation.FloatEvaluator
import android.view.animation.DecelerateInterpolator

/**
 * Created by jdrotos on 1/21/16.
 */
object GameHelper {
    
    val CHAPTER_ONE_LEVEL_THRESH = 0
    val CHAPTER_TWO_LEVEL_THRESH = 5
    val CHAPTER_THREE_LEVEL_THRESH = 10
    val CHAPTER_FOUR_LEVEL_THRESH = 20

    val MIN_SECONDS_DIVISOR = 1.0f
    val MAX_SECONDS_DIVISOR = 2.5f

    val secondsInterpolator = DecelerateInterpolator()
    val secondsEvaluator = FloatEvaluator()

    enum class Chapter {
        ONE,
        TWO,
        THREE,
        FOUR
    }

    fun levelIsChapterStart(lvl: Int): Boolean {
        for (chap in Chapter.values()) {
            if (lvl - 1 == threshForChap(chap)) {
                return true
            }
        }
        return false
    }

    fun threshForLevel(lvl: Int): Int {
        return threshForChap(chapterForLevel(lvl))
    }

    fun threshForChap(chap: Chapter): Int {
        return when (chap) {
            Chapter.ONE -> CHAPTER_ONE_LEVEL_THRESH
            Chapter.TWO -> CHAPTER_TWO_LEVEL_THRESH
            Chapter.THREE -> CHAPTER_THREE_LEVEL_THRESH
            Chapter.FOUR -> CHAPTER_FOUR_LEVEL_THRESH
        }
    }

    fun chapterForLevel(lvl: Int): Chapter {
        if (lvl > CHAPTER_FOUR_LEVEL_THRESH) {
            return Chapter.FOUR
        } else if (lvl > CHAPTER_THREE_LEVEL_THRESH) {
            return Chapter.THREE
        } else if (lvl > CHAPTER_TWO_LEVEL_THRESH) {
            return Chapter.TWO
        } else {
            return Chapter.ONE
        }
    }


    fun gangerDepthForLevel(lvl: Int): Int {
        if (chapterForLevel(lvl) == Chapter.TWO) {
            return gridDepthForLevel(lvl) * 2 - 1
        } else {
            return gridDepthForLevel(lvl)
        }
    }

    fun gangerBreadthForLevel(lvl: Int): Int {
        return gridBreadthForLevel(lvl)
    }

    /**
     * How tall should our grids be given the arg level
     */
    fun gridDepthForLevel(lvl: Int): Int {
        if (lvl > 0) {
            var workingLevel = (lvl - 1).toDouble()//zero-indexed level
            workingLevel = workingLevel - (threshForLevel(lvl))//distance from thresh
            return Math.floor(Math.sqrt(workingLevel)).toInt() + 2
        } else {
            return 1
        }
    }

    /**
     * How wide should our grids be given the arg level
     */
    fun gridBreadthForLevel(lvl: Int): Int {
        //6,4,6,8,6,8,10,12,14,16
        if (lvl > 0) {
            var workingLevel = lvl - (threshForLevel(lvl))
            return workingLevel * 2 + (4 - ((gridDepthForLevel(lvl) - 2) * 4))
        } else {
            return 1
        }
    }

    /**
     * How much time in seconds should we have to solve the puzzle given the arg level
     */
    fun secondsForLevel(lvl: Int): Int {
        var activeBlocks = (gridDepthForLevel(lvl) - 1) * gridBreadthForLevel(lvl)
        var multiplier = when (chapterForLevel(lvl)) {
            Chapter.FOUR -> 5
            Chapter.TWO -> 2
            else -> 1
        }
        activeBlocks *= multiplier
        return ((activeBlocks / secondsEvaluator.evaluate(secondsInterpolator.getInterpolation(Math.min(CHAPTER_THREE_LEVEL_THRESH, lvl) / CHAPTER_THREE_LEVEL_THRESH.toFloat()), MIN_SECONDS_DIVISOR, MAX_SECONDS_DIVISOR)).toInt())
    }
}
package com.hotpodata.blockelganger.helpers

/**
 * Created by jdrotos on 1/21/16.
 */
object GameHelper {

    val CHAPTER_STEP = 5

    enum class Chapter {
        ONE,
        TWO,
        THREE
    }

    fun chapterForLevel(lvl: Int): Chapter {
        if (lvl >= CHAPTER_STEP * 2) {
            return Chapter.THREE
        } else if (lvl >= CHAPTER_STEP) {
            return Chapter.TWO
        } else {
            return Chapter.ONE
        }
    }

    fun gangerHeightForLevel(lvl: Int): Int {
        if (chapterForLevel(lvl) == Chapter.TWO) {
            return gridHeightForLevel(lvl) * 2 - 1
        } else {
            return gridHeightForLevel(lvl)
        }
    }

    fun gangerWidthForLevel(lvl: Int): Int {
        return gridWidthForLevel(lvl)
    }

    /**
     * How tall should our grids be given the arg level
     */
    fun gridHeightForLevel(lvl: Int): Int {
        return (Math.max(1, lvl % CHAPTER_STEP) + 1) / 2 + 1
    }

    /**
     * How wide should our grids be given the arg level
     */
    fun gridWidthForLevel(lvl: Int): Int {
        return 4 + (Math.max(1, lvl % CHAPTER_STEP) - 1) * 2
    }

    /**
     * How much time in seconds should we have to solve the puzzle given the arg level
     */
    fun secondForLevel(lvl: Int): Int {
        return ((gridHeightForLevel(lvl) - 1 + gridWidthForLevel(lvl) / 2f).toInt() * if (chapterForLevel(lvl) == Chapter.ONE) 1f else 1.5f).toInt()
    }
}
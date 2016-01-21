package com.hotpodata.blockelganger.helpers

/**
 * Created by jdrotos on 1/21/16.
 */
object GameHelper {

    enum class Chapter {
        ONE,
        TWO
    }

    fun chapterForLevel(lvl:Int) : Chapter{
        if(lvl > 5){
            return Chapter.TWO
        }else{
            return Chapter.ONE
        }
    }

    fun gangerHeightForLevel(lvl: Int): Int {
        if(lvl == 0){
            return 1
        }
        return (lvl + 1) / 2 + 1
    }

    fun gangerWidthForLevel(lvl: Int): Int {
        if(lvl == 0){
            return 1
        }
        return 4 + (lvl - 1) * 2
    }

    /**
     * How tall should our grids be given the arg level
     */
    fun gridHeightForLevel(lvl: Int): Int {
        if(lvl == 0){
            return 1
        }
        return (lvl + 1) / 2 + 1
    }

    /**
     * How wide should our grids be given the arg level
     */
    fun gridWidthForLevel(lvl: Int): Int {
        if(lvl == 0){
            return 1
        }
        return 4 + (lvl - 1) * 2
    }

    /**
     * How much time in seconds should we have to solve the puzzle given the arg level
     */
    fun secondForLevel(lvl: Int): Int {
        return lvl * 2 + 1
    }
}
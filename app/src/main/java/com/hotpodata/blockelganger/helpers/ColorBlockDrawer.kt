package com.hotpodata.blockelganger.helpers

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.hotpodata.blocklib.view.GridBinderView

/**
 * Created by jdrotos on 1/9/16.
 */
class ColorBlockDrawer(color: Int) : GridBinderView.IBlockDrawer {
    var paint = Paint()

    init {
        paint.color = color
    }

    override fun drawBlock(canvas: Canvas, blockCoords: RectF, data: Any) {
        canvas.drawRect(blockCoords, paint)
    }
}
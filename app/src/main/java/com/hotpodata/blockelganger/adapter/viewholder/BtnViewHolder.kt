package com.hotpodata.blockelganger.adapter.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.Button
import com.hotpodata.blockelganger.R

/**
 * Created by jdrotos on 1/30/16.
 */
class BtnViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val btn: Button
        get() {
            return itemView.findViewById(R.id.btn) as Button
        }
}
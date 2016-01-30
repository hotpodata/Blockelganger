package com.hotpodata.blockelganger.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.hotpodata.blockelganger.BuildConfig
import com.hotpodata.blockelganger.R
import com.hotpodata.blockelganger.activity.SinglePlayerActivity
import com.hotpodata.blockelganger.adapter.viewholder.BtnViewHolder
import com.hotpodata.blockelganger.adapter.viewholder.SpacerViewHolder
import com.hotpodata.blockelganger.interfaces.IGooglePlayGameServicesProvider
import com.hotpodata.common.data.App
import com.hotpodata.common.enums.HotPoDataApps
import java.util.*

/**
 * Created by jdrotos on 1/30/16.
 */
class StartupAdapter(val ctx: Context) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    val ROWTYPE_SPACER = 0
    val ROWTYPE_BTN = 1

    class DataSpace {}
    abstract class DataBtn(val label: String) {
        abstract fun onClick()
    }


    var rows = ArrayList<Any>()
    var spacerHeight: Int = 256
        set(h: Int) {
            field = h
            rebuildRowSet()
        }
    var googleServicesProvider: IGooglePlayGameServicesProvider? = null
        set(prov: IGooglePlayGameServicesProvider?) {
            field = prov
            rebuildRowSet()
        }


    override fun onBindViewHolder(holder: RecyclerView.ViewHolder?, position: Int) {
        var data = rows.get(position)
        if (holder is BtnViewHolder && data is DataBtn) {
            holder.btn.setText(data.label)
            holder.btn.setOnClickListener {
                data.onClick()
            }
        }
        if (holder is SpacerViewHolder && data is DataSpace) {
            holder.spacer.layoutParams?.let {
                it.height = spacerHeight
                holder.spacer.layoutParams = it
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerView.ViewHolder? {
        var inflater = LayoutInflater.from(ctx)
        return when (viewType) {
            ROWTYPE_BTN -> BtnViewHolder(inflater.inflate(R.layout.row_btn, parent, false))
            ROWTYPE_SPACER -> SpacerViewHolder(inflater.inflate(R.layout.row_space, parent, false))
            else -> null
        }
    }

    override fun getItemViewType(position: Int): Int {
        var data = rows.get(position)
        if (data is DataSpace) {
            return ROWTYPE_SPACER
        }
        if (data is DataBtn) {
            return ROWTYPE_BTN
        }
        return super.getItemViewType(position)
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    fun rebuildRowSet() {
        var newRows = ArrayList<Any>()
        newRows.add(DataSpace())
        //Single player
        newRows.add(object : DataBtn(ctx.getString(R.string.single_player)) {
            override fun onClick() {
                ctx.startActivity(SinglePlayerActivity.IntentGenerator.generateIntent(ctx))
            }
        })

        //Multiplayer
        newRows.add(object : DataBtn(ctx.getString(R.string.quick_battle)) {
            override fun onClick() {

            }
        })
        newRows.add(object : DataBtn(ctx.getString(R.string.invite_players)) {
            override fun onClick() {

            }
        })
        newRows.add(object : DataBtn(ctx.getString(R.string.show_invitations)) {
            override fun onClick() {

            }
        })

        //Promo
        if (!BuildConfig.IS_PRO) {
            newRows.add(object : DataBtn(ctx.getString(R.string.go_pro)) {
                override fun onClick() {
                    var app = App.Factory.createApp(ctx, HotPoDataApps.BLOCKELGANGER)
                    app.firePlayStoreIntent(ctx, true)
                }
            })
        }

        rows = newRows
        notifyDataSetChanged()
    }
}
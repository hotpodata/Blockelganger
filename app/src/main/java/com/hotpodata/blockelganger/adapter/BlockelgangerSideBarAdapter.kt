package com.hotpodata.blockelganger.adapter

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.hotpodata.blockelganger.AnalyticsMaster
import com.hotpodata.blockelganger.BuildConfig
import com.hotpodata.blockelganger.R
import com.hotpodata.blockelganger.adapter.viewholder.SignInViewHolder
import com.hotpodata.blockelganger.interfaces.IGooglePlayGameServicesProvider
import com.hotpodata.common.adapter.SideBarAdapter
import com.hotpodata.common.data.App
import com.hotpodata.common.enums.HotPoDataApps
import com.hotpodata.common.enums.Libraries
import java.util.*

/**
 * Created by jdrotos on 11/7/15.
 */
class BlockelgangerSideBarAdapter(ctx: Context, val playGameServicesProvider: IGooglePlayGameServicesProvider) : SideBarAdapter(ctx, AnalyticsMaster, App.Factory.createApp(ctx, HotPoDataApps.BLOCKELGANGER), BuildConfig.IS_PRO, true, Libraries.AutoFitTextView, Libraries.RxAndroid, Libraries.RxJava, Libraries.RxKotlin, Libraries.Timber) {

    private val ROW_TYPE_SIGN_IN = 100

    init {
        rebuildRowSet()
    }

    override fun genCustomRows(): List<Any> {
        var sideBarRows = ArrayList<Any>()
        sideBarRows.add(ctx.resources.getString(R.string.game))
        if (playGameServicesProvider.isLoggedIn()) {
            sideBarRows.add(RowSettings(ctx.resources.getString(R.string.leader_board), "", View.OnClickListener {
                playGameServicesProvider.showLeaderBoard()
            }, R.drawable.ic_trophy_black_48dp))
            sideBarRows.add(RowDiv(true))
            sideBarRows.add(RowSettings(ctx.resources.getString(R.string.achievements), "", View.OnClickListener {
                playGameServicesProvider.showAchievements()
            }, R.drawable.ic_grade_24dp))
            sideBarRows.add(RowDiv(true))
            sideBarRows.add(RowSettings(ctx.resources.getString(R.string.sign_out), "", View.OnClickListener {
                playGameServicesProvider.logout()
            }, R.drawable.ic_highlight_remove_24dp))
        } else {
            sideBarRows.add(RowSignIn(View.OnClickListener {
                playGameServicesProvider.login()
            }))
        }
        return sideBarRows
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        return when (viewType) {
            ROW_TYPE_SIGN_IN -> {
                val inflater = LayoutInflater.from(parent.context)
                val v = inflater.inflate(R.layout.row_signin, parent, false)
                SignInViewHolder(v)
            }
            else -> super.onCreateViewHolder(parent, viewType)
        }
    }

    @Suppress("DEPRECATION")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val type = getItemViewType(position)
        val objData = mRows[position]
        when (type) {
            ROW_TYPE_SIGN_IN -> {
                val vh = holder as SignInViewHolder
                val data = objData as RowSignIn
                vh.signInBtn.setOnClickListener(data.onClickListener)
            }
            else -> super.onBindViewHolder(holder, position)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val data = mRows[position]
        return when (data) {
            is RowSignIn -> ROW_TYPE_SIGN_IN
            else -> super.getItemViewType(position)
        }
    }

    class RowSignIn(val onClickListener: View.OnClickListener)
}
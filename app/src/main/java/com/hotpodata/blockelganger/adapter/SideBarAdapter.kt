package com.hotpodata.blockelganger.adapter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.analytics.HitBuilders
import com.hotpodata.blockelganger.AnalyticsMaster
import com.hotpodata.blockelganger.R
import com.hotpodata.blockelganger.adapter.viewholder.*
import com.hotpodata.blockelganger.interfaces.IGooglePlayGameServicesProvider
import timber.log.Timber
import java.util.*

/**
 * Created by jdrotos on 11/7/15.
 */
class SideBarAdapter(ctx: Context, val playGameServicesProvider: IGooglePlayGameServicesProvider) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val ROW_TYPE_HEADER = 0
    private val ROW_TYPE_ONE_LINE = 1
    private val ROW_TYPE_TWO_LINE = 2
    private val ROW_TYPE_DIV = 3
    private val ROW_TYPE_DIV_INSET = 4
    private val ROW_TYPE_SIDE_BAR_HEADING = 5
    private val ROW_TYPE_SIGN_IN = 6

    private var mRows: List<Any>
    private var mColor: Int
    private var mContext: Context

    init {
        mColor = ContextCompat.getColor(ctx, R.color.colorPrimary)
        mContext = ctx
        mRows = buildRows()
    }

    public fun rebuildRowSet() {
        mRows = buildRows()
        notifyDataSetChanged()
    }

    public fun setAccentColor(color: Int) {
        mColor = color;
        if (itemCount > 0 && getItemViewType(0) == ROW_TYPE_HEADER) {
            notifyItemChanged(0)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder? {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            ROW_TYPE_HEADER -> {
                val v = inflater.inflate(R.layout.row_sidebar_section_header, parent, false)
                SideBarSectionHeaderViewHolder(v)
            }
            ROW_TYPE_ONE_LINE -> {
                val v = inflater.inflate(R.layout.row_text_one_line, parent, false)
                RowTextOneLineViewHolder(v)
            }
            ROW_TYPE_TWO_LINE -> {
                val v = inflater.inflate(R.layout.row_text_two_line, parent, false)
                RowTextTwoLineViewHolder(v)
            }
            ROW_TYPE_DIV, ROW_TYPE_DIV_INSET -> {
                val v = inflater.inflate(R.layout.row_div, parent, false)
                RowDivViewHolder(v)
            }
            ROW_TYPE_SIDE_BAR_HEADING -> {
                val v = inflater.inflate(R.layout.row_sidebar_header, parent, false)
                SideBarHeaderViewHolder(v)
            }
            ROW_TYPE_SIGN_IN -> {
                val v = inflater.inflate(R.layout.row_signin, parent, false)
                SignInViewHolder(v)
            }
            else -> null
        }
    }

    fun buildRows(): List<Any> {
        var sideBarRows = ArrayList<Any>()
        var version: String? = null
        try {
            val pInfo = mContext.packageManager.getPackageInfo(mContext.packageName, 0)
            version = mContext.resources.getString(R.string.version_template, pInfo.versionName)
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e, "Version fail")
        }
        sideBarRows.add(SideBarAdapter.SideBarHeading(mContext.resources.getString(R.string.app_label), version))

        sideBarRows.add(mContext.resources.getString(R.string.game))
        if (playGameServicesProvider.isLoggedIn()) {
            sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.leader_board), "", View.OnClickListener {
                playGameServicesProvider.showLeaderBoard()
            }, R.drawable.ic_trophy_black_48dp))
            sideBarRows.add(SideBarAdapter.Div(true))
            sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.achievements), "", View.OnClickListener {
                playGameServicesProvider.showAchievements()
            }, R.drawable.ic_grade_24dp))
            sideBarRows.add(SideBarAdapter.Div(true))
            sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.sign_out), "", View.OnClickListener {
                playGameServicesProvider.logout()
            }, R.drawable.ic_highlight_remove_24dp))
        } else {
            sideBarRows.add(RowSignIn(View.OnClickListener {
                playGameServicesProvider.login()
            }))
        }

        sideBarRows.add(SideBarAdapter.Div(false))
        sideBarRows.add(mContext.resources.getString(R.string.actions))
        //RATE US
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.rate_us), mContext.resources.getString(R.string.rate_us_blerb_template, mContext.resources.getString(R.string.app_name)), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_RATE_APP)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }


            val intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse("market://details?id=com.hotpodata.blockelganger"))
            mContext.startActivity(intent)
        }, R.drawable.ic_action_rate))

        //EMAIL
        sideBarRows.add(SideBarAdapter.Div(true))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.contact_the_developer), mContext.resources.getString(R.string.contact_email_addr_template, mContext.resources.getString(R.string.app_name)), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_CONTACT)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            val intent = Intent(Intent.ACTION_SEND)
            intent.setType("message/rfc822")
            intent.putExtra(Intent.EXTRA_EMAIL, arrayOf(mContext.resources.getString(R.string.contact_email_addr_template, mContext.resources.getString(R.string.app_name))))
            if (intent.resolveActivity(mContext.packageManager) != null) {
                mContext.startActivity(intent)
            }

        }, R.drawable.ic_action_mail))

        //TWITTER
        sideBarRows.add(SideBarAdapter.Div(true))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.follow_us_on_twitter), mContext.resources.getString(R.string.twitter_handle), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_TWITTER)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(mContext.resources.getString(R.string.twitter_url)))
            mContext.startActivity(intent)
        }, R.drawable.ic_action_twitter))

        //GITHUB
        sideBarRows.add(SideBarAdapter.Div(true))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.follow_on_github), mContext.resources.getString(R.string.github_url), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_GITHUB)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(mContext.resources.getString(R.string.github_url)))
            mContext.startActivity(intent)
        }, R.drawable.ic_action_github))

        //WEBSITE
        sideBarRows.add(SideBarAdapter.Div(true))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.visit_website), mContext.resources.getString(R.string.visit_website_blurb), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_WEBSITE)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setData(Uri.parse(mContext.resources.getString(R.string.website_url)))
            mContext.startActivity(intent)
        }, R.drawable.ic_action_web))


        sideBarRows.add(SideBarAdapter.Div(false))
        sideBarRows.add(mContext.resources.getString(R.string.apps))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.redchain), mContext.resources.getString(R.string.redchain_desc), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_REDCHAIN)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("market://details?id=com.hotpodata.redchain.free"))
                mContext.startActivity(intent)
            } catch(ex: Exception) {
                Timber.e(ex, "Failure to launch market intent")
            }
        }, R.mipmap.launcher_redchain))
        sideBarRows.add(SideBarAdapter.Div(true))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.filecat), mContext.resources.getString(R.string.filecat_desc), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_FILECAT)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("market://details?id=com.hotpodata.filecat.free"))
                mContext.startActivity(intent)
            } catch(ex: Exception) {
                Timber.e(ex, "Failure to launch market intent")
            }

        }, R.mipmap.launcher_filecat))
        sideBarRows.add(SideBarAdapter.Div(true))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.wikicat), mContext.resources.getString(R.string.wikicat_desc), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_WIKICAT)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("market://details?id=com.hotpodata.wikicat.free"))
                mContext.startActivity(intent)
            } catch(ex: Exception) {
                Timber.e(ex, "Failure to launch market intent")
            }
        }, R.mipmap.launcher_wikicat))

        sideBarRows.add(SideBarAdapter.Div(true))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.baconmasher), mContext.resources.getString(R.string.baconmasher_desc), View.OnClickListener {
            try {
                AnalyticsMaster.getTracker(mContext).send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsMaster.CATEGORY_ACTION)
                        .setAction(AnalyticsMaster.ACTION_BACONMASHER)
                        .build());
            } catch(ex: Exception) {
                Timber.e(ex, "Analytics Exception");
            }

            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setData(Uri.parse("market://details?id=com.hotpodata.baconmasher.free"))
                mContext.startActivity(intent)
            } catch(ex: Exception) {
                Timber.e(ex, "Failure to launch market intent")
            }
        }, R.mipmap.launcher_baconmasher))

        sideBarRows.add(SideBarAdapter.Div(false))
        sideBarRows.add(mContext.resources.getString(R.string.acknowledgements))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.autofittextview), mContext.resources.getString(R.string.autofittextview_license), View.OnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(mContext.resources.getString(R.string.autofittextview_url)))
            mContext.startActivity(i)
        }))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.rxandroid), mContext.resources.getString(R.string.rxandroid_license), View.OnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(mContext.resources.getString(R.string.rxandroid_url)))
            mContext.startActivity(i)
        }))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.rxjava), mContext.resources.getString(R.string.rxjava_license), View.OnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(mContext.resources.getString(R.string.rxjava_url)))
            mContext.startActivity(i)
        }))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.rxkotlin), mContext.resources.getString(R.string.rxkotlin_license), View.OnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(mContext.resources.getString(R.string.rxkotlin_url)))
            mContext.startActivity(i)
        }))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.timber), mContext.resources.getString(R.string.timber_license), View.OnClickListener {
            val i = Intent(Intent.ACTION_VIEW)
            i.setData(Uri.parse(mContext.resources.getString(R.string.timber_url)))
            mContext.startActivity(i)
        }))

        sideBarRows.add(SideBarAdapter.Div(false))
        sideBarRows.add(SideBarAdapter.SettingsRow(mContext.resources.getString(R.string.legal_heading), mContext.resources.getString(R.string.legal_blurb), View.OnClickListener { }))
        return sideBarRows
    }

    @Suppress("DEPRECATION")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val type = getItemViewType(position)
        val objData = mRows[position]
        when (type) {
            ROW_TYPE_SIDE_BAR_HEADING -> {
                val vh = holder as SideBarHeaderViewHolder
                val data = objData as SideBarHeading
                vh.mTitleTv.text = data.title
                vh.mSubTitleTv.text = data.subtitle
                vh.mContainer.setBackgroundColor(mColor)
            }
            ROW_TYPE_HEADER -> {
                val vh = holder as SideBarSectionHeaderViewHolder
                val data = objData as String
                vh.mTitleTv.text = data
            }
            ROW_TYPE_ONE_LINE -> {
                val vh = holder as RowTextOneLineViewHolder
                val data = objData as SettingsRow
                vh.mTextOne.text = data.title
                vh.itemView.setOnClickListener(data.onClickListener)
                if (data.iconResId != -1) {
                    vh.mIcon.setImageResource(data.iconResId)
                    vh.mIcon.visibility = View.VISIBLE
                } else {
                    vh.mIcon.setImageDrawable(null)
                    vh.mIcon.visibility = View.GONE
                }
            }
            ROW_TYPE_TWO_LINE -> {
                val vh = holder as RowTextTwoLineViewHolder
                val data = objData as SettingsRow
                vh.mTextOne.text = data.title
                vh.mTextTwo.text = data.subTitle
                vh.itemView.setOnClickListener(data.onClickListener)
                if (data.iconResId != -1) {
                    vh.mIcon.setImageResource(data.iconResId)
                    vh.mIcon.visibility = View.VISIBLE
                } else {
                    vh.mIcon.setImageDrawable(null)
                    vh.mIcon.visibility = View.GONE
                }
            }
            ROW_TYPE_DIV_INSET, ROW_TYPE_DIV -> {
                val vh = holder as RowDivViewHolder
                val data = objData as Div
                if (data.isInset) {
                    vh.mSpacer.visibility = View.VISIBLE
                } else {
                    vh.mSpacer.visibility = View.GONE
                }
            }
            ROW_TYPE_SIGN_IN -> {
                val vh = holder as SignInViewHolder
                val data = objData as RowSignIn
                vh.signInBtn.setOnClickListener(data.onClickListener)
            }
        }
    }

    override fun getItemCount(): Int {
        return mRows.size
    }

    override fun getItemViewType(position: Int): Int {
        val data = mRows[position]
        return when (data) {
            is String -> ROW_TYPE_HEADER
            is SettingsRow -> if (TextUtils.isEmpty(data.subTitle)) {
                ROW_TYPE_ONE_LINE
            } else {
                ROW_TYPE_TWO_LINE
            }
            is Div -> if (data.isInset) {
                ROW_TYPE_DIV_INSET
            } else {
                ROW_TYPE_DIV
            }
            is SideBarHeading -> ROW_TYPE_SIDE_BAR_HEADING
            is RowSignIn -> ROW_TYPE_SIGN_IN
            else -> super.getItemViewType(position)
        }
    }

    open class SettingsRow {
        var title: String? = null
            private set
        var subTitle: String? = null
            private set
        var onClickListener: View.OnClickListener? = null
            private set
        var iconResId = -1
            private set

        constructor(title: String, subTitle: String, onClickListener: View.OnClickListener) {
            this.title = title
            this.subTitle = subTitle
            this.onClickListener = onClickListener
        }

        constructor(title: String, subTitle: String, onClickListener: View.OnClickListener, iconResId: Int) {
            this.title = title
            this.subTitle = subTitle
            this.onClickListener = onClickListener
            this.iconResId = iconResId
        }
    }

    class Div(val isInset: Boolean)

    class SideBarHeading(val title: String, val subtitle: String?)

    class RowSignIn(val onClickListener: View.OnClickListener)
}
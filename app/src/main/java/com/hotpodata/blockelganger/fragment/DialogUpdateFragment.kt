package com.hotpodata.blockelganger.fragment

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.support.v7.app.AlertDialog
import com.hotpodata.blockelganger.R
import com.hotpodata.common.utils.AndroidUtils
import java.util.*

/**
 * Created by jdrotos on 12/28/15.
 */
class DialogUpdateFragment(val backToVersion: Int) : DialogFragment() {

    fun genUpdatesStrings(ctx: Context): Array<String> {
        var updateItems = ArrayList<String>()
        if (backToVersion < 3) {
            updateItems.addAll(ctx.resources.getStringArray(R.array.v3_updates))
        }
        updateItems.add(ctx.getString(R.string.bug_fixes_and_improvements))
        return updateItems.toTypedArray()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog? {
        var version = AndroidUtils.getVersionName(context)
        var builder = AlertDialog.Builder(context)
        builder.setTitle(context?.getString(R.string.whats_new_title_TEMPLATE, version))
        builder.setItems(genUpdatesStrings(context), null)
        builder.setCancelable(true)
        builder.setNeutralButton(R.string.got_it, null)
        return builder.create()
    }

}
package com.hotpodata.blockelganger.adapter.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import com.google.android.gms.common.SignInButton
import com.hotpodata.blockelganger.R


/**
 * Created by jdrotos on 1/21/15.
 */
class SignInViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    var signInBtn: SignInButton

    init {
        signInBtn = itemView.findViewById(R.id.row_sign_in_button) as SignInButton
    }
}

package com.hotpodata.blockelganger.fragment

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import com.hotpodata.blockelganger.R
import kotlinx.android.synthetic.main.fragment_dialog_how_to_play.*
import rx.Observable
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Created by jdrotos on 12/28/15.
 */
class DialogHowToPlayFragment : DialogFragment() {

    public interface IDialogHowToPlayListener {
        fun onHelpDialogDismissed()
    }

    var dialogLisetner: IDialogHowToPlayListener? = null
    var timerSub: Subscription? = null

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        dialogLisetner = context as? IDialogHowToPlayListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog? {
        val dialog = super.onCreateDialog(savedInstanceState)
        // request a window without the title
        dialog.window.requestFeature(Window.FEATURE_NO_TITLE)
        dialog.setCanceledOnTouchOutside(true)
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater?.inflate(R.layout.fragment_dialog_how_to_play, container, false)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btn_got_it.setOnClickListener {
            Timber.d("Calling dismiss()")
            dismiss()
        }

        step_one_video.setZOrderOnTop(true)
    }

    override fun onResume() {
        super.onResume()
        val uriPath = "android.resource://" + getActivity().getPackageName() + "/" + R.raw.blockelganger_how_to_play_step1
        val uri = Uri.parse(uriPath)

        step_one_video.setVideoURI(uri)
        step_one_video.setOnCompletionListener(MediaPlayer.OnCompletionListener {
            timerSub = Observable.timer(3, TimeUnit.SECONDS).observeOn(AndroidSchedulers.mainThread()).subscribe {
                if (isResumed && isAdded) {
                    step_one_video.start()
                }
            }
        })
        step_one_video.start()
    }

    override fun onPause() {
        super.onPause()
        timerSub?.unsubscribe()
        step_one_video.stopPlayback()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Timber.d("OnDismissListener firing!")
        dialogLisetner?.onHelpDialogDismissed()
    }
}
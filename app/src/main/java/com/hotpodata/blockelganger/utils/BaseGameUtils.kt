package com.hotpodata.blockelganger.utils


import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.IntentSender
import android.util.Log

import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GooglePlayServicesUtil
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.games.GamesActivityResultCodes
import com.hotpodata.blockelganger.R

object BaseGameUtils {

    /**
     * Show an [android.app.AlertDialog] with an 'OK' button and a message.

     * @param activity the Activity in which the Dialog should be displayed.
     * *
     * @param message  the message to display in the Dialog.
     */
    fun showAlert(activity: Activity, message: String) {
        AlertDialog.Builder(activity).setMessage(message).setNeutralButton(android.R.string.ok, null).create().show()
    }

    /**
     * Resolve a connection failure from
     * [com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener.onConnectionFailed]

     * @param activity             the Activity trying to resolve the connection failure.
     * *
     * @param client               the GoogleAPIClient instance of the Activity.
     * *
     * @param result               the ConnectionResult received by the Activity.
     * *
     * @param requestCode          a request code which the calling Activity can use to identify the result
     * *                             of this resolution in onActivityResult.
     * *
     * @param fallbackErrorMessage a generic error message to display if the failure cannot be resolved.
     * *
     * @return true if the connection failure is resolved, false otherwise.
     */
    fun resolveConnectionFailure(activity: Activity,
                                 client: GoogleApiClient?, result: ConnectionResult?, requestCode: Int,
                                 fallbackErrorMessage: String): Boolean {

        if (result?.hasResolution() ?: false) {
            try {
                result?.startResolutionForResult(activity, requestCode)
                return true
            } catch (e: IntentSender.SendIntentException) {
                // The intent was canceled before it was sent.  Return to the default
                // state and attempt to connect to get an updated ConnectionResult.
                client?.connect()
                return false
            }

        } else {
            // not resolvable... so show an error message
            val errorCode = result?.errorCode ?: 1
            val dialog = GooglePlayServicesUtil.getErrorDialog(errorCode,
                    activity, requestCode)
            if (dialog != null) {
                dialog.show()
            } else {
                // no built-in dialog: show the fallback error message
                showAlert(activity, fallbackErrorMessage)
            }
            return false
        }
    }

    /**
     * For use in sample code only. Checks if the sample was set up correctly,
     * including changing the package name to a non-Google package name and
     * replacing the placeholder IDs. Shows alert dialogs to notify about problems.
     * DO NOT call this method from a production app, it's meant only for samples!

     * @param resIds the resource IDs to check for placeholders
     * *
     * @return true if sample is set up correctly; false otherwise.
     */
    fun verifySampleSetup(activity: Activity, vararg resIds: Int): Boolean {
        val problems = StringBuilder()
        var problemFound = false
        problems.append("The following set up problems were found:\n\n")

        // Did the developer forget to change the package name?
        if (activity.packageName.startsWith("com.google.example.games")) {
            problemFound = true
            problems.append("- Package name cannot be com.google.*. You need to change the " + "sample's package name to your own package.").append("\n")
        }

        for (i in resIds) {
            if (activity.getString(i).toLowerCase().contains("replaceme")) {
                problemFound = true
                problems.append("- You must replace all " + "placeholder IDs in the ids.xml file by your project's IDs.").append("\n")
                break
            }
        }

        if (problemFound) {
            problems.append("\n\nThese problems may prevent the app from working properly.")
            showAlert(activity, problems.toString())
            return false
        }

        return true
    }

    /**
     * Show a [android.app.Dialog] with the correct message for a connection error.

     * @param activity         the Activity in which the Dialog should be displayed.
     * *
     * @param requestCode      the request code from onActivityResult.
     * *
     * @param actResp          the response code from onActivityResult.
     * *
     * @param errorDescription the resource id of a String for a generic error message.
     */
    fun showActivityResultError(activity: Activity?, requestCode: Int, actResp: Int, errorDescription: Int) {
        if (activity == null) {
            Log.e("BaseGameUtils", "*** No Activity. Can't show failure dialog!")
            return
        }
        var errorDialog: Dialog?

        when (actResp) {
            GamesActivityResultCodes.RESULT_APP_MISCONFIGURED -> errorDialog = makeSimpleDialog(activity,
                    activity.getString(R.string.app_misconfigured))
            GamesActivityResultCodes.RESULT_SIGN_IN_FAILED -> errorDialog = makeSimpleDialog(activity,
                    activity.getString(R.string.sign_in_failed))
            GamesActivityResultCodes.RESULT_LICENSE_FAILED -> errorDialog = makeSimpleDialog(activity,
                    activity.getString(R.string.license_failed))
            else -> {
                // No meaningful Activity response code, so generate default Google
                // Play services dialog
                val errorCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity)
                errorDialog = GooglePlayServicesUtil.getErrorDialog(errorCode,
                        activity, requestCode, null)
                if (errorDialog == null) {
                    // get fallback dialog
                    Log.e("BaseGamesUtils",
                            "No standard error dialog available. Making fallback dialog.")
                    errorDialog = makeSimpleDialog(activity, activity.getString(errorDescription))
                }
            }
        }

        errorDialog.show()
    }

    /**
     * Create a simple [Dialog] with an 'OK' button and a message.

     * @param activity the Activity in which the Dialog should be displayed.
     * *
     * @param text     the message to display on the Dialog.
     * *
     * @return an instance of [android.app.AlertDialog]
     */
    fun makeSimpleDialog(activity: Activity, text: String): Dialog {
        return AlertDialog.Builder(activity).setMessage(text).setNeutralButton(android.R.string.ok, null).create()
    }

    /**
     * Create a simple [Dialog] with an 'OK' button, a title, and a message.

     * @param activity the Activity in which the Dialog should be displayed.
     * *
     * @param title    the title to display on the dialog.
     * *
     * @param text     the message to display on the Dialog.
     * *
     * @return an instance of [android.app.AlertDialog]
     */
    fun makeSimpleDialog(activity: Activity, title: String, text: String): Dialog {
        return AlertDialog.Builder(activity).setTitle(title).setMessage(text).setNeutralButton(android.R.string.ok, null).create()
    }

}
package com.hereliesaz.click

import android.app.Activity
import com.hereliesaz.aznavrail.updates.AzUpdateChecker
import com.hereliesaz.aznavrail.updates.play.AzPlayUpdates

/** Checks for a release from the app's installed distribution. */
internal fun checkAppUpdate(activity: Activity) =
    AzUpdateChecker.check(activity, "HereLiesAz/Click", playSource = AzPlayUpdates)

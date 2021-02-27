/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.utils

import android.app.ActionBar
import android.app.Activity
import android.view.View
import android.view.WindowInsets
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment

/** Returns `true` if fullscreen or immersive mode is not set. */
private fun Activity.isSystemUiVisible(): Boolean {
    return this.window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0
}

/** Enable fullscreen or immersive mode. */
fun Activity.hideSystemUi() {
    this.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
}

/** Disable fullscreen or immersive mode. */
fun Activity.showSystemUi() {
    this.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
}

/** Toggle fullscreen or immersive mode. */
fun Activity.toggleSystemUi() {
    if (this.isSystemUiVisible()) {
        this.hideSystemUi()
    } else {
        this.showSystemUi()
    }
}

fun Fragment.setSystemUiVisibility(visible: Boolean) {
    if (visible)
        requireActivity().showSystemUi()
    else
        requireActivity().hideSystemUi()

    // Seems to be required to adjust padding when moving from the outlines to the screen reader
    requireView().requestApplyInsets()
}

fun View.padSystemUi(insets: WindowInsets, activity: Activity) =
    setPadding(
        insets.systemWindowInsetLeft,
        insets.systemWindowInsetTop + (activity as AppCompatActivity).supportActionBar!!.height,
        insets.systemWindowInsetRight,
        insets.systemWindowInsetBottom
    )

fun View.clearPadding() =
    setPadding(0, 0, 0,0)

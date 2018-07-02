/**
Copyright 2018 Readium Foundation. All rights reserved.
Use of this source code is governed by a BSD-style license which is detailed in the LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.permissions

import android.support.design.widget.Snackbar
import android.view.View
import android.widget.Button
import android.widget.TextView
import org.readium.r2.testapp.R
import org.readium.r2.testapp.color


object PermissionSnackbar {

  /**
   * @param root the root for the snackbar
   * @param text the text to be displayed
   * @param duration the duration of the snackbar
   * @param action the text that should be set as action
   * @param listener the listener that should be invoked when an action was made
   */
  fun make(
          root: View,
          text: String,
          duration: Duration = Duration.INDEFINITE_NO_DISMISS,
          action: String? = null,
          listener: (() -> Unit)? = null) {
    val bar = Snackbar.make(root, text, duration.internalDuration)
    bar.addCallback(
        object : Snackbar.Callback() {
          override fun onDismissed(snackbar: Snackbar?, event: Int) {
            if (event == Snackbar.Callback.DISMISS_EVENT_SWIPE && duration == Duration.INDEFINITE_NO_DISMISS) {
              // show again to enforce a decision
              make(root, text, duration, action, listener)
            }
          }
        }
    )

    // set action if set
    if (action != null && listener != null) {
      bar.setAction(action) {
        listener()
      }
    }

    // theme background
    bar.view.setBackgroundColor(root.context.color(R.color.snackbar_background_color))

    // theme text color
    val textView = bar.view.findViewById<TextView>(android.support.design.R.id.snackbar_text)
    textView.setTextColor(root.context.color(R.color.snackbar_text_color))

    // theme action color
    val actionView = bar.view.findViewById<Button>(android.support.design.R.id.snackbar_action)
    actionView.setTextColor(root.context.color(R.color.snackbar_text_color))

    bar.show()
  }

  enum class Duration(val internalDuration: Int) {
    INDEFINITE_NO_DISMISS(Snackbar.LENGTH_INDEFINITE)
  }
}

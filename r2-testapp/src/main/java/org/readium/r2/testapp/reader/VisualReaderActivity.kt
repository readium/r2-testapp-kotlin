/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import kotlinx.android.synthetic.main.activity_reader.*
import org.readium.r2.testapp.utils.clearPadding
import org.readium.r2.testapp.utils.padSystemUi
import org.readium.r2.testapp.utils.showSystemUi

open class VisualReaderActivity : ReaderActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without this, activity_reader_container receives the insets only once,
        // although we need a call every time the reader is hidden
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val newInsets = view.onApplyWindowInsets(insets)
            activity_container.dispatchApplyWindowInsets(newInsets)
        }

        activity_container.setOnApplyWindowInsetsListener { container, insets ->
            updateSystemUiPadding(container, insets)
            insets
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateSystemUiVisibility()
        }
    }

    override fun onStart() {
        super.onStart()
        updateSystemUiVisibility()
    }

    private fun updateSystemUiVisibility() {
        if (readerFragment.isHidden)
            showSystemUi()
        else
            readerFragment.updateSystemUiVisibility()

        // Seems to be required to adjust padding when transitioning from the outlines to the screen reader
        activity_container.requestApplyInsets()
    }

    private fun updateSystemUiPadding(container: View, insets: WindowInsets) {
        if (readerFragment.isHidden)
            container.padSystemUi(insets, this)
        else
            container.clearPadding()
    }
}
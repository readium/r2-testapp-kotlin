/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import kotlinx.android.synthetic.main.fragment_reader.*
import org.readium.r2.navigator.Navigator
import org.readium.r2.testapp.utils.clearPadding
import org.readium.r2.testapp.utils.padSystemUi
import org.readium.r2.testapp.utils.setSystemUiVisibility

abstract class VisualReaderFragment : BaseReaderFragment() {

    private lateinit var navigatorFragment: Fragment

    private val systemUiCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentResumed(fm: FragmentManager, f: Fragment) =
            setSystemUiVisibility(f !is Navigator)

        override fun onFragmentPaused(fm: FragmentManager, f: Fragment) =
            setSystemUiVisibility(f is Navigator)
    }

    private val windowInsetsListener = View.OnApplyWindowInsetsListener { container, insets ->
        if (navigatorFragment.isHidden) {
            container.padSystemUi(insets, requireActivity())
        } else {
            container.clearPadding()
        }
        insets
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemUiVisibility(isHidden)
        if (!isHidden) {
            fragment_reader_container.setOnApplyWindowInsetsListener(windowInsetsListener)
            childFragmentManager.registerFragmentLifecycleCallbacks(systemUiCallbacks, false)
        }

        navigatorFragment = navigator as Fragment
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setSystemUiVisibility(hidden || navigatorFragment.isHidden)
        if(hidden) {
            fragment_reader_container.setOnApplyWindowInsetsListener(null)
            childFragmentManager.unregisterFragmentLifecycleCallbacks(systemUiCallbacks)
        } else {
            fragment_reader_container.setOnApplyWindowInsetsListener(windowInsetsListener)
            childFragmentManager.registerFragmentLifecycleCallbacks(systemUiCallbacks, false)
        }
    }
}
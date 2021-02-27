/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.os.Bundle
import android.view.View
import org.readium.r2.testapp.utils.extensions.hideSystemUi
import org.readium.r2.testapp.utils.extensions.showSystemUi

abstract class VisualReaderFragment : BaseReaderFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSystemUiVisibility(isHidden)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setSystemUiVisibility(hidden)
    }

    private fun setSystemUiVisibility(hidden: Boolean) {
        if (hidden)
            requireActivity().showSystemUi()
        else
            requireActivity().hideSystemUi()
    }
}
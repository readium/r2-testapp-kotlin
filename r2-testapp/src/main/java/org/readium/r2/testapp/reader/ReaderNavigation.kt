/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.outline.OutlineFragment

interface ReaderNavigation {

    fun showOutlineFragment()

    fun closeOutlineFragment(locator: Locator)

    fun showDrmManagementFragment()
}
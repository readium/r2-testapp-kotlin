package org.readium.r2.testapp.reader

import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.outline.OutlineFragment

interface ReaderNavigation {

    fun showOutlineFragment()

    fun closeOutlineFragment(locator: Locator)
}
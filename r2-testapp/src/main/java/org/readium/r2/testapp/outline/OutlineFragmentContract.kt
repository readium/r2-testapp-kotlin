package org.readium.r2.testapp.outline

import android.os.Bundle
import org.readium.r2.shared.publication.Locator

object OutlineFragmentContract {

    val REQUEST_KEY: String = OutlineFragmentContract::class.java.name

    fun createBundle(locator: Locator): Bundle =
        Bundle().apply { putParcelable("result", locator) }

    fun parseResult(result: Bundle): Locator =
        result.getParcelable("result")!!
}
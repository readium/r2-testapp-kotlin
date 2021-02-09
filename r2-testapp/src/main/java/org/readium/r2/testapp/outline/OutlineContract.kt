package org.readium.r2.testapp.outline

import android.os.Bundle
import org.readium.r2.shared.publication.Locator

object OutlineContract {

    val REQUEST_KEY: String = OutlineContract::class.java.name

    fun createResult(locator: Locator): Bundle =
        Bundle().apply { putParcelable("result", locator) }

    fun parseResult(result: Bundle): Locator =
        requireNotNull(result.getParcelable("result"))
}
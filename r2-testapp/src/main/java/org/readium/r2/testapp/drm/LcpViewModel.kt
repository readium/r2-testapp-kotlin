/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.drm

import androidx.fragment.app.Fragment
import org.readium.r2.lcp.LcpLicense
import org.readium.r2.lcp.MaterialRenewListener
import org.readium.r2.shared.util.Try
import java.util.*

class LcpViewModel(private val lcpLicense: LcpLicense, fragment: Fragment) : DrmViewModel() {

    override val type: String = "LCP"

    override val state: String? = lcpLicense.status?.status?.rawValue

    override val provider: String? = lcpLicense.license.provider

    override val issued: Date? = lcpLicense.license.issued

    override val updated: Date? = lcpLicense.license.updated

    override val start: Date? = lcpLicense.license.rights.start

    override val end: Date? = lcpLicense.license.rights.end

    override val copiesLeft: String =
        lcpLicense.charactersToCopyLeft
            ?.let { "$it characters" }
            ?: super.copiesLeft

    override val printsLeft: String =
        lcpLicense.pagesToPrintLeft
            ?.let { "$it pages" }
            ?: super.printsLeft

    override val canRenewLoan: Boolean = lcpLicense.canRenewLoan

    override suspend fun renewLoan(): Try<Date?, Exception> =
        lcpLicense.renewLoan(renewListener)

    private val renewListener = MaterialRenewListener(
        license = lcpLicense,
        caller = fragment,
        fragmentManager = fragment.childFragmentManager
    )

    override val canReturnPublication: Boolean
        get() = lcpLicense.canReturnPublication

    override suspend fun returnPublication(): Try<Unit, Exception> =
        lcpLicense.returnPublication()

}

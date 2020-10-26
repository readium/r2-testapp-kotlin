//TODO
/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi
 *
 * Copyright (c) 2019. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.joda.time.DateTime
import org.readium.r2.lcp.LcpLicense
import java.io.Serializable
import java.net.URL

class LCPViewModel(context: Context, val license: LcpLicense) : DRMViewModel(context), Serializable {

    override val type: String
        get() = "LCP"
    override val state: String?
        get() = license.status?.status?.rawValue
    override val provider: String?
        get() = license.license.provider
    override val issued: DateTime?
        get() = license.license.issued
    override val updated: DateTime?
        get() = license.license.updated
    override val start: DateTime?
        get() = license.license.rights.start
    override val end: DateTime?
        get() = license.license.rights.end
    override val copiesLeft: String
        get() {
            license.charactersToCopyLeft?.let {
                return "$it characters"
            }
            return super.copiesLeft
        }
    override val printsLeft: String
        get() {
            license.pagesToPrintLeft?.let {
                return "$it pages"
            }
            return super.printsLeft
        }
    override val canRenewLoan: Boolean
        get() = license.canRenewLoan ?: false

    // TODO do i need this?
//    private var renewCallbacks: Map<Int, () -> Unit> = mapOf()

    override fun renewLoan(end: DateTime?, completion: (Exception?) -> Unit) {
        license.renewLoan(end, { url: URL, dismissed: () -> Unit ->
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url.toString())
            context.startActivity(intent)
        }, completion)
    }

    override val canReturnPublication: Boolean
        get() = license.canReturnPublication

    override fun returnPublication(completion: (Exception?) -> Unit) {
        license.returnPublication(completion = completion)
    }
}

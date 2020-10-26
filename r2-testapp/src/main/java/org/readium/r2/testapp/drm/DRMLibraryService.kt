/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi
 *
 * Copyright (c) 2019. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.drm

import android.app.ProgressDialog
import android.net.Uri
import org.readium.r2.shared.drm.DRM


data class DRMFulfilledPublication(
        val localURL: String,
        val suggestedFilename: String)

interface DRMLibraryService {
    fun canFulfill(file: String) : Boolean
    fun fulfill(byteArray: ByteArray, completion: (Any?) -> Unit)
}

interface LCPLibraryActivityService {
    fun parseIntentLcpl(uriString: String, networkAvailable: Boolean)
    fun processLcpActivityResult(uri: Uri, progress: ProgressDialog, networkAvailable: Boolean)
}

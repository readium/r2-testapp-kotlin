/* Module: r2-testapp-kotlin
* Developers:
*
* Copyright (c) 2021. European Digital Reading Lab. All rights reserved.
* Licensed to the Readium Foundation under one or more contributor license agreements.
* Use of this source code is governed by a BSD-style license which is detailed in the
* LICENSE file present in the project repository where this source code is maintained.
*/

package org.readium.r2.testapp.utils.extensions

import android.content.Context
import android.net.Uri
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.testapp.utils.ContentResolverUtil
import java.io.File
import java.util.*

suspend fun Uri.copyToTempFile(context: Context, dir: String): File? = tryOrNull {
    val filename = UUID.randomUUID().toString()
    val mediaType = MediaType.ofUri(this, context.contentResolver)
    val path = "$dir$filename.${mediaType?.fileExtension ?: "tmp"}"
    ContentResolverUtil.getContentInputStream(context, this, path)
    return File(path)
}
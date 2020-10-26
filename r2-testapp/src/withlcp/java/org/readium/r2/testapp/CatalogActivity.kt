/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. Readium Foundation. All rights reserved.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp

import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ListPopupWindow
import android.widget.PopupWindow
import android.widget.TextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mcxiaoke.koi.ext.fileExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat
import org.jetbrains.anko.design.longSnackbar
import org.readium.r2.lcp.*
import org.readium.r2.shared.drm.DRM
import org.readium.r2.shared.publication.ContentProtection
import org.readium.r2.shared.publication.Publication
import org.readium.r2.streamer.parser.PubBox
import org.readium.r2.testapp.BuildConfig.DEBUG
import org.readium.r2.testapp.db.Book
import org.readium.r2.testapp.drm.DRMFulfilledPublication
import org.readium.r2.testapp.drm.DRMLibraryService
import org.readium.r2.testapp.drm.LCPLibraryActivityService
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.utils.extensions.parse
import org.readium.r2.testapp.utils.toFile
import timber.log.Timber
import java.io.File
import java.net.URL
import java.util.*
import kotlin.coroutines.CoroutineContext

class CatalogActivity : LibraryActivity(), LCPLibraryActivityService, CoroutineScope, DRMLibraryService {


    /**
     * Context of this scope.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private var currenProgressDialog: ProgressDialog? = null

    private var lcpService: LcpService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        lcpService = LcpService(this)?.also {
            contentProtections.add(it.contentProtection())
        }

        super.onCreate(savedInstanceState)
        listener = this
    }

    private var authenticationCallbacks: MutableMap<String, (String?) -> Unit> = mutableMapOf()

    override fun canFulfill(file: String): Boolean =
            file.fileExtension().toLowerCase() == "lcpl"

    override fun fulfill(byteArray: ByteArray, completion: (Any?) -> Unit) {
        val lcpService = lcpService ?: run {
            completion(null)
            return
        }

        launch {
            try {
                val result = lcpService.acquirePublication(byteArray)
                    .map { DRMFulfilledPublication(localURL = it.localFile.path, suggestedFilename = it.suggestedFilename) }
                    .getOrThrow()
                completion(result)
            } catch (e: Exception) {
                completion(e)
            }
        }
    }

    override fun parseIntentLcpl(uriString: String, networkAvailable: Boolean) {
        val uri: Uri? = Uri.parse(uriString)
        uri?.let {
            val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_downloading_book))
            progress.show()

            currenProgressDialog = progress
            Thread {
                val bytes = try {
                    URL(uri.toString()).openStream().readBytes()
                } catch (e: Exception) {
                    contentResolver.openInputStream(uri)?.readBytes()
                }

                bytes?.let { it1 ->
                    fulfill(it1) { result ->
                        if (result is Exception) {

                            progress.dismiss()
                            catalogView.longSnackbar("${(result as LCPError).errorDescription}")

                        } else {
                            result?.let {
                                val publication = result as DRMFulfilledPublication

                                if (DEBUG) Timber.d(publication.localURL)
                                if (DEBUG) Timber.d(publication.suggestedFilename)
                                val file = File(publication.localURL)
                                preparePublication(publication.localURL, file.name, progress)
                            } ?: run {
                                progress.dismiss()
                            }
                        }
                    }
                }
            }.start()
        }
    }


    override fun processLcpActivityResult(uri: Uri, progress: ProgressDialog, networkAvailable: Boolean) {

        currenProgressDialog = progress

        val bytes = contentResolver.openInputStream(uri)?.readBytes()
        bytes?.let {

            fulfill(bytes) { result ->

                if (result is Exception) {

                    progress.dismiss()
                    catalogView.longSnackbar("${(result as LCPError).errorDescription}")

                } else {
                    result?.let {
                        val publication = result as DRMFulfilledPublication

                        if (DEBUG) Timber.d(result.localURL)
                        if (DEBUG) Timber.d(result.suggestedFilename)

                        val file = File(result.localURL)
                        val filename = UUID.randomUUID().toString()
                        val publicationPath = "$R2DIRECTORY$filename.${file.extension}"
                        file.inputStream().toFile(publicationPath)
                        file.delete()

                        preparePublication(publicationPath, filename, progress)

                    } ?: run {
                        progress.dismiss()
                    }
                }
            }
        }
    }
}
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
import org.readium.r2.testapp.BuildConfig.DEBUG
import org.readium.r2.shared.Injectable
import org.readium.r2.shared.drm.DRM
import org.readium.r2.shared.publication.Publication
import org.readium.r2.streamer.parser.PubBox
import org.readium.r2.streamer.parser.epub.EpubParser
import org.readium.r2.testapp.db.Book
import org.readium.r2.testapp.drm.DRMFulfilledPublication
import org.readium.r2.testapp.drm.DRMLibraryService
import org.readium.r2.testapp.drm.LCPLibraryActivityService
import org.readium.r2.testapp.epub.EpubActivity
import org.readium.r2.testapp.library.LibraryActivity
import timber.log.Timber
import tti.NavigatorExtension
import java.io.File
import java.net.URL
import kotlin.coroutines.CoroutineContext

open class CatalogActivity : LibraryActivity(), LCPLibraryActivityService, CoroutineScope, DRMLibraryService, LCPAuthenticating, LCPAuthenticationDelegate {


    /**
     * Context of this scope.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private lateinit var lcpService: LCPService

    private var currenProgressDialog: ProgressDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        lcpService = R2MakeLCPService(this)
        super.onCreate(savedInstanceState)
        listener = this
    }

    private var authenticationCallbacks: MutableMap<String, (String?) -> Unit> = mutableMapOf()

    override val brand: DRM.Brand
        get() = DRM.Brand.lcp

    override fun canFulfill(file: String): Boolean =
            file.fileExtension().toLowerCase() == "lcpl"

    override fun fulfill(byteArray: ByteArray, completion: (Any?) -> Unit) {
        lcpService.importPublication(byteArray, this) { result, error ->
            result?.let {
                val publication = DRMFulfilledPublication(localURL = result.localURL, suggestedFilename = result.suggestedFilename)
                lcpService.retrieveLicense(result.localURL, this) { license, error ->
                    completion(publication)
                }
            }
            error?.let {
                completion(error)
            }
            if (result == null && error == null) {
                completion(null)
            }
        }
    }

    override fun loadPublication(publication: String, drm: DRM, completion: (Any?) -> Unit) {
        lcpService.retrieveLicense(publication, this) { license, error ->
            license?.let {
                drm.license = license
                completion(drm)
            } ?: run {
                error?.let {
                    completion(error)
                }
            }
        }
    }

    override fun authenticate(license: LCPAuthenticatedLicense, passphrase: String) {
        val callback = authenticationCallbacks.remove(license.document.id) ?: return
        callback(passphrase)
    }

    override fun didCancelAuthentication(license: LCPAuthenticatedLicense) {
        val callback = authenticationCallbacks.remove(license.document.id) ?: return
        callback(null)
    }

    override fun requestPassphrase(license: LCPAuthenticatedLicense, reason: LCPAuthenticationReason, completion: (String?) -> Unit) {

        authenticationCallbacks[license.document.id] = completion

        fun promptPassphrase(reason: String? = null) {
            launch {

                currenProgressDialog?.let {
                    if (it.isShowing) {
                        it.dismiss()
                    }
                }

                // Initialize a new instance of LayoutInflater service
                val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

                // Inflate the custom layout/view
                val customView = inflater.inflate(R.layout.popup_passphrase, null)

                // Initialize a new instance of popup window
                val mPopupWindow = PopupWindow(
                        customView,
                        ListPopupWindow.MATCH_PARENT,
                        ListPopupWindow.MATCH_PARENT
                )
                mPopupWindow.isOutsideTouchable = false
                mPopupWindow.isFocusable = true

                // Set an elevation value for popup window
                // Call requires API level 21
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    mPopupWindow.elevation = 5.0f
                }

                val title = customView.findViewById(R.id.title) as TextView
                val description = customView.findViewById(R.id.description) as TextView
                val hint = customView.findViewById(R.id.hint) as TextView
                val passwordLayout = customView.findViewById(R.id.passwordLayout) as TextInputLayout
                val password = customView.findViewById(R.id.password) as TextInputEditText
                val confirmButton = customView.findViewById(R.id.confirm_button) as Button
                val cancelButton = customView.findViewById(R.id.cancel_button) as Button
                val forgotButton = customView.findViewById(R.id.forgot_link) as Button
                val helpButton = customView.findViewById(R.id.help_link) as Button

                if (license.supportLinks.isEmpty()) {
                    helpButton.visibility = View.GONE
                } else {
                    helpButton.visibility = View.VISIBLE
                }

                when (reason) {
                    "passphraseNotFound" -> title.text = "Passphrase Required"
                    "invalidPassphrase" -> {
                        title.text = "Incorrect Passphrase"
                        passwordLayout.error = "Incorrect Passphrase"
                    }
                }

                val provider = try {
                    val test = URL(license.provider)
                    URL(license.provider).host
                } catch (e: Exception) {
                    license.provider
                }

                description.text = "This publication is protected by Readium LCP.\n\nIn order to open it, we need to know the passphrase required by: \n\n$provider.\n\nTo help you remember it, the following hint is available:"
                hint.text = license.hint

                // Set a click listener for the popup window close button
                cancelButton.setOnClickListener {
                    // Dismiss the popup window
                    didCancelAuthentication(license)
                    mPopupWindow.dismiss()
                }

                confirmButton.setOnClickListener {
                    currenProgressDialog?.let {
                        if (!it.isShowing) {
                            it.show()
                        }
                    }

                    authenticate(license, password.text.toString())
                    mPopupWindow.dismiss()
                }

                forgotButton.setOnClickListener {
                    license.hintLink?.href?.let { href ->
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(href)
                        startActivity(intent)
                    }
                }

                helpButton.setOnClickListener {
                    alert(Appcompat) {
                        customView {
                            verticalLayout {
                                license.supportLinks.forEach { link ->
                                    button {
                                        link.title?.let {
                                            title.text = it
                                        } ?: run {
                                            title.text = try {
                                                when (URL(link.href).protocol) {
                                                    "http" -> "Website"
                                                    "https" -> "Website"
                                                    "tel" -> "Phone"
                                                    "mailto" -> "Mail"
                                                    else -> "Support"
                                                }
                                            } catch (e: Exception) {
                                                "Support"
                                            }
                                        }
                                        setOnClickListener {
                                            val intent = try {
                                                when (URL(link.href).protocol) {
                                                    "http" -> Intent(Intent.ACTION_VIEW)
                                                    "https" -> Intent(Intent.ACTION_VIEW)
                                                    "tel" -> Intent(Intent.ACTION_CALL)
                                                    "mailto" -> Intent(Intent.ACTION_SEND)
                                                    else -> Intent(Intent.ACTION_VIEW)
                                                }
                                            } catch (e: Exception) {
                                                Intent(Intent.ACTION_VIEW)
                                            }
                                            intent.data = Uri.parse(link.href)
                                            startActivity(intent)
                                        }
                                    }
                                }
                            }
                        }
                    }.build().apply {
                        // nothing
                    }.show()
                }

                // Finally, show the popup window at the center location of root relative layout
                mPopupWindow.showAtLocation(contentView, Gravity.CENTER, 0, 0)

            }
        }

        promptPassphrase(reason.name)

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
                                launch {
                                    val parser = EpubParser()
                                    val pub = parser.parse(publication.localURL)
                                    if (pub != null) {
                                        prepareToServe(pub, file.name, file.absolutePath, add = true, lcp = true)
                                        progress.dismiss()
                                        catalogView.longSnackbar("publication added to your library")
                                    }
                                }
                            } ?: run {
                                progress.dismiss()
                            }
                        }
                    }
                }
            }.start()
        }
    }

    override fun prepareAndStartActivityWithLCP(drm: DRM, pub: PubBox, book: Book, file: File, publicationPath: String, parser: EpubParser, publication: Publication, networkAvailable: Boolean) {
        loadPublication(file.absolutePath, drm) {
            launch {

                if (it is Exception) {

                    catalogView.longSnackbar("${(it as LCPError).errorDescription}")

                } else {

                    prepareToServe(pub, book.fileName!!, file.absolutePath, add = false, lcp = true)
                    server.addEpub(publication, pub.container, "/" + book.fileName, applicationContext.filesDir.path + "/" + Injectable.Style.rawValue + "/UserProperties.json")

                    val cls = NavigatorExtension.getEpubActivityClass() ?: EpubActivity::class.java
                    val intent = Intent(this@CatalogActivity,cls)
                    intent.putExtra("publicationPath",publicationPath)
                    intent.putExtra("publicationFileName",book.fileName)
                    intent.putExtra("publication",publication)
                    intent.putExtra("bookId", book.id)
                    intent.putExtra("drm", true)
                    this@CatalogActivity.startActivity(intent)
                    //this@CatalogActivity.startActivity(intentFor<EpubActivity>("publicationPath" to publicationPath, "publicationFileName" to book.fileName, "publication" to publication, "bookId" to book.id, "drm" to true))
                }
            }
        }
    }

    override fun processLcpActivityResult(uri: Uri, it: Uri, progress: ProgressDialog, networkAvailable: Boolean) {

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
                        launch {
                            val parser = EpubParser()
                            val pub = parser.parse(result.localURL)
                            if (pub != null) {
                                prepareToServe(pub, file.name, file.absolutePath, add = true, lcp = true)
                                progress.dismiss()
                                catalogView.longSnackbar("publication added to your library")
                            }
                        }
                    } ?: run {
                        progress.dismiss()
                    }
                }
            }
        }
    }
}
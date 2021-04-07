package org.readium.r2.testapp.bookshelf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.View
import android.widget.ProgressBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nl.komponents.kovenant.ui.successUi
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.Injectable
import org.readium.r2.shared.extensions.extension
import org.readium.r2.shared.extensions.mediaType
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.publication.asset.PublicationAsset
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.protectionError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.flatMap
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.server.Server
import org.readium.r2.testapp.BuildConfig
import org.readium.r2.testapp.R2App
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.domain.model.Book
import org.readium.r2.testapp.opds.OPDSDownloader
import org.readium.r2.testapp.utils.ContentResolverUtil
import org.readium.r2.testapp.utils.extensions.download
import org.readium.r2.testapp.utils.extensions.moveTo
import org.readium.r2.testapp.utils.extensions.toFile
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.*


// TODO Break this file up, maybe after the server is replaced
// TODO Consider using a WorkManager for a lot of this stuff
class BookService(val context: Context) {

    private val mPreferences =
        context.getSharedPreferences("org.readium.r2.settings", Context.MODE_PRIVATE)
    private var mStreamer: Streamer
    private var mBookRepository: BookRepository
    private var mServer: Server
    private var mLcpService: Try<LcpService, Exception>
    private var mOpdsDownloader: OPDSDownloader
    private var mR2Directory: String

    init {
        val booksDao = BookDatabase.getDatabase(context).booksDao()
        mBookRepository = BookRepository(booksDao)

        mOpdsDownloader = OPDSDownloader(context)

        mLcpService = LcpService(context)
            ?.let { Try.success(it) }
            ?: Try.failure(Exception("liblcp is missing on the classpath"))

        mStreamer = Streamer(
            context,
            contentProtections = listOfNotNull(
                mLcpService.getOrNull()?.contentProtection()
            )
        )

        mR2Directory = R2App.R2DIRECTORY

        mServer = R2App.server
        startServer()
    }

    private suspend fun addPublicationToDatabase(
        href: String,
        extension: String,
        publication: Publication
    ): Long {
        val id = mBookRepository.insertBook(href, extension, publication)
        storeCoverImage(publication, id.toString())
        return id
    }

    suspend fun copySamplesFromAssetsToStorage(onError: ((msg: String?) -> Unit?)) {
        withContext(Dispatchers.IO) {
            if (!mPreferences.contains("samples")) {
                val dir = File(mR2Directory)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val samples = context.assets.list("Samples")?.filterNotNull().orEmpty()
                for (element in samples) {
                    val file = context.assets.open("Samples/$element").copyToTempFile()
                    if (file != null)
                        importPublication(file, onError = onError)
                    else if (BuildConfig.DEBUG)
                        error("Unable to load sample into the library")
                }
                mPreferences.edit().putBoolean("samples", true).apply()
            }
        }
    }

    private suspend fun InputStream.copyToTempFile(): File? = tryOrNull {
        val filename = UUID.randomUUID().toString()
        File(mR2Directory + filename)
            .also { toFile(it.path) }
    }

    private suspend fun Uri.copyToTempFile(): File? = tryOrNull {
        val filename = UUID.randomUUID().toString()
        val mediaType = MediaType.ofUri(this, context.contentResolver)
        val path = "$mR2Directory$filename.${mediaType?.fileExtension ?: "tmp"}"
        ContentResolverUtil.getContentInputStream(context, this, path)
        return File(path)
    }

    private suspend fun URL.copyToTempFile(): File? = tryOrNull {
        val filename = UUID.randomUUID().toString()
        val path = "$mR2Directory$filename.$extension"
        download(path)
    }

    suspend fun importPublicationFromUri(
        uri: Uri,
        progressBar: ProgressBar? = null,
        onError: ((msg: String?) -> Unit?)? = null
    ) {
        progressBar?.visibility = View.VISIBLE
        uri.copyToTempFile()
            ?.let {
                importPublication(it, progress = progressBar, onError = onError)
            }
    }

    private suspend fun importPublication(
        sourceFile: File,
        sourceUrl: String? = null,
        progress: ProgressBar? = null,
        onError: ((msg: String?) -> Unit?)? = null
    ) {
        val foreground = progress != null
        val sourceMediaType = sourceFile.mediaType()
        val publicationAsset: FileAsset =
            if (sourceMediaType != MediaType.LCP_LICENSE_DOCUMENT)
                FileAsset(sourceFile, sourceMediaType)
            else {
                mLcpService
                    .flatMap { it.acquirePublication(sourceFile) }
                    .fold(
                        {
                            val mediaType =
                                MediaType.of(fileExtension = File(it.suggestedFilename).extension)
                            FileAsset(it.localFile, mediaType)
                        },
                        {
                            tryOrNull { sourceFile.delete() }
                            Timber.d(it)
                            progress?.visibility = View.GONE
                            if (foreground && onError != null) {
                                onError("fulfillment error: ${it.message}")
                            }
                            return
                        }
                    )
            }

        val mediaType = publicationAsset.mediaType()
        val fileName = "${UUID.randomUUID()}.${mediaType.fileExtension}"
        val libraryAsset = FileAsset(File(mR2Directory + fileName), mediaType)

        try {
            publicationAsset.file.moveTo(libraryAsset.file)
        } catch (e: Exception) {
            Timber.d(e)
            tryOrNull { publicationAsset.file.delete() }
            progress?.visibility = View.GONE
            if (foreground && onError != null) onError("unable to move publication into the library")
            return
        }

        val extension = libraryAsset.let {
            it.mediaType().fileExtension ?: it.file.extension
        }

        val isRwpm = libraryAsset.mediaType().isRwpm

        val bddHref =
            if (!isRwpm)
                libraryAsset.file.path
            else
                sourceUrl ?: run {
                    Timber.e("Trying to add a RWPM to the database from a file without sourceUrl.")
                    progress?.visibility = View.GONE
                    return
                }

        mStreamer.open(libraryAsset, allowUserInteraction = false, sender = context)
            .onSuccess {
                addPublicationToDatabase(bddHref, extension, it).let { id ->

                    progress?.visibility = View.GONE
                    val msg =
                        if (id != -1L)
                            "publication added to your library"
                        else
                            "unable to add publication to the database"
                    if (foreground && onError != null)
                        onError(msg)
                    else
                        Timber.d(msg)
                    if (id != -1L && isRwpm)
                        tryOrNull { libraryAsset.file.delete() }
                }
            }
            .onFailure {
                tryOrNull { libraryAsset.file.delete() }
                Timber.d(it)
                progress?.visibility = View.GONE
                if (foreground && onError != null) onError(it.getUserMessage(context))
            }
    }

    suspend fun openBook(
        book: Book,
        callback: (file: FileAsset, mediaType: MediaType?, publication: Publication, remoteAsset: FileAsset?, url: URL?) -> Unit,
        onError: (msg: String) -> Unit?
    ) {

        val remoteAsset: FileAsset? =
            tryOrNull { URL(book.href).copyToTempFile()?.let { FileAsset(it) } }
        val mediaType = MediaType.of(fileExtension = book.ext.removePrefix("."))
        val asset = remoteAsset // remote file
            ?: FileAsset(File(book.href), mediaType = mediaType) // local file

        mStreamer.open(asset, allowUserInteraction = true, sender = context)
            .onFailure {
                Timber.d(it)
                onError(it.getUserMessage(context))
            }
            .onSuccess {
                if (it.isRestricted) {
                    it.protectionError?.let { error ->
                        Timber.d(error)
                        onError(error.getUserMessage(context))
                    }
                } else {
                    val url = prepareToServe(it, asset)
                    callback.invoke(asset, mediaType, it, remoteAsset, url)
                }
            }
    }

    private fun prepareToServe(publication: Publication, asset: PublicationAsset): URL? {
        val userProperties =
            context.filesDir.path + "/" + Injectable.Style.rawValue + "/UserProperties.json"
        return mServer.addPublication(publication, userPropertiesFile = File(userProperties))
    }

    private fun startServer() {
        if (!mServer.isAlive) {
            try {
                mServer.start()
            } catch (e: IOException) {
                // do nothing
                if (BuildConfig.DEBUG) Timber.e(e)
            }
            if (mServer.isAlive) {
//                // Add your own resources here
//                server.loadCustomResource(assets.open("scripts/test.js"), "test.js")
//                server.loadCustomResource(assets.open("styles/test.css"), "test.css")
//                server.loadCustomFont(assets.open("fonts/test.otf"), applicationContext, "test.otf")

                mServer.loadCustomResource(
                    context.assets.open("Search/mark.js"),
                    "mark.js",
                    Injectable.Script
                )
                mServer.loadCustomResource(
                    context.assets.open("Search/search.js"),
                    "search.js",
                    Injectable.Script
                )
                mServer.loadCustomResource(
                    context.assets.open("Search/mark.css"),
                    "mark.css",
                    Injectable.Style
                )

                isServerStarted = true
            }
        }
    }

    fun stopServer() {
        if (mServer.isAlive) {
            mServer.stop()
            isServerStarted = false
        }
    }

    private suspend fun storeCoverImage(publication: Publication, imageName: String) {
        // TODO Figure out where to store these cover images
        val coverImageDir = File("${mR2Directory}covers/")
        if (!coverImageDir.exists()) {
            coverImageDir.mkdirs()
        }
        val coverImageFile = File("${mR2Directory}covers/${imageName}.png")

        var bitmap: Bitmap? = null
        if (publication.cover() == null) {
            publication.coverLink?.let { link ->
                bitmap = getBitmapFromURL(link.href)
            } ?: run {
                if (publication.images.isNotEmpty()) {
                    bitmap = getBitmapFromURL(publication.images.first().href)
                }
            }
        } else {
            bitmap = publication.cover()
        }

        val resized = bitmap?.let { Bitmap.createScaledBitmap(it, 120, 200, true) }
        GlobalScope.launch(Dispatchers.IO) {
            val fos = FileOutputStream(coverImageFile)
            resized?.compress(Bitmap.CompressFormat.PNG, 80, fos)
            fos.flush()
            fos.close()
        }
    }

    fun downloadPublication(publication: Publication, progressBar: ProgressBar) {
        progressBar.visibility = View.VISIBLE
        val downloadUrl = getDownloadURL(publication)
        mOpdsDownloader.publicationUrl(downloadUrl.toString()).successUi { pair ->
            GlobalScope.launch {
                withContext(Dispatchers.IO) {
                    addPublicationToDatabase(pair.first, "epub", publication)
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun getDownloadURL(publication: Publication): URL? {
        var url: URL? = null
        val links = publication.links
        for (link in links) {
            val href = link.href
            if (href.contains(Publication.EXTENSION.EPUB.value) || href.contains(Publication.EXTENSION.LCPL.value)) {
                url = URL(href)
                break
            }
        }
        return url
    }

    private fun getBitmapFromURL(src: String): Bitmap? {
        return try {
            val url = URL(src)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    companion object {

        var isServerStarted = false
            private set

    }
}
package org.readium.r2.testapp.bookshelf

import android.app.ProgressDialog
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.lcp.LcpService
import org.readium.r2.shared.Injectable
import org.readium.r2.shared.extensions.extension
import org.readium.r2.shared.extensions.mediaType
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.publication.asset.PublicationAsset
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.protectionError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.flatMap
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.server.Server
import org.readium.r2.testapp.BuildConfig
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.domain.model.Book
import org.readium.r2.testapp.utils.ContentResolverUtil
import org.readium.r2.testapp.utils.extensions.authorName
import org.readium.r2.testapp.utils.extensions.download
import org.readium.r2.testapp.utils.extensions.moveTo
import org.readium.r2.testapp.utils.extensions.toFile
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URL
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

var activitiesLaunched: AtomicInteger = AtomicInteger(0)

class BookService(val context: Context) {

    private val mPreferences = context.getSharedPreferences("org.readium.r2.settings", Context.MODE_PRIVATE)
    private var mStreamer: Streamer
    private var mBookRepository: BookRepository
    private var mServer: Server
    private var mLcpService: Try<LcpService, Exception>
    private var mLocalPort: Int = 0

    init {
        val booksDao = BookDatabase.getDatabase(context).booksDao()
        mBookRepository = BookRepository(booksDao)

        mLcpService = LcpService(context)
                ?.let { Try.success(it) }
                ?: Try.failure(Exception("liblcp is missing on the classpath"))

        mStreamer = Streamer(context,
                contentProtections = listOfNotNull(
                        mLcpService.getOrNull()?.contentProtection()
                )
        )

//        val s = ServerSocket(if (BuildConfig.DEBUG) 8080 else 0)
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(if (BuildConfig.DEBUG) 8080 else 0))
        s.close()

        mLocalPort = s.localPort
        mServer = Server(mLocalPort, context)
        startServer()
    }

    val R2DIRECTORY: String
        get() {
            val properties = Properties()
            val inputStream = context.assets.open("configs/config.properties")
            properties.load(inputStream)
            val useExternalFileDir = properties.getProperty("useExternalFileDir", "false")!!.toBoolean()
            return if (useExternalFileDir) {
                context.getExternalFilesDir(null)?.path + "/"
            } else {
                context.filesDir?.path + "/"
            }
        }

    private suspend fun addPublicationToDatabase(href: String, extension: String, publication: Publication): Long {

        val book = Book(
                title = publication.metadata.title,
                author = publication.metadata.authorName,
                href = href,
                identifier = publication.metadata.identifier ?: "",
                ext = ".$extension",
                progression = "{}"
        )

        return mBookRepository.insertBook(book)
    }

    suspend fun deleteBook(book: Book)  {
        // TODO delete bookmarks and highlights from db
        book.id?.let {
            mBookRepository.deleteBook(it)
            tryOrNull { File(book.href).delete() }
            tryOrNull { File("${R2DIRECTORY}covers/${book.id}.png").delete() }
        }
    }

    suspend fun copySamplesFromAssetsToStorage() {
        withContext(Dispatchers.IO) {
            if (!mPreferences.contains("samples")) {
                val dir = File(R2DIRECTORY)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val samples = context.assets.list("Samples")?.filterNotNull().orEmpty()
                for (element in samples) {
                    val file = context.assets.open("Samples/$element").copyToTempFile()
                    if (file != null)
                        importPublication(file)
                    else if (BuildConfig.DEBUG)
                        error("Unable to load sample into the library")
                }
                mPreferences.edit().putBoolean("samples", true).apply()
            }
        }
    }

    private suspend fun InputStream.copyToTempFile(): File? = tryOrNull {
        val filename = UUID.randomUUID().toString()
        File(R2DIRECTORY + filename)
                .also { toFile(it.path) }
    }

    private suspend fun Uri.copyToTempFile(): File? = tryOrNull {
        val filename = UUID.randomUUID().toString()
        val mediaType = MediaType.ofUri(this, context.contentResolver)
        val path = "$R2DIRECTORY$filename.${mediaType?.fileExtension ?: "tmp"}"
        ContentResolverUtil.getContentInputStream(context, this, path)
        return File(path)
    }

    private suspend fun URL.copyToTempFile(): File? = tryOrNull {
        val filename = UUID.randomUUID().toString()
        val path = "$R2DIRECTORY$filename.$extension"
        download(path)
    }

    suspend fun importPublicationFromUri(uri: Uri) {
        uri.copyToTempFile()
                ?.let {
                    importPublication(it)
                }
    }

    private suspend fun importPublication(sourceFile: File, sourceUrl: String? = null, progress: ProgressDialog? = null) {
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
                                        val mediaType = MediaType.of(fileExtension = File(it.suggestedFilename).extension)
                                        FileAsset(it.localFile, mediaType)
                                    },
                                    {
                                        tryOrNull { sourceFile.delete() }
                                        Timber.d(it)
                                        progress?.dismiss()
//                                        if (foreground) catalogView.longSnackbar("fulfillment error: ${it.message}")
                                        return
                                    }
                            )
                }

        val mediaType = publicationAsset.mediaType()
        val fileName = "${UUID.randomUUID()}.${mediaType.fileExtension}"
        val libraryAsset = FileAsset(File(R2DIRECTORY + fileName), mediaType)

        try {
            publicationAsset.file.moveTo(libraryAsset.file)
        } catch (e: Exception) {
            Timber.d(e)
            tryOrNull { publicationAsset.file.delete() }
            progress?.dismiss()
//            if (foreground) catalogView.longSnackbar("unable to move publication into the library")
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
                        progress?.dismiss()
                        return
                    }

        mStreamer.open(libraryAsset, allowUserInteraction = false, sender = context)
                .onSuccess {
                    addPublicationToDatabase(bddHref, extension, it).let { id ->

                        // Save the cover image of book using the same name of its ID in the database
                        // TODO Figure out where to store these cover images
                        val coverImageDir = File("${R2DIRECTORY}covers/")
                        if (!coverImageDir.exists()) {
                            coverImageDir.mkdirs()
                        }
                        val coverImageFile = File("${R2DIRECTORY}covers/${id}.png")
                        val fos = FileOutputStream(coverImageFile)
                        val resized = it.cover()?.let { it1 -> Bitmap.createScaledBitmap(it1, 120, 200, true) }
                        resized?.compress(Bitmap.CompressFormat.PNG, 80, fos)
                        fos.flush()
                        fos.close()

                        progress?.dismiss()
                        val msg =
                                if (id != -1L)
                                    "publication added to your library"
                                else
                                    "unable to add publication to the database"
                        if (foreground)
//                            catalogView.longSnackbar(msg)
                        else
                            Timber.d(msg)
                        if (id != -1L && isRwpm)
                            tryOrNull { libraryAsset.file.delete() }
                    }
                }
                .onFailure {
                    tryOrNull { libraryAsset.file.delete() }
                    Timber.d(it)
                    progress?.dismiss()
//                    if (foreground) presentOpeningException(it)
                }
    }

    suspend fun openBook(book: Book, callback: (file: FileAsset, mediaType: MediaType?, publication: Publication, remoteAsset: FileAsset?, url: URL?) -> Unit) {

        val remoteAsset: FileAsset? = tryOrNull { URL(book.href).copyToTempFile()?.let { FileAsset(it) } }
        val mediaType = MediaType.of(fileExtension = book.ext.removePrefix("."))
        val asset = remoteAsset // remote file
                ?: FileAsset(File(book.href), mediaType = mediaType) // local file

        mStreamer.open(asset, allowUserInteraction = true, sender = context)
                .onFailure {
                    Timber.d(it)
//                    progress.dismiss()
//                    presentOpeningException(it)
                }
                .onSuccess { it ->
                    if (it.isRestricted) {
//                        progress.dismiss()
                        it.protectionError?.let { error ->
                            Timber.d(error)
//                            catalogView.longSnackbar(error.getUserMessage(this@LibraryActivity))
                        }
                    } else {
                        val url = prepareToServe(it, asset)
//                        progress.dismiss()
                        callback.invoke(asset, mediaType, it, remoteAsset, url)
                    }
                }
    }

    private fun prepareToServe(publication: Publication, asset: PublicationAsset): URL? {
        val key = publication.metadata.identifier ?: publication.metadata.title
        mPreferences.edit().putString("$key-publicationPort", mLocalPort.toString()).apply()
        val userProperties = context.filesDir.path + "/" + Injectable.Style.rawValue + "/UserProperties.json"
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

                mServer.loadCustomResource(context.assets.open("Search/mark.js"), "mark.js", Injectable.Script)
                mServer.loadCustomResource(context.assets.open("Search/search.js"), "search.js", Injectable.Script)
                mServer.loadCustomResource(context.assets.open("Search/mark.css"), "mark.css", Injectable.Style)

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

    companion object {

        var isServerStarted = false
            private set

    }
}
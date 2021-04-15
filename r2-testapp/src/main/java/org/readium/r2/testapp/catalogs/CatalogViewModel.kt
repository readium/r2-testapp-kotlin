package org.readium.r2.testapp.catalogs

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.databinding.ObservableBoolean
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import nl.komponents.kovenant.Promise
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.testapp.R2App
import org.readium.r2.testapp.bookshelf.BookRepository
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.domain.model.OPDS
import org.readium.r2.testapp.opds.OPDSDownloader
import org.readium.r2.testapp.opds.OpdsDownloadResult
import org.readium.r2.testapp.utils.EventChannel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL

class CatalogViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: OpdsRepository
    private val bookRepository: BookRepository
    private var mOpdsDownloader: OPDSDownloader
    private var mR2Directory: String
    val detailChannel = EventChannel(Channel<Event.DetailEvent>(Channel.BUFFERED), viewModelScope)
    val eventChannel = EventChannel(Channel<Event.FeedEvent>(Channel.BUFFERED), viewModelScope)
    val parseData = MutableLiveData<ParseData>()
    val showProgressBar = ObservableBoolean()

    init {
        // FIXME
        val opdsDao = BookDatabase.getDatabase(application).opdsDao()
        val bookDao = BookDatabase.getDatabase(application).booksDao()
        repository = OpdsRepository(opdsDao)
        bookRepository = BookRepository(bookDao)

        mOpdsDownloader = OPDSDownloader(application.applicationContext)

        mR2Directory = R2App.R2DIRECTORY
    }

    val opds = repository.getOpdsFromDatabase()

    fun insertOpds(opds: OPDS) = viewModelScope.launch {
        repository.insertOpds(opds)
    }

    fun deleteOpds(id: Long) = viewModelScope.launch {
        repository.deleteOpds(id)
    }

    fun parseOpds(opds: OPDS) {
        var parsePromise: Promise<ParseData, Exception>? = null
        opds.href.let {
            try {
                parsePromise = if (opds.type == 1) {
                    OPDS1Parser.parseURL(URL(it))
                } else {
                    OPDS2Parser.parseURL(URL(it))
                }
            } catch (e: MalformedURLException) {
                eventChannel.send(Event.FeedEvent.OpdsParseFailed)
            }
        }
        parsePromise?.success {
            parseData.postValue(it)
        }
    }

    suspend fun downloadPublication(publication: Publication) {
        showProgressBar.set(true)
        val downloadUrl = getDownloadURL(publication)
        val publicationUrl = mOpdsDownloader.publicationUrl(downloadUrl.toString())
        when (publicationUrl) {
            is OpdsDownloadResult.OnSuccess -> {
                val id = addPublicationToDatabase(publicationUrl.data.first, "epub", publication)
                if (id != -1L) {
                    detailChannel.send(Event.DetailEvent.ImportPublicationSuccess)
                } else {
                    detailChannel.send(Event.DetailEvent.ImportPublicationFailed)
                }
            }
            is OpdsDownloadResult.OnFailure -> detailChannel.send(Event.DetailEvent.ImportPublicationFailed)
        }

        showProgressBar.set(false)
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

    private suspend fun addPublicationToDatabase(
        href: String,
        extension: String,
        publication: Publication
    ): Long {
        val id = bookRepository.insertBook(href, extension, publication)
        storeCoverImage(publication, id.toString())
        return id
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

    sealed class Event {

        sealed class FeedEvent : Event() {

            object OpdsParseFailed : FeedEvent()
        }

        sealed class DetailEvent : Event() {

            object ImportPublicationSuccess : DetailEvent()

            object ImportPublicationFailed : DetailEvent()
        }
    }
}
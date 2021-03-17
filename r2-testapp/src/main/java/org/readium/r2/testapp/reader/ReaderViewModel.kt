/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.joda.time.DateTime
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.testapp.bookshelf.BookRepository
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.domain.model.Bookmark
import org.readium.r2.testapp.domain.model.Highlight
import org.readium.r2.testapp.utils.EventChannel

class ReaderViewModel(context: Context, arguments: ReaderContract.Input) : ViewModel() {

    val publication: Publication = arguments.publication
    val channel = EventChannel(Channel<Event>(Channel.BUFFERED), viewModelScope)
    val bookId = arguments.bookId
    private val repository: BookRepository

    init {
        val booksDao = BookDatabase.getDatabase(context).booksDao()
        repository = BookRepository(booksDao)
    }

    fun saveProgression(locator: String) = viewModelScope.launch {
        repository.saveProgression(locator, bookId)
    }

    fun getBookmarks() = repository.getBookmarks(bookId)

    // TODO this allows multiple inserts of the same bookmark, fix here or in database
    fun insertBookmark(locator: Locator) = viewModelScope.launch {
        val resource = publication.readingOrder.indexOfFirstWithHref(locator.href)!!
        val bookmark = Bookmark(
                bookId = bookId,
                publicationId = publication.metadata.identifier ?: publication.metadata.title,
                resourceIndex = resource.toLong(),
                resourceHref = locator.href,
                resourceType = locator.type,
                resourceTitle = locator.title.orEmpty(),
                location = Locator.Locations(progression = locator.locations.progression, position = locator.locations.position).toJSON().toString(),
                locatorText = Locator.Text().toJSON().toString()
        )
        bookmark.creation = DateTime().toDate().time
        repository.insertBookmark(bookmark)
    }

    fun deleteBookmark(id: Long) = viewModelScope.launch {
        repository.deleteBookmark(id)
    }

    fun getHighlights(href: String? = null): LiveData<List<Highlight>> {
        return if (href == null)
            repository.getHighlights(bookId)
        else
            repository.getHighlights(bookId, href)
    }

    suspend fun getHighlightByHighlightId(highlightId: String): Highlight {
        return checkNotNull(repository.getHighlightByHighlightId(highlightId).firstOrNull())
    }

    fun insertHighlight(navigatorHighlight: org.readium.r2.navigator.epub.Highlight, progression: Double, annotation: String? = null) = viewModelScope.launch {
        val resource = publication.readingOrder.indexOfFirstWithHref(navigatorHighlight.locator.href)!!

        // This is required to be able to go right to a highlight from the Outline fragment,
        // as Navigator.go doesn't support DOM ranges yet.
        val locations = navigatorHighlight.locator.locations.copy(progression = progression)

        val highlight = Highlight(
                bookId = bookId,
                highlightId = navigatorHighlight.id,
                publicationId = publication.metadata.identifier ?: publication.metadata.title,
                style = "style",
                color = navigatorHighlight.color,
                annotation = annotation ?: "",
                annotationMarkStyle = navigatorHighlight.annotationMarkStyle ?: "",
                resourceIndex = resource.toLong(),
                resourceHref = navigatorHighlight.locator.href,
                resourceType = navigatorHighlight.locator.type,
                resourceTitle = navigatorHighlight.locator.title.orEmpty(),
                location = locations.toJSON().toString(),
                locatorText = navigatorHighlight.locator.text.toJSON().toString()
        )

        highlight.creation = DateTime().toDate().time
        repository.insertHighlight(highlight)
    }

    // TODO do a proper update
    fun updateHighlight(id: String, color: Int? = null, annotation: String? = null, markStyle: String? = null) =  viewModelScope.launch {
        val highlight = getHighlightByHighlightId(id)
        val progression = highlight.locator.locations.progression
        val color = color ?: highlight.color
        val annotation = annotation ?: highlight.annotation
        val markStyle = markStyle ?: highlight.annotationMarkStyle

        insertHighlight(
                highlight.toNavigatorHighlight().copy(color = color, annotationMarkStyle = markStyle),
                progression = progression!!,
                annotation = annotation
        )
    }

    fun deleteHighlightByHighlightId(highlightId: String) = viewModelScope.launch {
        repository.deleteHighlightByHighlightId(highlightId)
    }

    class Factory(private val context: Context, private val arguments: ReaderContract.Input)
        : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            modelClass.getDeclaredConstructor(Context::class.java, ReaderContract.Input::class.java)
                .newInstance(context.applicationContext, arguments)
    }

    sealed class Event {

        object OpenOutlineRequested : Event()

        object OpenDrmManagementRequested : Event()
    }
}


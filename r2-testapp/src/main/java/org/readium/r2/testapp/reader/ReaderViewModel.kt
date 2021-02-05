package org.readium.r2.testapp.reader

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.testapp.db.Bookmark
import org.readium.r2.testapp.db.BookmarksDatabase
import org.readium.r2.testapp.db.BooksDatabase
import org.readium.r2.testapp.db.Highlight
import org.readium.r2.testapp.db.HighligtsDatabase

class ReaderViewModel(val publication: Publication, val persistence: BookData) : ViewModel() {

    class Factory(private val publication: Publication, private val persistence: BookData)
        : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            modelClass.getDeclaredConstructor(Publication::class.java, BookData::class.java)
                .newInstance(publication, persistence)
    }
}


class BookData(context: Context, val bookId: Long, val publication: Publication) {

    private val pubId: String = publication.metadata.identifier ?: publication.metadata.title
    private val booksDb = BooksDatabase(context)
    private val bookmarksDb = BookmarksDatabase(context)
    private val highlightsDb = HighligtsDatabase(context)

    var savedLocation: Locator?
        get() = booksDb.books.currentLocator(bookId)
        set(locator) { booksDb.books.saveProgression(locator, bookId) }

    fun addBookmark(locator: Locator): Boolean {
        val resource = publication.readingOrder.indexOfFirstWithHref(locator.href)!!
        val bookmark = Bookmark(bookId, pubId, resource.toLong(), locator)
        return bookmarksDb.bookmarks.insert(bookmark) != null
    }

    fun removeBookmark(bookmark: Bookmark) {
        bookmarksDb.bookmarks.delete(bookmark)
    }

    fun getBookmarks(comparator: Comparator<Bookmark>): List<Bookmark> {
        return bookmarksDb.bookmarks.list(bookId).sortedWith(comparator)
    }

    fun getHighlights(comparator: Comparator<Highlight>): List<Highlight> {
        return highlightsDb.highlights.listAll(bookId).sortedWith(comparator)
    }

    fun removeHighlight(highlight: Highlight) {
        highlightsDb.highlights.delete(highlight)
    }
}
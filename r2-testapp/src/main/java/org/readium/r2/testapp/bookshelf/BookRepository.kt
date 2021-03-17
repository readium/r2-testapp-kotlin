package org.readium.r2.testapp.bookshelf

import androidx.lifecycle.LiveData
import org.readium.r2.testapp.db.BooksDao
import org.readium.r2.testapp.domain.model.Book
import org.readium.r2.testapp.domain.model.Bookmark
import org.readium.r2.testapp.domain.model.Highlight

class BookRepository(private val booksDao: BooksDao) {

    suspend fun insertBook(book: Book): Long {
        return booksDao.insertBook(book)
    }

    suspend fun deleteBook(id: Long) = booksDao.deleteBook(id)

    fun getBooksFromDatabase(): LiveData<List<Book>> = booksDao.getAllBooks()

    suspend fun insertBookmark(bookmark: Bookmark): Long {
        return booksDao.insertBookmark(bookmark)
    }

    suspend fun insertHighlight(highlight: Highlight) {
        booksDao.insertHighlight(highlight)
    }

    suspend fun saveProgression(locator: String, bookId: Long) = booksDao.saveProgression(locator, bookId)

    fun getBookmarks(bookId: Long): LiveData<MutableList<Bookmark>> = booksDao.getBookmarksForBook(bookId)

    fun getHighlights(bookId: Long, href: String): LiveData<List<Highlight>> = booksDao.getHighlightsForBook(bookId, href)

    fun getHighlights(bookId: Long): LiveData<List<Highlight>> = booksDao.getHighlightsForBook(bookId)

    suspend fun deleteBookmark(bookmarkId: Long) = booksDao.deleteBookmark(bookmarkId)

    suspend fun deleteHighlight(id: Long) = booksDao.deleteHighlight(id)

    suspend fun getHighlightByHighlightId(highlightId: String): MutableList<Highlight> = booksDao.getHighlightByHighlightId(highlightId)

    suspend fun deleteHighlightByHighlightId(highlightId: String) = booksDao.deleteHighlightByHighlightId(highlightId)
}
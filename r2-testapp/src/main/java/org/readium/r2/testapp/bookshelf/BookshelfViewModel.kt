package org.readium.r2.testapp.bookshelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.testapp.R2App
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.domain.model.Book
import java.io.File

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository

    init {
        val booksDao = BookDatabase.getDatabase(application).booksDao()
        repository = BookRepository(booksDao)
    }

    val books = repository.getBooksFromDatabase()

    fun deleteBook(book: Book) = viewModelScope.launch {
        book.id?.let { repository.deleteBook(it) }
        tryOrNull { File(book.href).delete() }
        tryOrNull { File("${(getApplication() as R2App).R2DIRECTORY}covers/${book.id}.png").delete() }
    }
}
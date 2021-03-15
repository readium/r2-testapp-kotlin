package org.readium.r2.testapp.bookshelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import org.readium.r2.testapp.db.BookDatabase
import java.util.*

class BookshelfViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BookRepository

    init {
        val booksDao = BookDatabase.getDatabase(application).booksDao()
        repository = BookRepository(booksDao)
    }

    val books = repository.getBooksFromDatabase()

}
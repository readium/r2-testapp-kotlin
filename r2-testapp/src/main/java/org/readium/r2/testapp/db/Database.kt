package org.readium.r2.testapp.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.readium.r2.testapp.domain.model.Book
import org.readium.r2.testapp.domain.model.Bookmark
import org.readium.r2.testapp.domain.model.Highlight
import org.readium.r2.testapp.domain.model.OPDS

@Database(entities = [Book::class, Bookmark::class, Highlight::class, OPDS::class], version = 1, exportSchema = false)
abstract class BookDatabase : RoomDatabase() {

    abstract fun booksDao(): BooksDao

    abstract fun opdsDao(): OpdsDao

    companion object {
        @Volatile
        private var INSTANCE: BookDatabase? = null

        fun getDatabase(context: Context): BookDatabase {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Room.databaseBuilder(
                        context.applicationContext,
                        BookDatabase::class.java,
                        "books_database"
                ).build()
                INSTANCE = instance
                return instance
            }
        }
    }
}
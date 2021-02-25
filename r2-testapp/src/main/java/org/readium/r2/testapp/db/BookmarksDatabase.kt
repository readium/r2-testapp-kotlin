/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Mostapha Idoubihi, Paul Stoica
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import nl.komponents.kovenant.task
import nl.komponents.kovenant.then
import org.jetbrains.anko.db.*
import org.joda.time.DateTime
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

class Bookmark(
    val bookID: Long,
    val publicationID: String,
    val resourceIndex: Long,
    val resourceHref: String,
    val resourceType: String,
    val resourceTitle: String,
    val location: Locator.Locations,
    val locatorText: Locator.Text,
    var creationDate: Long = DateTime().toDate().time,
    var id: Long? = null
) {

    constructor(bookID: Long, publicationID: String, resourceIndex: Long, locator: Locator):
        this(
            bookID = bookID,
            publicationID = publicationID,
            resourceIndex = resourceIndex,
            resourceHref = locator.href,
            resourceType = locator.type,
            resourceTitle = locator.title ?: "",
            location = locator.locations,
            locatorText = locator.text
        )

    val locator get() = Locator(
        href = resourceHref,
        type = resourceType,
        title = resourceTitle,
        locations = location,
        text = locatorText
    )

}

class BookmarksDatabase(context: Context) {

    val shared: BookmarksDatabaseOpenHelper = BookmarksDatabaseOpenHelper(context)
    var bookmarks: BOOKMARKS

    init {
        bookmarks = BOOKMARKS(shared)
    }

}

class BookmarksDatabaseOpenHelper(ctx: Context) : ManagedSQLiteOpenHelper(ctx, "bookmarks_database", null, DATABASE_VERSION) {
    companion object {
        private var instance: BookmarksDatabaseOpenHelper? = null
        private const val DATABASE_VERSION = 3

        @Synchronized
        fun getInstance(ctx: Context): BookmarksDatabaseOpenHelper {
            if (instance == null) {
                instance = BookmarksDatabaseOpenHelper(ctx.applicationContext)
            }
            return instance!!
        }
    }

    override fun onCreate(db: SQLiteDatabase) {

        db.createTable(BOOKMARKSTable.NAME, true,
                BOOKMARKSTable.ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
                BOOKMARKSTable.BOOK_ID to INTEGER,
                BOOKMARKSTable.PUBLICATION_ID to TEXT,
                BOOKMARKSTable.RESOURCE_INDEX to INTEGER,
                BOOKMARKSTable.RESOURCE_HREF to TEXT,
                BOOKMARKSTable.RESOURCE_TYPE to TEXT,
                BOOKMARKSTable.RESOURCE_TITLE to TEXT,
                BOOKMARKSTable.LOCATION to TEXT,
                BOOKMARKSTable.LOCATOR_TEXT to TEXT,
                BOOKMARKSTable.CREATION_DATE to INTEGER)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        when (oldVersion) {
            1 -> {
                try {

                    task {
                        //  add migration: rename timestamp to creationDate
                        db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " RENAME COLUMN 'timestamp' to " + BOOKMARKSTable.CREATION_DATE + ";")
                    } then {
                        //  add migration: add publicationId
                        db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " ADD COLUMN " + BOOKMARKSTable.PUBLICATION_ID + " TEXT DEFAULT '';")
                    } then {
                        // add migration: add location
                        db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " ADD COLUMN " + BOOKMARKSTable.LOCATION + " TEXT DEFAULT '{}';")
                    } then {
                        //  add migration: convert progression into location
                        val cursor = db.query(BOOKMARKSTable.NAME, arrayOf(BOOKMARKSTable.ID, "progression", BOOKMARKSTable.LOCATION), null, null, null, null, null, null)
                        if (cursor != null) {
                            var hasItem = cursor.moveToFirst()
                            while (hasItem) {
                                val id = cursor.getInt(cursor.getColumnIndex(BOOKMARKSTable.ID))
                                val progression = cursor.getDouble(cursor.getColumnIndex("progression"))
                                val values = ContentValues()
                                values.put(BOOKMARKSTable.LOCATION, Locator.Locations(progression = progression).toJSON().toString())
                                db.update(BOOKMARKSTable.NAME, values, "${BOOKMARKSTable.ID}=?", arrayOf(id.toString()))
                                hasItem = cursor.moveToNext()
                            }
                            cursor.close()
                        }
                    } then {
                        //  add migration: remove progression
                        db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " DROP COLUMN 'progression';")
                    } then {
                        //  add migration: add resourceTitle
                        db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " ADD COLUMN " + BOOKMARKSTable.RESOURCE_TITLE + " TEXT DEFAULT '';")
                    } then {
                        //  add migration: add locatorText
                        db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " ADD COLUMN " + BOOKMARKSTable.LOCATOR_TEXT + " TEXT DEFAULT '{}';")
                    } then {
                        //  add migration: add resourceType
                        db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " ADD COLUMN " + BOOKMARKSTable.RESOURCE_TYPE + " TEXT DEFAULT '';")
                    }

                } catch (e: SQLiteException) { }
            }
            2 -> {
                try {

                    db.execSQL("ALTER TABLE " + BOOKMARKSTable.NAME + " ADD COLUMN " + BOOKMARKSTable.RESOURCE_TYPE + " TEXT DEFAULT '';")

                } catch (e: SQLiteException) { }
            }
        }

    }


}

object BOOKMARKSTable {
    const val NAME = "BOOKMARKS"
    const val ID = "id"
    const val BOOK_ID = "bookID"
    const val PUBLICATION_ID = "publicationID"
    const val RESOURCE_INDEX = "resourceIndex"
    const val RESOURCE_HREF = "resourceHref"
    const val RESOURCE_TYPE = "resourceType"
    const val RESOURCE_TITLE = "resourceTitle"
    const val LOCATION = "location"
    const val LOCATOR_TEXT = "locatorText"
    const val CREATION_DATE = "creationDate"
    var RESULT_COLUMNS = arrayOf(ID, BOOK_ID, PUBLICATION_ID, RESOURCE_INDEX, RESOURCE_HREF, RESOURCE_TYPE, RESOURCE_TITLE, LOCATION, LOCATOR_TEXT, CREATION_DATE)

}

class BOOKMARKS(private var database: BookmarksDatabaseOpenHelper) {

    fun dropTable() {
        database.use {
            dropTable(BOOKMARKSTable.NAME, true)
        }
    }

    fun emptyTable() {
        database.use {
            delete(BOOKMARKSTable.NAME, "")
        }
    }

    fun insert(bookmark: Bookmark): Long? {
        if (bookmark.bookID < 0 ||
                bookmark.resourceIndex < 0){
            return null
        }
        val exists = has(bookmark)
        if (exists.isEmpty()) {
            return database.use {
                return@use insert(BOOKMARKSTable.NAME,
                        BOOKMARKSTable.BOOK_ID to bookmark.bookID,
                        BOOKMARKSTable.PUBLICATION_ID to bookmark.publicationID,
                        BOOKMARKSTable.RESOURCE_INDEX to bookmark.resourceIndex,
                        BOOKMARKSTable.RESOURCE_HREF to bookmark.resourceHref,
                        BOOKMARKSTable.RESOURCE_TYPE to bookmark.resourceType,
                        BOOKMARKSTable.RESOURCE_TITLE to bookmark.resourceTitle,
                        BOOKMARKSTable.LOCATION to bookmark.location.toJSON().toString(),
                        BOOKMARKSTable.LOCATOR_TEXT to bookmark.locatorText.toJSON().toString(),
                        BOOKMARKSTable.CREATION_DATE to bookmark.creationDate)
            }
        }
        return null
    }

    private fun has(bookmark: Bookmark): List<Bookmark> {
        return database.use {
            select(BOOKMARKSTable.NAME,
                    BOOKMARKSTable.ID,
                    BOOKMARKSTable.BOOK_ID,
                    BOOKMARKSTable.PUBLICATION_ID,
                    BOOKMARKSTable.RESOURCE_INDEX,
                    BOOKMARKSTable.RESOURCE_HREF,
                    BOOKMARKSTable.RESOURCE_TYPE,
                    BOOKMARKSTable.RESOURCE_TITLE,
                    BOOKMARKSTable.LOCATION,
                    BOOKMARKSTable.LOCATOR_TEXT,
                    BOOKMARKSTable.CREATION_DATE)
                    .whereArgs("(bookID = {bookID}) AND (publicationID = {publicationID}) AND (resourceIndex = {resourceIndex}) AND (resourceHref = {resourceHref})  AND (resourceHref = {resourceHref}) AND (location = {location}) AND (locatorText = {locatorText})",
                            "bookID" to bookmark.bookID,
                            "publicationID" to bookmark.publicationID,
                            "resourceIndex" to bookmark.resourceIndex,
                            "resourceHref" to bookmark.resourceHref,
                            "resourceType" to bookmark.resourceType,
                            "location" to bookmark.location.toJSON().toString(),
                            "locatorText" to bookmark.locatorText.toJSON().toString())
                    .exec {
                        parseList(MyRowParser())
                    }
        }
    }

    fun delete(locator: Bookmark) {
        database.use {
            delete(BOOKMARKSTable.NAME, "id = {id}",
                    "id" to locator.id!!)
        }
    }

    fun deleteBook(book_id: Long?) {
        book_id?.let {
            database.use {
                delete(BOOKMARKSTable.NAME, "${BOOKMARKSTable.BOOK_ID} = {bookID}",
                        "bookID" to book_id)
            }
        }
    }

    fun delete(bookmark_id: Long) {
        database.use {
            delete(BOOKMARKSTable.NAME, "id = {id}",
                "id" to bookmark_id)
        }
    }

    fun listAll(): MutableList<Bookmark> {
        return database.use {
            select(BOOKMARKSTable.NAME,
                    BOOKMARKSTable.ID,
                    BOOKMARKSTable.BOOK_ID,
                    BOOKMARKSTable.PUBLICATION_ID,
                    BOOKMARKSTable.RESOURCE_INDEX,
                    BOOKMARKSTable.RESOURCE_HREF,
                    BOOKMARKSTable.RESOURCE_TYPE,
                    BOOKMARKSTable.RESOURCE_TITLE,
                    BOOKMARKSTable.LOCATION,
                    BOOKMARKSTable.LOCATOR_TEXT,
                    BOOKMARKSTable.CREATION_DATE)
                    .exec {
                        parseList(MyRowParser()).toMutableList()
                    }
        }
    }


    fun list(bookID: Long): MutableList<Bookmark> {
        return database.use {
            select(BOOKMARKSTable.NAME,
                    BOOKMARKSTable.ID,
                    BOOKMARKSTable.BOOK_ID,
                    BOOKMARKSTable.PUBLICATION_ID,
                    BOOKMARKSTable.RESOURCE_INDEX,
                    BOOKMARKSTable.RESOURCE_HREF,
                    BOOKMARKSTable.RESOURCE_TYPE,
                    BOOKMARKSTable.RESOURCE_TITLE,
                    BOOKMARKSTable.LOCATION,
                    BOOKMARKSTable.LOCATOR_TEXT,
                    BOOKMARKSTable.CREATION_DATE)
                    .whereArgs("bookID = {bookID}", "bookID" to bookID as Any)
                    .orderBy(BOOKMARKSTable.RESOURCE_INDEX, SqlOrderDirection.ASC)
                    .orderBy(BOOKMARKSTable.CREATION_DATE, SqlOrderDirection.ASC)
                    .exec {
                        parseList(MyRowParser()).toMutableList()
                    }
        }
    }

    class MyRowParser : RowParser<Bookmark> {
        override fun parseRow(columns: Array<Any?>): Bookmark {
            val id = columns[0]?.let {
                return@let it
            } ?: kotlin.run { return@run 0 }
            val bookID = columns[1]?.let {
                return@let it
            } ?: kotlin.run { return@run 0 }
            val publicationID = columns[2]?.let {
                return@let it
            } ?: kotlin.run { return@run "" }
            val resourceIndex = columns[3]?.let {
                return@let it
            } ?: kotlin.run { return@run 0 }
            val resourceHref = columns[4]?.let {
                return@let it
            } ?: kotlin.run { return@run "" }
            val resourceType = columns[5]?.let {
                return@let it
            } ?: kotlin.run { return@run "" }
            val resourceTitle = columns[6]?.let {
                return@let it
            } ?: kotlin.run { return@run "" }
            val location = columns[7]?.let {
                return@let it
            } ?: kotlin.run { return@run "" }
            val locatorText = columns[8]?.let {
                return@let it
            } ?: kotlin.run { return@run "" }
            val created = columns[9]?.let {
                return@let it
            } ?: kotlin.run { return@run null }

            return Bookmark(bookID as Long, publicationID as String, resourceIndex as Long, resourceHref as String, resourceType as String, resourceTitle as String, Locator.Locations.fromJSON(JSONObject(location as String)), Locator.Text.fromJSON(JSONObject(locatorText as String)), created as Long, id as Long)
        }
    }

}
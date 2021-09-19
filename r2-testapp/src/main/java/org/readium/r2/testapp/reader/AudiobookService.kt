/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import org.readium.r2.navigator.ExperimentalAudiobook
import org.readium.r2.navigator.media.MediaService
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.PublicationId
import org.readium.r2.testapp.bookshelf.BookRepository
import org.readium.r2.testapp.db.BookDatabase

@OptIn(ExperimentalAudiobook::class)
class AudiobookService : MediaService() {

    private val books by lazy {
        BookRepository(BookDatabase.getDatabase(this).booksDao())
    }

    override suspend fun onCurrentLocatorChanged(publication: Publication, publicationId: PublicationId, locator: Locator) {
        books.saveProgression(locator, publicationId.toLong())
    }

    override suspend fun onCreateNotificationIntent(publication: Publication, publicationId: PublicationId): PendingIntent? {
        val bookId = publicationId.toLong()
        val book = books.get(bookId) ?: return null

        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }

        val intent = ReaderContract().createIntent(this, ReaderContract.Input(
            mediaType = book.mediaType(),
            publication = publication,
            bookId = bookId,
        ))
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(this, 0, intent, flags)
    }

}
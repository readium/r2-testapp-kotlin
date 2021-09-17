/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.audiobook

import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.launch
import org.readium.r2.navigator.media.MediaService
import org.readium.r2.shared.AudiobookNavigator
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.PublicationId
import org.readium.r2.testapp.bookshelf.BookRepository
import org.readium.r2.testapp.db.BookDatabase
import org.readium.r2.testapp.reader.ReaderActivity

@OptIn(AudiobookNavigator::class)
class AudiobookService : MediaService() {

    private val books by lazy {
        BookRepository(BookDatabase.getDatabase(this).booksDao())
    }

    override fun onCurrentLocatorChanged(publication: Publication, publicationId: PublicationId, locator: Locator) {
        launch {
            books.saveProgression(locator, publicationId.toLong())
        }
    }

    override val navigatorActivityIntent: PendingIntent?
        get() {
            val intent = Intent(this, ReaderActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

}

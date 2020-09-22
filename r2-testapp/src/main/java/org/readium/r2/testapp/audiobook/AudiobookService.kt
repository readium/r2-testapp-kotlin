/*
 * Copyright 2020 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.audiobook

import android.app.PendingIntent
import android.content.Intent
import org.readium.r2.navigator.media.MediaService
import org.readium.r2.shared.AudioSupport
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.PublicationId
import org.readium.r2.testapp.db.BooksDatabase

@OptIn(AudioSupport::class)
class AudiobookService : MediaService() {

    private val books by lazy { BooksDatabase(this).books }

    override fun onCurrentLocatorChanged(publication: Publication, publicationId: PublicationId, locator: Locator) {
        books.saveProgression(locator, publicationId.toLong())
    }

    override val navigatorActivityIntent: PendingIntent?
        get() {
            val intent = Intent(this, AudiobookActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

}

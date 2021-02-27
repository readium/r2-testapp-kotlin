/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.utils.EventChannel

class ReaderViewModel(val publication: Publication, val persistence: BookData) : ViewModel() {

    val channel = EventChannel(Channel<Event>(Channel.BUFFERED), viewModelScope)

    class Factory(private val publication: Publication, private val persistence: BookData)
        : ViewModelProvider.NewInstanceFactory() {

        override fun <T : ViewModel?> create(modelClass: Class<T>): T =
            modelClass.getDeclaredConstructor(Publication::class.java, BookData::class.java)
                .newInstance(publication, persistence)
    }

    sealed class Event {

        object OpenOutlineRequested : Event()

        object OpenDrmManagementRequested : Event()
    }
}


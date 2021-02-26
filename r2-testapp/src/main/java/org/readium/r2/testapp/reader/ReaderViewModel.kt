/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.utils.observeWhenStarted

class ReaderViewModel(val publication: Publication, val persistence: BookData) : ViewModel() {

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

    private val channel = Channel<Event>(Channel.BUFFERED)

    fun sendEvent(event: Event) {
        viewModelScope.launch {
            channel.send(event)
        }
    }

    fun subscribeEvents(lifecycleOwner: LifecycleOwner, collector: suspend (Event) -> Unit) {
        channel.receiveAsFlow().observeWhenStarted(lifecycleOwner, collector)
    }
}


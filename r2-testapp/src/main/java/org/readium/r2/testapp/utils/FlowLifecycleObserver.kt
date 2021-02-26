package org.readium.r2.testapp.utils

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

// See https://proandroiddev.com/android-singleliveevent-redux-with-kotlin-flow-b755c70bb055

inline fun <reified T> Flow<T>.observeWhenStarted(
    lifecycleOwner: LifecycleOwner,
    noinline collector: suspend (T) -> Unit
) {
    val observer = FlowObserver(lifecycleOwner, this, collector)
    lifecycleOwner.lifecycle.addObserver(observer)
}


class FlowObserver<T> (
    private val lifecycleOwner: LifecycleOwner,
    private val flow: Flow<T>,
    private val collector: suspend (T) -> Unit
) : LifecycleObserver {

    private var job: Job? = null

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (job == null) {
            job = lifecycleOwner.lifecycleScope.launch {
                flow.collect { collector(it) }
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        job?.cancel()
        job = null
    }
}


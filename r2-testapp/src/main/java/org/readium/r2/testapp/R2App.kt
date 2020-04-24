/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import nl.komponents.kovenant.android.startKovenant
import nl.komponents.kovenant.android.stopKovenant
import org.readium.r2.testapp.BuildConfig.DEBUG
import timber.log.Timber
import co.endao.EndaoExtension
import tti.NavigatorExtension

class R2App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Configure Kovenant with standard dispatchers
        // suitable for an Android environment.
        startKovenant()
        if (DEBUG) Timber.plant(Timber.DebugTree())
        EndaoExtension.initInjectInfo()
        NavigatorExtension.addExtension(EndaoExtension())
    }

    override fun onTerminate() {
        super.onTerminate()
        // Dispose of the Kovenant thread pools.
        // For quicker shutdown you could use
        // `force=true`, which ignores all current
        // scheduled tasks
        stopKovenant()
    }
}

val Context.resolver: ContentResolver
    get() = applicationContext.contentResolver

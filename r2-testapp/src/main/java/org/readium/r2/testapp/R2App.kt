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

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import nl.komponents.kovenant.android.startKovenant
import nl.komponents.kovenant.android.stopKovenant
import org.readium.r2.shared.Injectable
import org.readium.r2.streamer.server.Server
import org.readium.r2.testapp.BuildConfig.DEBUG
import timber.log.Timber
import java.io.IOException
import java.net.ServerSocket
import java.util.*

class R2App : Application() {

    override fun onCreate() {
        super.onCreate()
        // Configure Kovenant with standard dispatchers
        // suitable for an Android environment.
        startKovenant()
        if (DEBUG) Timber.plant(Timber.DebugTree())
        val s = ServerSocket(if (DEBUG) 8080 else 0)
        s.close()
        server = Server(s.localPort, applicationContext)
        startServer()
        R2DIRECTORY = r2Directory
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        lateinit var server: Server
            private set

        lateinit var R2DIRECTORY: String
            private set

        var isServerStarted = false
            private set
    }

    override fun onTerminate() {
        super.onTerminate()
        // Dispose of the Kovenant thread pools.
        // For quicker shutdown you could use
        // `force=true`, which ignores all current
        // scheduled tasks
        stopKovenant()
        stopServer()
    }

    private fun startServer() {
        if (!server.isAlive) {
            try {
                server.start()
            } catch (e: IOException) {
                // do nothing
                if (BuildConfig.DEBUG) Timber.e(e)
            }
            if (server.isAlive) {
//                // Add your own resources here
//                server.loadCustomResource(assets.open("scripts/test.js"), "test.js")
//                server.loadCustomResource(assets.open("styles/test.css"), "test.css")
//                server.loadCustomFont(assets.open("fonts/test.otf"), applicationContext, "test.otf")

                server.loadCustomResource(
                    applicationContext.assets.open("Search/mark.js"),
                    "mark.js",
                    Injectable.Script
                )
                server.loadCustomResource(
                    applicationContext.assets.open("Search/search.js"),
                    "search.js",
                    Injectable.Script
                )
                server.loadCustomResource(
                    applicationContext.assets.open("Search/mark.css"),
                    "mark.css",
                    Injectable.Style
                )

                isServerStarted = true
            }
        }
    }

    fun stopServer() {
        if (server.isAlive) {
            server.stop()
            isServerStarted = false
        }
    }

    private val r2Directory: String
        get() {
            val properties = Properties()
            val inputStream = applicationContext.assets.open("configs/config.properties")
            properties.load(inputStream)
            val useExternalFileDir =
                properties.getProperty("useExternalFileDir", "false")!!.toBoolean()
            return if (useExternalFileDir) {
                applicationContext.getExternalFilesDir(null)?.path + "/"
            } else {
                applicationContext.filesDir?.path + "/"
            }
        }
}

val Context.resolver: ContentResolver
    get() = applicationContext.contentResolver

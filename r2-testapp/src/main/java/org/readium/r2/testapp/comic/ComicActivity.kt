/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.comic

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.readium.r2.navigator.NavigatorDelegate
import org.readium.r2.navigator.cbz.R2CbzActivity
import org.readium.r2.shared.extensions.putPublicationFrom
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.BooksDatabase
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.library.activitiesLaunched
import org.readium.r2.testapp.outline.OutlineFragment
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


/**
 * ComicActivity : Extension of the R2CbzActivity() from navigator
 *
 * That Activity manage everything related to the menu
 *      ( Table of content, User Settings, Drm, Bookmarks )
 *
 */
class ComicActivity : R2CbzActivity(), CoroutineScope, NavigatorDelegate {

    /**
     * Context of this scope.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private var menuToc: MenuItem? = null

    private lateinit var booksDB: BooksDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        if (activitiesLaunched.incrementAndGet() > 1 || !LibraryActivity.isServerStarted) {
            finish()
        }
        super.onCreate(savedInstanceState)

        booksDB = BooksDatabase(this)

        navigatorDelegate = this
        bookId = intent.getLongExtra("bookId", -1)

        launch {
            val positionCount = publication.positions().size

            currentLocator.asLiveData().observe(this@ComicActivity, Observer { locator ->
                locator ?: return@Observer
                Timber.d("locationDidChange position ${locator.locations.position ?: 0}/${positionCount} $locator")
                booksDB.books.saveProgression(locator, bookId)
            })
        }

        toggleActionBar()

        // Loads the last read location
        booksDB.books.currentLocator(bookId)?.let {
            go(it, false, {})
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_comic, menu)
        menuToc = menu?.findItem(R.id.toc)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toc -> {
                val intent = Intent(this, OutlineFragment::class.java).apply {
                    putPublicationFrom(this@ComicActivity)
                    putExtra("bookId", bookId)
                }
                startActivityForResult(intent, 2)
                true
            }
            else -> false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activitiesLaunched.getAndDecrement()
    }


}
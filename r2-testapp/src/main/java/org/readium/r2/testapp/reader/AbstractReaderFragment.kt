/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import org.jetbrains.anko.support.v4.toast
import org.readium.r2.lcp.lcpLicense
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.utils.extensions.hideSystemUi
import org.readium.r2.testapp.utils.extensions.showSystemUi

abstract class AbstractReaderFragment : Fragment(R.layout.fragment_reader) {

    protected abstract var model: ReaderViewModel
    protected abstract var publication: Publication
    protected abstract var persistence: BookData
    protected abstract var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState?.getBoolean(IS_VISIBLE_KEY) != false)
            requireActivity().hideSystemUi()
        else
            requireActivity().showSystemUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_VISIBLE_KEY, isVisible)
    }

    override fun onStop() {
        super.onStop()
        persistence.savedLocation = navigator.currentLocator.value
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setMenuVisibility(!hidden)
        requireActivity().invalidateOptionsMenu()
        if (!hidden) {
            requireActivity().hideSystemUi()
        } else {
            requireActivity().showSystemUi()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_reader, menu)
        menu.findItem(R.id.drm).isVisible = publication.lcpLicense != null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toc -> {
                model.sendEvent(ReaderFragmentEvent.OpenOutlineRequested)
                true
            }
            R.id.bookmark -> {
                val added = persistence.addBookmark(navigator.currentLocator.value)
                toast(if (added) "Bookmark added" else "Bookmark already exists")
                true
            }
            R.id.drm -> {
                model.sendEvent(ReaderFragmentEvent.OpenDrmManagementRequested)
                true
            }
            else -> false
        }
    }

    fun go(locator: Locator, animated: Boolean) =
        navigator.go(locator, animated)

    companion object {
        private const val IS_VISIBLE_KEY = "isVisible"
    }
}
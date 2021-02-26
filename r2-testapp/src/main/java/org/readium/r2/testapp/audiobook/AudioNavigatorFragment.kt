/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.audiobook

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.fragment_audiobook.*
import kotlinx.coroutines.launch
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.jetbrains.anko.support.v4.toast
import org.readium.r2.lcp.lcpLicense
import org.readium.r2.navigator.audiobook.R2MediaPlayer
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.testapp.R
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.reader.ReaderViewModel

class AudioNavigatorFragment : Fragment(R.layout.fragment_audiobook) {

    private lateinit var model: ReaderViewModel
    private lateinit var publication: Publication
    private lateinit var persistence: BookData

    private val activity: AudiobookActivity
        get() = requireActivity() as AudiobookActivity

    private val mediaPlayer: R2MediaPlayer
        get() = activity.mediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        val activity = requireActivity()

        ViewModelProvider(activity).get(ReaderViewModel::class.java).let {
            model = it
            publication = it.publication
            persistence = it.persistence
        }


        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setting cover
        viewLifecycleOwner.lifecycleScope.launch {
            publication.cover()?.let {
                imageView.setImageBitmap(it)
            }
        }

        // Loads the last read location
        persistence.savedLocation?.let {
            activity.go(it)
        }

        mediaPlayer.progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_preparing_audiobook))
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_reader, menu)
        menu.findItem(R.id.drm).isVisible = publication.lcpLicense != null
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toc -> {
                model.sendEvent(ReaderViewModel.Event.OpenOutlineRequested)
                true
            }
            R.id.bookmark -> {
                val added = persistence.addBookmark(activity.currentLocator.value)
                toast(if (added) "Bookmark added" else "Bookmark already exists")
                true
            }
            R.id.drm -> {
                model.sendEvent(ReaderViewModel.Event.OpenDrmManagementRequested)
                true
            }
            else -> false
        }
    }

    override fun onStop() {
        super.onStop()
        persistence.savedLocation = activity.currentLocator.value
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setMenuVisibility(!hidden)
        requireActivity().invalidateOptionsMenu()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer.progress!!.dismiss()
    }
}
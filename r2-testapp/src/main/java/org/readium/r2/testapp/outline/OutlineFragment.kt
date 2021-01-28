/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Mostapha Idoubihi, Paul Stoica
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.outline

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.setFragmentResult
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.fragment_outline.*
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.landmarks
import org.readium.r2.shared.publication.epub.pageList
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.testapp.R
import org.readium.r2.testapp.reader.BookData

class OutlineFragment private constructor(
    val publication: Publication,
    val bookData: BookData,
    val resultKey: String
) : Fragment(R.layout.fragment_outline) {

    override fun onCreate(savedInstanceState: Bundle?) {

        childFragmentManager.setFragmentResultListener(
            resultKey,
            this,
            FragmentResultListener { _, bundle -> setFragmentResult(resultKey, bundle) }
        )

        super.onCreate(savedInstanceState)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //view.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        //ViewCompat.requestApplyInsets(view)

        /*(requireActivity() as AppCompatActivity).supportActionBar?.apply {
            setShowHideAnimationEnabled(false)
            hide()
            show()
            setDisplayShowTitleEnabled(true)
        }*/

        val outlines: List<Outline> = when (publication.type) {
            Publication.TYPE.AUDIO, Publication.TYPE.CBZ  -> listOf(Outline.Contents, Outline.Bookmarks)
            Publication.TYPE.DiViNa -> listOf(Outline.Contents)
            else -> listOf(Outline.Contents, Outline.Bookmarks, Outline.Highlights, Outline.PageList, Outline.Landmarks)
        }

        outline_pager.adapter = OutlineFragmentStateAdapter(this, outlines, resultKey)
        TabLayoutMediator(outline_tab_layout, outline_pager) { tab, idx -> tab.setText(outlines[idx].label) }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()

       /* (requireActivity() as AppCompatActivity).supportActionBar?.apply {
            setShowHideAnimationEnabled(true)
            show()
            setDisplayShowTitleEnabled(false)
        }*/
    }

    companion object {

        const val RESULT_KEY = "outline_result"

        fun parseResult(result: Bundle): Locator =
            result.getParcelable("locator")
                ?: throw Exception("It looks like this is not a result from an OutlineFragment.")

        fun createFactory(publication: Publication, bookData: BookData, resultKey: String): FragmentFactory = object : FragmentFactory() {

            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                return when (className) {
                    OutlineFragment::class.java.name -> OutlineFragment(publication, bookData, resultKey)
                    ContentsFragment::class.java.name -> ContentsFragment(publication, resultKey)
                    LandmarksFragment::class.java.name -> LandmarksFragment(publication, resultKey)
                    PageListFragment::class.java.name -> PageListFragment(publication, resultKey)
                    //SyntheticPageListFragment::class.java.name -> SyntheticPageListFragment(bookData, resultKey)
                    BookmarksFragment::class.java.name -> BookmarksFragment(publication, bookData, resultKey)
                    HighlightsFragment::class.java.name -> HighlightsFragment(publication, bookData, resultKey)
                    else -> super.instantiate(classLoader, className)
                }
            }
        }
    }
}

private class OutlineFragmentStateAdapter(fragment: OutlineFragment, val outlines: List<Outline>, val resultKey: String)
    : FragmentStateAdapter(fragment) {

    val publication: Publication = fragment.publication
    val bookData: BookData = fragment.bookData

    override fun getItemCount(): Int {
        return outlines.size
    }

    override fun createFragment(position: Int): Fragment {
        return when (this.outlines[position]) {
            Outline.Bookmarks -> BookmarksFragment(publication, bookData, resultKey)
            Outline.Highlights -> HighlightsFragment(publication, bookData, resultKey)
            Outline.Landmarks -> LandmarksFragment(publication, resultKey)
            Outline.Contents -> ContentsFragment(publication, resultKey)
            Outline.PageList ->
                //if (publication.pageList.isNotEmpty())
                    PageListFragment(publication, resultKey)
               /* else
                    SyntheticPageListFragment(bookData, resultKey)*/
        }
    }
}

class ContentsFragment(publication: Publication, resultKey: String)
    : NavigationFragment(
        when {
            publication.tableOfContents.isNotEmpty() -> publication.tableOfContents
            publication.readingOrder.isNotEmpty() -> publication.readingOrder
            publication.images.isNotEmpty() -> publication.images
            else -> mutableListOf()
        },
        resultKey
    )

class PageListFragment(publication: Publication, resultKey: String)
    : NavigationFragment(publication.pageList, resultKey)

class LandmarksFragment(publication: Publication, resultKey: String)
    : NavigationFragment(publication.landmarks, resultKey)

private enum class Outline(val label: Int) {
    Contents(R.string.contents_tab_label),
    Bookmarks(R.string.bookmarks_tab_label),
    Highlights(R.string.highlights_tab_label),
    PageList(R.string.pagelist_tab_label),
    Landmarks(R.string.landmarks_tab_label)
}
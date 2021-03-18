/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.outline

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.domain.model.Bookmark
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.utils.extensions.outlineTitle
import kotlin.math.roundToInt

class BookmarksFragment : Fragment(R.layout.fragment_listview) {

    lateinit var publication: Publication
    lateinit var viewModel: ReaderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java).let {
            publication = it.publication
            viewModel = it
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val comparator: Comparator<Bookmark> = compareBy( {it.resourceIndex },{ it.locator.locations.progression })
//        val bookmarks = persistence.getBookmarks(comparator).toMutableList()
        viewModel.getBookmarks().observe(viewLifecycleOwner, {
            val bookmarks = it.sortedWith(comparator).toMutableList()
            view.findViewById<ListView>(R.id.list_view).adapter = BookmarksAdapter(
                    requireActivity(),
                    bookmarks,
                    publication,
                    onBookmarkDeleteRequested = { bookmark -> viewModel.deleteBookmark(bookmark.id!!) }
            )

            view.findViewById<ListView>(R.id.list_view).setOnItemClickListener { _, _, position, _ -> onBookmarkSelected(bookmarks[position]) }
        })
    }

    private fun onBookmarkSelected(bookmark: Bookmark) {
        setFragmentResult(
            OutlineContract.REQUEST_KEY,
            OutlineContract.createResult(bookmark.locator)
        )
    }
}

// TODO redo this to use ListAdapter
private class BookmarksAdapter(
        private val activity: Activity,
        private val bookmarks: MutableList<Bookmark>,
        private val publication: Publication,
        private val onBookmarkDeleteRequested: (Bookmark) -> Unit
) : BaseAdapter() {

    private class ViewHolder(row: View) {
        val bookmarkChapter: TextView = row.findViewById(R.id.bookmark_chapter)
        val bookmarkProgression: TextView = row.findViewById(R.id.bookmark_progression)
        val bookmarkTimestamp: TextView = row.findViewById(R.id.bookmark_timestamp)
        val bookmarkOverflow: ImageView = row.findViewById(R.id.overflow)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view =
            if (convertView == null) {
                val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                inflater.inflate(R.layout.item_recycle_bookmark, null).also {
                    it.tag = ViewHolder(it)
                }
            } else {
                convertView
            }

        val viewHolder = view.tag as ViewHolder

        val bookmark = getItem(position) as Bookmark

        val title = getBookSpineItem(bookmark.resourceHref)
            ?:  "*Title Missing*"

        viewHolder.bookmarkChapter.text = title
        bookmark.locator.locations.progression?.let { progression ->
            val formattedProgression = "${(progression * 100).roundToInt()}% through resource"
            viewHolder.bookmarkProgression.text = formattedProgression
        }

        val formattedDate = DateTime(bookmark.creation).toString(DateTimeFormat.shortDateTime())
        viewHolder.bookmarkTimestamp.text = formattedDate

        viewHolder.bookmarkOverflow.setOnClickListener {

            val popupMenu = PopupMenu(parent?.context, viewHolder.bookmarkChapter)
            popupMenu.menuInflater.inflate(R.menu.menu_bookmark, popupMenu.menu)
            popupMenu.show()

            popupMenu.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.delete) {
                    onBookmarkDeleteRequested(bookmarks[position])
                    bookmarks.removeAt(position)
                    notifyDataSetChanged()
                }
                false
            }
        }

        return view
    }

    override fun getCount(): Int {
        return bookmarks.size
    }

    override fun getItem(position: Int): Any {
        return bookmarks[position]
    }

    private fun getBookSpineItem(href: String): String? {
        for (link in publication.tableOfContents) {
            if (link.href == href) {
                return link.outlineTitle
            }
        }
        for (link in publication.readingOrder) {
            if (link.href == href) {
                return link.outlineTitle
            }
        }
        return null
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }
}

package org.readium.r2.testapp.outline

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import kotlinx.android.synthetic.main.fragment_listview.*
import kotlinx.android.synthetic.main.item_recycle_bookmark.view.*
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.Bookmark
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.utils.extensions.outlineTitle
import kotlin.math.roundToInt

class BookmarksFragment(private val publication: Publication, private val bookData: BookData, private val resultKey: String)
    : Fragment(R.layout.fragment_listview) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val comparator: Comparator<Bookmark> = compareBy( {it.resourceIndex },{ it.location.progression })
        val bookmarks = bookData.getBookmarks(comparator).toMutableList()

        list_view.adapter = BookMarksAdapter(
            requireActivity(),
            bookmarks,
            publication,
            onBookmarkDeleteRequested = { bookData.removeBookmark(it) }
        )

        list_view.setOnItemClickListener { _, _, position, _ -> onBookmarkSelected(bookmarks[position]) }
    }

    private fun onBookmarkSelected(bookmark: Bookmark) {
        val bookmarkProgression = bookmark.location.progression

        val locator = Locator(
            href =  bookmark.resourceHref,
            type = bookmark.resourceType,
            locations = Locator.Locations(progression = bookmarkProgression)
        )

        val bundle = Bundle().apply {
            putParcelable(resultKey, locator)
        }
        setFragmentResult(resultKey, bundle)
    }
}

private class BookMarksAdapter(
    val activity: Activity,
    private val bookmarks: MutableList<Bookmark>,
    private val publication: Publication,
    private val onBookmarkDeleteRequested: (Bookmark) -> Unit
) : BaseAdapter() {

    private inner class ViewHolder(row: View?) {
        internal var bookmarkChapter: TextView? = null
        internal var bookmarkProgression: TextView? = null
        internal var bookmarkTimestamp: TextView? = null
        internal var bookmarkOverflow: ImageView? = null

        init {
            this.bookmarkChapter = row?.bookmark_chapter as TextView
            this.bookmarkProgression = row.bookmark_progression as TextView
            this.bookmarkTimestamp = row.bookmark_timestamp as TextView
            this.bookmarkOverflow = row.overflow as ImageView
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View?
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.item_recycle_bookmark, null)
            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val bookmark = getItem(position) as Bookmark

        val title = getBookSpineItem(bookmark.resourceHref)
            ?:  "*Title Missing*"

        viewHolder.bookmarkChapter!!.text = title

        bookmark.location.progression?.let { progression ->
            val formattedProgression = "${(progression * 100).roundToInt()}% through resource"
            viewHolder.bookmarkProgression!!.text = formattedProgression
        }

        val formattedDate = DateTime(bookmark.creationDate).toString(DateTimeFormat.shortDateTime())
        viewHolder.bookmarkTimestamp!!.text = formattedDate

        viewHolder.bookmarkOverflow?.setOnClickListener {

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

        return view as View
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

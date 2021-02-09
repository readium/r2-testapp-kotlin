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
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.fragment_listview.*
import kotlinx.android.synthetic.main.item_recycle_highlight.view.*
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.Highlight
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.utils.extensions.outlineTitle

class HighlightsFragment : Fragment(R.layout.fragment_listview) {

    lateinit var publication: Publication
    lateinit var persistence: BookData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java).let {
            publication = it.publication
            persistence = it.persistence
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val comparator: Comparator<Highlight> = compareBy( {it.resourceIndex },{ it.location.progression })
        val highlights = persistence.getHighlights(comparator).toMutableList()

        list_view.adapter = HighlightsAdapter(
            requireActivity(),
            highlights,
            publication,
            onDeleteHighlightRequested = { persistence.removeHighlight(it) }
        )

        list_view.setOnItemClickListener { _, _, position, _ -> onHighlightSelected(highlights[position]) }
    }

    private fun onHighlightSelected(highlight: Highlight) {
        val highlightProgression = highlight.location.progression

        val locator = Locator(
            href = highlight.resourceHref,
            type = highlight.resourceType,
            locations = Locator.Locations(progression = highlightProgression)
        )

        setFragmentResult(
            OutlineContract.REQUEST_KEY,
            OutlineContract.createResult(locator)
        )
    }
}

private class HighlightsAdapter(
    private val activity: Activity,
    private val items: MutableList<Highlight>,
    private val publication: Publication,
    private val onDeleteHighlightRequested: (Highlight) -> Unit
) : BaseAdapter() {

    private inner class ViewHolder(row: View?) {
        internal var highlightedText: TextView? = null
        internal var highlightTimestamp: TextView? = null
        internal var highlightChapter: TextView? = null
        internal var highlightOverflow: ImageView? = null
        internal var annotation: TextView? = null

        init {
            this.highlightedText = row?.highlight_text as TextView
            this.highlightTimestamp = row.highlight_time_stamp as TextView
            this.highlightChapter = row.highlight_chapter as TextView
            this.highlightOverflow = row.highlight_overflow as ImageView
            this.annotation = row.annotation as TextView
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

        val view: View?
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.item_recycle_highlight, null)
            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val highlight = getItem(position) as Highlight

        viewHolder.highlightChapter!!.text = getHighlightSpineItem(highlight.resourceHref)
        viewHolder.highlightedText!!.text = highlight.locatorText.highlight
        viewHolder.annotation!!.text = highlight.annotation

        val formattedDate = DateTime(highlight.creationDate).toString(DateTimeFormat.shortDateTime())
        viewHolder.highlightTimestamp!!.text = formattedDate

        viewHolder.highlightOverflow?.setOnClickListener {

            val popupMenu = PopupMenu(parent?.context, viewHolder.highlightChapter)
            popupMenu.menuInflater.inflate(R.menu.menu_bookmark, popupMenu.menu)
            popupMenu.show()

            popupMenu.setOnMenuItemClickListener { item ->
                if (item.itemId == R.id.delete) {
                    onDeleteHighlightRequested(items[position])
                    items.removeAt(position)
                    notifyDataSetChanged()
                }
                false
            }
        }

        return view as View
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Any {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    private fun getHighlightSpineItem(href: String): String? {
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
}


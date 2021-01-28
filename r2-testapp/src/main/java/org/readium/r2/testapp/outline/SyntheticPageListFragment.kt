package org.readium.r2.testapp.outline

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import kotlinx.android.synthetic.main.fragment_listview.*
import kotlinx.android.synthetic.main.item_recycle_navigation.view.*
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.PositionsDatabase
import org.readium.r2.testapp.epub.Position

class SyntheticPageListFragment(private val bookId: Long, private val resultKey: String)
    : Fragment(R.layout.fragment_listview) {

    private lateinit var positionsDB: PositionsDatabase

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        positionsDB = PositionsDatabase(requireActivity())

        val syntheticPageList = positionsDB.positions.getSyntheticPageList(bookId)
            ?.let { Position.fromJSON(it) }
            .orEmpty()

        list_view.adapter = SyntheticPageListAdapter(requireActivity(), syntheticPageList.toMutableList())

        list_view.setOnItemClickListener { _, _, position, _ ->

            val page = syntheticPageList[position]
            val pageProgression = syntheticPageList[position].progression

            val locator =  Locator(
                href = page.href ?: "",
                type = page.type ?: "",
                locations = Locator.Locations(progression = pageProgression)
            )

            val bundle = Bundle().apply {
                putParcelable(resultKey, locator)
            }
            setFragmentResult(resultKey, bundle)
        }
    }
}

private class SyntheticPageListAdapter(
    private val activity: Activity,
    private var items: MutableList<Position>
) : BaseAdapter() {

    private inner class ViewHolder(row: View?) {
        var navigationTextView: TextView? = null

        init {
            this.navigationTextView = row?.navigation_textView
        }
    }

    /**
     * Get the data item associated with the specified position in the data set.
     *
     * @param position Position of the item whose data we want within the adapter's
     * data set.
     * @return The data at the specified position.
     */
    override fun getItem(position: Int): Any {
        return items[position]
    }

    /**
     * Get the row id associated with the specified position in the list.
     *
     * @param position The position of the item within the adapter's data set whose row id we want.
     * @return The id of the item at the specified position.
     */
    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    /**
     * How many items are in the data set represented by this Adapter.
     *
     * @return Count of items.
     */
    override fun getCount(): Int {
        return items.size
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val view: View?
        val viewHolder: ViewHolder
        if (convertView == null) {
            val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            view = inflater.inflate(R.layout.item_recycle_navigation, null)
            viewHolder = ViewHolder(view)
            view?.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val item = getItem(position) as Position

        viewHolder.navigationTextView!!.text = "Page ${item.pageNumber}"

        return view as View
    }
}

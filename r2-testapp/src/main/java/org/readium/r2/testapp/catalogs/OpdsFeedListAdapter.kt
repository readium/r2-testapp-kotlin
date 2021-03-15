package org.readium.r2.testapp.catalogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.databinding.DataBindingUtil
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.ItemRecycleOpdsListBinding
import org.readium.r2.testapp.domain.model.OPDS
import org.readium.r2.testapp.utils.singleClick

class OpdsFeedListAdapter(val parent: OpdsFeedListFragment) : ListAdapter<OPDS, OpdsFeedListAdapter.ViewHolder>(OPDSListDiff()) {

    override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
    ): ViewHolder {
        return ViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(parent.context),
                        R.layout.item_recycle_opds_list, parent, false
                )
        )
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val opds = getItem(position)

        viewHolder.bind(opds)
    }

    inner class ViewHolder(private val binding: ItemRecycleOpdsListBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(opds: OPDS) {
            binding.opds = opds
            // TODO decide whether or not to use this or the RecyclerViewClickListener
            binding.button.setOnClickListener {
                val bundle = bundleOf(OPDSFEED to opds)
                Navigation.findNavController(it).navigate(R.id.action_navigation_catalog_list_to_navigation_catalog, bundle)
            }
            binding.button.setOnLongClickListener {
                MaterialAlertDialogBuilder(parent.requireContext())
                        .setTitle(parent.getString(R.string.confirm_delete_opds_title))
                        .setMessage(parent.getString(R.string.confirm_delete_opds_text))
                        .setNegativeButton(parent.getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setPositiveButton(parent.getString(R.string.delete)) { dialog, _ ->

                            dialog.dismiss()
                        }
                        .show()
                true
            }
        }
    }

    companion object {
        const val OPDSFEED = "opdsFeed"
    }

    interface RecyclerViewClickListener {

        //this is method to handle the event when clicked on the image in Recyclerview
        fun recyclerViewListClicked(v: View, position: Int)

        fun recyclerViewListLongClicked(v: View, position: Int)
    }

    private class OPDSListDiff : DiffUtil.ItemCallback<OPDS>() {

        override fun areItemsTheSame(
                oldItem: OPDS,
                newItem: OPDS
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
                oldItem: OPDS,
                newItem: OPDS
        ): Boolean {
            return oldItem.title == newItem.title
                    && oldItem.href == newItem.href
                    && oldItem.type == newItem.type
        }
    }

}
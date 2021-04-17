package org.readium.r2.testapp.catalogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import org.readium.r2.testapp.MainActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.bookshelf.BookshelfFragment
import org.readium.r2.testapp.catalogs.OpdsFeedListAdapter.Companion.OPDSFEED
import org.readium.r2.testapp.domain.model.OPDS
import org.readium.r2.testapp.opds.GridAutoFitLayoutManager


class CatalogFragment : Fragment() {

    private lateinit var catalogViewModel: CatalogViewModel
    private lateinit var catalogListAdapter: CatalogListAdapter
    private lateinit var opds: OPDS

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        ViewModelProvider(this).get(CatalogViewModel::class.java).let { model ->
            model.eventChannel.receive(this) { handleEvent(it) }
            catalogViewModel = model
        }
        opds = arguments?.get(OPDSFEED) as OPDS
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        catalogListAdapter = CatalogListAdapter()
        val progressBar = view.findViewById<ProgressBar>(R.id.catalog_ProgressBar)

        view.findViewById<RecyclerView>(R.id.catalog_DetailList).apply {
            setHasFixedSize(true)
            layoutManager = GridAutoFitLayoutManager(requireContext(), 120)
            adapter = catalogListAdapter
            addItemDecoration(
                BookshelfFragment.VerticalSpaceItemDecoration(
                    10
                )
            )
        }

        (activity as MainActivity).supportActionBar?.title = opds.title

        // TODO this feels hacky, I don't want to parse the file if it has not changed
        if (catalogViewModel.parseData.value == null) {
            progressBar.visibility = View.VISIBLE
            catalogViewModel.parseOpds(opds)
        }
        catalogViewModel.parseData.observe(viewLifecycleOwner, { result ->

            result.feed!!.navigation.forEachIndexed { index, navigation ->
                val button = Button(requireContext())
                button.apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    text = navigation.title
                    setOnClickListener {
                        val opds = OPDS(
                            href = navigation.href,
                            title = navigation.title!!,
                            type = opds.type
                        )
                        val bundle = bundleOf(OPDSFEED to opds)
                        Navigation.findNavController(it)
                            .navigate(R.id.action_navigation_catalog_self, bundle)
                    }
                }
                view.findViewById<LinearLayout>(R.id.catalog_LinearLayout).addView(button, index)
            }

            if (result.feed!!.publications.isNotEmpty()) {
                catalogListAdapter.submitList(result.feed!!.publications)
            }

            //TODO group publications

            for (group in result.feed!!.groups) {
                if (group.navigation.isNotEmpty()) {
                    for (navigation in group.navigation) {
                        val button = Button(requireContext())
                        button.apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            text = navigation.title
                            setOnClickListener {
                                val opds = OPDS(
                                    href = navigation.href,
                                    title = navigation.title!!,
                                    type = opds.type
                                )
                                val bundle = bundleOf(OPDSFEED to opds)
                                Navigation.findNavController(it)
                                    .navigate(R.id.action_navigation_catalog_self, bundle)
                            }
                        }
                        view.findViewById<LinearLayout>(R.id.catalog_LinearLayout).addView(button)
                    }
                }
            }
            progressBar.visibility = View.GONE
        })
    }

    private fun handleEvent(event: CatalogViewModel.Event.FeedEvent) {
        val message =
            when (event) {
                is CatalogViewModel.Event.FeedEvent.OpdsParseFailed -> getString(R.string.failed_parsing_opds)
            }
        Snackbar.make(
            requireView(),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }
}
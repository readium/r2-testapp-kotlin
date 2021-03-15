package org.readium.r2.testapp.catalogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.ui.successUi
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.testapp.MainActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.bookshelf.BookshelfFragment
import org.readium.r2.testapp.catalogs.OpdsFeedListAdapter.Companion.OPDSFEED
import org.readium.r2.testapp.domain.model.OPDS
import org.readium.r2.testapp.opds.GridAutoFitLayoutManager
import org.readium.r2.testapp.opds.OPDSModel
import java.net.MalformedURLException
import java.net.URL


class CatalogFragment : Fragment() {

    private lateinit var mCatalogsViewModel: CatalogsViewModel
    private lateinit var mCatalogListAdapter: CatalogListAdapter
    private var mParsePromise: Promise<ParseData, Exception>? = null
    private lateinit var mOpds: OPDS

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        mCatalogsViewModel =
                ViewModelProvider(this).get(CatalogsViewModel::class.java)
        mOpds = arguments?.get(OPDSFEED) as OPDS
        return inflater.inflate(R.layout.fragment_catalog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mCatalogListAdapter = CatalogListAdapter(this)

        view.findViewById<RecyclerView>(R.id.opdsDetailList).apply {
            setHasFixedSize(false)
            layoutManager = GridAutoFitLayoutManager(requireContext(), 120)
            adapter = mCatalogListAdapter
            addItemDecoration(
                    BookshelfFragment.VerticalSpaceItemDecoration(
                            10
                    )
            )
        }

        mOpds.href.let {
            try {
                mParsePromise = if (mOpds.type == 1) {
                    OPDS1Parser.parseURL(URL(it))
                } else {
                    OPDS2Parser.parseURL(URL(it))
                }
            } catch (e: MalformedURLException) {
                Snackbar.make(requireActivity().findViewById(android.R.id.content),
                        "Failed parsing OPDS", Snackbar.LENGTH_LONG).show()
            }
            (activity as MainActivity).supportActionBar?.title = mOpds.title
        }

        mParsePromise?.successUi { result ->

            result.feed!!.navigation.forEachIndexed { index, navigation ->
                val button = Button(requireContext())
                button.apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT)
                    text = navigation.title
                    setOnClickListener {
                        val model = OPDSModel(navigation.title!!, navigation.href, mOpds.type)
                        //TODO go to catalog fragment
                    }
                }
                view.findViewById<LinearLayout>(R.id.catalogLayout).addView(button, index)
            }

            if (result.feed!!.publications.isNotEmpty()) {
                mCatalogListAdapter.submitList(result.feed!!.publications)
            }

            //TODO group publications

            for (group in result.feed!!.groups) {
                if (group.navigation.isNotEmpty()) {
                    for (navigation in group.navigation) {
                        val button = Button(requireContext())
                        button.apply {
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT)
                            text = navigation.title
                            setOnClickListener {
                                val model = OPDSModel(navigation.title!!, navigation.href, mOpds.type)
                                //TODO go to catalog fragment
                            }
                        }
                        view.findViewById<LinearLayout>(R.id.catalogLayout).addView(button)
                    }
                }
            }
        }
    }
}
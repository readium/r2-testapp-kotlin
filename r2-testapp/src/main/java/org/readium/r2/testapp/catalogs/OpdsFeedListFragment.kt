package org.readium.r2.testapp.catalogs

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kittinunf.fuel.Fuel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import nl.komponents.kovenant.Promise
import nl.komponents.kovenant.then
import nl.komponents.kovenant.ui.successUi
import org.json.JSONObject
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.shared.promise
import org.readium.r2.testapp.R
import org.readium.r2.testapp.domain.model.OPDS
import java.net.URL


class OpdsFeedListFragment : Fragment() {

    private lateinit var mCatalogsViewModel: CatalogsViewModel
    private lateinit var mCatalogsAdapter: OpdsFeedListAdapter

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        mCatalogsViewModel =
                ViewModelProvider(this).get(CatalogsViewModel::class.java)
        return inflater.inflate(R.layout.fragment_opds_feed_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferences = requireContext().getSharedPreferences("org.readium.r2.testapp", Context.MODE_PRIVATE)

        mCatalogsAdapter = OpdsFeedListAdapter(this)

        view.findViewById<RecyclerView>(R.id.opds_list).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = mCatalogsAdapter
            addItemDecoration(
                    VerticalSpaceItemDecoration(
                            10
                    )
            )
        }

        mCatalogsViewModel.opds.observe(viewLifecycleOwner, {
            mCatalogsAdapter.submitList(it)
        })

        val version = 2
        val VERSION_KEY = "OPDS_CATALOG_VERSION"

        if (preferences.getInt(VERSION_KEY, 0) < version) {

            preferences.edit().putInt(VERSION_KEY, version).apply()

            val r2TestCatalog = OPDS(title = "R2 Reader Test Catalog", href = "https://d2g.dita.digital/opds/collections/10040", type = 1)
            val oPDS2Catalog = OPDS(title = "OPDS 2.0 Test Catalog", href = "https://test.opds.io/2.0/home.json", type = 2)
            val oTBCatalog = OPDS(title = "Open Textbooks Catalog", href = "http://open.minitex.org/textbooks/", type = 1)
            val sEBCatalog = OPDS(title = "Standard eBooks Catalog", href = "https://standardebooks.org/opds/all", type = 1)

            mCatalogsViewModel.insertOpds(r2TestCatalog)
            mCatalogsViewModel.insertOpds(oPDS2Catalog)
            mCatalogsViewModel.insertOpds(oTBCatalog)
            mCatalogsViewModel.insertOpds(sEBCatalog)
        }

        view.findViewById<FloatingActionButton>(R.id.addOpds).setOnClickListener {
            val layout = LinearLayout(requireContext())
            layout.orientation = LinearLayout.VERTICAL
            val titleEditText = EditText(requireContext())
            val urlEditText = EditText(requireContext())
            titleEditText.hint = getString(R.string.enter_title)
            urlEditText.hint = getString(R.string.enter_url)
            layout.addView(titleEditText)
            layout.addView(urlEditText)


            MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.add_opds))
                    .setView(layout)
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                        dialog.cancel()
                    }
                    .setPositiveButton(getString(R.string.save)) { _, _ ->
                        val parseData: Promise<ParseData, Exception>?
                        parseData = parseURL(URL(urlEditText.text.toString()))
                        parseData.successUi {
                            val opds = OPDS(
                                    title = titleEditText.text.toString(),
                                    href = urlEditText.text.toString(),
                                    type = it.type)
                            mCatalogsViewModel.insertOpds(opds)
                        }
                    }
                    .show()
        }
    }

    private fun parseURL(url: URL): Promise<ParseData, Exception> {
        return Fuel.get(url.toString(), null).promise() then {
            val (_, _, result) = it
            if (isJson(result)) {
                OPDS2Parser.parse(result, url)
            } else {
                OPDS1Parser.parse(result, url)
            }
        }
    }

    private fun isJson(byteArray: ByteArray): Boolean {
        return try {
            JSONObject(String(byteArray))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deleteOpdsModel(opdsModelId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            mCatalogsViewModel.deleteOpds(opdsModelId)
        }
    }

    class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView,
                                    state: androidx.recyclerview.widget.RecyclerView.State) {
            outRect.bottom = verticalSpaceHeight
        }
    }
}
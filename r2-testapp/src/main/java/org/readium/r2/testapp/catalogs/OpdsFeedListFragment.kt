package org.readium.r2.testapp.catalogs

import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
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
import nl.komponents.kovenant.ui.failUi
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

    private lateinit var mCatalogViewModel: CatalogViewModel
    private lateinit var mCatalogsAdapter: OpdsFeedListAdapter

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        mCatalogViewModel =
                ViewModelProvider(this).get(CatalogViewModel::class.java)
        return inflater.inflate(R.layout.fragment_opds_feed_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val preferences = requireContext().getSharedPreferences("org.readium.r2.testapp", Context.MODE_PRIVATE)

        mCatalogsAdapter = OpdsFeedListAdapter(onLongClick = { opds -> onLongClick(opds) })

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

        mCatalogViewModel.opds.observe(viewLifecycleOwner, {
            mCatalogsAdapter.submitList(it)
        })

        val version = 2
        val VERSION_KEY = "OPDS_CATALOG_VERSION"

        if (preferences.getInt(VERSION_KEY, 0) < version) {

            preferences.edit().putInt(VERSION_KEY, version).apply()

            val oPDS2Catalog = OPDS(title = "OPDS 2.0 Test Catalog", href = "https://test.opds.io/2.0/home.json", type = 2)
            val oTBCatalog = OPDS(title = "Open Textbooks Catalog", href = "http://open.minitex.org/textbooks/", type = 1)
            val sEBCatalog = OPDS(title = "Standard eBooks Catalog", href = "https://standardebooks.org/opds/all", type = 1)

            mCatalogViewModel.insertOpds(oPDS2Catalog)
            mCatalogViewModel.insertOpds(oTBCatalog)
            mCatalogViewModel.insertOpds(sEBCatalog)
        }

        view.findViewById<FloatingActionButton>(R.id.addOpds).setOnClickListener {
            val alertDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.add_opds))
                    .setView(R.layout.add_opds_dialog)
                    .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                        dialog.cancel()
                    }
                    .setPositiveButton(getString(R.string.save), null)
                    .show()
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = alertDialog.findViewById<EditText>(R.id.opdsTitle)
                val url = alertDialog.findViewById<EditText>(R.id.opdsUrl)
                if (TextUtils.isEmpty(title?.text)) {
                    title?.error = getString(R.string.invalid_title)
                } else if (TextUtils.isEmpty(url?.text)) {
                    url?.error = getString(R.string.invalid_url)
                } else if (!URLUtil.isValidUrl(url?.text.toString())) {
                    url?.error = getString(R.string.invalid_url)
                } else {
                    val parseData: Promise<ParseData, Exception>?
                    parseData = parseURL(URL(url?.text.toString()))
                    parseData.successUi { data ->
                        val opds = OPDS(
                                title = title?.text.toString(),
                                href = url?.text.toString(),
                                type = data.type)
                        mCatalogViewModel.insertOpds(opds)
                    }
                    parseData.failUi {

                    }
                    alertDialog.dismiss()
                }
            }
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

    private fun deleteOpdsModel(opdsModelId: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            mCatalogViewModel.deleteOpds(opdsModelId)
        }
    }

    private fun onLongClick(opds: OPDS) {
        MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.confirm_delete_opds_title))
                .setMessage(getString(R.string.confirm_delete_opds_text))
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                    opds.id?.let { deleteOpdsModel(it) }
                    dialog.dismiss()
                }
                .show()
    }

    class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                                    state: RecyclerView.State) {
            outRect.bottom = verticalSpaceHeight
        }
    }
}
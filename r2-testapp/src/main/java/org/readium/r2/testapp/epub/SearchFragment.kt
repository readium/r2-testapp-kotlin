package org.readium.r2.testapp.epub

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_epub.*
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.search.MarkJSSearchEngine
import org.readium.r2.testapp.search.SearchLocatorAdapter
import kotlinx.android.synthetic.main.fragment_search.*
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.testapp.BuildConfig.DEBUG
import timber.log.Timber

class SearchFragment : Fragment(R.layout.fragment_search) {

    private val activity: EpubActivity
        get() = requireActivity() as EpubActivity

    private val publication: Publication
        get() = activity.publication

    private val bookId: Long
        get() = activity.bookId

    private val preferences: SharedPreferences
        get() = activity.preferences

    private val epubNavigator: EpubNavigatorFragment
        get() = activity.main_content as EpubNavigatorFragment

    private val menuSearch: MenuItem
        get() = activity.menuSearch

    private val searchView: SearchView
        get() = menuSearch.actionView as SearchView

    private var searchTerm = ""
    private lateinit var searchStorage: SharedPreferences
    private lateinit var searchResultAdapter: SearchLocatorAdapter
    private lateinit var searchResult: MutableList<Locator>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchStorage = activity.getSharedPreferences("org.readium.r2.search", Context.MODE_PRIVATE)
        searchResult = mutableListOf()
        searchResultAdapter =
            SearchLocatorAdapter(activity, searchResult, object : SearchLocatorAdapter.RecyclerViewClickListener {
                override fun recyclerViewListClicked(v: View, position: Int) {
                    searchView.clearFocus()
                    if (searchView.isShown) {
                        menuSearch.collapseActionView()
                        epubNavigator.resourcePager.offscreenPageLimit = 1
                    }

                    val locator = searchResult[position]
                    onSearchResult(locator)
                }

            })
        search_listView.adapter = searchResultAdapter
        search_listView.layoutManager = LinearLayoutManager(activity)


        val searchView = menuSearch.actionView as SearchView

        searchView.isFocusable = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean {

                searchResult.clear()
                searchResultAdapter.notifyDataSetChanged()

                query?.let {
                    epubNavigator.resourcePager.offscreenPageLimit = publication.readingOrder.size

                    //Saving searched term
                    searchTerm = query
                    //Initializing our custom search interfaces
                    val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_searching_book))
                    progress.show()

                    val markJSSearchInterface = MarkJSSearchEngine(activity)
                    Handler().postDelayed({
                        markJSSearchInterface.search(query) { (last, result) ->
                            searchResult.clear()
                            searchResult.addAll(result)
                            searchResultAdapter.notifyDataSetChanged()

                            //Saving results + keyword only when JS is fully executed on all resources
                            val editor = searchStorage.edit()
                            val stringResults = Gson().toJson(result)
                            editor.putString("result", stringResults)
                            editor.putString("term", searchTerm)
                            editor.putLong("book", bookId)
                            editor.apply()

                            if (last) {
                                progress.dismiss()
                            }
                        }
                    }, 500)


                }
                return false
            }

            override fun onQueryTextChange(s: String): Boolean {
                return false
            }
        })

        searchView.setOnSearchClickListener {
            val previouslySearchBook = searchStorage.getLong("book", -1)
            if (previouslySearchBook == bookId) {
                //Loading previous results + keyword

                val keyword = searchStorage.getString("term", null) ?: ""
                searchView.setQuery(keyword, false)

                searchStorage.getString("result", null)?.let {
                    searchResult.clear()
                    searchResult.addAll(Gson().fromJson(it, Array<Locator>::class.java))
                    searchResultAdapter.notifyDataSetChanged()
                    searchView.clearFocus()
                }
            }
            epubNavigator.resourcePager.offscreenPageLimit = publication.readingOrder.size
        }

        val closeButton = searchView.findViewById(R.id.search_close_btn) as ImageView
        closeButton.setOnClickListener {
            searchResult.clear()
            searchResultAdapter.notifyDataSetChanged()
            searchView.setQuery("", false)

            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(
                InputMethodManager.SHOW_FORCED,
                InputMethodManager.HIDE_IMPLICIT_ONLY
            )

            val editor = searchStorage.edit()
            editor.remove("result")
            editor.remove("term")
            editor.apply()
        }
    }

    private fun onSearchResult(locator: Locator) {
        locator.locations.fragments.firstOrNull()?.let { fragment ->
            val fragments = fragment.split(",")
                .map { it.split("=") }
                .filter { it.size == 2 }
                .associate { it[0] to it[1] }

            val index = fragments["i"]?.toInt()
            if (index != null) {
                val searchStorage = activity.getSharedPreferences("org.readium.r2.search", Context.MODE_PRIVATE)
                Handler().postDelayed({
                    if (publication.metadata.presentation.layout == EpubLayout.REFLOWABLE) {
                        val currentFragment = (epubNavigator.resourcePager.adapter as R2PagerAdapter).getCurrentFragment() as R2EpubPageFragment
                        val resource = publication.readingOrder[epubNavigator.resourcePager.currentItem]
                        val resourceHref = resource.href
                        val resourceType = resource.type ?: ""
                        val resourceTitle = resource.title ?: ""

                        currentFragment.webView?.runJavaScript("markSearch('${searchStorage.getString("term", null)}', null, '$resourceHref', '$resourceType', '$resourceTitle', '$index')") { result ->
                            if (DEBUG) Timber.d("###### $result")

                        }
                    }
                }, 1200)
            }
        }
    }
}
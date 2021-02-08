package org.readium.r2.testapp.reader

import android.content.Context
import android.graphics.PointF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import androidx.appcompat.widget.SearchView
import androidx.core.content.edit
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.testapp.BuildConfig
import org.readium.r2.testapp.R
import org.readium.r2.testapp.epub.EpubActivity
import org.readium.r2.testapp.epub.ScreenReaderFragment
import org.readium.r2.testapp.epub.SearchFragment
import org.readium.r2.testapp.epub.SearchViewModel
import org.readium.r2.testapp.search.MarkJSSearchEngine
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.extensions.toggleSystemUi
import timber.log.Timber

class EpubReaderFragment : AbstractReaderFragment(), EpubNavigatorFragment.Listener {

    override lateinit var publication: Publication
    override lateinit var persistence: BookData
    override lateinit var navigator: Navigator
    lateinit var navigatorFragment: EpubNavigatorFragment

    private var isScreenReaderVisible = false
    private lateinit var menuScreenReader: MenuItem

    private lateinit var menuSearch: MenuItem
    lateinit var menuSearchView: SearchView
    lateinit var searchResult: MutableLiveData<List<Locator>>

    private val activity: EpubActivity
        get() = requireActivity() as EpubActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java).let {
            publication = it.publication
            persistence = it.persistence
        }

        val baseUrl = checkNotNull(requireArguments().getString(BASE_URL_ARG))

        childFragmentManager.fragmentFactory = CompositeFragmentFactory(
            EpubNavigatorFragment.createFactory(publication, baseUrl, persistence.savedLocation, this)
        )

        childFragmentManager.setFragmentResultListener(
            SearchFragment::class.java.name,
            this,
            FragmentResultListener { _, result ->
                val locator = result.getParcelable<Locator>(SearchFragment::class.java.name)!!
                closeSearchFragment(locator)
            }
        )

        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val navigatorFragmentTag = getString(R.string.epub_navigator_tag)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.fragment_reader_container, EpubNavigatorFragment::class.java,  Bundle(), navigatorFragmentTag)
                .commitNow()
        }

        navigator = childFragmentManager.findFragmentByTag(navigatorFragmentTag) as Navigator
        navigatorFragment = navigator as EpubNavigatorFragment
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, menuInflater)
        menuInflater.inflate(R.menu.menu_epub2, menu)

        menuScreenReader = menu.findItem(R.id.screen_reader)
        menuSearch = menu.findItem(R.id.search)
        menuSearchView = menuSearch.actionView as SearchView

        ViewModelProvider(this).get(SearchViewModel::class.java).let {
            searchResult = it.result
        }

        menuSearch.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {

            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                showSearchFragment()
                // Try to preload all resources once the SearchView has been expanded
                // Delay to allow the keyboard to be shown immediately
                Handler(Looper.getMainLooper()).postDelayed({
                    navigatorFragment.resourcePager.offscreenPageLimit = publication.readingOrder.size
                }, 100)
                menuSearchView.clearFocus()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                childFragmentManager.popBackStack()
                childFragmentManager.executePendingTransactions()
                Handler(Looper.getMainLooper()).postDelayed({
                    navigatorFragment.resourcePager.offscreenPageLimit = 1
                }, 100)
                menuSearchView.clearFocus()
                return true
            }
        })

        prepareSearchView()
    }

    private fun prepareSearchView() {
        val searchStorage = requireActivity().getSharedPreferences("org.readium.r2.search", Context.MODE_PRIVATE)
        val markJSSearchInterface = MarkJSSearchEngine(activity)
        val bookId = checkNotNull(requireArguments().getLong(BOOK_ID_ARG))

        menuSearchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String): Boolean {
                val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_searching_book)).apply {
                    show()
                }

                // Ensure all resources are loaded
                navigatorFragment.resourcePager.offscreenPageLimit = publication.readingOrder.size

                searchResult.value = emptyList()
                Handler(Looper.getMainLooper()).postDelayed({
                    markJSSearchInterface.search(query) { (last, result) ->
                        searchResult.value = result
                        progress.dismiss()

                        if (last) {
                            // Save query and result
                            val stringResults = Gson().toJson(result)
                            searchStorage.edit {
                                putString("result", stringResults)
                                putString("term", query)
                                putLong("book", bookId)
                            }
                        }
                    }
                }, 500)
                menuSearchView.clearFocus()

                return false
            }

            override fun onQueryTextChange(s: String): Boolean {
                return false
            }
        })

        menuSearchView.setOnSearchClickListener {
            val previouslySearchBook = searchStorage.getLong("book", -1)
            if (previouslySearchBook == bookId) {
                // Load previous research
                searchStorage.getString("term", null)?.let {
                    menuSearchView.setQuery(it, false)
                }
                // Load previous result and give up focus
                searchStorage.getString("result", null)?.let {
                    searchResult.value = (Gson().fromJson(it, Array<Locator>::class.java)).toList()
                    menuSearchView.clearFocus()
                }
            }
        }

        val closeButton = menuSearchView.findViewById(R.id.search_close_btn) as ImageView
        closeButton.setOnClickListener {
            menuSearchView.requestFocus()
            searchResult.value = emptyList()
            menuSearchView.setQuery("", false)

            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(
                InputMethodManager.SHOW_FORCED,
                InputMethodManager.HIDE_IMPLICIT_ONLY
            )

           searchStorage.edit {
                remove("result")
                remove("term")
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }

       return when (item.itemId) {
           R.id.settings -> {
               activity.userSettings.userSettingsPopUp().showAsDropDown(requireView().findViewById(R.id.settings), 0, 0, Gravity.END)
               true
           }
           R.id.search -> {
               super.onOptionsItemSelected(item)
           }

           android.R.id.home -> {
               menuSearch.collapseActionView()
               true
           }

           R.id.screen_reader -> {
               if (isScreenReaderVisible) {
                   closeScreenReaderFragment()
               } else {
                   showScreenReaderFragment()
               }
               true
           }
            else -> false
        }
    }

    override fun onTap(point: PointF): Boolean {
        val viewWidth = requireView().width
        val leftRange = 0.0..(0.2 * viewWidth)

        when {
            leftRange.contains(point.x) -> navigator.goBackward(animated = true)
            leftRange.contains(viewWidth - point.x) -> navigator.goForward(animated = true)
            else -> requireActivity().toggleSystemUi()
        }

        return true
    }

    private fun showSearchFragment() {
        childFragmentManager.beginTransaction()
            .add(R.id.fragment_supp_container, SearchFragment::class.java, Bundle(), SEARCH_FRAGMENT_TAG)
            .hide(navigatorFragment)
            .addToBackStack(SEARCH_FRAGMENT_TAG)
            .commit()
        childFragmentManager.executePendingTransactions()
    }

    private fun closeSearchFragment(locator: Locator) {
        menuSearch.collapseActionView()
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
                        val currentFragment = (navigatorFragment.resourcePager.adapter as R2PagerAdapter).getCurrentFragment() as R2EpubPageFragment
                        val resource = publication.readingOrder[navigatorFragment.resourcePager.currentItem]
                        val resourceHref = resource.href
                        val resourceType = resource.type ?: ""
                        val resourceTitle = resource.title ?: ""

                        currentFragment.webView?.runJavaScript("markSearch('${searchStorage.getString("term", null)}', null, '$resourceHref', '$resourceType', '$resourceTitle', '$index')") { result ->
                            if (BuildConfig.DEBUG) Timber.d("###### $result")

                        }
                    }
                }, 1200)
            }
        }
    }

    private fun showScreenReaderFragment() {
        menuScreenReader.title = resources.getString(R.string.epubactivity_read_aloud_stop)
        isScreenReaderVisible = true
        childFragmentManager.beginTransaction().apply {
            replace(R.id.fragment_supp_container, ScreenReaderFragment::class.java, Bundle())
            hide(navigatorFragment)
            addToBackStack(null)
            commit()
        }
    }

    private fun closeScreenReaderFragment() {
        menuScreenReader.title = resources.getString(R.string.epubactivity_read_aloud_start)
        isScreenReaderVisible = false
        childFragmentManager.popBackStack()
    }

    companion object {

        private const val BASE_URL_ARG = "baseUrl"
        private const val BOOK_ID_ARG = "bookId"

        private const val SEARCH_FRAGMENT_TAG = "search"

        fun newInstance(baseUrl: String, bookId: Long): EpubReaderFragment {
            return EpubReaderFragment().apply {
                arguments = Bundle().apply {
                    putString(BASE_URL_ARG, baseUrl)
                    putLong(BOOK_ID_ARG, bookId)
                }
            }
        }
    }
}
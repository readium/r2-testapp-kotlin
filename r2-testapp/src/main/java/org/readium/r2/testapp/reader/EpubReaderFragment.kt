package org.readium.r2.testapp.reader

import android.graphics.PointF
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.epub.EpubReaderActivity
import org.readium.r2.testapp.epub.SearchFragment
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.extensions.toggleSystemUi

class EpubReaderFragment : AbstractReaderFragment(), EpubNavigatorFragment.Listener {

    override lateinit var publication: Publication
    override lateinit var persistence: BookData
    override lateinit var navigator: Navigator
    private lateinit var navigatorFragment: EpubNavigatorFragment

    private val activity: EpubReaderActivity
        get() = requireActivity() as EpubReaderActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java).let {
            publication = it.publication
            persistence = it.persistence
        }

        val baseUrl = requireArguments().getString(BASE_URL_ARG)!!

        childFragmentManager.fragmentFactory = CompositeFragmentFactory(
            EpubNavigatorFragment.createFactory(publication, baseUrl, persistence.savedLocation, this)
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

        menu.findItem(R.id.search).setOnActionExpandListener(object : MenuItem.OnActionExpandListener {

            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                showSearchFragment()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                childFragmentManager.popBackStack()
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }

       return when (item.itemId) {
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
            .add(R.id.fragment_supp_container, SearchFragment::class.java, Bundle())
            .hide(navigatorFragment)
            .addToBackStack(null)
            .commit()

        navigatorFragment.resourcePager.offscreenPageLimit = publication.readingOrder.size
    }

    companion object {

        private const val BASE_URL_ARG = "baseUrl"

        fun newInstance(baseUrl: String): EpubReaderFragment {
            return EpubReaderFragment().apply {
                arguments = Bundle().apply {
                    putString("baseUrl", baseUrl)
                }
            }
        }
    }
}
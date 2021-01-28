package org.readium.r2.testapp.reader

import android.graphics.PointF
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.jetbrains.anko.support.v4.toast
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.image.ImageNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.testapp.R
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.extensions.hideSystemUi
import org.readium.r2.testapp.utils.extensions.toggleSystemUi

class ReaderFragment : Fragment(R.layout.fragment_reader), ImageNavigatorFragment.Listener {

    private lateinit var publication: Publication
    private lateinit var persistence: BookData
    private lateinit var navigator: Navigator
    private lateinit var fragment: Fragment
    private lateinit var navigation: ReaderNavigation

    override fun onCreate(savedInstanceState: Bundle?) {
        val activity = requireActivity()
        navigation = activity as ReaderNavigation

        val readerModel = ViewModelProvider(activity).get(ReaderViewModel::class.java)
        publication = readerModel.publication
        persistence = readerModel.persistence

        childFragmentManager.fragmentFactory = CompositeFragmentFactory(
            ImageNavigatorFragment.createFactory(publication, persistence.savedLocation, this)
        )

        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)

        if (savedInstanceState?.getBoolean(IS_VISIBLE_KEY) != false)
            requireActivity().hideSystemUi()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.reader_main_content, ImageNavigatorFragment::class.java,  Bundle(), NAVIGATOR_FRAGMENT_TAG)
                .commitNow()
        }

        fragment = childFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG)!!
        navigator = fragment as Navigator
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_VISIBLE_KEY, isVisible)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        setMenuVisibility(!hidden)
        requireActivity().invalidateOptionsMenu()
        if (!hidden) {
            requireActivity().hideSystemUi()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_reader, menu)
        menu.findItem(R.id.drm).isVisible = publication.isProtected
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.toc -> {
                navigation.showOutlineFragment()
                true
            }
            R.id.bookmark -> {
                val added = persistence.addBookmark(navigator.currentLocator.value)
                toast(if (added) "Bookmark added" else "Bookmark already exists")
                true
            }
            R.id.drm -> {
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

    fun go(locator: Locator, animated: Boolean) =
        navigator.go(locator, animated)

    companion object {
        private const val NAVIGATOR_FRAGMENT_TAG = "navigator"
        private const val IS_VISIBLE_KEY = "isVisible"
    }
}
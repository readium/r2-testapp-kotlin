package org.readium.r2.testapp.reader

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import org.jetbrains.anko.support.v4.toast
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.testapp.R
import org.readium.r2.testapp.utils.extensions.hideSystemUi

abstract class AbstractReaderFragment : Fragment(R.layout.fragment_reader) {

    private lateinit var navigation: ReaderNavigation
    protected abstract var publication: Publication
    protected abstract var persistence: BookData
    protected abstract var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        navigation = requireActivity() as ReaderNavigation
        setHasOptionsMenu(true)
        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState?.getBoolean(IS_VISIBLE_KEY) != false)
            requireActivity().hideSystemUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(IS_VISIBLE_KEY, isVisible)
    }

    override fun onStop() {
        super.onStop()
        persistence.savedLocation = navigator.currentLocator.value
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
                navigation.showDrmManagementFragment()
                true
            }
            else -> false
        }
    }

    fun go(locator: Locator, animated: Boolean) =
        navigator.go(locator, animated)

    companion object {
        private const val IS_VISIBLE_KEY = "isVisible"
    }
}
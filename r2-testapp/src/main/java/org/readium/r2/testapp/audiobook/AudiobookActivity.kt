package org.readium.r2.testapp.audiobook

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import kotlinx.coroutines.launch
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.toast
import org.readium.r2.navigator.NavigatorDelegate
import org.readium.r2.navigator.audiobook.R2AudiobookActivity
import org.readium.r2.shared.extensions.getPublication
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.testapp.DRMManagementActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.Bookmark
import org.readium.r2.testapp.db.BookmarksDatabase
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.library.activitiesLaunched
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.utils.CompositeFragmentFactory

class AudiobookActivity : R2AudiobookActivity(), NavigatorDelegate {

    private lateinit var bookmarksDB: BookmarksDatabase

    private lateinit var menuDrm: MenuItem
    private lateinit var menuToc: MenuItem
    private lateinit var menuBmk: MenuItem
    private lateinit var menuSettings: MenuItem

    private lateinit var persistence: BookData

    private val navigatorFragment: Fragment
        get() = supportFragmentManager.findFragmentByTag("audio_navigator") as AudioNavigatorFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        if (activitiesLaunched.incrementAndGet() > 1 || !LibraryActivity.isServerStarted) {
            finish()
        }

        // The FragmentFactory must be set before the call to super.onCreate
        // Let us get publication before R2AudiobookNavigator
        publication = intent.getPublication(this)
        val bookId = intent.getLongExtra("bookId", -1)
        persistence = BookData(applicationContext, bookId, publication)

        supportFragmentManager.fragmentFactory = CompositeFragmentFactory(
            AudioNavigatorFragment.createFactory(publication, bookId,  intent.getByteArrayExtra("cover")),
            OutlineFragment.createFactory(publication, persistence, OutlineFragment::class.java.name)
        )

        supportFragmentManager.setFragmentResultListener(
            OutlineFragment::class.java.name,
            this,
            FragmentResultListener { _, result -> closeOutlineFragment(result)}
        )

        super.onCreate(savedInstanceState)

        bookmarksDB = BookmarksDatabase(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_audio, menu)
        menuToc = menu.findItem(R.id.toc)
        menuBmk = menu.findItem(R.id.bookmark)

        menuDrm = menu.findItem(R.id.drm)
        menuDrm.isVisible = publication.isProtected

        menuSettings = menu.findItem(R.id.settings)
        menuSettings.isVisible = false

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.toc -> {
                showOutlineFragment()
                return true
            }
            R.id.settings -> {
                // TODO do we need any settings ?
                return true
            }
            R.id.bookmark -> {
                addBookmark()
                return true
            }
            R.id.drm -> {
                startActivityForResult(intentFor<DRMManagementActivity>("publication" to publicationPath), 1)
                return true
            }

            else -> return false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activitiesLaunched.getAndDecrement()
    }

    private fun addBookmark() {
        val locator = currentLocator.value

        val bookmark = Bookmark(bookId, publicationIdentifier, resourceIndex = currentResource.toLong(), locator = locator)
        val msg = if (bookmarksDB.bookmarks.insert(bookmark) != null) {
            "Bookmark added"
        } else {
            "Bookmark already exists"
        }

        launch {
            toast(msg)
        }
    }

    private fun showOutlineFragment() {
        val outlineFragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            OutlineFragment::class.java.name
        )

        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            hide(navigatorFragment)
            add(R.id.main_content, outlineFragment)
            addToBackStack(null)
            commit()
        }
    }

    private fun closeOutlineFragment(result: Bundle) {
        val locator = result.getParcelable(OutlineFragment::class.java.name) as? Locator
        go(locator!!)
        supportFragmentManager.popBackStack()
    }
}





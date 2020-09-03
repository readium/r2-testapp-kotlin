package org.readium.r2.testapp.audiobook

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.jetbrains.anko.toast
import org.readium.r2.navigator.MediaNavigator
import org.readium.r2.navigator.NavigatorDelegate
import org.readium.r2.navigator.audio.AudioNavigatorFragment
import org.readium.r2.navigator.media.MediaService
import org.readium.r2.shared.AudioSupport
import org.readium.r2.shared.FragmentNavigator
import org.readium.r2.shared.extensions.getPublicationOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.Bookmark
import org.readium.r2.testapp.db.BookmarksDatabase
import org.readium.r2.testapp.db.BooksDatabase
import org.readium.r2.testapp.outline.R2OutlineActivity

@OptIn(AudioSupport::class, FragmentNavigator::class)
class AudiobookActivity : AppCompatActivity(), NavigatorDelegate {

    private var bookId: Long = -1
    private lateinit var booksDB: BooksDatabase
    private lateinit var bookmarksDB: BookmarksDatabase

    private lateinit var outlineLauncher: ActivityResultLauncher<R2OutlineActivity.Contract.Input>

    private var menuDrm: MenuItem? = null
    private var menuToc: MenuItem? = null
    private var menuBmk: MenuItem? = null
    private var menuSettings: MenuItem? = null

    private val mediaService by lazy { MediaService.connect(this, AudiobookService::class.java) }

    val navigator: MediaNavigator
        get() = supportFragmentManager.findFragmentById(org.readium.r2.navigator.R.id.audio_navigator) as MediaNavigator

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        booksDB = BooksDatabase(this)
        bookmarksDB = BookmarksDatabase(this)
        bookId = intent.getLongExtra(EXTRA_BOOK_ID, -1)

        val publication = intent.getPublicationOrNull(this)
        val initialLocator = intent.getParcelableExtra(EXTRA_LOCATOR) as? Locator

        val mediaNavigator =
            if (publication != null && bookId != -1L) mediaService.getNavigator(publication, bookId.toString(), initialLocator)
            else mediaService.currentNavigator.value
                ?: run {
                    finish()
                    return
                }

        bookId = mediaNavigator.publicationId.toLongOrNull() ?: bookId

        mediaNavigator.play()

        supportFragmentManager.fragmentFactory = AudioNavigatorFragment.Factory(mediaNavigator)

        super.onCreate(savedInstanceState)

        setContentView(org.readium.r2.navigator.R.layout.r2_audio_activity)

        menuDrm?.isVisible = navigator.publication.isProtected

        outlineLauncher = registerForActivityResult(R2OutlineActivity.Contract()) { locator: Locator? ->
            if (locator != null) {
                navigator.go(locator)
            }
        }
    }

    override fun onDestroy() {
//        navigator.stop()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_audio, menu)
        menuDrm = menu?.findItem(R.id.drm)
        menuToc = menu?.findItem(R.id.toc)
        menuBmk = menu?.findItem(R.id.bookmark)
        menuSettings = menu?.findItem(R.id.settings)
        menuSettings?.isVisible = false
        menuDrm?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.toc -> {
                outlineLauncher.launch(R2OutlineActivity.Contract.Input(
                    publication = navigator.publication,
                    bookId = bookId
                ))
                return true
            }
            R.id.bookmark -> {
                val locator = navigator.currentLocator.value ?:
                     return true

                val bookmark = Bookmark(bookId, navigator.publication.metadata.identifier ?: bookId.toString(), resourceIndex = 0, locator = locator)
                if (bookmarksDB.bookmarks.insert(bookmark) != null) {
                    toast("Bookmark added")
                } else {
                    toast("Bookmark already exists")
                }

                return true
            }

            else -> return false
        }

    }

    companion object {

        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_LOCATOR = "locator"

    }

}

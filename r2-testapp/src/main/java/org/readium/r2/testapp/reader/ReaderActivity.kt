package org.readium.r2.testapp.reader

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentResultListener
import androidx.fragment.app.replace
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.allAreBitmap
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.testapp.R
import org.readium.r2.testapp.drm.DrmManagementContract
import org.readium.r2.testapp.drm.DrmManagementFragment
import org.readium.r2.testapp.outline.OutlineContract
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.NavigatorContract
import java.lang.IllegalArgumentException

class ReaderActivity : AppCompatActivity(R.layout.activity_reader), ReaderNavigation {

    private lateinit var modelFactory: ReaderViewModel.Factory
    private lateinit var readerFragment: AbstractReaderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        val inputData = NavigatorContract.parseIntent(this)
        val publication = inputData.publication
        val bookId = inputData.bookId
        val persistence = BookData(applicationContext, bookId, publication)

        title = publication.metadata.title
        modelFactory = ReaderViewModel.Factory(publication, persistence)

        supportFragmentManager.setFragmentResultListener(
            OutlineContract.REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                val locator = OutlineContract.parseResult(result).destination
                closeOutlineFragment(locator)
            }
        )

        supportFragmentManager.setFragmentResultListener(
            DrmManagementContract.REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                if (DrmManagementContract.parseResult(result).hasReturned)
                    finish()
            }
        )

        super.onCreate(savedInstanceState)

        val readerClass: Class<out Fragment> =
            if (publication.readingOrder.all { it.mediaType == MediaType.PDF })
                PdfReaderFragment::class.java
            else if (publication.readingOrder.allAreBitmap)
                ImageReaderFragment::class.java
            else
                throw IllegalArgumentException("Cannot render publication")

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.activity_reader_container, readerClass, Bundle(), READER_FRAGMENT_TAG)
                .commitNow()
        }

        readerFragment = supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as AbstractReaderFragment
    }

    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        return modelFactory
    }

    override fun finish() {
        setResult(Activity.RESULT_OK, intent)
        super.finish()
    }

    override fun showOutlineFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_supp_container, OutlineFragment::class.java, Bundle(), OUTLINE_FRAGMENT_TAG)
            .hide(readerFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun closeOutlineFragment(locator: Locator) {
        readerFragment.go(locator, true)
        supportFragmentManager.popBackStack()
    }

    override fun showDrmManagementFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_supp_container, DrmManagementFragment::class.java, Bundle(), DRM_FRAGMENT_TAG)
            .hide(readerFragment)
            .addToBackStack(null)
            .commit()
    }

    companion object {
        const val READER_FRAGMENT_TAG = "reader"
        const val OUTLINE_FRAGMENT_TAG = "outline"
        const val DRM_FRAGMENT_TAG = "drm"
    }
}

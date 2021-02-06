package org.readium.r2.testapp.epub

import android.app.Activity
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.reader.EpubReaderFragment
import org.readium.r2.testapp.reader.ReaderActivity
import org.readium.r2.testapp.reader.ReaderNavigation
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.NavigatorContract

class EpubReaderActivity : AppCompatActivity(R.layout.activity_reader), ReaderNavigation {

    private lateinit var modelFactory: ReaderViewModel.Factory
    private lateinit var readerFragment: EpubReaderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        val inputData = NavigatorContract.parseIntent(this)
        val publication = inputData.publication
        val bookId = inputData.bookId
        val baseUrl = requireNotNull(inputData.baseUrl)
        val persistence = BookData(applicationContext, bookId, publication)

        title = publication.metadata.title
        modelFactory = ReaderViewModel.Factory(publication, persistence)

        supportFragmentManager.fragmentFactory = CompositeFragmentFactory(
            OutlineFragment.createFactory(publication, persistence, ReaderNavigation.OUTLINE_REQUEST_KEY)
        )

        supportFragmentManager.setFragmentResultListener(
            ReaderNavigation.OUTLINE_REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                val locator = result.getParcelable<Locator>(OutlineFragment::class.java.name)!!
                closeOutlineFragment(locator)
            }
        )

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            readerFragment = EpubReaderFragment.newInstance(baseUrl)

            supportFragmentManager.beginTransaction()
                .replace(R.id.activity_reader_container, readerFragment, READER_FRAGMENT_TAG)
                .commitNow()
        } else {
            readerFragment = supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as EpubReaderFragment
        }
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

    companion object {
        const val READER_FRAGMENT_TAG = "reader"
        const val OUTLINE_FRAGMENT_TAG = "outline"
    }
}

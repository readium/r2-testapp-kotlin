package org.readium.r2.testapp.audiobook

import android.os.Bundle
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.navigator.audiobook.R2AudiobookActivity
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.outline.OutlineContract
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.reader.ReaderActivity
import org.readium.r2.testapp.reader.ReaderNavigation
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.NavigatorContract

class AudiobookActivity : R2AudiobookActivity(), ReaderNavigation {

    private lateinit var modelFactory: ReaderViewModel.Factory
    private lateinit var readerFragment: AudioNavigatorFragment

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
                val locator = OutlineContract.parseResult(result)
                closeOutlineFragment(locator)
            }
        )

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.activity_reader_container, AudioNavigatorFragment::class.java, Bundle(), ReaderActivity.READER_FRAGMENT_TAG)
                .commitNow()
        }

        readerFragment = supportFragmentManager.findFragmentByTag(ReaderActivity.READER_FRAGMENT_TAG) as AudioNavigatorFragment
    }

    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        return modelFactory
    }

    override fun showOutlineFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.activity_supp_container, OutlineFragment::class.java, Bundle(), ReaderActivity.OUTLINE_FRAGMENT_TAG)
            .hide(readerFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun closeOutlineFragment(locator: Locator) {
        go(locator)
        supportFragmentManager.popBackStack()
    }
}





package org.readium.r2.testapp.reader

import android.app.Activity
import android.os.Bundle
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.NavigatorContract

class ReaderActivity : AppCompatActivity(R.layout.activity_reader), ReaderNavigation {

    private lateinit var modelFactory: ReaderViewModel.Factory
    private lateinit var readerFragment: ReaderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        /*requestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)
        supportRequestWindowFeature(Window.FEATURE_ACTION_BAR_OVERLAY)*/
        val inputData = NavigatorContract.parseIntent(this)
        val publication = inputData.publication
        val bookId = inputData.bookId
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

        readerFragment = supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as ReaderFragment
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
            .replace(R.id.supp_container, OutlineFragment::class.java, Bundle(), OUTLINE_FRAGMENT_TAG)
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

package org.readium.r2.testapp.audiobook

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.navigator.audiobook.R2AudiobookActivity
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.drm.DrmManagementContract
import org.readium.r2.testapp.drm.DrmManagementFragment
import org.readium.r2.testapp.outline.OutlineContract
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.reader.AudioReaderFragment
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.reader.ReaderActivity
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.reader.ReaderContract

class AudiobookActivity : R2AudiobookActivity() {

    private lateinit var modelFactory: ReaderViewModel.Factory
    private lateinit var readerFragment: AudioReaderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        val inputData = ReaderContract.parseIntent(this)
        val publication = inputData.publication

        modelFactory = ReaderViewModel.Factory(applicationContext, inputData)
        super.onCreate(savedInstanceState)

        ViewModelProvider(this).get(ReaderViewModel::class.java)
            .channel.receive(this) {
                when(it) {
                    is ReaderViewModel.Event.OpenOutlineRequested -> showOutlineFragment()
                    is ReaderViewModel.Event.OpenDrmManagementRequested -> showDrmManagementFragment()
                }
            }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.activity_container, AudioReaderFragment::class.java, Bundle(), ReaderActivity.READER_FRAGMENT_TAG)
                .commitNow()
        }

        readerFragment = supportFragmentManager.findFragmentByTag(ReaderActivity.READER_FRAGMENT_TAG) as AudioReaderFragment

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

        supportFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks()  {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                this@AudiobookActivity.title =  when (f) {
                    is OutlineFragment -> publication.metadata.title
                    is DrmManagementFragment -> getString(R.string.title_fragment_drm_management)
                    else -> null
                }
            }

            override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                this@AudiobookActivity.title = null
            }
        }, false)
    }

    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        return modelFactory
    }

    private fun showOutlineFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.activity_container, OutlineFragment::class.java, Bundle(), ReaderActivity.OUTLINE_FRAGMENT_TAG)
            .hide(readerFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun closeOutlineFragment(locator: Locator) {
        go(locator)
        supportFragmentManager.popBackStack()
    }

    private fun showDrmManagementFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.activity_container, DrmManagementFragment::class.java, Bundle(), ReaderActivity.DRM_FRAGMENT_TAG)
            .hide(readerFragment)
            .addToBackStack(null)
            .commit()
    }
}





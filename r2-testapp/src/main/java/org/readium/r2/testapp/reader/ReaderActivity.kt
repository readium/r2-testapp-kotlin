/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.reader

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.ViewModelProvider
import kotlinx.android.synthetic.main.activity_reader.*
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.allAreBitmap
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.testapp.R
import org.readium.r2.testapp.drm.DrmManagementContract
import org.readium.r2.testapp.drm.DrmManagementFragment
import org.readium.r2.testapp.outline.OutlineContract
import org.readium.r2.testapp.outline.OutlineFragment

class ReaderActivity : AppCompatActivity(R.layout.activity_reader) {

    private lateinit var modelFactory: ReaderViewModel.Factory
    private lateinit var readerFragment: VisualReaderFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        val inputData = ReaderContract.parseIntent(this)
        val publication = inputData.publication
        val bookId = inputData.bookId
        val persistence = BookData(applicationContext, bookId, publication)

        modelFactory = ReaderViewModel.Factory(publication, persistence)
        super.onCreate(savedInstanceState)

        ViewModelProvider(this).get(ReaderViewModel::class.java)
            .subscribeEvents(this) {
                when(it) {
                    is ReaderViewModel.Event.OpenOutlineRequested -> showOutlineFragment()
                    is ReaderViewModel.Event.OpenDrmManagementRequested -> showDrmManagementFragment()
                }
        }

        val readerClass: Class<out Fragment> = when {
            publication.readingOrder.all { it.mediaType == MediaType.PDF } -> PdfReaderFragment::class.java
            publication.readingOrder.allAreBitmap -> ImageReaderFragment::class.java
            else -> throw IllegalArgumentException("Cannot render publication")
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.activity_container, readerClass, Bundle(), READER_FRAGMENT_TAG)
                .commitNow()
        }

        readerFragment = supportFragmentManager.findFragmentByTag(READER_FRAGMENT_TAG) as VisualReaderFragment

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
                this@ReaderActivity.title =  when (f) {
                    is OutlineFragment -> publication.metadata.title
                    is DrmManagementFragment -> getString(R.string.title_fragment_drm_management)
                    else -> null
                }
            }

            override fun onFragmentViewDestroyed(fm: FragmentManager, f: Fragment) {
                this@ReaderActivity.title = null
            }
        }, false)

        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
           val newInsets = view.onApplyWindowInsets(insets)
            // Without this, activity_reader_container receives the insets only once,
            // although we need a call every time the reader is hidden
           activity_container.dispatchApplyWindowInsets(newInsets)
        }

        activity_container.setOnApplyWindowInsetsListener { view, insets ->
              if (readerFragment.isHidden) {
                view.setPadding(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop + supportActionBar!!.height,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
                insets
            } else {
                view.setPadding(0, 0, 0, 0)
                insets
            }
        }

        // Add support for display cutout.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        return modelFactory
    }

    override fun finish() {
        setResult(Activity.RESULT_OK, intent)
        super.finish()
    }

    private fun showOutlineFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.activity_container, OutlineFragment::class.java, Bundle(), OUTLINE_FRAGMENT_TAG)
            .hide(readerFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun closeOutlineFragment(locator: Locator) {
        readerFragment.go(locator, true)
        supportFragmentManager.popBackStack()
    }

    private fun showDrmManagementFragment() {
        supportFragmentManager.beginTransaction()
            .add(R.id.activity_container, DrmManagementFragment::class.java, Bundle(), DRM_FRAGMENT_TAG)
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

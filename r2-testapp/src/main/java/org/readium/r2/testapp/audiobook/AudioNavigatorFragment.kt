package org.readium.r2.testapp.audiobook

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.fragment_audiobook.*
import kotlinx.coroutines.launch
import org.jetbrains.anko.indeterminateProgressDialog
import org.jetbrains.anko.support.v4.indeterminateProgressDialog
import org.readium.r2.navigator.audiobook.R2MediaPlayer
import org.readium.r2.testapp.utils.createFragmentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.BooksDatabase
import timber.log.Timber

class AudioNavigatorFragment private constructor(
    private val publication: Publication,
    private val bookId: Long,
    private val cover: ByteArray?
) : Fragment(R.layout.fragment_audiobook) {

    private lateinit var booksDB: BooksDatabase

    private val activity: AudiobookActivity
        get() = requireActivity() as AudiobookActivity

    private val mediaPlayer: R2MediaPlayer
        get() = activity.mediaPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        booksDB = BooksDatabase(requireActivity())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setting cover
        if (cover != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                val bmp = BitmapFactory.decodeByteArray(cover, 0, cover.size)
                imageView.setImageBitmap(bmp)
            }
        }

        // Loads the last read location
        booksDB.books.currentLocator(bookId)?.let {
            activity.go(it)
        }

        // Register current location listener
        viewLifecycleOwner.lifecycleScope.launch {
            val positionCount = publication.positions().size

            activity.currentLocator.asLiveData().observe(viewLifecycleOwner, Observer { locator ->
                locator ?: return@Observer

                Timber.d("locationDidChange position ${locator.locations.position ?: 0}/${positionCount} $locator")
                booksDB.books.saveProgression(locator, bookId)
            })
        }

        mediaPlayer.progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_preparing_audiobook))
    }

    companion object {
        fun createFactory(publication: Publication, bookId: Long, cover: ByteArray?): FragmentFactory =
            createFragmentFactory { AudioNavigatorFragment(publication, bookId, cover) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer.progress!!.dismiss()
    }
}
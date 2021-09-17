package org.readium.r2.testapp.reader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import org.readium.r2.navigator.MediaNavigator
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.audio.AudioNavigatorFragment
import org.readium.r2.navigator.media.MediaService
import org.readium.r2.shared.AudiobookNavigator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.audiobook.AudiobookService

@OptIn(AudiobookNavigator::class)
class AudioReaderFragment : BaseReaderFragment() {

    override val model: ReaderViewModel by activityViewModels()
    override val navigator: Navigator get() = mediaNavigator

    private lateinit var mediaNavigator: MediaNavigator
    private lateinit var mediaService: MediaService.Connection

    override fun onCreate(savedInstanceState: Bundle?) {
        val context = requireContext()

        mediaService = MediaService.connect(context, AudiobookService::class.java)

        // Get the currently playing navigator from the media service, if it is the same pub ID.
        // Otherwise, ask to switch to the new publication.
        mediaNavigator = mediaService.currentNavigator.value?.takeIf { it.publicationId == model.publicationId }
            ?: mediaService.getNavigator(model.publication, model.publicationId, model.initialLocation)

        mediaNavigator.play()

        childFragmentManager.fragmentFactory = AudioNavigatorFragment.createFactory(mediaNavigator)

        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        if (savedInstanceState == null) {
            childFragmentManager.commitNow {
                add(R.id.fragment_reader_container, AudioNavigatorFragment::class.java, Bundle(), NAVIGATOR_FRAGMENT_TAG)
            }
        }
        return view
    }

    companion object {

        const val NAVIGATOR_FRAGMENT_TAG = "navigator"
    }
}
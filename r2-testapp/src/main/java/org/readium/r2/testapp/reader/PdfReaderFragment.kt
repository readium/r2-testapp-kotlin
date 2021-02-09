package org.readium.r2.testapp.reader

import android.graphics.PointF
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.fetcher.Resource
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.R
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.extensions.toggleSystemUi

class PdfReaderFragment : AbstractReaderFragment(), PdfNavigatorFragment.Listener {

    override lateinit var publication: Publication
    override lateinit var persistence: BookData
    override lateinit var navigator: Navigator

    override fun onCreate(savedInstanceState: Bundle?) {
        ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java).let {
            publication = it.publication
            persistence = it.persistence
        }

        childFragmentManager.fragmentFactory = CompositeFragmentFactory(
            PdfNavigatorFragment.createFactory(publication, persistence.savedLocation, this)
        )

        super.onCreate(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .add(R.id.fragment_reader_container, PdfNavigatorFragment::class.java,  Bundle(), NAVIGATOR_FRAGMENT_TAG)
                .commitNow()
        }

        navigator = childFragmentManager.findFragmentByTag(NAVIGATOR_FRAGMENT_TAG)!! as Navigator
    }

    override fun onResourceLoadFailed(link: Link, error: Resource.Exception) {
        val message = when (error) {
            is Resource.Exception.OutOfMemory -> "The PDF is too large to be rendered on this device"
            else -> "Failed to render this PDF"
        }
        Toast.makeText(requireActivity(), message, Toast.LENGTH_LONG).show()

        // There's nothing we can do to recover, so we quit the Activity.
        requireActivity().finish()
    }

    override fun onTap(point: PointF): Boolean {
        val viewWidth = requireView().width
        val leftRange = 0.0..(0.2 * viewWidth)

        when {
            leftRange.contains(point.x) -> navigator.goBackward(animated = true)
            leftRange.contains(viewWidth - point.x) -> navigator.goForward(animated = true)
            else -> requireActivity().toggleSystemUi()
        }

        return true
    }

    companion object {

        const val NAVIGATOR_FRAGMENT_TAG = "navigator"
    }
}
package org.readium.r2.testapp.catalogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.squareup.picasso.Picasso
import nl.komponents.kovenant.ui.successUi
import org.readium.r2.shared.extensions.getPublicationOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.testapp.MainActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.bookshelf.BookService
import org.readium.r2.testapp.databinding.FragmentOpdsDetailBinding
import org.readium.r2.testapp.opds.OPDSDownloader
import java.net.URL


class OpdsDetailFragment : Fragment() {

    private var mPublication: Publication? = null
    private lateinit var mOpdsDownloader: OPDSDownloader
    private lateinit var mBookService: BookService

    private var _binding: FragmentOpdsDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.fragment_opds_detail, container, false
        )
        mPublication = arguments?.getPublicationOrNull()
        binding.publication = mPublication
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).supportActionBar?.title = mPublication?.metadata?.title

        mBookService = (activity as MainActivity).bookService
        mOpdsDownloader = OPDSDownloader(requireContext())

        mPublication?.coverLink?.let { link ->
            Picasso.with(requireContext()).load(link.href).into(binding.coverImageView)
        } ?: run {
            if (mPublication?.images?.isNotEmpty() == true) {
                Picasso.with(requireContext()).load(mPublication!!.images.first().href).into(binding.coverImageView)
            }
        }
//        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
//            binding.coverImageView.setImageBitmap(mPublication?.cover())
//        }

        binding.downloadButton.setOnClickListener {
            downloadPublication()
        }
    }

    private fun downloadPublication() {
        val downloadUrl = mPublication?.let { getDownloadURL(it) }
        mOpdsDownloader.publicationUrl(downloadUrl.toString()).successUi { pair ->
            // FIXME this will error if you leave fragment before it completes downloading
            viewLifecycleOwner.lifecycleScope.launchWhenCreated {
                mPublication?.let {
                    mBookService.addPublicationToDatabase(pair.first, "epub", it)
                }
            }
        }
    }

    private fun getDownloadURL(publication: Publication): URL? {
        var url: URL? = null
        val links = publication.links
        for (link in links) {
            val href = link.href
            if (href.contains(Publication.EXTENSION.EPUB.value) || href.contains(Publication.EXTENSION.LCPL.value)) {
                url = URL(href)
                break
            }
        }
        return url
    }
}

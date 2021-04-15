package org.readium.r2.testapp.catalogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import org.readium.r2.shared.extensions.getPublicationOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.opds.images
import org.readium.r2.testapp.MainActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.FragmentOpdsDetailBinding


class OpdsDetailFragment : Fragment() {

    private var mPublication: Publication? = null
    private lateinit var mCatalogViewModel: CatalogViewModel

    private var _binding: FragmentOpdsDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.fragment_opds_detail, container, false
        )
        ViewModelProvider(this).get(CatalogViewModel::class.java).let { model ->
            model.detailChannel.receive(this) { handleEvent(it) }
            mCatalogViewModel = model
        }
        mPublication = arguments?.getPublicationOrNull()
        binding.publication = mPublication
        binding.viewModel = mCatalogViewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).supportActionBar?.title = mPublication?.metadata?.title

        mPublication?.coverLink?.let { link ->
            Picasso.with(requireContext()).load(link.href).into(binding.coverImageView)
        } ?: run {
            if (mPublication?.images?.isNotEmpty() == true) {
                Picasso.with(requireContext()).load(mPublication!!.images.first().href)
                    .into(binding.coverImageView)
            }
        }
//        viewLifecycleOwner.lifecycleScope.launchWhenCreated {
//            binding.coverImageView.setImageBitmap(mPublication?.cover())
//        }

        binding.downloadButton.setOnClickListener {
            mPublication?.let { it1 ->
                mCatalogViewModel.downloadPublication(
                    it1
                )
                GlobalScope.launch {
                    mCatalogViewModel.downloadPublication(
                        it1
                    )
                }
            }
        }
    }

    private fun handleEvent(event: CatalogViewModel.Event.DetailEvent) {
        val message =
            when (event) {
                is CatalogViewModel.Event.DetailEvent.ImportPublicationSuccess -> getString(R.string.import_publication_success)
                is CatalogViewModel.Event.DetailEvent.ImportPublicationFailed -> getString(R.string.unable_add_pub_database)
            }
        Snackbar.make(
            requireView(),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }
}

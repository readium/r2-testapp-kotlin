package org.readium.r2.testapp.catalogs

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import org.readium.r2.shared.extensions.getPublicationOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.MainActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.OpdsDetailItemBinding


class OpdsDetailFragment : Fragment() {

    private var mPublication: Publication? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val binding: OpdsDetailItemBinding = DataBindingUtil.inflate(
                LayoutInflater.from(context),
                R.layout.opds_detail_item, container, false
        )
        mPublication = arguments?.getPublicationOrNull()
        binding.publication = mPublication
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).supportActionBar?.title = mPublication?.metadata?.title
    }
}
package org.readium.r2.testapp.drm

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.alert
import org.jetbrains.anko.appcompat.v7.Appcompat
import org.jetbrains.anko.design.longSnackbar
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.readium.r2.lcp.LcpContentProtectionService
import org.readium.r2.shared.UserException
import org.readium.r2.shared.publication.services.ContentProtectionService
import org.readium.r2.testapp.R
import org.readium.r2.testapp.reader.ReaderViewModel
import java.util.Date

class DrmManagementFragment : Fragment(R.layout.fragment_drm_management) {

    private lateinit var model: DRMViewModel
    private lateinit var endTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val publication = ViewModelProvider(requireActivity()).get(ReaderViewModel::class.java).publication
        val service = publication.findService(ContentProtectionService::class)!! as LcpContentProtectionService
        val license = service.license!!
        model = LCPViewModel(license, this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Information
        view.findViewById<TextView>(R.id.drm_value_license_type).text = model.type
        view.findViewById<TextView>(R.id.drm_value_state).text = model.state
        view.findViewById<TextView>(R.id.drm_value_provider).text = model.provider
        view.findViewById<TextView>(R.id.drm_value_issued).text = model.issued.toFormattedString()
        view.findViewById<TextView>(R.id.drm_value_updated).text = model.updated.toFormattedString()

        // Rights
        view.findViewById<TextView>(R.id.drm_value_prints_left).text = model.printsLeft
        view.findViewById<TextView>(R.id.drm_value_copies_left).text = model.copiesLeft

        val datesVisibility =
            if (model.start != null && model.end != null && model.start != model.end)
                View.VISIBLE
            else
                View.GONE

        view.findViewById<View>(R.id.drm_start).run {
            visibility = datesVisibility
        }

        view.findViewById<TextView>(R.id.drm_value_start).run {
            text = model.start.toFormattedString()
        }
        view.findViewById<View>(R.id.drm_end).run {
            visibility = datesVisibility
        }
        endTextView = view.findViewById<TextView>(R.id.drm_value_end).apply {
            text = model.end?.toFormattedString()
        }

        // Actions
        view.findViewById<TextView>(R.id.drm_label_actions).run {
            visibility = if (model.canRenewLoan || model.canReturnPublication) View.VISIBLE else View.GONE
        }
        view.findViewById<Button>(R.id.drm_button_renew).run {
            visibility = if (model.canRenewLoan) View.VISIBLE else View.GONE
            setOnClickListener {onRenewLoan() }
        }
        view.findViewById<Button>(R.id.drm_button_return).run {
            visibility = if (model.canReturnPublication) View.VISIBLE else View.GONE
            setOnClickListener { onReturnPublication() }
        }
    }

    private fun onRenewLoan() {
        lifecycleScope.launch {
            model.renewLoan()
                .onSuccess { newDate ->
                    endTextView.text = newDate.toFormattedString()
                }.onFailure { exception ->
                    (exception as? UserException)?.getUserMessage(requireContext())
                        ?.let { requireView().longSnackbar(it) }
                }
        }
    }

    private fun onReturnPublication() {
        requireContext().alert(Appcompat, "This will return the publication") {
            negativeButton("Cancel") { }
            positiveButton("Return") {
                lifecycleScope.launch {
                    model.returnPublication()
                        .onSuccess {
                            val result = DrmManagementContract.createResult(hasReturned = true)
                            setFragmentResult(DrmManagementContract.REQUEST_KEY, result)
                        }.onFailure { exception ->
                            (exception as? UserException)?.getUserMessage(requireContext())
                                ?.let { requireView().longSnackbar(it) }
                        }
                }
            }

        }.build().apply {
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }.show()
    }
}

private fun Date?.toFormattedString() =
    DateTime(this).toString(DateTimeFormat.shortDateTime()).orEmpty()
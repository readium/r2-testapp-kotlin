package org.readium.r2.testapp.bookshelf

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.squareup.picasso.Picasso
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.FragmentBookshelfBinding
import org.readium.r2.testapp.domain.model.Book
import org.readium.r2.testapp.opds.GridAutoFitLayoutManager
import org.readium.r2.testapp.permissions.*
import org.readium.r2.testapp.reader.ReaderContract
import timber.log.Timber
import java.io.File


class BookshelfFragment : Fragment() {

    private lateinit var mBookshelfViewModel: BookshelfViewModel
    private lateinit var mBookshelfAdapter: BookshelfAdapter
    private lateinit var mDocumentPickerLauncher: ActivityResultLauncher<String>
    private lateinit var mReaderLauncher: ActivityResultLauncher<ReaderContract.Input>
    private var permissionAsked: Boolean = false
    private var _binding: FragmentBookshelfBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                importBooks()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        ViewModelProvider(this).get(BookshelfViewModel::class.java).let { model ->
            model.channel.receive(this) { handleEvent(it) }
            mBookshelfViewModel = model
        }
        _binding = FragmentBookshelfBinding.inflate(
            inflater, container, false
        )
        binding.viewModel = mBookshelfViewModel
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBookshelfAdapter = BookshelfAdapter(onBookClick = { book -> openBook(book) },
            onBookLongClick = { book -> confirmDeleteBook(book) })

        mDocumentPickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                uri?.let {
                    mBookshelfViewModel.importPublicationFromUri(it)
                }
            }

        mReaderLauncher =
            registerForActivityResult(ReaderContract()) { pubData: ReaderContract.Output? ->
                if (pubData == null)
                    return@registerForActivityResult

                tryOrNull { pubData.publication.close() }
                Timber.d("Publication closed")
                if (pubData.deleteOnResult)
                    tryOrNull { pubData.file.delete() }
            }

        binding.bookList.apply {
            setHasFixedSize(true)
            layoutManager = GridAutoFitLayoutManager(requireContext(), 120)
            adapter = mBookshelfAdapter
            addItemDecoration(
                VerticalSpaceItemDecoration(
                    10
                )
            )
        }

        mBookshelfViewModel.books.observe(viewLifecycleOwner, {
            mBookshelfAdapter.submitList(it)
        })

        importBooks()

        // FIXME embedded dialogs like this are ugly
        binding.addBook.setOnClickListener {
            var selected = 0
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.add_book))
                .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                    dialog.cancel()
                }
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    if (selected == 0) {
                        mDocumentPickerLauncher.launch("*/*")
                    } else {
                        val urlEditText = EditText(requireContext())
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.add_book))
                            .setMessage(R.string.enter_url)
                            .setView(urlEditText)
                            .setNegativeButton(R.string.cancel) { _, _ ->

                            }
                            .setPositiveButton(R.string.ok) { _, _ ->
                                val url = urlEditText.text.toString()
                                val uri = Uri.parse(url)
                                mBookshelfViewModel.importPublicationFromUri(uri, url)
                            }
                            .show()

                    }
                }
                .setSingleChoiceItems(R.array.documentSelectorArray, 0) { _, which ->
                    selected = which
                }
                .show()
        }
    }

    private fun handleEvent(event: BookshelfViewModel.Event) {
        val message =
            when (event) {
                is BookshelfViewModel.Event.ImportPublicationFailed -> {
                    "Error: " + event.errorMessage
                }
                is BookshelfViewModel.Event.UnableToMovePublication -> getString(R.string.unable_to_move_pub)
                is BookshelfViewModel.Event.ImportPublicationSuccess -> getString(R.string.import_publication_success)
                is BookshelfViewModel.Event.ImportDatabaseFailed -> getString(R.string.unable_add_pub_database)
                is BookshelfViewModel.Event.OpenBookError -> {
                    "Error: " + event.errorMessage
                }
            }
        Snackbar.make(
            requireView(),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) :
        RecyclerView.ItemDecoration() {

        override fun getItemOffsets(
            outRect: Rect, view: View, parent: RecyclerView,
            state: RecyclerView.State
        ) {
            outRect.bottom = verticalSpaceHeight
        }
    }

    private fun requestStoragePermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Snackbar.make(
                this.requireView(),
                R.string.permission_external_new_explanation,
                Snackbar.LENGTH_LONG
            )
                .setAction(R.string.permission_retry) {
                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                .show()
        } else {
            // FIXME this is an ugly hack for when user has said don't ask again
            if (permissionAsked) {
                Snackbar.make(
                    this.requireView(),
                    R.string.permission_external_new_explanation,
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction(R.string.action_settings) {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            addCategory(Intent.CATEGORY_DEFAULT)
                            data = Uri.parse("package:${view?.context?.packageName}")
                        }.run(::startActivity)
                    }
                    .setActionTextColor(
                        ContextCompat.getColor(
                            requireContext(),
                            R.color.snackbar_text_color
                        )
                    )
                    .show()
            } else {
                permissionAsked = true
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun importBooks() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            mBookshelfViewModel.copySamplesFromAssetsToStorage()
        } else requestStoragePermission()

    }

    private fun deleteBook(book: Book) {
        mBookshelfViewModel.deleteBook(book)
    }

    private fun openBook(book: Book) {
        GlobalScope.launch {
            mBookshelfViewModel.openBook(
                book,
                callback = { asset, mediaType, publication, remoteAsset, url ->
                    mReaderLauncher.launch(
                        ReaderContract.Input(
                            file = asset.file,
                            mediaType = mediaType,
                            publication = publication,
                            bookId = book.id!!,
                            initialLocator = Locator.fromJSON(
                                JSONObject(
                                    book.progression
                                        ?: "{}"
                                )
                            ),
                            deleteOnResult = remoteAsset != null,
                            baseUrl = url
                        )
                    )
                })
        }
    }

    private fun confirmDeleteBook(book: Book) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.confirm_delete_book_title))
            .setMessage(getString(R.string.confirm_delete_book_text))
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(getString(R.string.delete)) { dialog, _ ->
                deleteBook(book)
                dialog.dismiss()
            }
            .show()
    }
}

@BindingAdapter("coverImage")
fun loadImage(view: ImageView, bookId: Long?) {
    val coverImageFile = File("${view.context?.filesDir?.path}/covers/${bookId}.png")
    Picasso.with(view.context)
        .load(coverImageFile)
        .placeholder(R.drawable.cover)
        .into(view)
}
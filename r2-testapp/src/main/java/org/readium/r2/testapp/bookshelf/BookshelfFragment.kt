package org.readium.r2.testapp.bookshelf

import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.databinding.BindingAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.MainActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.domain.model.Book
import org.readium.r2.testapp.opds.GridAutoFitLayoutManager
import org.readium.r2.testapp.permissions.storagePermission
import org.readium.r2.testapp.reader.ReaderContract
import timber.log.Timber
import java.io.File


class BookshelfFragment : Fragment(), BookshelfAdapter.RecyclerViewClickListener {

    private lateinit var mBookshelfViewModel: BookshelfViewModel
    private lateinit var mBookshelfAdapter: BookshelfAdapter
    private lateinit var mDocumentPickerLauncher: ActivityResultLauncher<String>
    private lateinit var mReaderLauncher: ActivityResultLauncher<ReaderContract.Input>
    private lateinit var mBookService: BookService

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {

        mBookshelfViewModel =
                ViewModelProvider(this).get(BookshelfViewModel::class.java)
        return inflater.inflate(R.layout.fragment_bookshelf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mBookshelfAdapter = BookshelfAdapter(this)
        mBookService = (activity as MainActivity).bookService

        mDocumentPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            viewLifecycleOwner.lifecycleScope.launch {
                uri?.let { mBookService.importPublicationFromUri(it) }
            }
        }

        mReaderLauncher = registerForActivityResult(ReaderContract()) { pubData: ReaderContract.Output? ->
            if (pubData == null)
                return@registerForActivityResult

            tryOrNull { pubData.publication.close() }
            Timber.d("Publication closed")
            if (pubData.deleteOnResult)
                tryOrNull { pubData.file.delete() }
        }

        view.findViewById<RecyclerView>(R.id.book_list).apply {
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

        storagePermission {
            if (mBookshelfAdapter.itemCount == 0) {
                viewLifecycleOwner.lifecycleScope.launch {
                    mBookService.copySamplesFromAssetsToStorage()
                }
            }
        }.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

        // FIXME embedded dialogs like this are ugly
        view.findViewById<FloatingActionButton>(R.id.addBook).setOnClickListener {
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
                                        viewLifecycleOwner.lifecycleScope.launch {
                                            val uri = Uri.parse(urlEditText.text.toString())
                                            mBookService.importPublicationFromUri(uri)
                                        }
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

    class VerticalSpaceItemDecoration(private val verticalSpaceHeight: Int) : RecyclerView.ItemDecoration() {

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                                    state: RecyclerView.State) {
            outRect.bottom = verticalSpaceHeight
        }
    }

    fun deleteBook(book: Book) {
        viewLifecycleOwner.lifecycleScope.launch {
            mBookService.deleteBook(book)
        }
    }

    fun openBook(book: Book) {
        viewLifecycleOwner.lifecycleScope.launch {
            mBookService.openBook(book) { asset, mediaType, publication, remoteAsset, url ->
                mReaderLauncher.launch(
                        ReaderContract.Input(
                                file = asset.file,
                                mediaType = mediaType,
                                publication = publication,
                                bookId = book.id!!,
                                initialLocator = Locator.fromJSON(JSONObject(book.progression)),
                                deleteOnResult = remoteAsset != null,
                                baseUrl = url
                        )
                )
            }
        }
    }

    override fun recyclerViewListClicked(book: Book) {
        openBook(book)
    }

    override fun recyclerViewListLongClicked(book: Book) {
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
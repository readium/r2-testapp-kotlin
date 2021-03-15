/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Clément Baumann
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.bookshelf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.readium.r2.testapp.R
import org.readium.r2.testapp.databinding.ItemRecycleBookBinding
import org.readium.r2.testapp.domain.model.Book
import org.readium.r2.testapp.utils.singleClick


class BookshelfAdapter(val parent: BookshelfFragment) : ListAdapter<Book, BookshelfAdapter.ViewHolder>(BookListDiff()) {

    override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
    ): ViewHolder {
        return ViewHolder(
                DataBindingUtil.inflate(
                        LayoutInflater.from(parent.context),
                        R.layout.item_recycle_book, parent, false
                )
        )
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val book = getItem(position)

        viewHolder.bind(book)
    }

    inner class ViewHolder(private val binding: ItemRecycleBookBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(book: Book) {
            binding.book = book
            // TODO decide whether or not to use this or the RecyclerViewClickListener
            binding.root.singleClick { _ ->
                parent.openBook(book)
            }
            binding.root.setOnLongClickListener {
                MaterialAlertDialogBuilder(parent.requireContext())
                        .setTitle(parent.getString(R.string.confirm_delete_book_title))
                        .setMessage(parent.getString(R.string.confirm_delete_book_text))
                        .setNegativeButton(parent.getString(R.string.cancel)) { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setPositiveButton(parent.getString(R.string.delete)) { dialog, _ ->
                            parent.deleteBook(book)
                            dialog.dismiss()
                        }
                        .show()
                true
            }
        }
    }

    interface RecyclerViewClickListener {

        //this is method to handle the event when clicked on the image in Recyclerview
        fun recyclerViewListClicked(v: View, position: Int)

        fun recyclerViewListLongClicked(v: View, position: Int)
    }

    private class BookListDiff : DiffUtil.ItemCallback<Book>() {

        override fun areItemsTheSame(
                oldItem: Book,
                newItem: Book
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
                oldItem: Book,
                newItem: Book
        ): Boolean {
            return oldItem.title == newItem.title
                    && oldItem.href == newItem.href
                    && oldItem.author == newItem.author
                    && oldItem.identifier == newItem.identifier
                    && oldItem.progression == newItem.progression
        }
    }

}
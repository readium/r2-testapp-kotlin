/**
Copyright 2018 Readium Foundation. All rights reserved.
Use of this source code is governed by a BSD-style license which is detailed in the LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.opds

import android.app.Activity
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.mcxiaoke.koi.ext.onClick
import com.squareup.picasso.Picasso
import org.jetbrains.anko.intentFor
import org.readium.r2.shared.Publication
import org.readium.r2.testapp.R

class RecyclerViewAdapter(private val activity: Activity, private val strings: MutableList<Publication>) : RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = activity.layoutInflater
        val view = inflater.inflate(R.layout.item_recycle_opds, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {

        val publication = strings[position]
        viewHolder.textView.text = publication.metadata.title

        if (publication.images.isNotEmpty()) {
            Picasso.with(activity).load(publication.images[0].href).into(viewHolder.imageView);
        } else {
            for (link in publication.links) {
                if (link.rel.contains("http://opds-spec.org/image/thumbnail")) {
                    Picasso.with(activity).load(link.href).into(viewHolder.imageView);
                }
            }
        }
        viewHolder.itemView.onClick {
            activity.startActivity(activity.intentFor<OPDSDetailActivity>("publication" to publication))
        }

    }

    override fun getItemCount(): Int {
        return strings.size
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView
        val imageView: ImageView

        init {
            textView = view.findViewById<View>(R.id.titleTextView) as TextView
            imageView = view.findViewById(R.id.coverImageView) as ImageView
        }
    }
}

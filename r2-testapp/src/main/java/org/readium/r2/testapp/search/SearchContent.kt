/*
 * Copyright 2021 Readium Foundation. All rights reserved.
 * Use of this source code is governed by the BSD-style license
 * available in the top-level LICENSE file of the project.
 */

package org.readium.r2.testapp.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemsIndexed
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.reader.ReaderViewModel

@Composable
fun SearchContent(readerViewmodel: ReaderViewModel) {
    val pager = remember { readerViewmodel.searchPager }
    val lazyPagingItems = pager.flow.collectAsLazyPagingItems()

    if (lazyPagingItems.itemCount == 0) {
        NoResult()
    } else {
        SearchList(lazyPagingItems) { readerViewmodel.onSearchItemClicked(it) }
    }
}

@Composable
private fun SearchList(
    lazyPagingItems: LazyPagingItems<Locator>,
    onItemClicked: (Locator) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val stickySectionTitle: MutableState<String?> = remember { mutableStateOf("") }

    val index = lazyListState.firstVisibleItemIndex
    if (index in 0..lazyPagingItems.itemCount) {
        lazyPagingItems[index]?.run {
            if (stickySectionTitle.value != title) stickySectionTitle.value = title
        }
    }

    Box(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)) {
        LazyColumn(state = lazyListState) {
            itemsIndexed(lazyPagingItems) { itemPos, locator ->
                if (!locator?.title.isNullOrEmpty() &&
                    isStartOfSection(itemPos, lazyPagingItems)
                ) {
                    SectionTitle(title = locator?.title)
                } else if (itemPos != 0) {
                    Divider(
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp),
                        color = Color.LightGray
                    )
                }
                SearchItem(locator, onItemClicked)
            }
        }
        if (!stickySectionTitle.value.isNullOrEmpty()) {
            SectionTitle(title = stickySectionTitle.value)
        }
    }
}

@Composable
private fun SearchItem(locator: Locator?, onItemClicked: (Locator) -> Unit) {
    Text(
        text = buildAnnotatedString {
            locator?.text?.run {
                append(before.toString())
                withStyle(SpanStyle(background = Color.Yellow)) {
                    append(highlight.toString())
                }
                append(after.toString())
            }
        },
        modifier = Modifier
            .padding(10.dp)
            .clickable(onClick = { locator?.let { onItemClicked(it) } })
    )
}

private fun isStartOfSection(itemPos: Int, lazyPagingItems: LazyPagingItems<Locator>) =
    lazyPagingItems.run {
        when {
            itemPos == 0 -> true
            itemPos < 0 -> false
            itemPos >= itemCount -> false
            else -> get(itemPos)?.title != get(itemPos - 1)?.title
        }
    }

@Composable
private fun SectionTitle(title: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 3.dp, end = 3.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color.LightGray)
    ) {
        Text(
            text = title.toString(),
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            overflow = TextOverflow.Ellipsis,
            maxLines = 3,
            style = MaterialTheme.typography.h6
        )
    }
}

@Composable
private fun NoResult() {
    Text(
        text = stringResource(R.string.no_result),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.h6
    )
}

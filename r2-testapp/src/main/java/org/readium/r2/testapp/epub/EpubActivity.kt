/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi, Mostapha Idoubihi, Paul Stoica
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp.epub

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import kotlinx.android.synthetic.main.activity_epub.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.anko.appcompat.v7.coroutines.onClose
import org.jetbrains.anko.indeterminateProgressDialog
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.toast
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.NavigatorDelegate
import org.readium.r2.navigator.epub.R2EpubActivity
import org.readium.r2.navigator.epub.Style
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.shared.APPEARANCE_REF
import org.readium.r2.shared.ReadiumCSSName
import org.readium.r2.shared.SCROLL_REF
import org.readium.r2.shared.extensions.putPublicationFrom
import org.readium.r2.shared.publication.ContentLayout
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.epub.EpubLayout
import org.readium.r2.shared.publication.presentation.presentation
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.testapp.BuildConfig.DEBUG
import org.readium.r2.testapp.DRMManagementActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.*
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.library.activitiesLaunched
import org.readium.r2.testapp.outline.R2OutlineActivity
import org.readium.r2.testapp.search.MarkJSSearchEngine
import org.readium.r2.testapp.search.SearchLocatorAdapter
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


/**
 * EpubActivity : Extension of the EpubActivity() from navigator
 *
 * That Activity manage everything related to the menu
 *      ( Table of content, User Settings, DRM, Bookmarks )
 *
 */
class EpubActivity : R2EpubActivity(), CoroutineScope, NavigatorDelegate/*, VisualNavigatorDelegate, OutlineTableViewControllerDelegate*/ {

    /**
     * Context of this scope.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    //UserSettings
    private lateinit var userSettings: UserSettings

    //Accessibility
    private var isExploreByTouchEnabled = false
    private var pageEnded = false

    // Provide access to the Bookmarks & Positions Databases
    private lateinit var bookmarksDB: BookmarksDatabase
    private lateinit var booksDB: BooksDatabase
    private lateinit var positionsDB: PositionsDatabase
    private lateinit var highlightDB: HighligtsDatabase


    private lateinit var screenReader: R2ScreenReader

    private var menuDrm: MenuItem? = null
    private var menuToc: MenuItem? = null
    private var menuBmk: MenuItem? = null
    private var menuSearch: MenuItem? = null

    private var menuScreenReader: MenuItem? = null

    private var searchTerm = ""
    private lateinit var searchStorage: SharedPreferences
    private lateinit var searchResultAdapter: SearchLocatorAdapter
    private lateinit var searchResult: MutableList<Locator>

    private var mode: ActionMode? = null
    private var popupWindow: PopupWindow? = null

    /**
     * Manage activity creation.
     *   - Load data from the database
     *   - Set background and text colors
     *   - Set onClickListener callbacks for the [screenReader] buttons
     *   - Initialize search.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        if (activitiesLaunched.incrementAndGet() > 1 || !LibraryActivity.isServerStarted) {
            finish()
        }
        super.onCreate(savedInstanceState)
        bookmarksDB = BookmarksDatabase(this)
        booksDB = BooksDatabase(this)
        positionsDB = PositionsDatabase(this)
        highlightDB = HighligtsDatabase(this)

        navigatorDelegate = this
        bookId = intent.getLongExtra("bookId", -1)


        launch {
            val positionCount = publication.positions().size

            currentLocator.observe(this@EpubActivity, Observer { locator ->
                locator ?: return@Observer

                Timber.d("locationDidChange position ${locator.locations.position ?: 0}/${positionCount} $locator")
                booksDB.books.saveProgression(locator, bookId)

                if (::screenReader.isInitialized && locator.locations.progression == 0.0) {
                    screenReader.currentUtterance = 0
                }
            })
        }

        launch {
            delay(100)
            menuDrm?.isVisible = publication.isProtected
        }

        val appearancePref = preferences.getInt(APPEARANCE_REF, 0)
        val backgroundsColors = mutableListOf("#ffffff", "#faf4e8", "#000000")
        val textColors = mutableListOf("#000000", "#000000", "#ffffff")
        resourcePager.setBackgroundColor(Color.parseColor(backgroundsColors[appearancePref]))
        (resourcePager.focusedChild?.findViewById(org.readium.r2.navigator.R.id.book_title) as? TextView)?.setTextColor(Color.parseColor(textColors[appearancePref]))
        toggleActionBar()

        resourcePager.offscreenPageLimit = 1

        titleView.text = publication.metadata.title

        play_pause.setOnClickListener {
            if (screenReader.isPaused) {
                screenReader.resumeReading()
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                screenReader.pauseReading()
                play_pause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
        fast_forward.setOnClickListener {
            if (screenReader.nextSentence()) {
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                next_chapter.callOnClick()
            }
        }
        next_chapter.setOnClickListener {
            if (goForward(false, completion = {})) {
                screenReader.nextResource()
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }
        fast_back.setOnClickListener {
            if (screenReader.previousSentence()) {
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                prev_chapter.callOnClick()
            }
        }
        prev_chapter.setOnClickListener {
            if (goBackward(false, completion = {})) {
                screenReader.previousResource()
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            }
        }


        // SEARCH
        searchStorage = getSharedPreferences("org.readium.r2.search", Context.MODE_PRIVATE)
        searchResult = mutableListOf()
        searchResultAdapter = SearchLocatorAdapter(this, searchResult, object : SearchLocatorAdapter.RecyclerViewClickListener {
            override fun recyclerViewListClicked(v: View, position: Int) {

                search_overlay.visibility = View.INVISIBLE
                val searchView = menuSearch?.actionView as SearchView

                searchView.clearFocus()
                if (searchView.isShown) {
                    menuSearch?.collapseActionView()
                    resourcePager.offscreenPageLimit = 1
                }

                val locator = searchResult[position]
                val intent = Intent().apply {
                    putPublicationFrom(this@EpubActivity)
                    putExtra("publicationPath", publicationPath)
                    putExtra("epubName", publicationFileName)
                    putExtra("bookId", bookId)
                    putExtra("locator", locator)
                }
                onActivityResult(2, Activity.RESULT_OK, intent)
            }

        })
        search_listView.adapter = searchResultAdapter
        search_listView.layoutManager = LinearLayoutManager(this)
    }


    /**
     * Pause the screenReader if view is paused.
     */
    override fun onPause() {
        super.onPause()
        screenReader.pauseReading()
    }

    /**
     * Stop the screenReader if app is view is stopped.
     */
    override fun onStop() {
        super.onStop()
        screenReader.stopReading()
    }

    /**
     * The function allows to access the [R2ScreenReader] instance and set the TextToSpeech speech speed.
     * Values are limited between 0.25 and 3.0 included.
     *
     * @param speed: Float - The speech speed we wish to use with Android's TextToSpeech.
     */
    fun updateScreenReaderSpeed(speed: Float, restart: Boolean) {
        var rSpeed = speed

        if (speed < 0.25) {
            rSpeed = 0.25.toFloat()
        } else if (speed > 3.0) {
            rSpeed = 3.0.toFloat()
        }
        screenReader.setSpeechSpeed(rSpeed, restart)
    }

    /**
     * Override Android's option menu by inflating a custom view instead.
     *   - Initialize the search component.
     *
     * @param menu: Menu? - The menu view.
     * @return Boolean - return true.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_epub, menu)
        menuDrm = menu?.findItem(R.id.drm)
        menuToc = menu?.findItem(R.id.toc)
        menuBmk = menu?.findItem(R.id.bookmark)
        menuSearch = menu?.findItem(R.id.search)

        menuScreenReader = menu?.findItem(R.id.screen_reader)

        menuScreenReader?.isVisible = !isExploreByTouchEnabled

        menuDrm?.isVisible = false

        val searchView = menuSearch?.actionView as SearchView

        searchView.isFocusable = false
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {

            override fun onQueryTextSubmit(query: String?): Boolean {

                searchResult.clear()
                searchResultAdapter.notifyDataSetChanged()

                query?.let {
                    search_overlay.visibility = View.VISIBLE
                    resourcePager.offscreenPageLimit = publication.readingOrder.size

                    //Saving searched term
                    searchTerm = query
                    //Initializing our custom search interfaces
                    val progress = indeterminateProgressDialog(getString(R.string.progress_wait_while_searching_book))
                    progress.show()

                    val markJSSearchInterface = MarkJSSearchEngine(this@EpubActivity)
                    Handler().postDelayed({
                        markJSSearchInterface.search(query) { (last, result) ->
                            searchResult.clear()
                            searchResult.addAll(result)
                            searchResultAdapter.notifyDataSetChanged()

                            //Saving results + keyword only when JS is fully executed on all resources
                            val editor = searchStorage.edit()
                            val stringResults = Gson().toJson(result)
                            editor.putString("result", stringResults)
                            editor.putString("term", searchTerm)
                            editor.putLong("book", bookId)
                            editor.apply()

                            if (last) {
                                progress.dismiss()
                            }
                        }
                    }, 500)


                }
                return false
            }

            override fun onQueryTextChange(s: String): Boolean {
                return false
            }
        })
        searchView.setOnQueryTextFocusChangeListener { _, b ->
            if (!b) {
                search_overlay.visibility = View.INVISIBLE
            } else {
                search_overlay.visibility = View.VISIBLE
                resourcePager.offscreenPageLimit = publication.readingOrder.size
            }
        }
        searchView.onClose {
            search_overlay.visibility = View.INVISIBLE

        }
        searchView.setOnCloseListener {
            if (searchView.isShown) {
                menuSearch?.collapseActionView()
            }
            search_overlay.visibility = View.INVISIBLE

            true
        }
        searchView.setOnSearchClickListener {
            val previouslySearchBook = searchStorage.getLong("book", -1)
            if (previouslySearchBook == bookId) {
                //Loading previous results + keyword
                val tmp = searchStorage.getString("result", null)
                if (tmp != null) {
                    searchResult.clear()
                    searchResult.addAll(Gson().fromJson(tmp, Array<Locator>::class.java).asList().toMutableList())
                    searchResultAdapter.notifyDataSetChanged()

                    val keyword = searchStorage.getString("term", null)
                    searchView.setQuery(keyword, false)
                    searchView.clearFocus()
                }
                searchView.setQuery(searchStorage.getString("term", null), false)
            }

            search_overlay.visibility = View.VISIBLE
            resourcePager.offscreenPageLimit = publication.readingOrder.size
        }

        menuSearch?.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                search_overlay.visibility = View.VISIBLE
                resourcePager.offscreenPageLimit = publication.readingOrder.size
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                search_overlay.visibility = View.INVISIBLE
                return true
            }
        })

        val closeButton = searchView.findViewById(R.id.search_close_btn) as ImageView
        closeButton.setOnClickListener {
            searchResult.clear()
            searchResultAdapter.notifyDataSetChanged()

            searchView.setQuery("", false)

            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)

            val editor = searchStorage.edit()
            editor.remove("result")
            editor.remove("term")
            editor.apply()
        }

        return true
    }

    /**
     * Management of the menu bar.
     *
     * When (TOC):
     *   - Open TOC activity for current publication.
     *
     * When (Settings):
     *   - Show settings view as a dropdown menu starting from the clicked button
     *
     * When (Screen Reader):
     *   - Switch screen reader on or off.
     *   - If screen reader was off, get reading speed from preferences, update reading speed and sync it with the
     *       active section in the webView.
     *   - If screen reader was on, dismiss it.
     *
     * When (DRM):
     *   - Dismiss screen reader if it was on
     *   - Start the DRM management activity.
     *
     * When (Bookmark):
     *   - Create a bookmark marking the current page and insert it inside the database.
     *
     * When (Search):
     *   - Make the search overlay visible.
     *
     * When (Home):
     *   - Make the search view invisible.
     *
     * @param item: MenuItem - The button that was pressed.
     * @return Boolean - Return true if the button has a switch case. Return false otherwise.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.toc -> {
                val intent = Intent(this, R2OutlineActivity::class.java).apply {
                    putPublicationFrom(this@EpubActivity)
                    putExtra("bookId", bookId)
                }
                startActivityForResult(intent, 2)
                return true
            }
            R.id.settings -> {
                userSettings.userSettingsPopUp().showAsDropDown(this.findViewById(R.id.settings), 0, 0, Gravity.END)
                return true
            }
            R.id.screen_reader -> {
                if (!screenReader.isSpeaking && !screenReader.isPaused && item.title == resources.getString(R.string.epubactivity_read_aloud_start)) {

                    //Get user settings speed when opening the screen reader. Get a neutral percentage (corresponding to
                    //the normal speech speed) if no user settings exist.
                    val speed = preferences.getInt("reader_TTS_speed",
                            (2.75 * 3.toDouble() / 11.toDouble() * 100).toInt())
                    //Convert percentage to a float value between 0.25 and 3.0
                    val ttsSpeed = 0.25.toFloat() + (speed.toFloat() / 100.toFloat()) * 2.75.toFloat()

                    updateScreenReaderSpeed(ttsSpeed, false)

                    if (screenReader.goTo(resourcePager.currentItem)) {

                        item.title = resources.getString(R.string.epubactivity_read_aloud_stop)
                        tts_overlay.visibility = View.VISIBLE
                        play_pause.setImageResource(android.R.drawable.ic_media_pause)
                        allowToggleActionBar = false
                    } else {
                        Toast.makeText(applicationContext, "No further chapter contains text to read", Toast.LENGTH_LONG).show()
                    }

                } else {
                    dismissScreenReader()
                }

                return true
            }
            R.id.drm -> {
                if (screenReader.isSpeaking) {
                    dismissScreenReader()
                }
                startActivityForResult(intentFor<DRMManagementActivity>("publication" to publicationPath), 1)
                return true
            }
            R.id.bookmark -> {
                val resourceIndex = resourcePager.currentItem.toLong()
                val resource = publication.readingOrder[resourcePager.currentItem]
                val resourceHref = resource.href
                val resourceType = resource.type ?: ""
                val resourceTitle = resource.title ?: ""
                val currentPage = positionsDB.positions.getCurrentPage(bookId, resourceHref, currentLocator.value?.locations?.progression!!)?.let {
                    it
                }

                val bookmark = Bookmark(
                        bookId,
                        publicationIdentifier,
                        resourceIndex,
                        resourceHref,
                        resourceType,
                        resourceTitle,
                        Locator.Locations(progression = currentLocator.value?.locations?.progression, position = currentPage?.toInt()),
                        Locator.Text()
                )

                bookmarksDB.bookmarks.insert(bookmark)?.let {
                    launch {
                        currentPage?.let {
                            toast("Bookmark added at page $currentPage")
                        } ?: run {
                            toast("Bookmark added")
                        }
                    }
                } ?: run {
                    launch {
                        toast("Bookmark already exists")
                    }
                }

                return true
            }
            R.id.search -> {
                search_overlay.visibility = View.VISIBLE
                resourcePager.offscreenPageLimit = publication.readingOrder.size

                val searchView = menuSearch?.actionView as SearchView

                searchView.clearFocus()

                return super.onOptionsItemSelected(item)
            }

            android.R.id.home -> {
                search_overlay.visibility = View.INVISIBLE
                resourcePager.offscreenPageLimit = 1
                val searchView = menuSearch?.actionView as SearchView
                searchView.clearFocus()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getBooleanExtra("returned", false)) {
                finish()
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == 2 && resultCode == Activity.RESULT_OK && data != null) {
                val locator = data.getParcelableExtra("locator") as? Locator
                locator?.let {
                    locator.locations.fragments.firstOrNull()?.let { fragment ->
                        val fragments = fragment.split(",")
                                .map { it.split("=") }
                                .filter { it.size == 2 }
                                .associate { it[0] to it[1] }

                        val index = fragments["i"]?.toInt()
                        if (index != null) {
                            val searchStorage = getSharedPreferences("org.readium.r2.search", Context.MODE_PRIVATE)
                            Handler().postDelayed({
                                if (publication.metadata.presentation.layout == EpubLayout.REFLOWABLE) {
//                                    val currentFragment = (resourcePager.adapter as R2PagerAdapter).getCurrentFragment() as R2EpubPageFragment
                                    val currentFragment = (resourcePager.adapter as R2PagerAdapter).fm.findFragmentByTag("f${resourcePager.currentItem}") as R2EpubPageFragment
                                    val resource = publication.readingOrder[resourcePager.currentItem]
                                    val resourceHref = resource.href
                                    val resourceType = resource.type ?: ""
                                    val resourceTitle = resource.title ?: ""

                                    currentFragment.webView.runJavaScript("markSearch('${searchStorage.getString("term", null)}', null, '$resourceHref', '$resourceType', '$resourceTitle', '$index')") { result ->

                                        if (DEBUG) Timber.d("###### $result")

                                    }
                                }
                            }, 1200)
                        }
                    }
                }
            }
        }
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        super.onActionModeStarted(mode)
        mode?.menu?.run {
            menuInflater.inflate(R.menu.menu_action_mode, this)
            findItem(R.id.highlight).setOnMenuItemClickListener {
//                val currentFragment = ((resourcePager.adapter as R2PagerAdapter).mFragments.get((resourcePager.adapter as R2PagerAdapter).getItemId(resourcePager.currentItem))) as? R2EpubPageFragment
                val currentFragment = (resourcePager.adapter as R2PagerAdapter).fm.findFragmentByTag("f${resourcePager.currentItem}") as? R2EpubPageFragment

                currentFragment?.webView?.getCurrentSelectionRect {
                    val rect = JSONObject(it).run {
                        try {
                            val display = windowManager.defaultDisplay
                            val metrics = DisplayMetrics()
                            display.getMetrics(metrics)
                            val left = getDouble("left")
                            val width = getDouble("width")
                            val top = getDouble("top") * metrics.density
                            val height = getDouble("height") * metrics.density
                            Rect(left.toInt(), top.toInt(), width.toInt() + left.toInt(), top.toInt() + height.toInt())
                        } catch (e: JSONException) {
                            null
                        }
                    }
                    showHighlightPopup(size = rect) {
                        mode.finish()
                    }
                }
                true
            }
            findItem(R.id.note).setOnMenuItemClickListener {
                showAnnotationPopup()
                true
            }
        }
        this.mode = mode
    }

    private fun showHighlightPopup(highlightID: String? = null, size: Rect?, dismissCallback: () -> Unit) {
        popupWindow?.let {
            if (it.isShowing) {
                return
            }
        }
        var highlight: org.readium.r2.navigator.epub.Highlight? = null

        highlightID?.let { id ->
            highlightDB.highlights.list(id).forEach {
                highlight = convertHighlight2NavigationHighlight(it)
            }
        }

        val display = windowManager.defaultDisplay
        val rect = size ?: Rect()

        val mDisplaySize = Point()
        display.getSize(mDisplaySize)

        val popupView = layoutInflater.inflate(
                if (rect.top > rect.height()) R.layout.view_action_mode_reverse else R.layout.view_action_mode,
                null,
                false
        )
        popupView.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))

        popupWindow = PopupWindow(popupView, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        popupWindow?.isFocusable = true

        val x = rect.left
        val y = if (rect.top > rect.height()) rect.top - rect.height() - 80 else rect.bottom

        popupWindow?.showAtLocation(popupView, Gravity.NO_GRAVITY, x, y)

        popupView.run {
            findViewById<View>(R.id.notch).run {
                setX((rect.left * 2).toFloat())
            }
            findViewById<View>(R.id.red).setOnClickListener {
                changeHighlightColor(highlight, Color.rgb(247, 124, 124))
            }
            findViewById<View>(R.id.green).setOnClickListener {
                changeHighlightColor(highlight, Color.rgb(173, 247, 123))
            }
            findViewById<View>(R.id.blue).setOnClickListener {
                changeHighlightColor(highlight, Color.rgb(124, 198, 247))
            }
            findViewById<View>(R.id.yellow).setOnClickListener {
                changeHighlightColor(highlight, Color.rgb(249, 239, 125))
            }
            findViewById<View>(R.id.purple).setOnClickListener {
                changeHighlightColor(highlight, Color.rgb(182, 153, 255))
            }
            findViewById<View>(R.id.annotation).setOnClickListener {
                showAnnotationPopup(highlight)
                popupWindow?.dismiss()
                mode?.finish()
            }
            findViewById<View>(R.id.del).run {
                visibility = if (highlight != null) View.VISIBLE else View.GONE
                setOnClickListener {
                    deleteHighlight(highlight)
                }
            }
        }

    }

    private fun changeHighlightColor(highlight: org.readium.r2.navigator.epub.Highlight? = null, color: Int) {
        if (highlight != null) {
            val navigatorHighlight = org.readium.r2.navigator.epub.Highlight(
                    highlight.id,
                    highlight.locator,
                    color,
                    highlight.style,
                    highlight.annotationMarkStyle
            )
            showHighlight(navigatorHighlight)
            addHighlight(navigatorHighlight)
        } else {
            createHighlight(color) {
                addHighlight(it)
            }
        }
        popupWindow?.dismiss()
        mode?.finish()
    }

    private fun addHighlight(highlight: org.readium.r2.navigator.epub.Highlight) {
        val annotation = highlightDB.highlights.list(highlight.id).run {
            if (isNotEmpty()) first().annotation
            else ""
        }

        highlightDB.highlights.insert(
                convertNavigationHighlight2Highlight(
                        highlight,
                        annotation,
                        highlight.annotationMarkStyle
                )
        )
    }

    private fun deleteHighlight(highlight: org.readium.r2.navigator.epub.Highlight?) {
        highlight?.let {
            highlightDB.highlights.delete(it.id)
            hideHighlightWithID(it.id)
            popupWindow?.dismiss()
            mode?.finish()
        }
    }

    private fun addAnnotation(highlight: org.readium.r2.navigator.epub.Highlight, annotation: String) {
        highlightDB.highlights.insert(
                convertNavigationHighlight2Highlight(highlight, annotation, "annotation")
        )
    }

    private fun drawHighlight() {
        val resource = publication.readingOrder[resourcePager.currentItem]
        highlightDB.highlights.listAll(bookId, resource.href).forEach {
            val highlight = convertHighlight2NavigationHighlight(it)
            showHighlight(highlight)
        }
    }

    private fun showAnnotationPopup(highlight: org.readium.r2.navigator.epub.Highlight? = null) {
        val view = layoutInflater.inflate(R.layout.popup_note, null, false)
        val alert = AlertDialog.Builder(this)
                .setView(view)
                .create()

        val annotation = highlight?.run {
            highlightDB.highlights.list(id).first().run {
                if (annotation.isEmpty() and annotationMarkStyle.isEmpty()) ""
                else annotation
            }
        }

        with(view) {
            val note = findViewById<EditText>(R.id.note)
            findViewById<TextView>(R.id.positive).setOnClickListener {
                if (note.text.isEmpty().not()) {
                    createAnnotation(highlight) {
                        addAnnotation(it, note.text.toString())
                        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(note.applicationWindowToken, InputMethodManager.HIDE_NOT_ALWAYS)
                    }
                }
                alert.dismiss()
                mode?.finish()
                popupWindow?.dismiss()
            }
            findViewById<TextView>(R.id.negative).setOnClickListener {
                alert.dismiss()
                mode?.finish()
                popupWindow?.dismiss()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(note.applicationWindowToken, InputMethodManager.HIDE_NOT_ALWAYS)
            }
            if (highlight != null) {
                findViewById<TextView>(R.id.select_text).text = highlight.locator.text.highlight
                note.setText(annotation)
            } else {
                currentSelection {
                    findViewById<TextView>(R.id.select_text).text = it?.text?.highlight
                }
            }
        }
        alert.show()
    }

    override fun onPageLoaded() {
        super.onPageLoaded()
        drawHighlight()
    }

    override fun highlightActivated(id: String) {
        rectangleForHighlightWithID(id) {
            showHighlightPopup(id, it) {
            }
        }
    }

    override fun highlightAnnotationMarkActivated(id: String) {
        val highlight = highlightDB.highlights.list(id.replace("ANNOTATION", "HIGHLIGHT")).first()
        showAnnotationPopup(convertHighlight2NavigationHighlight(highlight))
    }

    private fun convertNavigationHighlight2Highlight(highlight: org.readium.r2.navigator.epub.Highlight, annotation: String? = null, annotationMarkStyle: String? = null): Highlight {
        val resourceIndex = resourcePager.currentItem.toLong()
        val resource = publication.readingOrder[resourcePager.currentItem]
        val resourceHref = resource.href
        val resourceType = resource.type ?: ""
        val resourceTitle = resource.title ?: ""
        val currentPage = positionsDB.positions.getCurrentPage(bookId, resourceHref, currentLocator.value?.locations?.progression!!)?.let {
            it
        }

        val highlightLocations = highlight.locator.locations.copy(
            progression = currentLocator.value?.locations?.progression,
            position = currentPage?.toInt()
        )
        val locationText = highlight.locator.text

        return Highlight(
                highlight.id,
                publicationIdentifier,
                "style",
                highlight.color,
                annotation ?: "",
                annotationMarkStyle ?: "",
                resourceIndex,
                resourceHref,
                resourceType,
                resourceTitle,
                highlightLocations,
                locationText,
                bookID = bookId
        )
    }

    private fun convertHighlight2NavigationHighlight(highlight: Highlight) = org.readium.r2.navigator.epub.Highlight(
            highlight.highlightID,
            highlight.locator,
            highlight.color,
            Style.highlight,
            highlight.annotationMarkStyle
    )

    /**
     * Manage what happens when the focus is put back on the EpubActivity.
     *  - Synchronize the [R2ScreenReader] with the webView if the [R2ScreenReader] exists.
     *  - Create a [R2ScreenReader] instance if it was uninitialized.
     */
    override fun onResume() {
        super.onResume()

        /*
         * If TalkBack or any touch exploration service is activated
         * we force scroll mode (and override user preferences)
         */
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        isExploreByTouchEnabled = am.isTouchExplorationEnabled

        if (isExploreByTouchEnabled) {

            //Preset & preferences adapted
            publication.userSettingsUIPreset[ReadiumCSSName.ref(SCROLL_REF)] = true
            preferences.edit().putBoolean(SCROLL_REF, true).apply() //overriding user preferences

            userSettings = UserSettings(preferences, this, publication.userSettingsUIPreset)
            userSettings.saveChanges()

            Handler().postDelayed({
                userSettings.resourcePager = resourcePager
                userSettings.updateViewCSS(SCROLL_REF)
            }, 500)
        } else {
            if (publication.cssStyle != ContentLayout.CJK_VERTICAL.cssId) {
                publication.userSettingsUIPreset.remove(ReadiumCSSName.ref(SCROLL_REF))
            }

            userSettings = UserSettings(preferences, this, publication.userSettingsUIPreset)
            userSettings.resourcePager = resourcePager
        }

        if (this::screenReader.isInitialized) {
            if (tts_overlay.visibility == View.VISIBLE) {
                if (screenReader.currentResource != resourcePager.currentItem) {
                    screenReader.goTo(resourcePager.currentItem)
                }

                if (screenReader.isPaused) {
                    screenReader.resumeReading()
                    play_pause.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    screenReader.pauseReading()
                    play_pause.setImageResource(android.R.drawable.ic_media_play)
                }
                screenReader.onResume()
            }
        } else {
            Handler().postDelayed({
                val port = preferences.getString("$publicationIdentifier-publicationPort", 0.toString())?.toInt()
                port?.let {
                    screenReader = R2ScreenReader(this, this, this, publication, port, publicationFileName, resourcePager.currentItem)
                }
            }, 500)
        }
    }

    /**
     * Determine whether the touch exploration is enabled (i.e. that description of touched elements is orally
     * fed back to the user) and toggle the ActionBar if it is disabled and if the text to speech is invisible.
     */
    override fun toggleActionBar() {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        isExploreByTouchEnabled = am.isTouchExplorationEnabled

        if (!isExploreByTouchEnabled && tts_overlay.visibility == View.INVISIBLE) {
            super.toggleActionBar()
        }
        launch(coroutineContext) {
            mode?.finish()
        }
    }

    /**
     * Manage activity destruction.
     */
    override fun onDestroy() {
        super.onDestroy()
        activitiesLaunched.getAndDecrement()
        try {
            screenReader.shutdown()
        } catch (e: Exception) {
        }
    }

    /**
     * Communicate with the user using a toast if touch exploration is enabled, to indicate the end of a chapter.
     */
    override fun onPageEnded(end: Boolean) {
        if (isExploreByTouchEnabled) {
            if (!pageEnded == end && end) {
                toast("End of chapter")
            }
            pageEnded = end
        }
    }

    /**
     * - Stop screenReader's reading.
     * - Set the title of the menu's button for launching TTS to a value that indicates it was closed.
     * - Make the TTS view invisible.
     * - Update the TTS play/pause button to show the good resource picture.
     * - Enable toggling the scrollbar which was previously disable for TTS.
     */
    override fun dismissScreenReader() {
        super.dismissScreenReader()
        screenReader.stopReading()
        menuScreenReader?.title = resources.getString(R.string.epubactivity_read_aloud_start)
        tts_overlay.visibility = View.INVISIBLE
        play_pause.setImageResource(android.R.drawable.ic_media_play)
        allowToggleActionBar = true
    }

    override fun playTextChanged(text: String) {
        super.playTextChanged(text)
        findViewById<TextView>(R.id.tts_textView)?.text = text
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tts_textView!!, 1, 30, 1, TypedValue.COMPLEX_UNIT_DIP)
    }

    override fun playStateChanged(playing: Boolean) {
        super.playStateChanged(playing)
        if (playing) {
            play_pause?.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            play_pause?.setImageResource(android.R.drawable.ic_media_play)
        }
    }

}
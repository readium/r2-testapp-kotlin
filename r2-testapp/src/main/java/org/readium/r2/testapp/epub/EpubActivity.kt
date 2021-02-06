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
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.launch
import org.jetbrains.anko.intentFor
import org.jetbrains.anko.toast
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.R2EpubActivity
import org.readium.r2.navigator.epub.Style
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.shared.ReadiumCSSName
import org.readium.r2.shared.SCROLL_REF
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.isProtected
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.testapp.DRMManagementActivity
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.*
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.library.activitiesLaunched
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.reader.ReaderActivity
import org.readium.r2.testapp.reader.ReaderNavigation
import org.readium.r2.testapp.utils.CompositeFragmentFactory
import org.readium.r2.testapp.utils.NavigatorContract
import timber.log.Timber

class EpubActivity : R2EpubActivity(), ReaderNavigation {

    //private lateinit var model: PublicationViewModel

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

    //Menu
    private lateinit var menuDrm: MenuItem
    private lateinit var menuToc: MenuItem
    private lateinit var menuBmk: MenuItem
    lateinit var menuSearch: MenuItem
    private lateinit var menuScreenReader: MenuItem

    private var mode: ActionMode? = null
    private var popupWindow: PopupWindow? = null

    private lateinit var persistence: BookData

    private val isScreenReaderVisible: Boolean get() =
        supportFragmentManager.findFragmentById(R.id.tts_overlay) != null

    val epubNavigator: EpubNavigatorFragment get() =
        supportFragmentManager.findFragmentByTag(getString(R.string.epub_navigator_tag)) as EpubNavigatorFragment

    /**
     * Manage activity creation.
     *   - Load data from the database
     *   - Set background and text colors
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        if (activitiesLaunched.incrementAndGet() > 1 || !LibraryActivity.isServerStarted) {
            finish()
        }

        val inputData = NavigatorContract.parseIntent(this)
        val publication = inputData.publication
        val bookId = inputData.bookId
        persistence = BookData(applicationContext, bookId, publication)

        supportFragmentManager.fragmentFactory = CompositeFragmentFactory(
            OutlineFragment.createFactory(publication, persistence,  ReaderNavigation.OUTLINE_REQUEST_KEY)
        )

        supportFragmentManager.setFragmentResultListener(
            ReaderNavigation.OUTLINE_REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                val locator = result.getParcelable<Locator>(OutlineFragment::class.java.name)!!
                closeOutlineFragment(locator)
            }
        )

        super.onCreate(savedInstanceState)

        /*val modelFactory = PublicationViewModel.Factory(publication)
        model = ViewModelProvider(this, modelFactory).get(PublicationViewModel::class.java)*/

        bookmarksDB = BookmarksDatabase(this)
        booksDB = BooksDatabase(this)
        positionsDB = PositionsDatabase(this)
        highlightDB = HighligtsDatabase(this)

        launch {
            val positionCount = publication.positions().size

            currentLocator.asLiveData().observe(this@EpubActivity, Observer { locator ->
                locator ?: return@Observer

                Timber.d("locationDidChange position ${locator.locations.position ?: 0}/${positionCount} $locator")
                booksDB.books.saveProgression(locator, bookId)
            })
        }

        /*val appearancePref = preferences.getInt(APPEARANCE_REF, 0)
        val backgroundsColors = mutableListOf("#ffffff", "#faf4e8", "#000000")
        val textColors = mutableListOf("#000000", "#000000", "#ffffff")
        resourcePager.setBackgroundColor(Color.parseColor(backgroundsColors[appearancePref]))
        resourcePager.offscreenPageLimit = 1*/

        toggleActionBar()

    }

    /**
     * Override Android's option menu by inflating a custom view instead.
     *   - Initialize the search component.
     *
     * @param menu: Menu? - The menu view.
     * @return Boolean - return true.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_epub, menu)

        menuToc = menu.findItem(R.id.toc)
        menuBmk = menu.findItem(R.id.bookmark)

        menuDrm = menu.findItem(R.id.drm)
        menuDrm.isVisible = publication.isProtected

        menuScreenReader = menu.findItem(R.id.screen_reader)
        menuScreenReader.isVisible = !isExploreByTouchEnabled

        menuSearch = menu.findItem(R.id.search)
        menuSearch.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {

            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                val fragment = supportFragmentManager.fragmentFactory.instantiate(
                    classLoader,
                    SearchFragment::class.java.name
                )
                supportFragmentManager.beginTransaction().apply {
                    setReorderingAllowed(true)
                    replace(R.id.main_content, fragment)
                    setPrimaryNavigationFragment(fragment)
                    addToBackStack(null)
                    commit()
                }
                resourcePager.offscreenPageLimit = publication.readingOrder.size
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                supportFragmentManager.popBackStack()
                return true
            }
        })

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
                showOutlineFragment()
                return true
            }
            R.id.settings -> {
                userSettings.userSettingsPopUp().showAsDropDown(this.findViewById(R.id.settings), 0, 0, Gravity.END)
                return true
            }
            R.id.screen_reader -> {
                if (isScreenReaderVisible) {
                    closeScreenReaderFragment()
                } else {
                    showScreenReaderFragment()
                }
                return true
            }
            R.id.drm -> {
                startActivityForResult(intentFor<DRMManagementActivity>("publication" to publicationPath), 1)
                return true
            }
            R.id.bookmark -> {
                val resourceIndex = resourcePager.currentItem.toLong()
                val locator = currentLocator.value

                val bookmark = Bookmark(
                        bookId,
                        publicationIdentifier,
                        resourceIndex,
                        locator.href,
                        locator.type,
                        locator.title.orEmpty(),
                        locator.locations,
                        locator.text
                )

                val msg =
                    if (bookmarksDB.bookmarks.insert(bookmark) != null) {
                        locator.locations.position
                            ?.let { "Bookmark added at page $it" }
                            ?: "Bookmark added"
                        } else {
                            "Bookmark already exists"
                        }

                launch {
                    toast(msg)
                }

                Timber.d("bookmarks ${bookmarksDB.bookmarks.list(bookId).size}")

                return true
            }
            R.id.search -> {
                resourcePager.offscreenPageLimit = publication.readingOrder.size
                val searchView = menuSearch.actionView as SearchView
                searchView.clearFocus()
                return super.onOptionsItemSelected(item)
            }

            android.R.id.home -> {
                supportFragmentManager.popBackStack()
                resourcePager.offscreenPageLimit = 1
                val searchView = menuSearch.actionView as SearchView
                searchView.clearFocus()
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            if (data != null && data.getBooleanExtra("returned", false)) {
                finish()
            }
        }
    }

    override fun onActionModeStarted(mode: ActionMode?) {
        super.onActionModeStarted(mode)
        mode?.menu?.run {
            menuInflater.inflate(R.menu.menu_action_mode, this)
            findItem(R.id.highlight).setOnMenuItemClickListener {
                val currentFragment = ((resourcePager.adapter as R2PagerAdapter).mFragments.get((resourcePager.adapter as R2PagerAdapter).getItemId(resourcePager.currentItem))) as? R2EpubPageFragment

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
        val currentPage = positionsDB.positions.getCurrentPage(bookId, resourceHref, currentLocator.value.locations.progression!!)?.let {
            it
        }

        val highlightLocations = highlight.locator.locations.copy(
            progression = currentLocator.value.locations.progression,
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
            if (publication.cssStyle != "cjk-vertical") {
                publication.userSettingsUIPreset.remove(ReadiumCSSName.ref(SCROLL_REF))
            }

            userSettings = UserSettings(preferences, this, publication.userSettingsUIPreset)
            userSettings.resourcePager = resourcePager
        }
    }

    /**
     * Determine whether the touch exploration is enabled (i.e. that description of touched elements is orally
     * fed back to the user) and toggle the ActionBar if it is disabled and if the text to speech is invisible.
     */
    override fun toggleActionBar() {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        isExploreByTouchEnabled = am.isTouchExplorationEnabled

        if (!isExploreByTouchEnabled && !isScreenReaderVisible) {
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

    override fun showOutlineFragment() {
        supportFragmentManager.beginTransaction()
            .hide(epubNavigator)
            .add(R.id.main_content, OutlineFragment::class.java, Bundle(), ReaderActivity.OUTLINE_FRAGMENT_TAG)
            .addToBackStack(null)
            .commit()
    }

    override fun closeOutlineFragment(locator: Locator) {
        go(locator)
        supportFragmentManager.popBackStack()
    }

    private fun showScreenReaderFragment() {
        menuScreenReader.title = resources.getString(R.string.epubactivity_read_aloud_stop)
        allowToggleActionBar = false

        val screenReaderFragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            ScreenReaderFragment::class.java.name
        )

        supportFragmentManager.beginTransaction().apply {
            setReorderingAllowed(true)
            hide(epubNavigator)
            add(R.id.main_content, screenReaderFragment)
            addToBackStack(null)
            commit()
        }
    }

    private fun closeScreenReaderFragment() {
        menuScreenReader.title = resources.getString(R.string.epubactivity_read_aloud_start)
        allowToggleActionBar = true
        supportFragmentManager.popBackStack()
    }
}
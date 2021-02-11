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
import androidx.fragment.app.FragmentResultListener
import androidx.lifecycle.ViewModelProvider
import org.jetbrains.anko.toast
import org.json.JSONException
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.R2EpubActivity
import org.readium.r2.navigator.pager.R2EpubPageFragment
import org.readium.r2.navigator.pager.R2PagerAdapter
import org.readium.r2.shared.ReadiumCSSName
import org.readium.r2.shared.SCROLL_REF
import org.readium.r2.shared.publication.Locator
import org.readium.r2.testapp.R
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.outline.OutlineContract
import org.readium.r2.testapp.outline.OutlineFragment
import org.readium.r2.testapp.reader.BookData
import org.readium.r2.testapp.reader.EpubReaderFragment
import org.readium.r2.testapp.reader.ReaderNavigation
import org.readium.r2.testapp.reader.ReaderViewModel
import org.readium.r2.testapp.reader.toNavigatorHighlight
import org.readium.r2.testapp.tts.R2ScreenReader
import org.readium.r2.testapp.utils.NavigatorContract

class EpubActivity : R2EpubActivity(), ReaderNavigation {

    private lateinit var modelFactory: ReaderViewModel.Factory
    private lateinit var readerFragment: EpubReaderFragment

    lateinit var userSettings: UserSettings
    private lateinit var persistence: BookData

    //Accessibility
    private var isExploreByTouchEnabled = false
    private var pageEnded = false
    lateinit var screenReader: R2ScreenReader

    // Highlights
    private var mode: ActionMode? = null
    private var popupWindow: PopupWindow? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        check(LibraryActivity.isServerStarted)

        val inputData = NavigatorContract.parseIntent(this)
        val publication = inputData.publication
        val bookId = inputData.bookId
        val baseUrl = requireNotNull(inputData.baseUrl)

        persistence = BookData(applicationContext, bookId, publication)
        modelFactory = ReaderViewModel.Factory(publication, persistence)

        supportFragmentManager.setFragmentResultListener(
            OutlineContract.REQUEST_KEY,
            this,
            FragmentResultListener { _, result ->
                val locator = OutlineContract.parseResult(result)
                closeOutlineFragment(locator)
            }
        )

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            readerFragment = EpubReaderFragment.newInstance(baseUrl, bookId)

            supportFragmentManager.beginTransaction()
                .replace(R.id.activity_reader_container, readerFragment, EpubReaderActivity.READER_FRAGMENT_TAG)
                .commitNow()

        } else {
            readerFragment = supportFragmentManager.findFragmentByTag(EpubReaderActivity.READER_FRAGMENT_TAG) as EpubReaderFragment
        }

        /*val appearancePref = preferences.getInt(APPEARANCE_REF, 0)
        val backgroundsColors = mutableListOf("#ffffff", "#faf4e8", "#000000")
        val textColors = mutableListOf("#000000", "#000000", "#ffffff")
        resourcePager.setBackgroundColor(Color.parseColor(backgroundsColors[appearancePref]))
        resourcePager.offscreenPageLimit = 1*/
    }

    override fun onStart() {
        super.onStart()
        navigatorFragment = readerFragment.childFragmentManager.findFragmentByTag(getString(R.string.epub_navigator_tag)) as EpubNavigatorFragment

        // R2ScreenReader doesn't handle properly the initialization state of the underlying  TTS engine,
        // so screenReader must be started during the activity startup and access from ScreenReaderFragment
        Handler().postDelayed({
            val port = preferences.getString("$publicationIdentifier-publicationPort", 0.toString())?.toInt()
            port?.let {
                screenReader = R2ScreenReader(
                    this,
                    this,
                    this,
                    publication,
                    port,
                    publicationFileName
                )
            }
        }, 500)
    }

    override fun getDefaultViewModelProviderFactory(): ViewModelProvider.Factory {
        return modelFactory
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
                    showHighlightPopup(size = rect)
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

    private fun showHighlightPopup(highlightID: String? = null, size: Rect?) {
        popupWindow?.let {
            if (it.isShowing) {
                return
            }
        }
        val highlight: org.readium.r2.navigator.epub.Highlight? = highlightID
            ?.let { persistence.getHighlight(it).toNavigatorHighlight() }

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
                popupWindow?.dismiss()
                showAnnotationPopup(highlight)
            }
            findViewById<View>(R.id.del).run {
                visibility = if (highlight != null) View.VISIBLE else View.GONE
                setOnClickListener {
                    highlight?.let {
                        persistence.removeHighlight(highlight.id)
                        hideHighlightWithID(highlight.id)
                    }
                    popupWindow?.dismiss()
                    mode?.finish()
                }
            }
        }
    }

    private fun changeHighlightColor(highlight: org.readium.r2.navigator.epub.Highlight? = null, color: Int) {
        if (highlight != null) {
            showHighlight(highlight.copy(color = color))
            persistence.updateHighlight(highlight.id, color = color)
        } else {
            createHighlight(color) {
                persistence.addHighlight(it, currentLocator.value.locations.progression!!)
            }
        }
        popupWindow?.dismiss()
        mode?.finish()
    }

    private fun showAnnotationPopup(highlight: org.readium.r2.navigator.epub.Highlight? = null) {
        val view = layoutInflater.inflate(R.layout.popup_note, null, false)
        val alert = AlertDialog.Builder(this)
                .setView(view)
                .create()

        val annotation = highlight
            ?.let { persistence.getHighlight(highlight.id).annotation }
            .orEmpty()

        val note = view.findViewById<EditText>(R.id.note)

        fun dismiss() {
            alert.dismiss()
            mode?.finish()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(note.applicationWindowToken, InputMethodManager.HIDE_NOT_ALWAYS)
        }

        with(view) {
            if (highlight != null) {
                findViewById<TextView>(R.id.select_text).text = highlight.locator.text.highlight
                note.setText(annotation)
            } else {
                currentSelection {
                    findViewById<TextView>(R.id.select_text).text = it?.text?.highlight
                }
            }

            findViewById<TextView>(R.id.positive).setOnClickListener {
                val text = note.text.toString()
                val markStyle = if (text.isNotBlank()) "annotation" else ""

                if (highlight != null) {
                    persistence.updateHighlight(highlight.id, annotation = text, markStyle = markStyle)
                    val updatedHighlight = persistence.getHighlight(highlight.id)
                    val navHighlight = updatedHighlight.toNavigatorHighlight()
                    //FIXME: the annotation mark is not destroyed before reloading when the text becomes blank,
                    // probably because of an ID mismatch
                    showHighlight(navHighlight)
                 } else {
                    val progression = currentLocator.value.locations.progression!!
                    createAnnotation(highlight) {
                        val navHighlight = it.copy(annotationMarkStyle = markStyle)
                        persistence.addHighlight(navHighlight, progression, text)
                        showHighlight(navHighlight)
                    }
                }
                dismiss()
            }
            findViewById<TextView>(R.id.negative).setOnClickListener {
                dismiss()
            }
        }

        alert.show()
    }

    override fun onPageLoaded() {
        super.onPageLoaded()
        drawHighlight()
    }

    private fun drawHighlight() {
        val href = currentLocator.value.href
        persistence.getHighlights(href = href).forEach {
            showHighlight(it.toNavigatorHighlight())
        }
    }

    override fun highlightActivated(id: String) {
        rectangleForHighlightWithID(id) {
            showHighlightPopup(id, it)
        }
    }

    override fun highlightAnnotationMarkActivated(id: String) {
        val realId = id.replace("ANNOTATION", "HIGHLIGHT")
        val highlight = persistence.getHighlight(realId)
        showAnnotationPopup(highlight.toNavigatorHighlight())
    }

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
            .replace(R.id.activity_supp_container, OutlineFragment::class.java, Bundle(), OUTLINE_FRAGMENT_TAG)
            .hide(readerFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun closeOutlineFragment(locator: Locator) {
        readerFragment.go(locator, true)
        supportFragmentManager.popBackStack()
    }

    companion object {
        const val READER_FRAGMENT_TAG = "reader"
        const val OUTLINE_FRAGMENT_TAG = "outline"
    }
}

package org.readium.r2.testapp

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.GeneralSwipeAction
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Swipe
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import junit.framework.TestCase.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.setup.addPubToDatabase
import org.readium.r2.testapp.setup.clickCenter
import org.readium.r2.testapp.setup.copyPubFromAPKToDeviceInternalMemory
import org.readium.r2.testapp.setup.getStr
import org.readium.r2.testapp.setup.getWebViewStr
import org.readium.r2.testapp.setup.goToTOC
import org.readium.r2.testapp.setup.initTestEnv
import org.readium.r2.testapp.setup.remPubsFromDeviceInternalMemory
import org.readium.r2.testapp.setup.scrollUntilFoundTextAndClickUiAutomator
import org.readium.r2.testapp.setup.waitFor
import org.readium.r2.testapp.setup.withRecyclerViewSize
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
@LargeTest
class EPUBTests {
    @get:Rule
    var activityScenarioRule = activityScenarioRule<CatalogActivity>()

    /**
     * Destroy, recreate the books database and allow sdcard access.
     */
    @Before
    @After
    fun cleanPubs() {
        initTestEnv()
        remPubsFromDeviceInternalMemory()
    }

    /**
     * Get the current running LibraryActivity.
     */
    private fun getActivity(): Activity? {
        var activity: LibraryActivity? = null
        activityScenarioRule.scenario.onActivity {
            activity = it
        }
        return activity
    }

    /**
     * Copy file from internal app storage to external storage.
     * Add the selected epub to the database.
     * Click on the novel's button.
     *
     * @param pub: String - The name of the file in internal memory.
     */
    private fun addTestEPUB(pub: String) {
        copyPubFromAPKToDeviceInternalMemory(pub)
        addPubToDatabase(pub, getActivity() as LibraryActivity)
        waitFor(1000)

        onView(withText(getStr(R.string.epubTestName))).perform(ViewActions.click())
    }

    /**
     * Perform a swipe action right to left on given view ID.
     *
     * @param id: Int - The id of the object to swipe on.
     */
    private fun swipeRTL(id: Int, rotated: Boolean) {
        var startMove = GeneralLocation.CENTER_RIGHT
        var endMove = GeneralLocation.CENTER_LEFT

        if (rotated) {
            startMove = GeneralLocation.TOP_CENTER
            endMove = GeneralLocation.BOTTOM_CENTER
        }

        Timber.e("Start: $startMove")
        Timber.e("End: $endMove")

        //onView(withId(id)).perform(GeneralSwipeAction(Swipe.FAST, startMove,
        //    endMove, Press.FINGER))

        onView(withId(id)).perform(GeneralSwipeAction(Swipe.FAST, GeneralLocation.CENTER_RIGHT,
            GeneralLocation.CENTER_LEFT, Press.FINGER))
    }

    /**
     * Perform a swipe action left to right on the view with the given ID.
     *
     * @param id: Int - The id of the object to swipe on.
     */
    private fun swipeLTR(id: Int, rotated: Boolean) {
        var startMove = GeneralLocation.CENTER_LEFT
        var endMove = GeneralLocation.CENTER_RIGHT

        if (rotated) {
            startMove = GeneralLocation.BOTTOM_CENTER
            endMove = GeneralLocation.TOP_CENTER
        }
        onView(withId(id)).perform(GeneralSwipeAction(Swipe.FAST, startMove,
            endMove, Press.FINGER))
    }

    /**
     * Perform all the UI actions to access TTS interface.
     */
    private fun goToTTS() {
        waitFor(1000)
        clickCenter(R.id.resourcePager)
        onView(withId(R.id.screen_reader)).perform(ViewActions.click())
    }

    /**
     * Perform all the UI actions to access the settings interface.
     */
    private fun goToSettings() {
        waitFor(1000)
        clickCenter(R.id.resourcePager)
        onView(withId(R.id.settings)).perform(ViewActions.click())
    }

    /**
     * Perform all the UI actions to add a book mark and go to the bookmark interface.
     */
    private fun addBookmarkAndGoToBookmarks() {
        waitFor(1000)
        clickCenter(R.id.resourcePager)
        onView(withId(R.id.bookmark)).perform(ViewActions.click())
        onView(withId(R.id.toc)).perform(ViewActions.click())
        //onView(withId(R.id.bookmarks_tab)).perform(ViewActions.click())
        onView(withText("Bookmarks")).perform(ViewActions.click())
    }

    /**
     * Perform all the UI actions to access the Search interface.
     */
    private fun doSearch(toSearch: String) {
        waitFor(1000)
        clickCenter(R.id.resourcePager)
        waitFor(1000)
        onView(withId(R.id.search)).perform(ViewActions.click())
        onView(withId(R.id.search_src_text)).perform(ViewActions.replaceText(toSearch))
        onView(withId(R.id.search_src_text)).perform(ViewActions.pressImeActionButton())
    }

    /**
     * Test that swiping right to left page 1 behaves correctly.
     */
    @Test
    fun swipeRTLBeginning() {
        addTestEPUB(getStr(R.string.epubTestFile))
        swipeRTL(R.id.resourcePager, false)
        assertTrue(getWebViewStr().contains("J"))
    }

    /**
     * Test that swiping left to right page 1 behaves correctly.
     */
    @Test
    fun swipeLTRBeginning() {
        addTestEPUB(getStr(R.string.epubTestFile))
        swipeLTR(R.id.resourcePager, false)
        assertTrue(getWebViewStr().contains("Couverture"))
    }

    /**
     * Test that many swipes in succession will not kill the app
     */
    @Test
    fun infiniteSwipe() {
        var run = true

        addTestEPUB(getStr(R.string.epubTestFile))
        waitFor(1000)

        val t = Thread(Runnable {
            Thread.sleep(10000)
            run = false
        })

        t.start()
        var cycle = false
        while (run) {
            if (cycle) {
                cycle = false
                swipeLTR(R.id.resourcePager, false)
            } else {
                cycle = true
                swipeRTL(R.id.resourcePager, false)
            }
        }
        t.join()
    }

    /*/**
     * Test launching TTS.
     */
    @Test
    fun ttsLaunch() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTTS()
        onView(withId(R.id.play_pause)).check(matches(isDisplayed()))
    }

    /**
     * Test launching TTS and quitting it.
     */
    @Test
    fun ttsLaunchAndQuit() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTTS()
        onView(withId(R.id.screen_reader)).perform(ViewActions.click())
        onView(withId(R.id.play_pause)).check(matches(not(isDisplayed())))
    }

    /**
     * Test that text is visible when launching tts.
     */
    @Test
    fun ttsTextVisible() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTTS()
        onView(withText("Jeanne Loiseau")).check(matches(isDisplayed()))
    }

    /**
     * Test that pressing multiple times the play/pause button does not change the icon to the incorrect one.
     */
    @Test
    fun ttsPressPlayPause() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTTS()

        for (x in 0..8) // 9 iterations
            onView(withId(R.id.play_pause)).perform(ViewActions.click())

        onView(withTagValue(Is("playButton"))).check(matches(isDisplayed()))
    }

    /**
     * Test that swiping multiple times in the tts interface does not change the play/pause button.
     */
    @Test
    fun ttsSwipe() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTTS()

        for (x in 0..9)
            swipeRTL(R.id.resourcePager, false)

        onView(withTagValue(Is("pauseButton"))).check(matches(isDisplayed()))
    }*/

    /**
     * Test that going to the first chapter through the TOC works.
     */
    @Test
    fun contentGoToFirstChapter() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTOC()
        scrollUntilFoundTextAndClickUiAutomator("Chapter 1 - Loomings")
        assertTrue(getWebViewStr().contains("Chapter 1 Loomings"))
    }

    /**
     * Test that going to the last chapter through the TOC works.
     */
    @Test
    fun contentGoToEndChapter() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTOC()
        scrollUntilFoundTextAndClickUiAutomator("Epilogue")
        assertTrue(getWebViewStr().contains("Epilogue"))
    }

    /**
     * Test that going to the middle chapter through the TOC works.
     */
    @Test
    fun contentGoToMiddleChapter() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToTOC()
        scrollUntilFoundTextAndClickUiAutomator("Chapter 12 - Biographical")
        //throw Exception(getWebViewStr())
        assertTrue(getWebViewStr().contains("Chapter 12 Biographical"))
    }

    /**
     * Test bookmarking a page and jumping to it.
     */
    @Test
    fun bookmarkSimple() {
        addTestEPUB(getStr(R.string.epubTestFile))
        addBookmarkAndGoToBookmarks()
        onView(withText("cover")).perform(ViewActions.click())
        assertTrue(getWebViewStr().contains("Cover"))
    }

    /**
     * Test bookmarking a page and then deleting it.
     */
    @Test
    fun bookmarkAndDelete() {
        addTestEPUB(getStr(R.string.epubTestFile))
        addBookmarkAndGoToBookmarks()
        onView(withId(R.id.overflow)).perform(ViewActions.longClick())
        onView(withText("DELETE")).perform(ViewActions.click())
        onView(withId(R.id.overflow)).check(doesNotExist())
    }

    /**
     * Test that bookmarking twice at the same page does not generate two bookmarks.
     */
    @Test
    fun bookmarkSamePageTwice() {
        addTestEPUB(getStr(R.string.epubTestFile))
        waitFor(1000)
        clickCenter(R.id.resourcePager)
        onView(withId(R.id.bookmark)).perform(ViewActions.click())
        clickCenter(R.id.resourcePager)
        addBookmarkAndGoToBookmarks()
        onView(withId(R.id.overflow)).check(matches(isDisplayed())) //fails if present 2 times or more
    }

    /**
     * Test that the font can be changed.
     */
    @Test
    fun settingsChangeFont() {
        addTestEPUB(getStr(R.string.epubTestFile))

        swipeRTL(R.id.resourcePager, false)
        waitFor(500)
        swipeRTL(R.id.resourcePager, false)
        waitFor(500)
        swipeRTL(R.id.resourcePager, false)
        waitFor(500)

        clickCenter(R.id.resourcePager)
        onView(withId(R.id.settings)).perform(ViewActions.click())
        onView(withId(R.id.spinner_action_settings_intervall_values)).perform(ViewActions.click())
        onView(withText("Roboto")).perform(ViewActions.click())

        pressBack()
        waitFor(500)
        clickCenter(R.id.resourcePager)

        assertTrue(getWebViewStr().contains("WHOEL, Anglo-Saxon."))
    }

    /**
     * Test that the background selection can be used without crashing the app. The test does not check the result.
     */
    @Test
    fun settingsChangeBackground() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()
        onView(withId(R.id.appearance_sepia)).perform(ViewActions.click())
        pressBack()
        clickCenter(R.id.resourcePager)
    }

    /**
     * Test that enabling scrolling and scrolling through a chapter works.
     */
    @Test
    fun settingsEnableScrollModeAndScroll() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()
        onView(withId(R.id.scroll_mode)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
        UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().textContains("Cover"))
        assertTrue(getWebViewStr().contains("Cover"))
    }

    /**
     * Test that setting text justified works as intended.
     */
    @Test
    fun settingsTextJustified() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()
        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.alignment_justify)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)

    }

    /**
     * Test that setting left allignment works as intended.
     */
    @Test
    fun settingsTextLeft() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()
        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.alignment_left)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting auto columns works as intended.
     */
    @Test
    fun settingsAutoColumns() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.column_auto)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting one column works as intended.
     */
    @Test
    fun settingsOneColumn() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.column_one)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting two columns works as intended.
     */
    @Test
    fun settingsTwoColumns() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.column_two)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting min page margins works as intended.
     */
    @Test
    fun settingsMinPageMargin() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())

        onView(withId(R.id.pm_decrease)).perform(ViewActions.click())
        onView(withId(R.id.pm_decrease)).perform(ViewActions.click())
        onView(withId(R.id.pm_decrease)).perform(ViewActions.click())
        onView(withId(R.id.pm_decrease)).perform(ViewActions.click())
        onView(withId(R.id.pm_decrease)).perform(ViewActions.click())
        onView(withId(R.id.pm_decrease)).perform(ViewActions.click())

        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting max page margin works as intended.
     */
    @Test
    fun settingsMaxPageMargin() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())

        onView(withId(R.id.pm_increase)).perform(ViewActions.click())
        onView(withId(R.id.pm_increase)).perform(ViewActions.click())
        onView(withId(R.id.pm_increase)).perform(ViewActions.click())
        onView(withId(R.id.pm_increase)).perform(ViewActions.click())
        onView(withId(R.id.pm_increase)).perform(ViewActions.click())
        onView(withId(R.id.pm_increase)).perform(ViewActions.click())
        onView(withId(R.id.pm_increase)).perform(ViewActions.click())
        onView(withId(R.id.pm_increase)).perform(ViewActions.click())

        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting min word spacing works as intended.
     */
    @Test
    fun settingsMinWordSpacing() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.ws_decrease)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting max word spacing works as intended
     */
    @Test
    fun settingsMaxWordSpacing() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())

        onView(withId(R.id.ws_increase)).perform(ViewActions.click())
        onView(withId(R.id.ws_increase)).perform(ViewActions.click())

        pressBack()


        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting min letter spacing works as intended.
     */
    @Test
    fun settingsMinLetterSpacing() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.ls_decrease)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting max letter spacing works as intended.
     */
    @Test
    fun settingsMaxLetterSpacing() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())

        onView(withId(R.id.ls_increase)).perform(ViewActions.click())
        onView(withId(R.id.ls_increase)).perform(ViewActions.click())
        onView(withId(R.id.ls_increase)).perform(ViewActions.click())
        onView(withId(R.id.ls_increase)).perform(ViewActions.click())
        onView(withId(R.id.ls_increase)).perform(ViewActions.click())
        onView(withId(R.id.ls_increase)).perform(ViewActions.click())
        onView(withId(R.id.ls_increase)).perform(ViewActions.click())
        onView(withId(R.id.ls_increase)).perform(ViewActions.click())

        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting minimum line height works as intended.
     */
    @Test
    fun settingsMinLineHeight() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())
        onView(withId(R.id.lh_decrease)).perform(ViewActions.click())
        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that setting maximum line height works as intended.
     */
    @Test
    fun settingsMaxLineHeight() {
        addTestEPUB(getStr(R.string.epubTestFile))
        goToSettings()

        onView(withText("Advanced")).perform(ViewActions.click())

        onView(withId(R.id.lh_increase)).perform(ViewActions.click())
        onView(withId(R.id.lh_increase)).perform(ViewActions.click())
        onView(withId(R.id.lh_increase)).perform(ViewActions.click())
        onView(withId(R.id.lh_increase)).perform(ViewActions.click())

        pressBack()

        swipeRTL(R.id.resourcePager, false)
        swipeRTL(R.id.resourcePager, false)
    }

    /**
     * Test that searching for a word that is not present works as intended.
     */
    @Test
    fun searchSimpleNoResult() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("alibaba")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching for a Lorem Ipsum paragraph does not crash the app.
     */
    @Test
    fun searchLongNoResult() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch(getStr(R.string.lorem_ipsum))
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching for a word returns the right amount of elements.
     */
    @Test
    fun searchSimpleResult() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("his")
        //waitFor(20000)
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching for a sentance finds all the words.
     */
    @Test
    fun searchLongResult() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("I lay there dismally calculating that sixteen entire hours must elapse before I could hope for a resurrection.")
        //waitFor(10000)
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(13)))
    }

    /**
     * Test that searching a backslash doesn't crash the app.
     */
    @Test
    fun searchBackslash() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("\\")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching a double quote does not crash the app.
     */
    @Test
    fun searchDoubleQuotes() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("\"")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching two slashes does not crash the app.
     */
    @Test
    fun searchDoubleSlash() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("//")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching two backslashes does not crash the app.
     */
    @Test
    fun searchTwoBackSlash() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("\\\\")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching a word in Cyrillic does not crash the app.
     */
    @Test
    fun searchCyrillic() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("классическая")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching a work in Kanji does not crash the app.
     */
    @Test
    fun searchKanji() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("ロレム・イプサム")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching a work in arabic does not crash the app.
     */
    @Test
    fun searchArabic() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("خلافاَ للاعتقاد")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }

    /**
     * Test that searching an emoji does not crash the app.
     */
    @Test
    fun searchEmoji() {
        addTestEPUB(getStr(R.string.epubTestFile))
        doSearch("👦")
        onView(withId(R.id.search_listView)).check(matches(withRecyclerViewSize(0)))
    }
}
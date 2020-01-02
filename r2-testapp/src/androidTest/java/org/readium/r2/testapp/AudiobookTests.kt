/**
 * Author: Didier HEMERY
 * Trainee @EDRLab
 * File: AudiobookTests.kt
 */

package org.readium.r2.testapp

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.setup.addPubToDatabase
import org.readium.r2.testapp.setup.copyPubFromAPKToDeviceInternalMemory
import org.readium.r2.testapp.setup.getStr
import org.readium.r2.testapp.setup.initTestEnv
import org.readium.r2.testapp.setup.remPubsFromDeviceInternalMemory
import org.readium.r2.testapp.setup.waitFor
import org.hamcrest.CoreMatchers.`is` as Is


@RunWith(AndroidJUnit4::class)
@LargeTest
class AudiobookTests {
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

    private fun addTestAudiobook(pub: String) {
        copyPubFromAPKToDeviceInternalMemory(pub)
        addPubToDatabase(pub, getActivity() as LibraryActivity)
        waitFor(1000)

        onView(withId(R.id.coverImageView)).perform(ViewActions.click())
    }

    /**
     * Test that changing orientation doesn't resume playing if stopped before.
     */
    @Test
    fun testClickSwapOrientation() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))

        val device = UiDevice.getInstance(getInstrumentation())

        onView(withId(Is(R.id.play_pause))).perform(ViewActions.click())

        device.setOrientationLeft()
        device.setOrientationNatural()

        onView(withTagValue(Is(getStr(R.string.playButton)))).check(matches(isDisplayed()))
    }


    /**
     * Test that changing orientation doesn't pause the book playback.
     */
    @Test
    fun testSwapOrientation() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))

        val device = UiDevice.getInstance(getInstrumentation())

        device.setOrientationLeft()
        device.setOrientationNatural()

        onView(withTagValue(Is(getStr(R.string.pauseButton)))).check(matches(isDisplayed()))
    }

    /**
     * Test that a bookmark can be added.
     */
    @Test
    fun testBookmarkSimple() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))

        onView(withId(Is(R.id.bookmark))).perform(ViewActions.click())
        onView(withId(Is(R.id.toc))).perform(ViewActions.click())
        onView(withText("BOOKMARKS")).perform(ViewActions.click())
        onView(allOf(withId(R.id.bookmark_chapter), withText("00 - Dedication"))).check(matches(isDisplayed()))
    }

    /**
     * Test that the TOC works for the last chapter.
     */
    @Test
    fun testTOC() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        onView(withId(Is(R.id.toc))).perform(ViewActions.click())
        onView(withText("02 - John Bunyan")).perform(ViewActions.click())
        onView(withText("02 - John Bunyan")).check(matches(isDisplayed()))
    }

    /**
     * Test that the previous chapter arrow works for the middle chapter.
     */
    @Test
    fun testPrevChapMiddle() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        onView(withId(Is(R.id.toc))).perform(ViewActions.click())
        onView(withText("01 - Mr. Pepys")).perform(ViewActions.click())
        onView(withId(R.id.prev_chapter)).perform(ViewActions.click())
        onView(withText("00 - Dedication")).check(matches(isDisplayed()))
    }

    /**
     * Test that the next chapter arrow works for the middle chapter.
     */
    @Test
    fun testNextChapMiddle() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        onView(withId(Is(R.id.toc))).perform(ViewActions.click())
        onView(withText("01 - Mr. Pepys")).perform(ViewActions.click())
        onView(withId(R.id.next_chapter)).perform(ViewActions.click())
        onView(withText("02 - John Bunyan")).check(matches(isDisplayed()))
    }

    /**
     * Test that the next chapter arrow works for the last chapter.
     */
    @Test
    fun testNextChapEnd() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        onView(withId(Is(R.id.toc))).perform(ViewActions.click())
        onView(withText("02 - John Bunyan")).perform(ViewActions.click())
        onView(withId(R.id.next_chapter)).perform(ViewActions.click())
        onView(withText("02 - John Bunyan")).check(matches(isDisplayed()))
    }

    /**
     * Test that the previous chapter arrow works for the first chapter.
     */
    @Test
    fun testPrevChapBeginning() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        onView(withId(Is(R.id.toc))).perform(ViewActions.click())
        onView(withText("00 - Dedication")).perform(ViewActions.click())
        onView(withId(R.id.prev_chapter)).perform(ViewActions.click())
        onView(withText("00 - Dedication")).check(matches(isDisplayed()))
    }

    @Test
    fun testForward() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        onView(withId(Is(R.id.play_pause))).perform(ViewActions.click())
        onView(withId(Is(R.id.fast_forward))).perform(ViewActions.click())
        onView(withText("0:10")).check(matches(isDisplayed()))
    }

    @Test
    fun testForwardAndBackward() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        onView(withId(Is(R.id.play_pause))).perform(ViewActions.click())
        onView(withId(Is(R.id.fast_forward))).perform(ViewActions.click())
        onView(withId(Is(R.id.fast_back))).perform(ViewActions.click())
        onView(withText("0:0")).check(matches(isDisplayed()))
    }

    @Test
    fun testBackwardAt5Seconds() {
        addTestAudiobook(getStr(R.string.audiobookTestFile))
        waitFor(5000)
        onView(withId(Is(R.id.play_pause))).perform(ViewActions.click())
        onView(withId(Is(R.id.fast_back))).perform(ViewActions.click())
        onView(withText("0:5")).check(matches(isDisplayed()))
    }

    // TODO: ADD DRAG BAR TESTS
}

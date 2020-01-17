/**
 * Author: Didier HEMERY
 * Trainee @EDRLab
 * File: AddRemoteBook.kt
 */

package org.readium.r2.testapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.testapp.setup.getStr
import org.readium.r2.testapp.setup.initTestEnv
import org.readium.r2.testapp.setup.waitFor
import org.hamcrest.CoreMatchers.`is` as Is


@RunWith(AndroidJUnit4::class)
@LargeTest
class AddRemotePub
{
    @get:Rule var activityScenarioRule = activityScenarioRule<CatalogActivity>()

    /**
     * Destroy, recreate the books database and allow sdcard access.
     */
    @Before
    @After
    fun cleanPubs() {
        initTestEnv()
    }

    /**
     * Contain the code that will open the add publication dialog and fill it,
     * before pressing the ADD button.
     *
     * pubURL: String - The publication URL
     * waitTime: Long - The time to wait for the publication to be added
     */
    private fun setupImportPublication(pubURL: String, waitTime: Long) {
        onView(withTagValue(Is(getStr(R.string.tagButtonAddBook)))).perform(ViewActions.click())
        onView(withTagValue(Is(getStr(R.string.tagButtonAddURLBook)))).perform(ViewActions.click())
        onView(withTagValue(Is(getStr(R.string.tagInputAddURLBook)))).perform(ViewActions.typeText(pubURL))
        onView(withText("ADD")).perform(ViewActions.click())
        waitFor(waitTime)
    }

    /**
     * Core test function. Call setupImportPublication, then look if the result is displayed.
     *
     * pubUrl: String - The publication URL
     * displayName: String - The displayed name of the added publication
     * waitTime: Long - Time to wait before verifying results
     */
    private fun importPublicationWorks(pubURL: String, displayName: String, waitTime: Long) {
        setupImportPublication(pubURL, waitTime)
        onView(withText(displayName)).check(matches(isDisplayed()))
    }

    /**
     * Test if a remote Audiobook that is valid can be added.
     */
    @Test
    fun importRemoteAudioBookPublicationWorks() {
        importPublicationWorks(getStr(R.string.audiobookTestRemoteURL),
                getStr(R.string.audiobookTestName), 5000)
    }

    /**
     * Test if a remote web publication that is valid can be added.
     */
    @Test
    fun importRemoteWebPublicationWorks() {
        importPublicationWorks(getStr(R.string.webPublicationTestRemoteURL),
                getStr(R.string.webPublicationTestRemoteName), 5000)
    }

    /**
     * Test if a remote OPDS2 that is valid can be added.
     */
    @Test
    fun importRemoteOPDS2Works() {
        importPublicationWorks(getStr(R.string.OPDS2TestRemoteURL),
                getStr(R.string.OPDS2TestRemoteName), 5000)
    }

    /**
     * Test that an invalid link can't be added and does not crash the testapp.
     */
    @Test
    fun importRemotePublicationFail() {
        setupImportPublication("Book", 5000)
        onView(ViewMatchers.withId(R.id.coverImageView)).check(ViewAssertions.doesNotExist())
    }
}
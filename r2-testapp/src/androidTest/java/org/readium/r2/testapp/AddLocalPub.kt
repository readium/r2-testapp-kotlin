/**
 * Author: Didier HEMERY
 * Trainee @EDRLab
 * File: AddLocalBook.kt
 */

package org.readium.r2.testapp

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.hamcrest.CoreMatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.testapp.setup.clickButtonUiAutomator
import org.readium.r2.testapp.setup.copyPubFromAPKToDeviceInternalMemory
import org.readium.r2.testapp.setup.getDeviceModelName
import org.readium.r2.testapp.setup.getStr
import org.readium.r2.testapp.setup.initTestEnv
import org.readium.r2.testapp.setup.remPubsFromDeviceInternalMemory
import org.readium.r2.testapp.setup.scrollUntilFoundTextAndClickUiAutomator
import org.readium.r2.testapp.setup.waitFor


@RunWith(AndroidJUnit4::class)
@LargeTest
class AddLocalPub
{
    /**
     * Launches the CatalogActivity for the tests
     */
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
     * Once the device's file explorer is open, navigate into the test files' folder.
     * @param pub: String - The name of the file to select in the file explorer.
     */
    private fun selectFileInExplorer(pub: String) {
        //TODO: add a variant for each test device used, as the UI changes on different versions.

        when (getDeviceModelName()){
            "HUAWEI BTV-W09" -> {
                clickButtonUiAutomator(getStr(R.string.InternalStorage_SDK23))
                scrollUntilFoundTextAndClickUiAutomator(getStr(R.string.Folder1))
                clickButtonUiAutomator(getStr(R.string.Folder2))
                scrollUntilFoundTextAndClickUiAutomator(getStr(R.string.Folder3))
                clickButtonUiAutomator(getStr(R.string.Folder4))
                clickButtonUiAutomator(pub)
            }
            else -> throw Exception(getStr(R.string.UnsupportedDeviceTest))
        }

    }

    /**
     * Reset the imported database, imports the publication then waits some time.
     */
    fun importTestPublication(pub: String) {
        copyPubFromAPKToDeviceInternalMemory(pub)
        onView(withTagValue(CoreMatchers.`is`(getStr(R.string.tagButtonAddBook)))).perform(ViewActions.click())
        onView(withTagValue(CoreMatchers.`is`(getStr(R.string.tagButtonAddDeviceBook)))).perform(ViewActions.click())
        selectFileInExplorer(pub)
        waitFor(1000)
    }

    /**
     * Test that a valid publication could be imported by checking that there is a cover image view.
     */
    fun importTestPublicationWorks(pub: String) {
        importTestPublication(pub)
        onView(withId(R.id.coverImageView)).check(matches(isDisplayed()))
    }

    /**
     * Test that an invalid publication could not be imported by checking that.
     */
    fun importTestPublicationFail(pub: String) {
        importTestPublication(pub)
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a Local CBZ that is valid can be added.
     */
    @Test
    fun importLocalCBZPWorks() {
        importTestPublicationWorks(getStr(R.string.cbzTestFile))
        onView(withId(R.id.coverImageView)).check(matches(isDisplayed()))
    }

    /**
     * Test if a Local EPub that is valid can be added.
     */
    @Test
    fun importLocalEPubWorks() {
        importTestPublicationWorks(getStr(R.string.epubTestFile))
        onView(withId(R.id.coverImageView)).check(matches(isDisplayed()))
    }

    /**
     * Test if a Local Audiobook that is valid can be added.
     */
    @Test
    fun importLocalAudioBookWorks() {
        importTestPublicationWorks(getStr(R.string.audiobookTestFile))
        onView(withId(R.id.coverImageView)).check(matches(isDisplayed()))
    }

    /**
     * Running these tests will keep the app running. Once the issue that make them keep the app busy
     * is taken care of, they should be un-commented then.
     */

    /**
     * Test if a local Invalid CBZ fails as it should.
     */
    @Test
    fun importLocalCBZPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidCBZFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a local Invalid Epub (empty zipped) fails as it should.
     */
    @Test
    fun importLocalEpubPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidEPubFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a local Invalid Audiobook (empty zipped) fails as it should.
     */
    @Test
    fun importLocalAudiobookPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidAudioBookFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a local Invalid DiViNa (empty zipped) fails as it should.
     */
    @Test
    fun importLocalDivinaPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidDiViNaFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a local Invalid empty CBZ fails as it should.
     */
    @Test
    fun importLocalEmptyCBZPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidEmptyCBZFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a local Invalid empty Epub fails as it should.
     */
    @Test
    fun importLocalEmptyEpubPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidEmptyEPubFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a local Invalid empty Audiobook fails as it should.
     */
    @Test
    fun importLocalEmptyAudiobookPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidEmptyAudioBookFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }

    /**
     * Test if a local Invalid empty DiViNa fails as it should.
     */
    @Test
    fun importLocalEmptyDivinaPublicationFail() {
        importTestPublicationFail(getStr(R.string.invalidEmptyDiViNaFile))
        onView(withId(R.id.coverImageView)).check(doesNotExist())
    }
}
package org.readium.r2.testapp

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.testapp.library.LibraryActivity
import org.readium.r2.testapp.setup.addPubToDatabase
import org.readium.r2.testapp.setup.clickBottomSide
import org.readium.r2.testapp.setup.clickCenter
import org.readium.r2.testapp.setup.clickLeftSide
import org.readium.r2.testapp.setup.clickRightSide
import org.readium.r2.testapp.setup.copyPubFromAPKToDeviceInternalMemory
import org.readium.r2.testapp.setup.getStr
import org.readium.r2.testapp.setup.initTestEnv
import org.readium.r2.testapp.setup.remPubsFromDeviceInternalMemory
import org.readium.r2.testapp.setup.scrollUntilFoundTextAndClickUiAutomator
import org.readium.r2.testapp.setup.waitFor

@RunWith(AndroidJUnit4::class)
@LargeTest
class DiViNaTests {
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
        UiDevice.getInstance(getInstrumentation()).setOrientationNatural()
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
     * Add the selected DiViNa to the database.
     * Click on the novel's button.
     *
     * @param pub: String - The name of the file in internal memory.
     */
    private fun addTestDivINa(pub: String) {
        copyPubFromAPKToDeviceInternalMemory(pub)
        addPubToDatabase(pub, getActivity() as LibraryActivity)
        waitFor(1000)

        onView(withText(getStr(R.string.divinaTestName))).perform(ViewActions.click())
    }

    /**
     * Perform all the UI actions to access the TOC interface.
     */
    fun goToTOC() {
        waitFor(1000)
        clickCenter(R.id.divinaWebView)
        onView(ViewMatchers.withId(R.id.toc)).perform(ViewActions.click())
    }

    /**
     * Test that going to the third picture through the toc works.
     */
    @Test
    fun goToPicture3() {
        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        onView(withText("Will-slide-in.png")).perform(ViewActions.click())
        //TODO: implement a way to test if the displayed picture is the right one
        //  Tag the picture as it is displayed, then use a custom matcher to match reference picture with it
    }

    /**
     * Test that going to the first picture through the toc works.
     */
    @Test
    fun goToFirstPicture() {
        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        onView(withText("start.png")).perform(ViewActions.click())
    }

    /**
     * Test that going to the last picture through the toc works.
     */
    @Test
    fun goToLastPicture() {
        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        onView(withText("The-end.png")).perform(ViewActions.click())
    }

    /**
     * Test that clicking right in the middle of the DiViNa works.
     */
    @Test
    fun clickRightSideMiddlePub() {
        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        onView(withText("Will-push.png")).perform(ViewActions.click())
        clickRightSide(R.id.divinaWebView)
    }

    /**
     * Test that clicking left in the middle of the DiViNa works.
     */
    @Test
    fun clickLeftSideMiddlePub() {
        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        onView(withText("Will-push.png")).perform(ViewActions.click())
        clickLeftSide(R.id.divinaWebView)
    }

    /**
     * Test that clicking left at the beginning of the DiViNa works.
     */
    @Test
    fun clickLeftSideBeginningPub() {
        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        onView(withText("Will-push.png")).perform(ViewActions.click())
        clickLeftSide(R.id.divinaWebView)
    }

    /**
     * Test that clicking right at the end of the DiViNa works.
     */
    @Test
    fun clickRightSideEndPub() {
        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        onView(withText("The-end.png")).perform(ViewActions.click())
        waitFor(1000)
        clickRightSide(R.id.divinaWebView)
    }

    @Test
    fun clickRightSideEndPubRotate() {
        UiDevice.getInstance(getInstrumentation()).setOrientationLeft()

        addTestDivINa(getStr(R.string.divinaTestFile))
        goToTOC()
        scrollUntilFoundTextAndClickUiAutomator("The-end.png")
        waitFor(1000)
        clickBottomSide(R.id.divinaWebView)
    }
}
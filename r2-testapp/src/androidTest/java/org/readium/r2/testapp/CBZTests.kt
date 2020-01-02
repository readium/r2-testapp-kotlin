package org.readium.r2.testapp

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
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

@RunWith(AndroidJUnit4::class)
@LargeTest
class CBZTests {
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
     * Add the selected CBZ to the database.
     * Click on the novel's button.
     *
     * @param pub: String - The name of the file in internal memory.
     */
    private fun addTestCBZ(pub: String) {
        copyPubFromAPKToDeviceInternalMemory(pub)
        addPubToDatabase(pub, getActivity() as LibraryActivity)
        waitFor(1000)

        onView(withId(R.id.coverImageView)).perform(ViewActions.click())
    }

    /**
     * Test that importing a CBZ file works, and that the TOC can be used without crashing the app.
     */
    @Test
    fun importAndGoToPictureTest() {
        addTestCBZ(getStr(R.string.cbzTestFile))
        onView(withId(R.id.resourcePager)).perform(ViewActions.click())
        onView(withId(R.id.toc)).perform(ViewActions.click())
        onView(withText("edrlab_logo.jpg")).perform(ViewActions.click())
    }
}
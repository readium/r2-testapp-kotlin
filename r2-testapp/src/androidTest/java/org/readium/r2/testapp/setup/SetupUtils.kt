/**
 * Author: Didier HEMERY
 * Trainee @EDRLab
 * File: SetupUtils.kt
 */

package org.readium.r2.testapp.setup

import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.jetbrains.anko.db.AUTOINCREMENT
import org.jetbrains.anko.db.BLOB
import org.jetbrains.anko.db.INTEGER
import org.jetbrains.anko.db.PRIMARY_KEY
import org.jetbrains.anko.db.TEXT
import org.jetbrains.anko.db.createTable
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.BOOKMARKSTable
import org.readium.r2.testapp.db.BOOKSTable
import org.readium.r2.testapp.db.BookmarksDatabase
import org.readium.r2.testapp.db.BooksDatabase
import org.readium.r2.testapp.db.POSITIONSTable
import org.readium.r2.testapp.db.PositionsDatabase
import org.readium.r2.testapp.library.LibraryActivity
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Initializes the testing environment by destroying the Books, bookmarks and positions tables, and
 * creating them again. Would be preferable to have a function to create it in the BooksDatabase.kt
 * file. It also allows permission to access the sdcard. This function allows the tests to run
 * without having to manually configure the app.
 *
 * As this function will always be called before any test, check device used is allowed for UI tests.
 */
fun initTestEnv() {
    val db = BooksDatabase(getInstrumentation().targetContext)
    db.books.dropTable()
    db.shared.use {
        createTable(BOOKSTable.NAME, true,
            BOOKSTable.ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
            BOOKSTable.TITLE to TEXT,
            BOOKSTable.AUTHOR to TEXT,
            BOOKSTable.HREF to TEXT,
            BOOKSTable.IDENTIFIER to TEXT,
            BOOKSTable.COVER to BLOB,
            BOOKSTable.EXTENSION to TEXT,
            BOOKSTable.CREATION to INTEGER,
            BOOKSTable.PROGRESSION to TEXT)
    }

    val bmdb = BookmarksDatabase(getInstrumentation().targetContext)
    bmdb.bookmarks.dropTable()
    bmdb.shared.use {
        createTable(BOOKMARKSTable.NAME, true,
                BOOKMARKSTable.ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
                BOOKMARKSTable.BOOK_ID to INTEGER,
                BOOKMARKSTable.PUBLICATION_ID to TEXT,
                BOOKMARKSTable.RESOURCE_INDEX to INTEGER,
                BOOKMARKSTable.RESOURCE_HREF to TEXT,
                BOOKMARKSTable.RESOURCE_TYPE to TEXT,
                BOOKMARKSTable.RESOURCE_TITLE to TEXT,
                BOOKMARKSTable.LOCATION to TEXT,
                BOOKMARKSTable.LOCATOR_TEXT to TEXT,
                BOOKMARKSTable.CREATION_DATE to INTEGER)
    }

    val pdb = PositionsDatabase(getInstrumentation().targetContext)
    pdb.positions.dropTable()
    pdb.shared.use {
        createTable(POSITIONSTable.NAME, true,
                POSITIONSTable.ID to INTEGER + PRIMARY_KEY + AUTOINCREMENT,
                POSITIONSTable.BOOK_ID to INTEGER,
                POSITIONSTable.SYNTHETIC_PAGE_LIST to TEXT)
    }

    val perm = UiDevice.getInstance(getInstrumentation()).findObject(UiSelector().text("Allow"))
    if (perm.exists())
        perm.click()

    waitFor(3000)
    //TODO: Invalidate view (postInvalidate?) to redraw everything and not have some tests fail.
}

/**
 * Writes the file in the androidTest app assets folder to the device's internal storage.
 * @ /Android/data/org.readium.r2reader.test
 * Logs error if it happens.
 *
 * pub: String - The name of the publication to add to internal memory.
 */
fun copyPubFromAPKToDeviceInternalMemory(pub: String) {
    try {
        //Log.w("PATH", getInstrumentation().context.getExternalFilesDir(null)!!.absolutePath)
        val file = File(getInstrumentation().context.getExternalFilesDir(null), pub)
        val ins = getInstrumentation().context.assets.open(pub)
        val outs = FileOutputStream(file)
        var data = ByteArray(ins.available())
        ins.read(data)
        outs.write(data)
        ins.close()
        outs.close()
    } catch (e: IOException) {
        Log.e("IO ERROR", e.stackTrace.toString())
    }
}

/**
 * Removes every single file in the device's internal storage
 * @ /Android/data/org.readium.r2reader.test
 */
fun remPubsFromDeviceInternalMemory() {
    try {
        getInstrumentation().context.getExternalFilesDir(null)!!.walk().forEach {
            it.delete()
        }
        getInstrumentation().targetContext.getExternalFilesDir(null)!!.walk().forEach {
            it.delete()
        }
    } catch (e: IOException) {
        Log.e("IO ERROR", e.stackTrace.toString())
    }
}

/**
 * The function is a shortcut for calling UiAutomator. It performs a click on a view that holds the text contained in 'button'
 *
 * button: String - The view to click on. It is targeted through the text shown on screen.
 */
fun clickButtonUiAutomator(button: String) {
    UiDevice.getInstance(getInstrumentation()).findObject(UiSelector().text(button)).click()
}

/**
 * Uses UiAutomator to scroll a scrollable view until the text searched appears.
 * clickButtonUiAutomator is then called with the text to click on.
 *
 * test:String - The text the function should scroll to and click.
 */
fun scrollUntilFoundTextAndClickUiAutomator(text: String) {
    UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(text))
    clickButtonUiAutomator(text)
}

/**
 * Creates a separate thread that will wait for 'time'. Then synchronizes.
 *
 * time: Long - Time to wait in milliseconds
 */
fun waitFor(time: Long) {
    val t = Thread(Runnable {Thread.sleep(time)})
    t.start()
    t.join()
}

/**
 * Gets resource string for the given ID.
 *
 * strID: Int - The resource string ID.
 */
fun getStr(strID: Int) : String {
    return getInstrumentation().targetContext.getString(strID)
}

/**
 * Adds pub to the database
 *
 * pub: String - The name of the publication to add to the database
 * activity: LibraryActivity? - Instance of LibraryActivity.
 */
fun addPubToDatabase(pub: String, activity: LibraryActivity?) {
    val method = LibraryActivity::class.java.getDeclaredMethod("addBook", String::class.java,
            String::class.java, String::class.java, InputStream::class.java)
    method.isAccessible = true

    val attr = LibraryActivity::class.java.getDeclaredField("R2DIRECTORY")
    attr.isAccessible = true

    val uuid = UUID.randomUUID().toString()
    val outputFilePath = (attr.get(activity) as String) + "/" + uuid
    val input = getInstrumentation().context.assets.open(pub)
    method.invoke(activity, pub, uuid, outputFilePath, input)
}

fun withRecyclerViewSize(size: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {

        override fun matchesSafely(view: View): Boolean {
            val actualListSize = (view as RecyclerView).adapter!!.itemCount
            //Log.e(TAG, "RecyclerView actual size $actualListSize")
            return actualListSize == size
        }

        override fun describeTo(description: Description) {
            description.appendText("RecyclerView should have $size items")
        }
    }
}

/**
 * Perform a single click action on the view with the given ID.
 *
 * @param id: Int - The id of the object to swipe on.
 */
fun clickCenter(id: Int) {
    Espresso.onView(ViewMatchers.withId(id)).perform(GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER,
        Press.FINGER, InputDevice.SOURCE_ANY, MotionEvent.BUTTON_PRIMARY))
}

/**
 * Perform all the UI actions to access the TOC interface.
 */
fun goToTOC() {
    waitFor(1000)
    clickCenter(R.id.resourcePager)
    Espresso.onView(ViewMatchers.withId(R.id.toc)).perform(ViewActions.click())
}

/**
 * Returns the whole text (without html) contained inside a webview.
 *
 * @return: String - The content of the webview
 */
fun getWebViewStr(): String {
    val mDevice : UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val webView = mDevice.findObject(By.clazz(WebView::class.java))
    val str = listAllChildren(webView)
    //Log.e("WebView text", str)
    return str
}

/**
 * Appends all the parts of the text together.
 *
 * @param obj: UiObject2 - The ui element containing the text we want.
 */
private fun listAllChildren(obj: UiObject2): String {
    var str = obj.text

    for (a in obj.children)
        str += "\n" + listAllChildren(a)

    return str
}

/**
 * Click the middle of the right side of the UI element.
 *
 * @param id: Int - The id of the view to click on.
 */
fun clickRightSide(id: Int) {
    Espresso.onView(ViewMatchers.withId(id))
        .perform(GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER_RIGHT,Press.FINGER))// GeneralLocation.CENTER_RIGHT, Press.FINGER))
}

/**
 * Click the middle of the left side of the UI element.
 *
 * @param id: Int - The id of the view to click on.
 */
fun clickLeftSide(id: Int) {
    Espresso.onView(ViewMatchers.withId(id))
        .perform(GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER_LEFT,Press.FINGER))
}

/**
 * Click the middle of the top side of the UI element.
 *
 * @param id: Int - The id of the view to click on.
 */
fun clickTopSide(id: Int) {
    Espresso.onView(ViewMatchers.withId(id))
        .perform(GeneralClickAction(Tap.SINGLE, GeneralLocation.TOP_CENTER,Press.FINGER))
}

/**
 * Click the middle of the bottom side of the UI element.
 *
 * @param id: Int - The id of the view to click on.
 */
fun clickBottomSide(id: Int) {
    Espresso.onView(ViewMatchers.withId(id))
        .perform(GeneralClickAction(Tap.SINGLE, GeneralLocation.BOTTOM_CENTER,Press.FINGER))
}
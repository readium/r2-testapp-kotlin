/**
 * Author: Didier HEMERY
 * Trainee @EDRLab
 * File: SetupUtils.kt
 */

package org.readium.r2.testapp.setup

import android.app.Activity
import android.content.Context
import android.os.Build
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
import org.readium.r2.navigator.pager.R2ViewPager
import org.readium.r2.testapp.R
import org.readium.r2.testapp.db.BOOKMARKSTable
import org.readium.r2.testapp.db.BOOKSTable
import org.readium.r2.testapp.db.BookmarksDatabase
import org.readium.r2.testapp.db.BooksDatabase
import org.readium.r2.testapp.db.POSITIONSTable
import org.readium.r2.testapp.db.PositionsDatabase
import org.readium.r2.testapp.library.LibraryActivity
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

/**
 * Clears the user preferences. //FIXME DOES NOT WORK
 *
 * Initializes the testing environment by destroying that [BOOKSTable], [BOOKMARKSTable] and [POSITIONSTable] databases
 * and then recreating them.
 *
 * The function then checks if the window asking for permission exists and clicks on allow if
 * that is the case.
 *
 * TODO: Should then invalidate the CatalogActivity view to remove duplicate entries with just deleted items.
 *
 * The last instruction, [waitFor] is used to allow the databases to finish being created. Otherwise tests would fail
 * and the testsuite would directly stop.
 */
fun initTestEnv() {
    val context = getInstrumentation().targetContext

    context.getSharedPreferences("org.readium.r2.testapp", Context.MODE_PRIVATE).edit().clear().commit()

    val db = BooksDatabase(context)
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
}

/**
 * Writes the file in the androidTest app assets folder to the device's internal storage.
 * at /Android/data/org.readium.r2reader.test
 *
 * @param pub: String - The name of the publication to add to internal memory.
 */
fun copyPubFromAPKToDeviceInternalMemory(pub: String) {
    val file = File(getInstrumentation().context.getExternalFilesDir(null), pub)
    val ins = getInstrumentation().context.assets.open(pub)
    val outs = FileOutputStream(file)
    val data = ByteArray(ins.available())
    ins.read(data)
    outs.write(data)
    ins.close()
    outs.close()
}

/**
 * Removes every single file in the device's internal storage
 * at /Android/data/org.readium.r2reader.test
 *
 * [walk] returns a tree of each element present in the directory.
 */
fun remPubsFromDeviceInternalMemory() {
    getInstrumentation().context.getExternalFilesDir(null)!!.walk().forEach {
        it.delete()
    }
    getInstrumentation().targetContext.getExternalFilesDir(null)!!.walk().forEach {
        it.delete()
    }
}

/**
 * The function is a shortcut for calling UiAutomator. It performs a click on a view that holds the text contained in 'button'
 *
 * @param button: String - The view to click on. It is targeted through the text shown on screen.
 */
fun clickButtonUiAutomator(button: String) {
    UiDevice.getInstance(getInstrumentation()).findObject(UiSelector().text(button)).click()
}

/**
 * Uses UiAutomator to scroll a scrollable view until the text searched appears. If the view cannot be scrolled or text
 * is already present, the exception will be caught and the text directly clicked on by calling [clickButtonUiAutomator].
 *
 * @param text: String - The text the function should scroll to and click.
 */
fun scrollUntilFoundTextAndClickUiAutomator(text: String) {
    try {
        UiScrollable(UiSelector().scrollable(true)).scrollIntoView(UiSelector().text(text))
    } catch (e: Exception) { } finally {
        clickButtonUiAutomator(text)
    }

}

/**
 * Creates a separate thread that will wait for 'time'. Then synchronizes. Allows the test to wait for the app to be
 * available/not be busy.
 *
 * @param time: Long - Time to wait in milliseconds
 */
fun waitFor(time: Long) {
    val t = Thread(Runnable {Thread.sleep(time)})
    t.start()
    t.join()
}

/**
 * Gets resource string for the given ID.
 *
 * strID: Int - The resource string ID (testapp string ID).
 */
fun getStr(strID: Int) : String {
    return getInstrumentation().targetContext.getString(strID)
}

/**
 * Returns a string containing the device model name.
 */
fun getDeviceModelName(): String {
    val manufacturer = Build.MANUFACTURER;
    val model = Build.MODEL;
    if (model.startsWith(manufacturer)) {
        return model.toUpperCase()
    }
    return "$manufacturer $model".toUpperCase()
}

/**
 * Adds a publication to the database by using methods of [LibraryActivity].
 *
 * @param pub: String - The name of the publication to add to the database
 * @param activity: LibraryActivity? - Instance of LibraryActivity.
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

/**
 * Function that returns a custom matcher which is used to test if the number of elements in a recycler view matches
 * the prediction.
 *
 * @param size: Int - The predicted number of elements that should be inside the recycler view.
 * @return Matcher<View> - A custom matcher. Returns true if sizes match, false otherwise.
 */
fun withRecyclerViewSize(size: Int): Matcher<View> {
    return object : TypeSafeMatcher<View>() {
        override fun matchesSafely(view: View): Boolean {
            val actualListSize = (view as RecyclerView).adapter!!.itemCount
            return actualListSize == size
        }

        override fun describeTo(description: Description) {
            description.appendText("RecyclerView should have $size items")
        }
    }
}

/**
 * Perform a single click action on the center of the view with the given ID.
 *
 * @param id: Int - The id of the object to swipe on.
 */
fun clickCenter(id: Int) {
    Espresso.onView(ViewMatchers.withId(id)).perform(GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER,
        Press.FINGER, InputDevice.SOURCE_ANY, MotionEvent.BUTTON_PRIMARY))
}

/**
 * Perform all the UI actions to access the TOC interface. Waits for one second to ensure app and testsuite will stay
 * synchronized
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
    val mDevice : UiDevice = UiDevice.getInstance(getInstrumentation())
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
        .perform(GeneralClickAction(Tap.SINGLE, GeneralLocation.CENTER_RIGHT,Press.FINGER))
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
 * @param a: Activity - The activity to get the R2ViewPager from.
 * @return R2ViePager
 */
fun getResourcePager(a: Activity): R2ViewPager {
    return a.findViewById(R.id.resourcePager) as R2ViewPager
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
package org.readium.r2.testapp

import android.annotation.SuppressLint
import android.content.ContentValues.TAG
import android.content.Context
import android.net.Uri
import nl.komponents.kovenant.then
import org.readium.r2.lcp.LcpLicense
import timber.log.Timber
import java.net.URL

class DrmFetcher {
    @SuppressLint("TimberArgCount")
    fun fetch(uri: Uri, context: Context) : String {
        val lcpLicense = LcpLicense(URL(uri.toString()), false, context)
        return lcpLicense.fetchStatusDocument().then({
            Timber.i(TAG, "LCP fetchStatusDocument: $it")
            lcpLicense.checkStatus()
            lcpLicense.updateLicenseDocument().get()
        }).then({
            Timber.i(TAG, "LCP updateLicenseDocument: $it")
            lcpLicense.areRightsValid()
            lcpLicense.register()
            lcpLicense.fetchPublication()
        }).then({
            Timber.i(TAG, "LCP fetchPublication: $it")
            it?.let {
                lcpLicense.moveLicense(it, lcpLicense.archivePath)
            }
            it!!
        }).get()
    }
}
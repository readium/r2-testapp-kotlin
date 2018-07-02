/**
Copyright 2018 Readium Foundation. All rights reserved.
Use of this source code is governed by a BSD-style license which is detailed in the LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp

import android.app.Activity
import android.os.Bundle

import timber.log.Timber

/**
 * Created by aferditamuriqi on 1/16/18.
 */

class R2DispatcherActivity : Activity() {
    private val mMapper = R2IntentMapper(this, R2IntentHelper())

    private val TAG = this.javaClass.simpleName

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            mMapper.dispatchIntent(intent)
        } catch (iae: IllegalArgumentException) {
            if (BuildConfig.DEBUG) {
                Timber.e(iae, "Deep links  - Invalid URI")
            }
        } finally {
            finish()
        }
    }
}
package org.readium.r2.testapp.reader

sealed class ReaderFragmentEvent {

    object OpenOutlineRequested : ReaderFragmentEvent()

    object OpenDrmManagementRequested : ReaderFragmentEvent()
}
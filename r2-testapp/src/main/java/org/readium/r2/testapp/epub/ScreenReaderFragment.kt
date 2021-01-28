package org.readium.r2.testapp.epub

import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.activity_epub.main_content
import org.readium.r2.testapp.R
import kotlinx.android.synthetic.main.fragment_screen_reader.*
import org.readium.r2.navigator.IR2TTS
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication

class ScreenReaderFragment : Fragment(R.layout.fragment_screen_reader), IR2TTS {

    private val activity: EpubActivity get () =
        requireActivity() as EpubActivity

    private val publication: Publication get() =
        activity.publication

    private val preferences: SharedPreferences get() =
        activity.preferences

    private val epubNavigator: EpubNavigatorFragment get() =
        activity.epubNavigator

    private lateinit var screenReader: R2ScreenReader

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /*publication = ViewModelProvider(requireActivity())
            .get(PublicationViewModel::class.java)
            .publication*/

        preferences.getString("$activity.publicationIdentifier-publicationPort", 0.toString())?.toInt()?.let {
            screenReader = R2ScreenReader(activity, this, epubNavigator, publication, it, activity.publicationFileName)

        }

        titleView.text = publication.metadata.title

        play_pause.setOnClickListener {
            if (screenReader.isPaused) {
                screenReader.resumeReading()
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                screenReader.pauseReading()
                play_pause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
        fast_forward.setOnClickListener {
            if (screenReader.nextSentence()) {
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                next_chapter.callOnClick()
            }
        }
        next_chapter.setOnClickListener {
            screenReader.nextResource()
            play_pause.setImageResource(android.R.drawable.ic_media_pause)
        }

        fast_back.setOnClickListener {
            if (screenReader.previousSentence()) {
                play_pause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                prev_chapter.callOnClick()
            }
        }
        prev_chapter.setOnClickListener {
            screenReader.previousResource()
            play_pause.setImageResource(android.R.drawable.ic_media_pause)
        }

        //Get user settings speed when opening the screen reader. Get a neutral percentage (corresponding to
        //the normal speech speed) if no user settings exist.
        val speed = preferences.getInt(
            "reader_TTS_speed",
            (2.75 * 3.toDouble() / 11.toDouble() * 100).toInt()
        )
        //Convert percentage to a float value between 0.25 and 3.0
        val ttsSpeed = 0.25.toFloat() + (speed.toFloat() / 100.toFloat()) * 2.75.toFloat()

        updateScreenReaderSpeed(ttsSpeed, false)

        if (screenReader.goTo(epubNavigator.resourcePager.currentItem)) {
            play_pause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            Toast.makeText(requireActivity().applicationContext, "No further chapter contains text to read", Toast.LENGTH_LONG).show()
        }
    }

    override fun playStateChanged(playing: Boolean) {
        super.playStateChanged(playing)
        if (playing) {
            play_pause?.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            play_pause?.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun playTextChanged(text: String) {
        super.playTextChanged(text)
        tts_textView.text = text
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tts_textView!!, 1, 30, 1, TypedValue.COMPLEX_UNIT_DIP)
    }

    override fun onResume() {
        super.onResume()

        if (screenReader.currentResource != epubNavigator.resourcePager.currentItem) {
            screenReader.goTo(epubNavigator.resourcePager.currentItem)
        }

        if (screenReader.isPaused) {
            screenReader.resumeReading()
            play_pause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            screenReader.pauseReading()
            play_pause.setImageResource(android.R.drawable.ic_media_play)
        }
        screenReader.onResume()

    }


    /**
     * Shutdown sceenReader is view is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        try {
            screenReader.shutdown()
        } catch (e: Exception) {
        }
    }

    /**
     * Pause the screenReader if view is paused.
     */
    override fun onPause() {
        super.onPause()
        screenReader.pauseReading()
    }

    /**
     * Stop the screenReader if app is view is stopped.
     */
    override fun onStop() {
        super.onStop()
        screenReader.stopReading()
    }

    /**
     * The function allows to access the [R2ScreenReader] instance and set the TextToSpeech speech speed.
     * Values are limited between 0.25 and 3.0 included.
     *
     * @param speed: Float - The speech speed we wish to use with Android's TextToSpeech.
     */
    fun updateScreenReaderSpeed(speed: Float, restart: Boolean) {
        var rSpeed = speed

        if (speed < 0.25) {
            rSpeed = 0.25.toFloat()
        } else if (speed > 3.0) {
            rSpeed = 3.0.toFloat()
        }
        screenReader.setSpeechSpeed(rSpeed, restart)
    }

}
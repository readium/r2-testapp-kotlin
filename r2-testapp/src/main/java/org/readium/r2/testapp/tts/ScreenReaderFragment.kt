package org.readium.r2.testapp.tts

import android.content.SharedPreferences
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.Toast
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.readium.r2.testapp.R
import kotlinx.android.synthetic.main.fragment_screen_reader.*
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.testapp.epub.EpubActivity
import org.readium.r2.testapp.reader.EpubReaderFragment
import org.readium.r2.testapp.reader.ReaderViewModel

class ScreenReaderFragment : Fragment(R.layout.fragment_screen_reader), ScreenReaderEngine.Listener {

    private val activity: EpubActivity
        get () = requireActivity() as EpubActivity

    private val preferences: SharedPreferences get() =
        activity.preferences

    private val epubNavigator: EpubNavigatorFragment get() =
        (parentFragment as EpubReaderFragment).navigatorFragment

    private lateinit var publication: Publication

    private lateinit var screenReader: ScreenReaderEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ViewModelProvider(activity).get(ReaderViewModel::class.java).let {
            publication = it.publication
        }

        screenReader = ScreenReaderEngine(activity, publication)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        screenReader.addListener(this)

        titleView.text = publication.metadata.title

        play_pause.setOnClickListener {
            if (screenReader.isPaused) {
                screenReader.resumeReading()
            } else {
                screenReader.pauseReading()
            }
        }
        fast_forward.setOnClickListener {
            if (!screenReader.nextSentence()) {
                next_chapter.callOnClick()
            }
        }
        next_chapter.setOnClickListener {
            screenReader.nextResource()
        }

        fast_back.setOnClickListener {
            if (!screenReader.previousSentence()) {
                prev_chapter.callOnClick()
            }
        }
        prev_chapter.setOnClickListener {
            screenReader.previousResource()
        }

        //Get user settings speed when opening the screen reader. Get a neutral percentage (corresponding to
        //the normal speech speed) if no user settings exist.
        val speed = preferences.getInt(
            "reader_TTS_speed",
            (2.75 * 3.toDouble() / 11.toDouble() * 100).toInt()
        )
        //Convert percentage to a float value between 0.25 and 3.0
        val ttsSpeed = 0.25.toFloat() + (speed.toFloat() / 100.toFloat()) * 2.75.toFloat()

        updateScreenReaderSpeed(ttsSpeed)
        screenReader.goTo(epubNavigator.resourcePager.currentItem)
    }

    override fun onPlayStateChanged(playing: Boolean) {
        if (playing) {
            play_pause.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            play_pause.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    override fun onEndReached() {
        Toast.makeText(requireActivity().applicationContext, "No further chapter contains text to read", Toast.LENGTH_LONG).show()
    }

    override fun onPlayTextChanged(text: String) {
        tts_textView.text = text
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(tts_textView!!, 1, 30, 1, TypedValue.COMPLEX_UNIT_DIP)
    }

    override fun onDestroyView() {
        screenReader.removeListener(this)
        super.onDestroyView()
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
     * The function allows to access the [ScreenReaderEngine] instance and set the TextToSpeech speech speed.
     * Values are limited between 0.25 and 3.0 included.
     *
     * @param speed: Float - The speech speed we wish to use with Android's TextToSpeech.
     */
    private fun updateScreenReaderSpeed(speed: Float) {
        var rSpeed = speed

        if (speed < 0.25) {
            rSpeed = 0.25.toFloat()
        } else if (speed > 3.0) {
            rSpeed = 3.0.toFloat()
        }
        screenReader.setSpeechSpeed(rSpeed, restart = false)
    }

}
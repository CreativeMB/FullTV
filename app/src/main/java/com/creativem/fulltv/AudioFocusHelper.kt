import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import androidx.annotation.RequiresApi
import android.media.AudioAttributes
object AudioFocusHelper {
    private lateinit var audioManager: AudioManager
    private lateinit var focusRequest: AudioFocusRequest

    @RequiresApi(Build.VERSION_CODES.O)
    fun requestAudioFocus(context: Context): Boolean {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { /* manejar si se quiere */ }
            .build()

        val result = audioManager.requestAudioFocus(focusRequest)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun abandonAudioFocus() {
        if (::audioManager.isInitialized && ::focusRequest.isInitialized) {
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }

}

package ro.solarcasa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import ro.solarcasa.ui.SolarCasaApp
import ro.solarcasa.ui.theme.SolarCasaTheme

class MainActivity : ComponentActivity() {
    /** Screen requested by a notification (deep link), for example "emergency" or "alert:string2". */
    private val openRequest = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        openRequest.value = intent.getStringExtra(EXTRA_OPEN)
        setContent {
            SolarCasaTheme {
                SolarCasaApp(openRequest = openRequest.value, onOpenHandled = { openRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(EXTRA_OPEN)
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_EMERGENCY = "emergency"
        const val OPEN_ALERT = "alert:"
    }
}

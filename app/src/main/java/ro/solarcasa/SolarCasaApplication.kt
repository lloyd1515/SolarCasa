package ro.solarcasa

import android.app.Application
import ro.solarcasa.data.HouseRepository

/** Manual dependency injection: one repository for the whole app. */
class SolarCasaApplication : Application() {
    val repository by lazy { HouseRepository() }
}

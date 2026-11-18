package ro.solarcasa.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.solarcasa.SolarCasaApplication
import ro.solarcasa.data.HouseRepository

/** Creates a screen-level ViewModel with the app repository (manual DI, no Context in the ViewModel). */
@Composable
inline fun <reified VM : ViewModel> screenViewModel(crossinline create: (HouseRepository) -> VM): VM {
    val repository = (LocalContext.current.applicationContext as SolarCasaApplication).repository
    return viewModel { create(repository) }
}

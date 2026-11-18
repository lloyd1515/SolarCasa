# SolarCasa

Prototip de nivel înalt (Etapa 7) pentru proiectul PIU, Echipa 6: aplicație Android pentru
proiectarea, planificarea și monitorizarea unei case inteligente cu energie solară.

- Platformă: Android, Kotlin, Jetpack Compose, Material 3.
- Navigare: Navigation 3, bară de navigare jos pe telefon, rail pe tabletă (`NavigationSuiteScaffold`).
- Date demo: casă în Cluj-Napoca, 6 kWp (producție PVGIS 5.3), baterie de 10 kWh.
- „Asistentul Solar” (AI) este simulat local. Nicio acțiune nu rulează fără confirmarea utilizatorului.

## Rulare

```bash
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # teste unitare pentru calcule
```

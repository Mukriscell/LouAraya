package cl.duoc.louaraya

import android.app.Application

/**
 * Punto de entrada de la aplicación; mantiene el [AppContainer] de dependencias.
 */
class LouArayaApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

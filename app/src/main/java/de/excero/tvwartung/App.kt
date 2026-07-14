package de.excero.tvwartung

import android.app.Application
import de.excero.tvwartung.data.AppDatabase
import de.excero.tvwartung.data.Repository
import de.excero.tvwartung.files.PhotoStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    val repository: Repository by lazy { Repository(AppDatabase.get(this)) }
    val photoStore: PhotoStore by lazy { PhotoStore(this) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.seedIfEmpty(this@App)
            photoStore.cleanupEmptyFiles()
        }
    }
}

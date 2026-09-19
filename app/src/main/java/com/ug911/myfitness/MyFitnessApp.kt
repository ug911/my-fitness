package com.ug911.myfitness

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.ug911.myfitness.di.AppContainer
import com.ug911.myfitness.health.HealthSyncWorker

class MyFitnessApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        HealthSyncWorker.schedulePeriodic(this)
        // First launch: the coach and the food table come from the bundled copy.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.knowledgeSync.loadBundledIfEmpty()
        }
    }
}

package com.ug911.myfitness

import android.app.Application
import com.ug911.myfitness.di.AppContainer
import com.ug911.myfitness.health.HealthSyncWorker

class MyFitnessApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        HealthSyncWorker.schedulePeriodic(this)
    }
}

package com.tiarkaerell.redlights

import leakcanary.LeakCanary

class DebugApplication : RedlightsApplication() {
    override fun onCreate() {
        super.onCreate()
        setupLeakCanary()
    }

    private fun setupLeakCanary() {
        LeakCanary.config = LeakCanary.config.copy(
            dumpHeap = true,
            retainedVisibleThreshold = 5
        )
    }
}

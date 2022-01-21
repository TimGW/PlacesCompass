package com.timgortworst.placescompass

import android.app.Application
import com.google.android.libraries.places.api.Places

class CompassApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Places.initialize(applicationContext, BuildConfig.PLACES_API_KEY)
    }
}

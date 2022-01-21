package com.timgortworst.placescompass

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.RotateAnimation
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.*
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.snackbar.Snackbar
import com.timgortworst.placescompass.Compass.Companion.throttleLatest
import com.timgortworst.placescompass.Compass.CompassListener
import com.timgortworst.placescompass.databinding.ActivityMainBinding


class CompassActivity : AppCompatActivity(), CompassListener, PlaceSelectionListener {
    private lateinit var autocompleteFragment: AutocompleteSupportFragment
    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var compass: Compass? = null
    private var currentBearing = 0
    private var currentBearingNorth = 0
    private val onBearingChange = throttleLatest(lifecycleScope, ::updateUI, THROTTLE)
    private val locCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            compass?.setStartLocation(LatLng(location.latitude, location.longitude))
        }
    }
    private val locReq = LocationRequest.create().apply {
        priority = PRIORITY_HIGH_ACCURACY
        interval = 10000
        fastestInterval = 5000
    }
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { if (it) requestLocationUpdates() else showRationale { openSettings() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupAutoCompleteFragment()

        compass = Compass(this)
        compass?.setListener(this)
    }

    private fun setupAutoCompleteFragment() {
        autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                as AutocompleteSupportFragment
        autocompleteFragment.setPlaceFields(
            listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        )
        autocompleteFragment.setCountries("NL")
        val netherlands =
            LatLngBounds(LatLng(50.803721015, 3.31497114423), LatLng(53.5104033474, 7.09205325687))
        autocompleteFragment.setLocationBias(RectangularBounds.newInstance(netherlands))
        autocompleteFragment.setOnPlaceSelectedListener(this)
    }

    override fun onResume() {
        super.onResume()
        compass?.start()
        if (checkPermissions()) requestLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        compass?.stop()
        removeLocationUpdates()
    }

    override fun onError(status: Status) {
        Snackbar.make(binding.root, getString(R.string.generic_error), Snackbar.LENGTH_SHORT).show()
    }

    override fun onPlaceSelected(place: Place) {
        place.latLng?.let { compass?.setEndLocation(it) }
    }

    override fun onNewBearing(bearing: Bearing) {
        runOnUiThread { onBearingChange(bearing) }
    }

    private fun updateUI(bearing: Bearing) {
        bearing.distanceTo?.let { binding.distance.text = getString(R.string.meters, it) }

        animateArrow(bearing.locationDegrees)
        animateRose(bearing.trueNorthDegrees)
    }

    private fun animateArrow(azimuth: Int) {
        val an = RotateAnimation(
            -currentBearing.toFloat(), -azimuth.toFloat(),
            Animation.RELATIVE_TO_SELF, CENTER_PIVOT,
            Animation.RELATIVE_TO_SELF, CENTER_PIVOT
        )

        currentBearing = azimuth

        an.duration = COMPASS_ANIMATION_DURATION
        an.repeatCount = 0
        an.interpolator = LinearInterpolator()
        an.fillAfter = true

        binding.arrow.startAnimation(an)
    }

    private fun animateRose(azimuthNorth: Int) {
        val an = RotateAnimation(
            -currentBearingNorth.toFloat(), -azimuthNorth.toFloat(),
            Animation.RELATIVE_TO_SELF, CENTER_PIVOT,
            Animation.RELATIVE_TO_SELF, CENTER_PIVOT
        )

        currentBearingNorth = azimuthNorth

        an.duration = COMPASS_ANIMATION_DURATION
        an.repeatCount = 0
        an.interpolator = LinearInterpolator()
        an.fillAfter = true

        binding.compassRose.startAnimation(an)
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(locReq, locCallback, Looper.getMainLooper())
    }

    private fun removeLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locCallback)
    }

    private fun checkPermissions(): Boolean {
        when {
            isPermissionGranted() || Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> return true
            shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION) ->
                showRationale { requestPermissionLauncher.launch(ACCESS_FINE_LOCATION) }
            else -> requestPermissionLauncher.launch(ACCESS_FINE_LOCATION)
        }
        return false
    }

    private fun isPermissionGranted() =
        ContextCompat.checkSelfPermission(this, ACCESS_FINE_LOCATION) == PERMISSION_GRANTED

    private fun showRationale(action: () -> Unit) {
        Snackbar.make(
            binding.root,
            getString(R.string.snackbar_rationale_text),
            Snackbar.LENGTH_INDEFINITE
        ).setAction(getString(R.string.snackbar_rationale_button)) {
            action.invoke()
        }.show()
    }

    private fun openSettings() {
        startActivity(Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            addCategory(Intent.CATEGORY_DEFAULT)
            data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        })
    }

    companion object {
        private const val COMPASS_ANIMATION_DURATION = 300L
        private const val CENTER_PIVOT = 0.5f
        private const val THROTTLE = 300L
    }
}

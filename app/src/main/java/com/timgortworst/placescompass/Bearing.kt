package com.timgortworst.placescompass

data class Bearing(
    val locationDegrees: Int,
    val trueNorthDegrees: Int,
    val distanceTo: Float?
)
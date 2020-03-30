package com.example.maplab

import io.realm.RealmList
import io.realm.RealmObject
import com.google.android.gms.maps.model.LatLng

open class EntMarker: RealmObject() {
    private var lat: Double = 0.0
    private var lon: Double = 0.0
    private var paths: RealmList<String> = RealmList()

    fun getPosition(): LatLng {
        return LatLng(lat, lon)
    }

    fun setPosition(new_latlon: LatLng) {
        lat = new_latlon.latitude
        lon = new_latlon.longitude
    }

    fun addPath(path: String) {
        paths.add(path)
    }

    fun getPaths(): List<String> {
        return paths
    }

}
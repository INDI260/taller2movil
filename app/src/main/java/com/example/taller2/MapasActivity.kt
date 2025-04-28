package com.example.taller2

import android.Manifest
import android.app.UiModeManager
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import com.google.android.gms.location.LocationRequest
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.databinding.ActivityMapasBinding
import com.example.taller2.entities.MyLocation
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Date

class MapasActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMapasBinding
    private lateinit var locationClient : FusedLocationProviderClient
    private lateinit var locationRequest : LocationRequest
    private lateinit var locationCallback : LocationCallback
    private val locations = mutableListOf<JSONObject>()
    private val RADIUS_OF_EARTH_KM = 6371.0
    private lateinit var currentLocation : Location

    private lateinit var map : MapView
    private val bogota = GeoPoint(4.62, -74.07)

    //Sensor management
    private lateinit var sensorManager: SensorManager
    private lateinit var lightSensor : Sensor
    private lateinit var lightEventListener : SensorEventListener

    private val locationSettings =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult(),
            ActivityResultCallback {
            if(it.resultCode == RESULT_OK){
                startLocationUpdates()
            }else{
                Toast.makeText(this, "No se pudo acceder a la ubicación", Toast.LENGTH_LONG).show()
            }
        })

   private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if(it){
                locationSettings()
            }else{
                Toast.makeText(this, "No se pudo acceder a la ubicación", Toast.LENGTH_LONG).show()
            }
        }
    )


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        map = binding.mapa
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!

    }

    override fun onPause() {
        super.onPause();
        stopLocationUpdates();
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        map.controller.setZoom(18.0)
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
        val uims = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if(uims.nightMode == UiModeManager.MODE_NIGHT_YES){
            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
        }
    }

    private fun createLocationRequest() : LocationRequest{
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setWaitForAccurateLocation(true)
            .setMinUpdateDistanceMeters(30F)
            .setMinUpdateIntervalMillis(5000)
            .build()
        return locationRequest
    }

    private fun startLocationUpdates(){
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    private fun createLocationCallback() : LocationCallback{
        val locationCallback = object : LocationCallback(){
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                if(result!=null){
                    val location = result.lastLocation!!
                    currentLocation = location
                    map.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                    writeJSONObject()
                    addMarker(location)
                }
            }
        }
        return locationCallback
    }

    private fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener {
            // All location settings are satisfied. The client can initialize
            // location requests here.
            // ...
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    val isr: IntentSenderRequest = IntentSenderRequest.Builder(
                        exception.resolution
                    ).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    Toast.makeText(this, "El dispositivo no tiene GPS", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback);
    }

    fun distance(lat1 : Double, long1: Double, lat2:Double, long2:Double) : Double{
        val latDistance = Math.toRadians(lat1 - lat2)
        val lngDistance = Math.toRadians(long1 - long2)
        val a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)+
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        val result = RADIUS_OF_EARTH_KM * c;
        return Math.round(result*100.0)/100.0;
    }

    fun writeJSONObject() {
        val myLocation = MyLocation(
            Date(System.currentTimeMillis()),
            currentLocation.latitude,
            currentLocation.longitude
        )
        locations.add(myLocation.toJSON())
        val filename = "locations.json"
        val file = File(
            baseContext.getExternalFilesDir(null), filename)
        val output = BufferedWriter(FileWriter(file))
        output.write(locations.toString())
        output.close()
        Log.i("LOCATION", "File modified at path: " + file)
    }

    fun addMarker(location : Location){//Add Marker
        val markerPoint = GeoPoint(location.latitude, location.longitude)
        val marker = Marker(map)
        marker.title = "Mi Marcador"
        val myIcon = getResources().getDrawable(R.drawable.baseline_location_on_24, this.getTheme())
        marker.icon = myIcon
        marker.setPosition(markerPoint)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(marker)
    }

    fun removeMarkers(){
        map.overlays.clear()
    }
}
package com.example.taller2

import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.taller2.databinding.ActivityMapasBinding
import com.example.taller2.entities.MyLocation
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.Task
import org.json.JSONObject
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Date
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


class MapasActivity : AppCompatActivity() {

    private lateinit var binding : ActivityMapasBinding //Actividad principal
    private lateinit var locationClient : FusedLocationProviderClient //Cliente de la localización
    private lateinit var locationRequest : LocationRequest // Petición de localización
    private lateinit var locationCallback : LocationCallback // Callback de localización
    private val locations = mutableListOf<JSONObject>() // Lista de localizaciones visitadas
    private val RADIUS_OF_EARTH_KM = 6371.0 //Radio de la tierra en Km
    private lateinit var currentLocation : Location // Localización actual
    private val bogota = GeoPoint(4.609710, -74.081750) //Geopointer a bogotá para testing

    private lateinit var currentLocationMarker : Marker // Marcador de la localización actual
    private lateinit var searchLocationMarker : Marker // Marcador de la localización de búsqueda
    private var longPressedMarker : Marker? = null // Marcador de la localización de longpress

    private lateinit var geocoder : Geocoder //Instancia de geocoder

    private lateinit var map : MapView // Mapa OSM

    private lateinit var roadManager : OSRMRoadManager // Maejador de rutas
    private var roadOverlay: Polyline? = null // Dibujador de rutas

    private lateinit var sensorManager: SensorManager // Manejador de sensores
    private lateinit var lightSensor : Sensor // Sensor de luz
    private lateinit var lightEventListener : SensorEventListener // Listener de cambios de luz

    private val locationSettings =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult(), // Verifica si la localización está activa en el teléfono
            ActivityResultCallback {
            if(it.resultCode == RESULT_OK){ // Si se aceptó la petición de localización
                startLocationUpdates() // Inicia el monitoreo de la localización
            }else{ // Mensaje de error
                Toast.makeText(this, "No se pudo acceder a la ubicación", Toast.LENGTH_LONG).show()
            }
        })

    private val locationPermission = registerForActivityResult( // Verifica si la aplicación tien permiso para acceder a la localización
        ActivityResultContracts.RequestPermission(),
        ActivityResultCallback {
            if(it){
                locationSettings() // Revisa si está encendida en el dispositivo
            }else{
                Toast.makeText(this, "No se pudo acceder a la ubicación", Toast.LENGTH_LONG).show()
            }
        })


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configura el campo de texto de búsqueda con geocoder
        binding.ubicacion.setOnEditorActionListener { textView, i, keyEvent ->
            if (i == EditorInfo.IME_ACTION_SEND) { // Si se presiona enter en el campo de texto
                val input = binding.ubicacion.text.toString()
                val location = findLocation(input) // Busca la localización con geocoder
                if (location != null) {
                    val address = findAddress(LatLng(location.latitude, location.longitude)) // Busca la dirección de la localización
                    if (address != null) {
                        addMarker(GeoPoint(location.latitude, location.longitude), address, false ) // Agrega el marcador a la localización
                    }
                    map.controller.setCenter(GeoPoint(location.latitude, location.longitude)) // Centra el mapa en la localización
                    map.controller.setZoom(18.0)
                    Toast.makeText(this, "La distancia al punto es: " + distance(currentLocation.latitude, currentLocation.longitude, location.latitude, location.longitude) + "Km", Toast.LENGTH_LONG).show() // Calcula la distancia y la muestra en un toast
                    drawRoute(GeoPoint(currentLocation.latitude, currentLocation.longitude), GeoPoint(location.latitude, location.longitude)) // Dibuja la ruta a la locación encontrada
                }
            }
            true
        }

        // Inicializa los elementos de localización
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = createLocationRequest()
        locationCallback = createLocationCallback()

        // Inicializa geocoder
        geocoder = Geocoder(baseContext)

        // Inicializa la configuración del mapa en la app
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        map = binding.mapa
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.overlays.add(createOverlayEvents())

        // Inicializa el manejador de rutas
        roadManager = OSRMRoadManager(this, "ANDROID")
        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        // Inicializa el sensor de luz
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)!!
        lightEventListener = createLightSensorListener()

    }

    override fun onPause() {
        super.onPause();
        stopLocationUpdates(); // Detiene la actualización de localización para ahorrar recursos
        sensorManager.unregisterListener(lightEventListener) // Detiene el sensor de luz
    }

    override fun onResume() {
        super.onResume()
        map.onResume() // Resume el mapa
        map.controller.setZoom(18.0)
        locationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION) // Vuelve a confirmar los permisos y settings de localización
        sensorManager.registerListener(lightEventListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL) // Vuelve a iniciar el sensor de luz
    }

    // 
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
                val location = result.lastLocation!!
                currentLocation = location
                map.controller.setCenter(GeoPoint(location.latitude, location.longitude))
                writeJSONObject()
                addMarker(GeoPoint(location.latitude, location.longitude), "Mi ubicación", false)
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
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lngDistance / 2) * sin(lngDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a));
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
        Log.i("LOCATION", "File modified at path: $file")
    }

    fun longPressOnMap(p:GeoPoint){
        if(longPressedMarker!=null)
            map.getOverlays().remove(longPressedMarker)
        val address = findAddress(LatLng(p.latitude, p.longitude))
        val snippet : String
        if(address!=null) {
            snippet = address
        }else{
            snippet = ""
        }
        addMarker(p, snippet, true)
        Toast.makeText(this, "La distancia al punto es: " + distance(currentLocation.latitude, currentLocation.longitude, p.latitude, p.longitude) + "Km", Toast.LENGTH_LONG).show()
    }

    fun addMarker(p:GeoPoint, snippet : String, longPressed : Boolean){
        if(longPressed) {
            longPressedMarker = createMarker(p, snippet, "", R.drawable.baseline_location_on_24)
            if (longPressedMarker != null) {
                map.getOverlays().add(longPressedMarker)
            }
        }else{
            if(snippet == "Mi ubicación"){
                if(this::currentLocationMarker.isInitialized)
                    map.overlays.remove(currentLocationMarker)
                currentLocationMarker = createMarker(p, "Mi ubicación", "", R.drawable.baseline_location_on_24)!!
                map.overlays.add(currentLocationMarker)
            }
            else {
                if(this::searchLocationMarker.isInitialized)
                    map.overlays.remove(searchLocationMarker)
                searchLocationMarker = createMarker(p, snippet, "", R.drawable.baseline_location_on_24)!!
                map.overlays.add(searchLocationMarker)
            }
        }
    }

    private fun createMarker(p:GeoPoint, title: String, desc: String, iconID : Int) : Marker? {
        var marker : Marker? = null;
        if(map!=null) {
            marker = Marker(map);
            if (title != null) marker.title = title;
            if (desc != null) marker.subDescription = desc;
            if (iconID != 0) {
                val myIcon = getResources().getDrawable(iconID, this.getTheme());
                marker.icon = myIcon;
            }
            marker.setPosition(p);
            marker.setAnchor(Marker.
            ANCHOR_CENTER, Marker.
            ANCHOR_BOTTOM);
        }
        return marker
    }

    private fun createLightSensorListener() : SensorEventListener{
        val ret : SensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if(this@MapasActivity::map.isInitialized){
                    if (event != null) {
                        if(event.values[0] < 5000){
                            map.overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)

                        }
                        else{
                            map.overlayManager.tilesOverlay.setColorFilter(null)
                        }
                    }
                }
            }
            override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
            }
        }
        return ret
    }

    private fun findAddress (location : LatLng):String?{
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
        if(!addresses.isNullOrEmpty()){
            val addr = addresses[0]
            val locname = addr.getAddressLine(0)
            return locname
        }
        return null
    }

    private fun findLocation(address : String):LatLng?{
        val addresses = geocoder.getFromLocationName(address, 1)
        if(addresses != null && !addresses.isEmpty()){
            val addr = addresses[0]
            val location = LatLng(addr.
            latitude, addr.
            longitude)
            return location
        }
        return null
    }

    private fun createOverlayEvents() : MapEventsOverlay {
        val overlayEvents = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                if(p!=null) {
                    longPressOnMap(p)
                    drawRoute(GeoPoint(currentLocation.latitude, currentLocation.longitude), p)
                }
                return true
            }
        })
        return overlayEvents
    }

    fun drawRoute(start : GeoPoint, finish : GeoPoint){
        var routePoints = ArrayList<GeoPoint>()
        routePoints.add(start)
        routePoints.add(finish)
        val road = roadManager.getRoad(routePoints)
        Log.i("MapsApp", "Route length: "+road.mLength+" klm")
        Log.i("MapsApp", "Duration: "+road.mDuration/60+" min")
        if(map!=null){
            if(roadOverlay != null){
                map.overlays.remove(roadOverlay);
            }
            roadOverlay = RoadManager.buildRoadOverlay(road)
            roadOverlay!!.getOutlinePaint().setColor(Color.RED)
            roadOverlay!!.getOutlinePaint().strokeWidth = 10F
            map.overlays.add(roadOverlay)
        }
    }


}
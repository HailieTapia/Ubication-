/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.ubicacion.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Scaffold
import com.example.ubicacion.presentation.theme.UbicacionTheme
import android.Manifest
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.os.Looper
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationTextState = mutableStateOf("Estás en: (ubicación no disponible)")
    private val directionState = mutableStateOf("Debes ir hacia: -") // Estado para la dirección

    // Ciudad por defecto (Huejutla de Reyes) si no se detecta la ciudad
    private val defaultCity = "Huejutla de Reyes"
    private val defaultCityLatitude = 21.1403
    private val defaultCityLongitude = -98.4194
    private val referenceLocation = mutableStateOf(
        Location("reference").also {
            it.setLatitude(defaultCityLatitude)
            it.setLongitude(defaultCityLongitude)
        }
    )

    // SharedPreferences para almacenar la última ciudad y sus coordenadas
    private val prefs by lazy { getSharedPreferences("location_prefs", Context.MODE_PRIVATE) }

    // Lanzar para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getUserLocation()
        } else {
            locationTextState.value = "Estás en: (permisos denegados)"
            directionState.value = "Debes ir hacia: -"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        // Inicializar Fused Location Provider
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            UbicacionTheme {
                LocationScreen(
                    locationText = locationTextState.value,
                    directionText = directionState.value,
                    onButtonClick = { checkLocationPermission() }
                )
            }
        }
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                getUserLocation()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun getUserLocation() {
        try {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED) {

                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                    .setMinUpdateIntervalMillis(5000)
                    .build()

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult.lastLocation?.let { location ->
                            val latitude = location.latitude
                            val longitude = location.longitude
                            val (address, city) = getAddressAndCityFromLocation(latitude, longitude)
                            locationTextState.value = address ?: String.format(
                                Locale.getDefault(),
                                "Estás en: %.4f, %.4f",
                                latitude,
                                longitude
                            )

                            // Determinar la ciudad actual o usar la por defecto
                            val currentCity = city ?: defaultCity

                            // Verificar si ya tenemos coordenadas guardadas para esta ciudad
                            val lastCity = prefs.getString("last_city", null)
                            val lastCityLat = prefs.getFloat("last_city_lat", Float.MIN_VALUE)
                            val lastCityLon = prefs.getFloat("last_city_lon", Float.MIN_VALUE)

                            if (currentCity == lastCity && lastCityLat != Float.MIN_VALUE && lastCityLon != Float.MIN_VALUE) {
                                // Usar coordenadas guardadas
                                referenceLocation.value = Location("reference").also {
                                    it.setLatitude(lastCityLat.toDouble())
                                    it.setLongitude(lastCityLon.toDouble())
                                }
                            } else {
                                // Buscar coordenadas del centro de la ciudad
                                val cityCoords = getCityCenterCoordinates(currentCity)
                                referenceLocation.value = Location("reference").also {
                                    it.setLatitude(cityCoords.first)
                                    it.setLongitude(cityCoords.second)
                                }
                                // Guardar en SharedPreferences
                                prefs.edit().apply {
                                    putString("last_city", currentCity)
                                    putFloat("last_city_lat", cityCoords.first.toFloat())
                                    putFloat("last_city_lon", cityCoords.second.toFloat())
                                    apply()
                                }
                            }

                            // Calcular dirección hacia el centro de la ciudad
                            val bearing = location.bearingTo(referenceLocation.value)
                            directionState.value = "Debes ir hacia ${bearingToCardinalDirection(bearing)} para llegar al centro de $currentCity"

                            // Detener actualizaciones para ahorrar batería
                            fusedLocationClient.removeLocationUpdates(this)
                        } ?: run {
                            locationTextState.value = "Estás en: (ubicación no disponible)"
                            directionState.value = "Debes ir hacia: -"
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                ).addOnFailureListener {
                    locationTextState.value = "Estás en: (error: ${it.message})"
                    directionState.value = "Debes ir hacia: -"
                }
            } else {
                locationTextState.value = "Estás en: (permisos denegados)"
                directionState.value = "Debes ir hacia: -"
            }
        } catch (e: SecurityException) {
            locationTextState.value = "Estás en: (permisos denegados)"
            directionState.value = "Debes ir hacia: -"
        }
    }

    private fun getAddressAndCityFromLocation(latitude: Double, longitude: Double): Pair<String?, String?> {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            val address = addresses?.firstOrNull()?.getAddressLine(0)
            val city = addresses?.firstOrNull()?.locality
            Pair(address, city)
        } catch (e: Exception) {
            Pair(null, null) // Si falla (por ejemplo, sin internet), devolvemos null
        }
    }

    private fun getCityCenterCoordinates(city: String): Pair<Double, Double> {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocationName(city, 1)
            val address = addresses?.firstOrNull()
            if (address != null && address.hasLatitude() && address.hasLongitude()) {
                Pair(address.latitude, address.longitude)
            } else {
                // Usar coordenadas de Huejutla como respaldo
                Pair(defaultCityLatitude, defaultCityLongitude)
            }
        } catch (e: Exception) {
            // Usar coordenadas de Huejutla si falla la consulta
            Pair(defaultCityLatitude, defaultCityLongitude)
        }
    }

    // Convertir bearing (en grados) a dirección cardinal
    private fun bearingToCardinalDirection(bearing: Float): String {
        val normalizedBearing = (bearing % 360 + 360) % 360 // Normalizar a 0-360
        return when {
            normalizedBearing < 22.5 || normalizedBearing >= 337.5 -> "Norte"
            normalizedBearing < 67.5 -> "Noreste"
            normalizedBearing < 112.5 -> "Este"
            normalizedBearing < 157.5 -> "Sureste"
            normalizedBearing < 202.5 -> "Sur"
            normalizedBearing < 247.5 -> "Suroeste"
            normalizedBearing < 292.5 -> "Oeste"
            else -> "Noroeste"
        }
    }
}

@Composable
fun LocationScreen(
    locationText: String,
    directionText: String,
    onButtonClick: () -> Unit
) {
    // Obtener el tamaño de la pantalla para ajustar el texto
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val locationFontSize = if (screenWidthDp < 200) 10.sp else 12.sp // Ajustar tamaño según pantalla
    val directionFontSize = if (screenWidthDp < 200) 8.sp else 10.sp

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = locationText,
                color = Color.White,
                fontSize = locationFontSize,
                textAlign = TextAlign.Center,
                maxLines = 2, // Limitar a 2 líneas para evitar desbordamiento
                overflow = TextOverflow.Ellipsis, // Mostrar puntos suspensivos si el texto es largo
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            Text(
                text = directionText,
                color = Color.White,
                fontSize = directionFontSize,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp)
            )
            Button(
                onClick = onButtonClick,
                modifier = Modifier
                    .size(80.dp, 32.dp)
            ) {
                Text(
                    text = "Dónde estoy",
                    fontSize = 10.sp
                )
            }
        }
    }
}
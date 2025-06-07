/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter to find the
 * most up to date changes to the libraries and their usages.
 */

package com.example.ubicacion.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.ubicacion.presentation.theme.UbicacionTheme
import android.Manifest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import android.os.Looper
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Scaffold
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val locationTextState = mutableStateOf("Estás en: (ubicación no disponible)")

    // Lanzar para solicitar permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getUserLocation()
        } else {
            locationTextState.value = "Estás en: (permisos denegados)"
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

                // Usar LocationRequest.Builder
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                    .setMinUpdateIntervalMillis(5000) // Intervalo más rápido
                    .build()

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        locationResult.lastLocation?.let { location ->
                            val latitude = location.latitude
                            val longitude = location.longitude
                            val address = getAddressFromLocation(latitude, longitude)
                            locationTextState.value = address ?: String.format(
                                Locale.getDefault(),
                                "Estás en: %.4f, %.4f",
                                latitude,
                                longitude
                            )
                            // Detener actualizaciones para ahorrar batería
                            fusedLocationClient.removeLocationUpdates(this)
                        } ?: run {
                            locationTextState.value = "Estás en: (ubicación no disponible)"
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
                ).addOnFailureListener {
                    locationTextState.value = "Estás en: (error: ${it.message})"
                }
            } else {
                locationTextState.value = "Estás en: (permisos denegados)"
            }
        } catch (e: SecurityException) {
            locationTextState.value = "Estás en: (permisos denegados)"
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = Geocoder(this, Locale.getDefault())
            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            addresses?.firstOrNull()?.getAddressLine(0)
        } catch (e: Exception) {
            null // Si falla (por ejemplo, sin internet), devolvemos null
        }
    }
}

@Composable
fun LocationScreen(
    locationText: String,
    onButtonClick: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(8.dp), // Reducir padding para pantallas pequeñas
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = locationText,
                color = Color.White,
                fontSize = 12.sp, // Tamaño reducido para Wear OS
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Button(
                onClick = onButtonClick,
                modifier = Modifier
                    .size(80.dp, 32.dp) // Tamaño más pequeño para el botón
            ) {
                Text(
                    text = "Dónde estoy",
                    fontSize = 10.sp // Tamaño de texto reducido
                )
            }
        }
    }
}
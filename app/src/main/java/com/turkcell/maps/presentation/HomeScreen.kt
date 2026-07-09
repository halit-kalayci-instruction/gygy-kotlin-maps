package com.turkcell.maps.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.maplibre.android.geometry.LatLng


@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val mapController = rememberRencarMapController()

    var myLocation by remember { mutableStateOf<LatLng?>(null) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        hasLocationPermission = granted
        permissionDenied = !granted
    }

    LaunchedEffect(Unit) {
        if(!hasLocationPermission)
        {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    DisposableEffect(hasLocationPermission) {
        if(!hasLocationPermission) return@DisposableEffect onDispose { }

        val callback = object: LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    loc -> myLocation = LatLng(loc.latitude, loc.longitude)
                }
            }
        }

        startLocationUpdates(fusedClient, callback)

        onDispose { fusedClient.removeLocationUpdates(callback)  }
    }

    Scaffold(modifier= modifier.fillMaxSize()) { innerPadding ->

            Box(Modifier.fillMaxSize().padding(innerPadding)){
                RencarMap(
                    myLocation= myLocation,
                    modifier = Modifier.fillMaxSize(),
                    controller = mapController
                )

                // Sağ alt -> konumu yeniden al ve kamerayı tekrar zoomla.
                FloatingActionButton(
                    onClick = {
                        if (hasLocationPermission) {
                            fetchCurrentLocation(fusedClient) { target ->
                                myLocation = target
                                Log.d("MAP", "Konum yenilendi -> lat: ${target.latitude}, lng: ${target.longitude}")
                                mapController.animateTo(target)
                            }
                        } else {
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Konumuma git")
                }
            }
        }
}

@SuppressLint("MissingPermission")
private fun fetchCurrentLocation(
    fusedClient: FusedLocationProviderClient,
    onLocation: (LatLng) -> Unit
)
{
    // Cache'lenmiş son konum yerine taze bir konum iste.
    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
        .addOnSuccessListener { location ->
            if (location != null) onLocation(LatLng(location.latitude, location.longitude))
        }
}

@SuppressLint("MissingPermission")
private fun startLocationUpdates(
    fusedClient: FusedLocationProviderClient,
    callback: LocationCallback
)
{
    // 5sn aralıklarla Konumu yenileyecek..

    val request = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        5_000L
    ).setMinUpdateIntervalMillis(2_000L).build()

    fusedClient.lastLocation.addOnSuccessListener { location ->
        Log.d("LOKASYON","LOKASYON DEĞİŞTİ")
        if(location!=null){
            callback.onLocationResult(LocationResult.create(listOf(location)))
        }
    }

    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper());
}
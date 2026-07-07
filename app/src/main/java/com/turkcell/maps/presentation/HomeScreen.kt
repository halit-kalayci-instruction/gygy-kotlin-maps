package com.turkcell.maps.presentation

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    DisposableEffect(hasLocationPermission) {
        if(!hasLocationPermission) return@DisposableEffect onDispose { }

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
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
            }
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
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        5_000L
    ).setMinUpdateIntervalMillis(2_000L).build()

    fusedClient.lastLocation.addOnSuccessListener { location ->
        if(location!=null){
            callback.onLocationResult(LocationResult.create(listOf(location)))
        }
    }

    fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper());
}
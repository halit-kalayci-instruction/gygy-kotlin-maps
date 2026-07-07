package com.turkcell.maps.presentation

import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

val DEFAULT_CENTER: LatLng = LatLng(38.51740367746754, 27.161930350129918)

class RencarMapController internal constructor() {
    internal var map: MapLibreMap? = null

    fun animateTo(target: LatLng, zoom: Double = 10.0) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom))
    }
}

@Composable
fun rememberRencarMapController() : RencarMapController = remember { RencarMapController() }



@Composable
fun RencarMap(
    myLocation: LatLng?,
    modifier: Modifier = Modifier,
    initialCenter: LatLng = DEFAULT_CENTER,
    initialZoom: Double = 10.0,
    controller: RencarMapController? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current


    val mapView = remember {
        MapLibre.getInstance(context) // Native motoru başlat.
        MapView(context).apply { onCreate(null) }
    }

    var mapAndStyle by remember { mutableStateOf<Pair<MapLibreMap, Style>?>(null) }

    // Sayfa açıldığında bu method çalışsın, kapandığında da bir şeyler yapıcam..
    DisposableEffect(lifecycleOwner) {
        // Uygulamanın yaşam döngüsünün eventlerine bir şeyler yaz..
       val observer = LifecycleEventObserver { _, event ->
           when(event){
               Lifecycle.Event.ON_START -> mapView.onStart()
               Lifecycle.Event.ON_STOP -> mapView.onStop()
               Lifecycle.Event.ON_PAUSE -> mapView.onPause()
               Lifecycle.Event.ON_RESUME -> mapView.onResume()
               else -> {}
           }
       }
       lifecycleOwner.lifecycle.addObserver(observer)

       onDispose {
           lifecycleOwner.lifecycle.removeObserver(observer)
           mapView.onDestroy()
       }
    }

    // AndroidView -> Android ile @Composable köprüsü.
    AndroidView(factory = { mapView }, modifier = modifier) { mv ->
        val ready = mapAndStyle
        if(ready==null)
        {
            mv.getMapAsync { map ->
                controller?.map = map

                map.cameraPosition = CameraPosition.Builder().target(initialCenter).zoom(initialZoom).build()
                map.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) {
                    loaded ->
                    loaded.addSource(GeoJsonSource("me"))
                    loaded.addLayer(
                        CircleLayer("me-layer","me").withProperties(
                            PropertyFactory.circleColor(Color.BLUE)
                        )
                    )
                    mapAndStyle = map to loaded
                }
            }
        }
        else {
            val (map, style) = ready
            updateMe(style, myLocation)
            if (myLocation != null)
            {
                Log.d("MAP", "LOKASYON : ${myLocation.latitude} ${myLocation.longitude}")
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(myLocation, 10.0))
            }
        }
    }
}

private fun updateMe(style: Style, myLocation: LatLng?)
{
    val source = style.getSourceAs<GeoJsonSource>("me") ?: return
    if(myLocation == null)
    {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    }else {
        source.setGeoJson(Point.fromLngLat(myLocation.longitude, myLocation.latitude))
    }
}
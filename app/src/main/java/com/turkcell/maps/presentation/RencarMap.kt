package com.turkcell.maps.presentation

import android.graphics.Color
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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

private val ME_MARKER_COLOR = Color.parseColor("#4285F4")

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

    // Harita ve stil kurulumu -> yalnızca bir kez çalışır.
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            controller?.map = map

            map.cameraPosition = CameraPosition.Builder().target(initialCenter).zoom(initialZoom).build()
            map.setStyle(Style.Builder().fromJson(OSM_STYLE_JSON)) {
                loaded ->
                loaded.addSource(GeoJsonSource("me"))
                // Dış halka -> konumun etrafında yumuşak, yarı saydam bir halo.
                loaded.addLayer(
                    CircleLayer("me-halo-layer","me").withProperties(
                        PropertyFactory.circleColor(ME_MARKER_COLOR),
                        PropertyFactory.circleRadius(20f),
                        PropertyFactory.circleOpacity(0.2f),
                        PropertyFactory.circleBlur(0.4f)
                    )
                )
                // İç nokta -> beyaz kenarlıklı mavi nokta ("my location" tarzı).
                loaded.addLayer(
                    CircleLayer("me-layer","me").withProperties(
                        PropertyFactory.circleColor(ME_MARKER_COLOR),
                        PropertyFactory.circleRadius(9f),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(3f)
                    )
                )
                mapAndStyle = map to loaded
            }
        }
    }

    // Konum noktası -> myLocation her değiştiğinde güncellenir, kamera oynamaz.
    LaunchedEffect(mapAndStyle, myLocation) {
        val (_, style) = mapAndStyle ?: return@LaunchedEffect
        updateMe(style, myLocation)
    }

    // İlk açılışta kullanıcı konumuna tek seferlik zoom.
    var hasZoomedToUser by remember { mutableStateOf(false) }
    LaunchedEffect(mapAndStyle, myLocation) {
        if (hasZoomedToUser) return@LaunchedEffect
        val (map, _) = mapAndStyle ?: return@LaunchedEffect
        val location = myLocation ?: return@LaunchedEffect

        hasZoomedToUser = true
        Log.d("MAP", "İlk zoom -> lat: ${location.latitude}, lng: ${location.longitude}, zoom: 10.0")
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 10.0))
    }

    // AndroidView -> Android ile @Composable köprüsü.
    AndroidView(factory = { mapView }, modifier = modifier)
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
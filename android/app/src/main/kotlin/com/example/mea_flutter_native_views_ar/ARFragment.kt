package com.example.mea_flutter_native_views_ar

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.esri.arcgisruntime.geometry.AngularUnit
import com.esri.arcgisruntime.geometry.AngularUnitId
import com.esri.arcgisruntime.geometry.GeodeticCurveType
import com.esri.arcgisruntime.geometry.GeometryEngine
import com.esri.arcgisruntime.geometry.LinearUnit
import com.esri.arcgisruntime.geometry.LinearUnitId
import com.esri.arcgisruntime.geometry.Point
import com.esri.arcgisruntime.geometry.SpatialReferences
import com.esri.arcgisruntime.mapping.ArcGISMap
import com.esri.arcgisruntime.mapping.ArcGISScene
import com.esri.arcgisruntime.mapping.ArcGISTiledElevationSource
import com.esri.arcgisruntime.mapping.Basemap
import com.esri.arcgisruntime.mapping.BasemapStyle
import com.esri.arcgisruntime.mapping.NavigationConstraint
import com.esri.arcgisruntime.mapping.Surface
import com.esri.arcgisruntime.mapping.view.AtmosphereEffect
import com.esri.arcgisruntime.mapping.view.LocationDisplay
import com.esri.arcgisruntime.mapping.view.SpaceEffect
import com.esri.arcgisruntime.toolkit.ar.ArLocationDataSource
import com.esri.arcgisruntime.toolkit.ar.ArcGISArView
import com.example.mea_flutter_native_views_ar.databinding.ArFragmentBinding
import com.example.mea_flutter_native_views_ar.databinding.SettingBottomSheetLayoutBinding
import com.google.android.material.bottomsheet.BottomSheetDialog


class ARFragment : Fragment(R.layout.ar_fragment) {

    private var _binding: ArFragmentBinding? = null
    private val binding get() = _binding!!

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var sensorEventListener: SensorEventListener

    private var currentLocation: Point? = null
    private var targetLocation: Point? = null

    private var isFirstLocationUpdate = true

    private var isEnableAdjust: Boolean = true

    private var opacity: Float = 0.5f

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ArFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val someInt = requireArguments().getInt("some_int")

        setUpScene()
        setupMiniMap()
        detectDevice()
        setClick()
    }

    override fun onResume() {
        super.onResume()
        binding.arcGisArView.startTracking(ArcGISArView.ARLocationTrackingMode.INITIAL)
        binding.miniMap.resume()
        binding.mapFullscreen.resume()
        sensorManager.registerListener(sensorEventListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        super.onPause()
        binding.miniMap.pause()
        binding.mapFullscreen.pause()
        sensorManager.unregisterListener(sensorEventListener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.arcGisArView.stopTracking()
        binding.arcGisArView.locationDataSource = null
        binding.arcGisArView.sceneView.scene = null

        binding.miniMap.dispose()
        binding.mapFullscreen.dispose()

        _binding = null
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setUpScene() {
        // set up base scene
        binding.arcGisArView.registerLifecycle(lifecycle)
        binding.arcGisArView.sceneView.scene = ArcGISScene(BasemapStyle.ARCGIS_IMAGERY_STANDARD)
        binding.arcGisArView.locationDataSource = ArLocationDataSource(requireContext())

        // set up world-scale AR
        binding.arcGisArView.sceneView.scene.baseSurface.elevationSources.add(ArcGISTiledElevationSource("https://elevation3d.arcgis.com/arcgis/rest/services/WorldElevation3D/Terrain3D/ImageServer"))
        binding.arcGisArView.sceneView.scene.baseSurface.navigationConstraint = NavigationConstraint.NONE
        binding.arcGisArView.sceneView.spaceEffect = SpaceEffect.TRANSPARENT
        binding.arcGisArView.sceneView.atmosphereEffect = AtmosphereEffect.NONE

        // set up opacity for scene
        binding.arcGisArView.sceneView.scene.baseSurface.opacity = 0.5f
        binding.opacitySeekBar.progress = 50
        binding.opacitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                opacity = progress / 100f
                binding.arcGisArView.sceneView.scene.baseSurface.opacity = opacity
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupMiniMap() {
        // set up mini map
        binding.miniMap.map = ArcGISMap(Basemap.createStreets())
        binding.miniMap.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.miniMap.clipToOutline = true

        // disable user interactions (zoom, pan, rotate)
        binding.miniMap.interactionOptions.isZoomEnabled = false
        binding.miniMap.interactionOptions.isPanEnabled = false
        binding.miniMap.interactionOptions.isRotateEnabled = false
        // set up mini map current location
        val locationDisplayMiniMap = binding.miniMap.locationDisplay
        locationDisplayMiniMap.autoPanMode = LocationDisplay.AutoPanMode.RECENTER
        locationDisplayMiniMap.startAsync()
        locationDisplayMiniMap.addLocationChangedListener { event ->
            val location = event.location.position
            binding.miniMap.setViewpointCenterAsync(location, 72000.0)
            currentLocation = location
        }
    }

    private fun detectDevice() {
        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                getTargetLocation()
                setUpDisplayDistance()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
    }

    @SuppressLint("DefaultLocale")
    private fun setUpDisplayDistance() {
        val currentLocation = currentLocation
        val targetLocation = targetLocation
        if (targetLocation != null && targetLocation.x == 0.0 && targetLocation.y == 0.0 && targetLocation.z == 0.0) {
            binding.distance.visibility = View.INVISIBLE
            return
        }
        if (currentLocation != null && targetLocation != null) {
            val point2DCurrent = Point(currentLocation.x, currentLocation.y, SpatialReferences.getWgs84())
            val point2DTarget = Point(targetLocation.x, targetLocation.y, SpatialReferences.getWgs84())
            val geodeticDistance = GeometryEngine.distanceGeodetic(
                point2DCurrent,
                point2DTarget,
                LinearUnit(LinearUnitId.METERS),
                AngularUnit(AngularUnitId.DEGREES),
                GeodeticCurveType.GEODESIC
            )
            val formattedDistance = if (geodeticDistance.distance >= 1000) String.format("%.2f km.", geodeticDistance.distance / 1000) else String.format("%.0f m.", geodeticDistance.distance)
            binding.distance.text = formattedDistance
            binding.distance.visibility = View.VISIBLE
        } else {
            Log.d("checkDistanceAR", "NULL ---> $currentLocation | $targetLocation")
        }
    }

    private fun getTargetLocation() {
        val screenCenterX = binding.arcGisArView.width / 2
        val screenCenterY = binding.arcGisArView.height / 2
        val screenCenterPoint = android.graphics.Point(screenCenterX, screenCenterY)

        val futureMapPoint = binding.arcGisArView.sceneView.screenToLocationAsync(screenCenterPoint)
        futureMapPoint.addDoneListener {
            try {
                val mapPoint = futureMapPoint.get()
                targetLocation = mapPoint
            } catch (e: Exception) {
                targetLocation = null
            }
        }
    }

    private fun setFullscreenMap() {
        // set up fullscreen map
        binding.mapFullscreen.map = ArcGISMap(Basemap.createStreets())
        binding.mapFullscreen.outlineProvider = ViewOutlineProvider.BACKGROUND
        val locationDisplayFullscreenMap = binding.mapFullscreen.locationDisplay
        locationDisplayFullscreenMap.autoPanMode = LocationDisplay.AutoPanMode.OFF
        locationDisplayFullscreenMap.startAsync()
        locationDisplayFullscreenMap.addLocationChangedListener { event ->
            val location = event.location.position
            if (isFirstLocationUpdate) {
                binding.mapFullscreen.setViewpointCenterAsync(location, 72000.0)
                isFirstLocationUpdate = false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setClick() {
        // disable user interactions (zoom, pan, rotate)
        binding.arcGisArView.sceneView.setOnTouchListener { view, motionEvent -> return@setOnTouchListener false }
        // Fullscreen Map
        binding.constraintFullscreenMiniMapExpand.setOnClickListener { actionExpandedFullscreenMap() }
        binding.constraintFullscreenMiniMapExit.setOnClickListener { actionExitFullscreenMap() }
        // Adjust
        binding.constraintAdjust.setOnClickListener { actionAdjust(isEnableAdjust) }
        // Search
        binding.constraintSearch.setOnClickListener { actionSearch() }
        // TOC
        binding.constraintToc.setOnClickListener { actionToc() }
        // Layer
        binding.constraintLayer.setOnClickListener { actionLayer() }
        // Identify
        binding.constraintIdentify.setOnClickListener { actionIdentify() }
        // Setting
        binding.constraintSetting.setOnClickListener { actionSetting(requireContext()) }
    }

    private fun actionExpandedFullscreenMap() {
        isFirstLocationUpdate = true
        setFullscreenMap()

        binding.constraintOpacity.visibility = View.GONE
        binding.constraintDistance.visibility = View.GONE
        binding.lockCenter.visibility = View.GONE
        binding.constraintMiniMap.visibility = View.GONE
        binding.arcGisArView.visibility = View.GONE
        binding.constraintMainTools.visibility = View.GONE
        binding.constraintSetting.visibility = View.GONE
        binding.constraintFullscreenMiniMapExpand.visibility = View.GONE

        binding.constraintFullscreenMiniMapExit.visibility = View.VISIBLE

        binding.constraintMapFullscreen.visibility = View.VISIBLE
    }

    private fun actionExitFullscreenMap() {
        binding.constraintOpacity.visibility = View.VISIBLE
        binding.constraintDistance.visibility = View.VISIBLE
        binding.lockCenter.visibility = View.VISIBLE
        binding.constraintMiniMap.visibility = View.VISIBLE
        binding.arcGisArView.visibility = View.VISIBLE
        binding.constraintMainTools.visibility = View.VISIBLE
        binding.constraintSetting.visibility = View.VISIBLE
        binding.constraintFullscreenMiniMapExpand.visibility = View.VISIBLE

        binding.constraintFullscreenMiniMapExit.visibility = View.GONE

        binding.constraintMapFullscreen.visibility = View.GONE
    }

    private fun actionAdjust(isCanAdjust: Boolean) {
        if (isCanAdjust) {
            isEnableAdjust = false
            binding.constraintOpacity.visibility = View.GONE
            binding.constraintArCalibration.visibility = View.VISIBLE
            binding.arCalibration.bindArcGISArView(binding.arcGisArView)
            binding.arcGisArView.sceneView.scene.baseSurface.opacity = opacity
        } else {
            isEnableAdjust = true
            binding.constraintOpacity.visibility = View.VISIBLE
            binding.constraintArCalibration.visibility = View.GONE
            binding.arCalibration.unbindArcGISArView(binding.arcGisArView)
            binding.arcGisArView.sceneView.scene.baseSurface.opacity = opacity
        }
    }

    private fun actionSearch() {

    }

    private fun actionToc() {

    }

    private fun actionLayer() {

    }

    private fun actionIdentify() {

    }

    @SuppressLint("InflateParams")
    private fun actionSetting(context: Context) {
        val binding = SettingBottomSheetLayoutBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setContentView(binding.root)
        dialog.show()

        binding.reportIssueBtn.setOnClickListener { dialog.dismiss() }
        binding.manageWorkspaceBtn.setOnClickListener { dialog.dismiss() }
        binding.closeBtn.setOnClickListener { dialog.dismiss() }
    }

}
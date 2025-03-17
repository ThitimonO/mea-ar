package com.example.mea_flutter_native_views_ar

import android.Manifest
import android.content.Intent
import android.os.Bundle
import com.esri.arcgisruntime.ArcGISRuntimeEnvironment
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FlutterActivity() {

    private val CHANNEL = "channelAR"
    private val REQUEST_PERMISSIONS_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setApiKey()
        requestPermissions()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "openARPage") {
                val intent = Intent(this, ARActivity::class.java)
                startActivity(intent)
                result.success(null)
            } else {
                result.notImplemented()
            }
        }
    }

    private fun setApiKey() {
        ArcGISRuntimeEnvironment.setApiKey("AAPK2e13f42c6ce44c9b9e9fcb954d5b6117NgiO9fPunLtI2oPEQEqg1_pmMzfDD0NfMgqHueN-kPELHaMJYTYs6xqhgMl_5Ch9")
        //ArcGISRuntimeEnvironment.setLicense("runtimelite,1000,rud6395877228,none,JFB3LNBHPFESF5KHT002")
    }

    private fun requestPermissions() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.CAMERA
            ), REQUEST_PERMISSIONS_CODE
        )
    }
}
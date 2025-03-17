package com.example.mea_flutter_native_views_ar

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.google.ar.core.ArCoreApk

class ARActivity: AppCompatActivity(R.layout.ar_activity) {

    private var userRequestedInstall: Boolean = true
    private var isGooglePlayServicesArInstalled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "AR Page!"
        if (savedInstanceState == null) {
            val bundle = bundleOf("some_int" to 0)
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ARFragment>(R.id.fragment_container_view, args = bundle)
            }
            //checkGooglePlayServicesArInstalled()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isGooglePlayServicesArInstalled) {
            //checkGooglePlayServicesArInstalled()
        }
    }

    private fun checkGooglePlayServicesArInstalled() {
        try {
            when (ArCoreApk.getInstance().requestInstall(this, userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    userRequestedInstall = false
                    return
                }

                ArCoreApk.InstallStatus.INSTALLED -> {
                    isGooglePlayServicesArInstalled = true
                    startFragment()
                    return
                }
            }
        } catch (e: Exception) {
            Log.d("ArTabletopApp", "Error checking Google Play Services for AR: ${e.message}")
        }
    }

    private fun startFragment() {
        if (isGooglePlayServicesArInstalled) {
            val bundle = bundleOf("some_int" to 0)
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<ARFragment>(R.id.fragment_container_view, args = bundle)
            }
        } else {
            Toast.makeText(this, "ERROR!!!", Toast.LENGTH_LONG).show()
        }
    }

}
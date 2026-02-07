package com.agcforge.videodownloader.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionHelper {

    const val MEDIA_PERMISSION_REQUEST_CODE = 1001

    /**
     * Get required permissions based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        } else {
            // Android 12 and below
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }

    /**
     * Check if all media permissions are granted
     */
    fun hasMediaPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Request media permissions
     */
    fun requestMediaPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            getRequiredPermissions(),
            MEDIA_PERMISSION_REQUEST_CODE
        )
    }

    /**
     * Handle permission result
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (requestCode == MEDIA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                onGranted()
            } else {
                onDenied()
            }
        }
    }

    /**
     * Check if should show rationale
     */
    fun shouldShowRationale(activity: Activity): Boolean {
        return getRequiredPermissions().any { permission ->
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
}

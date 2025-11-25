package com.tarun3k.frontandbackvideorecorder.utils

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionUtils {
    
    /**
     * Get required permissions for camera recording based on API level
     * Note: We don't need storage permissions for saving to app storage,
     * only for saving to gallery (which is handled separately)
     */
    fun getCameraPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    }
    
    /**
     * Get storage permissions needed for saving videos to gallery
     */
    fun getStoragePermissionsForGallery(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30-32) - No permission needed for MediaStore
            emptyArray()
        } else {
            // Android 10 and below (API 29 and below)
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }
    }
    
    /**
     * Check if all camera permissions are granted
     */
    fun hasCameraPermissions(context: android.content.Context): Boolean {
        return getCameraPermissions().all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if storage permissions for gallery are granted
     */
    fun hasStoragePermissionsForGallery(context: android.content.Context): Boolean {
        val permissions = getStoragePermissionsForGallery()
        if (permissions.isEmpty()) {
            // Android 11+ doesn't need storage permissions for MediaStore
            return true
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * Check if we can save to gallery (either has permission or doesn't need it)
     */
    fun canSaveToGallery(context: android.content.Context): Boolean {
        // Android 11+ (API 30+) doesn't need storage permissions for MediaStore
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return true
        }
        return hasStoragePermissionsForGallery(context)
    }
}


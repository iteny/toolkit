package com.example.toolkit

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the privacy policy claim that the app is fully offline: no network or
 * broad storage permissions should ever be merged in by a dependency.
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class PrivacyManifestTest {

    @Test
    fun appDoesNotRequestNetworkOrStoragePermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions?.toList().orEmpty()
        listOf(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        ).forEach { permission ->
            assertFalse(
                "unexpected permission $permission in $permissions",
                permission in permissions,
            )
        }
    }
}

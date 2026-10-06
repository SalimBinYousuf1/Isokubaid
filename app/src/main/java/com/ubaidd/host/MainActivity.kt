package com.ubaidd.host

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.ubaidd.host.ui.main.MainStatusScreen
import com.ubaidd.host.ui.setup.SetupWizardScreen
import com.ubaidd.host.ui.theme.SurfaceWhite
import com.ubaidd.host.ui.theme.UbaidTheme
import com.ubaidd.host.ui.viewmodel.HostViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HostViewModel by viewModels()

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.onMediaProjectionGranted(result.data!!)
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Notification permission handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prompt for Android 13+ runtime POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            UbaidTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                    color = SurfaceWhite
                ) {
                    val isSetupCompleted by viewModel.isSetupCompleted.collectAsState()
                    val permissionsState by viewModel.permissionsState.collectAsState()

                    if (!isSetupCompleted) {
                        SetupWizardScreen(
                            viewModel = viewModel,
                            permissionsState = permissionsState,
                            onRequestMediaProjection = {
                                requestMediaProjectionConsent()
                            },
                            onCompleteSetup = {
                                // Transition to main screen
                            }
                        )
                    } else {
                        MainStatusScreen(
                            viewModel = viewModel,
                            onOpenSetup = {
                                viewModel.resetSetupWizard()
                            }
                        )
                    }
                }
            }
        }
    }

    private fun requestMediaProjectionConsent() {
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpm != null) {
            val intent = mpm.createScreenCaptureIntent()
            mediaProjectionLauncher.launch(intent)
        }
    }
}

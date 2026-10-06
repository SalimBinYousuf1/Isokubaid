package com.ubaidd.host.ui.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ubaidd.host.ui.theme.AccentPrimary
import com.ubaidd.host.ui.theme.BorderSubtle
import com.ubaidd.host.ui.theme.CardBackground
import com.ubaidd.host.ui.theme.StatusConnected
import com.ubaidd.host.ui.theme.StatusWaiting
import com.ubaidd.host.ui.theme.SurfaceWhite
import com.ubaidd.host.ui.theme.TextMuted
import com.ubaidd.host.ui.theme.TextPrimary
import com.ubaidd.host.ui.theme.TextSecondary
import com.ubaidd.host.ui.viewmodel.HostViewModel
import com.ubaidd.host.ui.viewmodel.PermissionsState

@Composable
fun SetupWizardScreen(
    viewModel: HostViewModel,
    permissionsState: PermissionsState,
    onRequestMediaProjection: () -> Unit,
    onCompleteSetup: () -> Unit
) {
    val context = LocalContext.current

    // Re-verify permissions every time the user navigates back to the app from Settings
    LifecycleResumeEffect(Unit) {
        viewModel.refreshSystemPermissions()
        onPauseOrDispose { }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SurfaceWhite
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                    Text(
                        text = "Ubaid Host Setup",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Configure device permissions to enable screen mirroring, touch input, file access, and live telemetry.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
            }

            // Step 1: Media Projection Consent
            item {
                SetupStepCard(
                    stepNumber = "1",
                    title = "Screen Capture Consent",
                    description = "Grants MediaProjection permission to capture and stream real display frames via WebRTC.",
                    icon = Icons.Default.ScreenShare,
                    isSatisfied = permissionsState.hasMediaProjectionConsent,
                    actionText = if (permissionsState.hasMediaProjectionConsent) "Consent Granted" else "Grant Screen Access",
                    testTag = "step_media_projection_button",
                    onAction = {
                        onRequestMediaProjection()
                    }
                )
            }

            // Step 2: Accessibility Service
            item {
                SetupStepCard(
                    stepNumber = "2",
                    title = "Accessibility Control Service",
                    description = "Required to execute remote taps, swipes, text injection, and navigation gestures on Ubaid.",
                    icon = Icons.Default.TouchApp,
                    isSatisfied = permissionsState.isAccessibilityBound,
                    actionText = if (permissionsState.isAccessibilityBound) "Active & Bound" else "Open Accessibility Settings",
                    testTag = "step_accessibility_button",
                    onAction = {
                        try {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }

            // Step 3: Notification Listener Access
            item {
                SetupStepCard(
                    stepNumber = "3",
                    title = "Notification Forwarding",
                    description = "Enables real-time notification forwarding from Ubaid to your authorized controller.",
                    icon = Icons.Default.Notifications,
                    isSatisfied = permissionsState.isNotificationListenerBound,
                    actionText = if (permissionsState.isNotificationListenerBound) "Active & Bound" else "Open Notification Settings",
                    testTag = "step_notification_button",
                    onAction = {
                        try {
                            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }

            // Step 4: All-Files Access
            item {
                SetupStepCard(
                    stepNumber = "4",
                    title = "Filesystem Access",
                    description = "Required for remote file browsing and chunked file transfers over peer-to-peer data channel.",
                    icon = Icons.Default.Folder,
                    isSatisfied = permissionsState.hasAllFilesAccess,
                    actionText = if (permissionsState.hasAllFilesAccess) "Access Granted" else "Grant Storage Access",
                    testTag = "step_storage_button",
                    onAction = {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                            }
                        } catch (e: Exception) {
                            // Fallback to general manage storage settings
                            val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(fallback)
                        }
                    }
                )
            }

            // Step 5: Battery Optimization Exemption
            item {
                SetupStepCard(
                    stepNumber = "5",
                    title = "Background Watchdog Exemption",
                    description = "Exempts Ubaid from aggressive OEM task killing so background connections stay persistent.",
                    icon = Icons.Default.Power,
                    isSatisfied = permissionsState.isBatteryOptimizationIgnored,
                    actionText = if (permissionsState.isBatteryOptimizationIgnored) "Exempted" else "Disable Optimization",
                    testTag = "step_battery_button",
                    onAction = {
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )
            }

            // Final step: Finish button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.markSetupCompleted()
                        viewModel.startHostEngine()
                        onCompleteSetup()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("setup_finish_button"),
                    enabled = permissionsState.allRequiredSatisfied,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary,
                        disabledContainerColor = Color(0xFFE2E8F0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (permissionsState.allRequiredSatisfied) "Launch Ubaid Engine" else "Complete Required Steps",
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (permissionsState.allRequiredSatisfied) Color.White else TextMuted
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    title: String,
    description: String,
    icon: ImageVector,
    isSatisfied: Boolean,
    actionText: String,
    testTag: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBackground)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSatisfied) StatusConnected else AccentPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSatisfied) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Satisfied",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Text(
                            text = stepNumber,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSatisfied) Color(0xFFECFDF5) else Color(0xFFFFFBEB))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSatisfied) "VERIFIED" else "PENDING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSatisfied) StatusConnected else StatusWaiting
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = description,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!isSatisfied) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag(testTag),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actionText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = StatusConnected,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = actionText,
                        color = StatusConnected,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

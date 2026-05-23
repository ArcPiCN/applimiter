package com.example.applimiter.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.applimiter.service.OverlayService
import com.example.applimiter.service.PageLimitService

data class PermissionsSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayPermitted: Boolean,
    val overlayRunning: Boolean
)

/**
 * 监听 Lifecycle.Resume，自动刷新权限状态。返回的 snapshot 是 Compose state。
 */
@Composable
fun rememberPermissionsSnapshot(): PermissionsSnapshot {
    val ctx = LocalContext.current
    var snapshot by remember {
        mutableStateOf(
            PermissionsSnapshot(
                accessibilityEnabled = PageLimitService.isEnabled(ctx),
                overlayPermitted = OverlayService.hasPermission(ctx),
                overlayRunning = OverlayService.isRunning()
            )
        )
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                snapshot = PermissionsSnapshot(
                    accessibilityEnabled = PageLimitService.isEnabled(ctx),
                    overlayPermitted = OverlayService.hasPermission(ctx),
                    overlayRunning = OverlayService.isRunning()
                )
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return snapshot
}

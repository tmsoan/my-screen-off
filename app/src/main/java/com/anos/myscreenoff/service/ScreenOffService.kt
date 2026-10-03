package com.anos.myscreenoff.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.anos.myscreenoff.MainActivity
import com.anos.myscreenoff.data.ButtonSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Shows the floating button over every app and locks the screen when it is tapped. An accessibility
 * service is the only way for an app to lock the screen while keeping fingerprint and face unlock.
 */
class ScreenOffService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val repository by lazy { ButtonSettingsRepository(this) }

    private var button: FloatingButton? = null
    private var lockDelayMs = 0L
    private val lock = Runnable { performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) }

    /** True while the screen is off or the lock screen is up, where the button would only be in the way. */
    private val screenLocked = MutableStateFlow(false)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Screen broadcasts can arrive late and out of order, so they only trigger a fresh
            // check. User-present is sent once the lock screen is gone.
            screenLocked.value = intent.action != Intent.ACTION_USER_PRESENT && isScreenLocked()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Exported because user-present comes from System UI, which is not the system uid. All
        // three actions are protected, so other apps still cannot send them.
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        screenLocked.value = isScreenLocked()
        scope.launch {
            val button = FloatingButton(
                context = this@ScreenOffService,
                position = repository.position(),
                onTap = ::onButtonTapped,
                onLongPress = ::openApp,
                onMoved = { position -> scope.launch { repository.savePosition(position) } },
                onDismissed = { scope.launch { repository.setVisible(false) } },
            )
            this@ScreenOffService.button = button
            combine(repository.settings, repository.visible, screenLocked) { settings, visible, locked ->
                settings to (visible && !locked)
            }.collect { (settings, shown) ->
                lockDelayMs = settings.lockDelayMs.toLong()
                button.apply(settings)
                if (shown) button.show() else button.hide()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        button?.onScreenChanged()
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacks(lock)
        unregisterReceiver(screenReceiver)
        button?.hide()
        button = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    /** Locks after the chosen delay. A second tap during the delay cancels it. */
    private fun onButtonTapped() {
        if (handler.hasCallbacks(lock)) {
            handler.removeCallbacks(lock)
        } else {
            handler.postDelayed(lock, lockDelayMs)
        }
    }

    /** Opens the settings screen, dropping a lock still waiting on its delay. */
    private fun openApp() {
        handler.removeCallbacks(lock)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun isScreenLocked(): Boolean =
        !getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked

    companion object {
        /** Whether the service is turned on in Accessibility settings. */
        fun isEnabled(context: Context): Boolean =
            EnabledServices.contains(enabledServices(context), component(context))

        /** Whether the app may turn the service on and off itself: WRITE_SECURE_SETTINGS, granted over adb. */
        fun canToggle(context: Context): Boolean =
            context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /** Turns the service on or off as Accessibility settings would. Returns false without [canToggle]. */
        fun setEnabled(context: Context, enabled: Boolean): Boolean {
            if (!canToggle(context)) return false
            val resolver = context.contentResolver
            val services = EnabledServices.with(enabledServices(context), component(context), enabled)
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, services)
            if (enabled) Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            return true
        }

        private fun enabledServices(context: Context): String? =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        private fun component(context: Context): String =
            ComponentName(context, ScreenOffService::class.java).flattenToString()
    }
}

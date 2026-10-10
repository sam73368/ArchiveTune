/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppLockKey

/**
 * Asks for the device fingerprint / PIN / pattern when the app is opened, and again after it has
 * been in the background for [GRACE_MS]. Uses the platform prompt (API 30+) or the system
 * "confirm credential" screen (API 26-29), so no extra dependency is needed.
 */
class AppLockController(
    private val activity: ComponentActivity,
) {
    var locked by mutableStateOf(false)
        private set

    private var authInProgress = false
    private var promptSuppressed = false
    private var stoppedAt = 0L
    private var cancellation: CancellationSignal? = null

    private val credentialLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            authInProgress = false
            if (result.resultCode == Activity.RESULT_OK) unlock() else promptSuppressed = true
        }

    private fun enabled(): Boolean = activity.dataStore[AppLockKey] == true && isDeviceSecure(activity)

    fun onCreate() {
        locked = enabled()
    }

    /** Adds the lock screen on top of the activity's content. Call after `setContent`. */
    fun install() {
        activity.addContentView(
            ComposeView(activity).apply {
                setContent { LockOverlay() }
            },
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
    }

    fun onStart() {
        if (!enabled()) {
            locked = false
            return
        }
        val away = if (stoppedAt == 0L) 0L else SystemClock.elapsedRealtime() - stoppedAt
        stoppedAt = 0L
        if (away > GRACE_MS) locked = true
        if (locked && !promptSuppressed) authenticate()
    }

    fun onStop() {
        if (authInProgress) return
        stoppedAt = SystemClock.elapsedRealtime()
        // A real trip to the background: allow the prompt to appear again on return.
        promptSuppressed = false
    }

    fun authenticate() {
        if (authInProgress || !locked) return
        authInProgress = true
        promptSuppressed = false
        val title = activity.getString(R.string.app_lock_prompt)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val signal = CancellationSignal()
            cancellation = signal
            runCatching {
                BiometricPrompt
                    .Builder(activity)
                    .setTitle(title)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                            BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                    ).build()
                    .authenticate(
                        signal,
                        activity.mainExecutor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                                authInProgress = false
                                unlock()
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence?,
                            ) {
                                authInProgress = false
                                promptSuppressed = true
                            }
                        },
                    )
            }.onFailure {
                authInProgress = false
                promptSuppressed = true
            }
        } else {
            val keyguard = activity.getSystemService(KeyguardManager::class.java)
            @Suppress("DEPRECATION")
            val intent = keyguard?.createConfirmDeviceCredentialIntent(title, null)
            if (intent == null) {
                authInProgress = false
                locked = false
            } else {
                credentialLauncher.launch(intent)
            }
        }
    }

    private fun unlock() {
        locked = false
        stoppedAt = 0L
        promptSuppressed = false
    }

    @androidx.compose.runtime.Composable
    private fun LockOverlay() {
        if (!locked) return
        MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
            BackHandler { activity.moveTaskToBack(true) }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        // Swallow every touch so nothing underneath can be used while locked.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.lock),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.app_lock_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { authenticate() }) {
                    Text(stringResource(R.string.app_lock_unlock))
                }
            }
        }
    }

    companion object {
        private const val GRACE_MS = 15_000L

        fun isDeviceSecure(context: Context): Boolean =
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }
}

/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.shared.platform.PlatformServices
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DAY_MS = 86_400_000L

/** Show the warning banner when the signature has this little time left. */
private const val WARNING_THRESHOLD_MS = 3 * DAY_MS

sealed interface SigningStatus {
    /** Not sideloaded / unknown (e.g. no provisioning profile). */
    data object Unknown : SigningStatus

    data class Valid(
        val msLeft: Long,
    ) : SigningStatus

    data object Expired : SigningStatus
}

/** Re-evaluated every 30 minutes so the banner appears even if the app stays open for days. */
@OptIn(ExperimentalTime::class)
@Composable
fun rememberSigningStatus(platform: PlatformServices): SigningStatus {
    val expiry = remember { platform.signingExpiryEpochMs() }
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60 * 1000L)
            now = Clock.System.now().toEpochMilliseconds()
        }
    }
    return when {
        expiry <= 0L -> SigningStatus.Unknown
        expiry <= now -> SigningStatus.Expired
        else -> SigningStatus.Valid(expiry - now)
    }
}

fun describeTimeLeft(msLeft: Long): String {
    val days = msLeft / DAY_MS
    val hours = (msLeft % DAY_MS) / 3_600_000L
    return when {
        days >= 2 -> "$days jours"
        days == 1L -> if (hours > 0) "1 jour et $hours h" else "1 jour"
        hours >= 1 -> "$hours h"
        else -> "moins d'une heure"
    }
}

/** Banner shown above the content when the app is about to expire (or has). */
@Composable
fun SigningExpiryBanner(
    status: SigningStatus,
    platform: PlatformServices,
    modifier: Modifier = Modifier,
) {
    var dismissed by remember { mutableStateOf(false) }
    val message =
        when (status) {
            SigningStatus.Expired -> "La signature d'ArchiveTune a expiré. Rafraîchis-la dans SideStore."
            is SigningStatus.Valid ->
                if (status.msLeft <= WARNING_THRESHOLD_MS) {
                    "ArchiveTune expire dans ${describeTimeLeft(status.msLeft)}. Rafraîchis-la dans SideStore."
                } else {
                    null
                }
            SigningStatus.Unknown -> null
        }
    if (message == null || dismissed) return

    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { dismissed = true }) { Text("Plus tard") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { platform.openSideStore() }) {
                    Text("Ouvrir SideStore", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Always-visible card (Library tab) with the time left and the SideStore button. */
@Composable
fun SigningInfoCard(
    status: SigningStatus,
    platform: PlatformServices,
    modifier: Modifier = Modifier,
) {
    val text =
        when (status) {
            SigningStatus.Unknown -> return
            SigningStatus.Expired -> "Signature expirée"
            is SigningStatus.Valid -> "Signature valide encore ${describeTimeLeft(status.msLeft)}"
        }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "SideStore la renouvelle automatiquement s'il est configuré.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { platform.openSideStore() }) { Text("SideStore") }
        }
    }
}

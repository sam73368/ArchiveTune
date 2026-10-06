/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.shared.platform

/** Small platform hooks the shared UI needs (implemented in Swift on iOS). */
interface PlatformServices {
    /**
     * When the app's code signature stops being valid, in epoch milliseconds; 0 when unknown.
     * For a sideloaded build this is the expiry of the provisioning profile SideStore signed it with.
     */
    fun signingExpiryEpochMs(): Long

    /** Opens SideStore (or AltStore) so the user can refresh the app; false if neither is installed. */
    fun openSideStore(): Boolean
}

/** Used where no platform implementation exists (JVM tests). */
object NoPlatformServices : PlatformServices {
    override fun signingExpiryEpochMs(): Long = 0L

    override fun openSideStore(): Boolean = false
}

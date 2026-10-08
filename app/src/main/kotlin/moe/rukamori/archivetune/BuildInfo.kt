/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune

import moe.rukamori.archivetune.constants.UpdateChannel

/**
 * The GitHub repository this build is published from. The in-app updater, the "GitHub" buttons and
 * the update changelog all read it from here so they can never point back at the original project.
 */
internal const val ForkRepository = "sam73368/ArchiveTune"
internal const val ForkRepositoryUrl = "https://github.com/$ForkRepository"

internal val isCanaryBuild: Boolean
    get() = BuildConfig.IS_NIGHTLY

internal val defaultUpdateChannel: UpdateChannel
    get() = if (isCanaryBuild) UpdateChannel.CANARY else UpdateChannel.STABLE

internal val currentBuildHash: String?
    get() = BuildConfig.NIGHTLY_BUILD_HASH.takeIf { it.isNotBlank() }

internal fun formatVersionName(
    versionName: String = BuildConfig.VERSION_NAME,
    buildHash: String? = currentBuildHash,
): String = listOfNotNull(versionName.takeIf { it.isNotBlank() }, buildHash).joinToString(" ")

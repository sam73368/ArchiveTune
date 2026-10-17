/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.navigation.NavController
import moe.rukamori.archivetune.ui.screens.Screens

fun NavController.backToMain() {
    val mainRoutes = Screens.MainScreens.map { it.route }

    while (previousBackStackEntry != null &&
        currentBackStackEntry?.destination?.route !in mainRoutes
    ) {
        popBackStack()
    }
}

/**
 * Jumps straight back to the Home tab from anywhere in the app.
 *
 * Pops back to an existing Home entry when there is one (keeps its scroll position and loaded
 * feed); otherwise navigates to Home the same way the bottom bar does, so the back stack never
 * grows a second Home copy.
 */
fun NavController.navigateHome() {
    val homeRoute = Screens.Home.route
    if (currentDestination?.route == homeRoute) return
    val popped = runCatching { popBackStack(homeRoute, false) }.getOrDefault(false)
    if (popped && currentDestination?.route == homeRoute) return
    runCatching {
        navigate(homeRoute) {
            popUpTo(graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

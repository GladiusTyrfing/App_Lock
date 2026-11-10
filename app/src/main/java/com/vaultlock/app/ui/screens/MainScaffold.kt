package com.vaultlock.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vaultlock.app.app
import com.vaultlock.app.data.AppSettings

object Routes {
    const val APPS = "apps"
    const val VAULT = "vault"
    const val INTRUDERS = "intruders"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{id}"
    const val NOTE = "note/{id}"
    const val PERMISSIONS = "permissions"
    const val LOCK_SETUP = "lock_setup"
    const val RECOVERY = "recovery"
    const val NOTIFICATIONS = "notifications"

    fun viewer(id: String) = "viewer/$id"
    fun note(id: String?) = "note/${id ?: "new"}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.APPS, "Apps", Icons.Rounded.Apps),
    Tab(Routes.VAULT, "Vault", Icons.Rounded.Lock),
    Tab(Routes.INTRUDERS, "Intruders", Icons.Rounded.PersonSearch),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

@Composable
fun MainScaffold(settings: AppSettings) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val records by context.app.intruders.records.collectAsStateWithLifecycle()
    val unseen = records.count { it.timestamp > settings.intrudersSeenAt }
    val showBar = route == null || tabs.any { it.route == route }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                if (tab.route == Routes.INTRUDERS && unseen > 0) {
                                    BadgedBox(badge = { Badge { Text(unseen.toString()) } }) {
                                        Icon(tab.icon, contentDescription = tab.label)
                                    }
                                } else {
                                    Icon(tab.icon, contentDescription = tab.label)
                                }
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.APPS,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable(Routes.APPS) {
                AppsScreen(settings = settings, onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) })
            }
            composable(Routes.VAULT) {
                VaultScreen(
                    settings = settings,
                    onOpenItem = { nav.navigate(Routes.viewer(it)) },
                    onOpenNote = { nav.navigate(Routes.note(it)) },
                )
            }
            composable(Routes.INTRUDERS) {
                IntrudersScreen(settings = settings, onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                    onChangeLock = { nav.navigate(Routes.LOCK_SETUP) },
                    onNewRecovery = { nav.navigate(Routes.RECOVERY) },
                    onOpenNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                )
            }
            composable(Routes.PERMISSIONS) { PermissionsScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.LOCK_SETUP) { ChangeLockScreen(onDone = { nav.popBackStack() }) }
            composable(Routes.RECOVERY) { NewRecoveryScreen(onDone = { nav.popBackStack() }) }
            composable(Routes.NOTIFICATIONS) { HiddenNotificationsScreen(settings = settings, onBack = { nav.popBackStack() }) }
            composable(Routes.VIEWER, arguments = listOf(navArgument("id") { type = NavType.StringType })) { back ->
                ViewerScreen(id = back.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable(Routes.NOTE, arguments = listOf(navArgument("id") { type = NavType.StringType })) { back ->
                val id = back.arguments?.getString("id")
                NoteEditorScreen(id = id?.takeIf { it != "new" }, onBack = { nav.popBackStack() })
            }
        }
    }
}

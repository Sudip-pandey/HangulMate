package com.hangulmate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hangulmate.data.preferences.SettingsRepository
import com.hangulmate.ui.navigation.HangulMateNavHost
import com.hangulmate.ui.navigation.Screen
import com.hangulmate.ui.theme.HangulMateTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = "system")
            HangulMateTheme(themeMode = themeMode) {
                HangulMateScaffold()
            }
        }
    }
}

@Composable
private fun HangulMateScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val tabs = listOf(
        BottomTab(Screen.Home, Icons.Filled.Home, R.string.tab_home),
        BottomTab(Screen.Dictionary, Icons.Filled.MenuBook, R.string.tab_dictionary),
        BottomTab(Screen.Camera, Icons.Filled.CameraAlt, R.string.tab_camera),
        BottomTab(Screen.Practice, Icons.Filled.School, R.string.tab_practice),
        BottomTab(Screen.Profile, Icons.Filled.Person, R.string.tab_profile),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected =
                        currentDestination?.hierarchy?.any { it.route == tab.screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            val route = when (tab.screen) {
                                is Screen.Dictionary -> Screen.Dictionary.createRoute()
                                else -> tab.screen.route
                            }
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        HangulMateNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

private data class BottomTab(
    val screen: Screen,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val labelRes: Int,
)

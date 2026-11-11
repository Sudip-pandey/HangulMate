package com.hangulmate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.hangulmate.ui.camera.CameraScreen
import com.hangulmate.ui.collections.CollectionsScreen
import com.hangulmate.ui.dictionary.AddWordScreen
import com.hangulmate.ui.dictionary.DictionaryScreen
import com.hangulmate.ui.dictionary.WordDetailScreen
import com.hangulmate.ui.home.HomeScreen
import com.hangulmate.ui.practice.PracticeScreen
import com.hangulmate.ui.profile.ProfileScreen

@Composable
fun HangulMateNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToDictionary = {
                    navController.navigate(Screen.Dictionary.createRoute())
                },
                onNavigateToPractice = { navController.navigate(Screen.Practice.route) },
                onNavigateToWord = { wordId ->
                    navController.navigate(Screen.WordDetail.createRoute(wordId))
                },
                onNavigateToCamera = { navController.navigate(Screen.Camera.route) },
            )
        }
        composable(
            route = Screen.Dictionary.route,
            arguments = listOf(
                navArgument("query") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { backStackEntry ->
            val rawQuery = backStackEntry.arguments?.getString("query").orEmpty()
            // Defensive: the bottom bar navigates with the raw route pattern
            // ("dictionary?query={query}"), which would otherwise arrive here
            // as a literal "{query}" query. Treat it as no query. MainActivity
            // should navigate with Screen.Dictionary.createRoute() instead —
            // see the integration notes.
            val initialQuery = if (rawQuery == "{query}") "" else rawQuery
            DictionaryScreen(
                onWordClick = { wordId ->
                    navController.navigate(Screen.WordDetail.createRoute(wordId))
                },
                initialQuery = initialQuery,
                onAddWord = { navController.navigate(Screen.AddWord.createRoute()) },
            )
        }
        composable(
            route = Screen.AddWord.route,
            arguments = listOf(
                navArgument("prefillKorean") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            AddWordScreen(
                onWordSaved = { wordId ->
                    navController.navigate(Screen.WordDetail.createRoute(wordId))
                },
            )
        }
        composable(
            route = Screen.WordDetail.route,
            arguments = listOf(navArgument("wordId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val wordId = backStackEntry.arguments?.getString("wordId").orEmpty()
            WordDetailScreen(
                wordId = wordId,
                onWordClick = { targetWordId ->
                    navController.navigate(Screen.WordDetail.createRoute(targetWordId))
                },
            )
        }
        composable(Screen.Camera.route) {
            CameraScreen(
                onWordClick = { wordId ->
                    navController.navigate(Screen.WordDetail.createRoute(wordId))
                },
                onAddWord = { korean ->
                    navController.navigate(Screen.AddWord.createRoute(korean))
                },
            )
        }
        composable(Screen.Practice.route) {
            PracticeScreen()
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateToCollections = {
                    navController.navigate(Screen.Collections.route)
                },
                onNavigateToAddWord = {
                    navController.navigate(Screen.AddWord.createRoute())
                },
            )
        }
        composable(Screen.Collections.route) {
            CollectionsScreen(
                onWordClick = { wordId ->
                    navController.navigate(Screen.WordDetail.createRoute(wordId))
                },
            )
        }
    }
}

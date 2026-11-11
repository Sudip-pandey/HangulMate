package com.hangulmate.ui.navigation

import android.net.Uri

/**
 * All navigation destinations. Five bottom tabs + word detail + collections.
 *
 * Contract (owned by the Home/Dictionary agent; other agents conform):
 * - [Screen.Dictionary] carries an optional `query` argument (default "").
 *   Use [Screen.Dictionary.createRoute] to build it; navigating to the bare
 *   "dictionary" route is the same as an empty query.
 * - [Screen.Collections] is implemented by the collections agent as
 *   `CollectionsScreen(onWordClick: (String) -> Unit)` in
 *   package `com.hangulmate.ui.collections`.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")

    data object Dictionary : Screen("dictionary?query={query}") {
        fun createRoute(query: String = ""): String =
            if (query.isBlank()) "dictionary"
            else "dictionary?query=${Uri.encode(query)}"
    }

    data object Camera : Screen("camera")
    data object Practice : Screen("practice")
    data object Profile : Screen("profile")
    data object Collections : Screen("collections")

    data object WordDetail : Screen("word_detail/{wordId}") {
        fun createRoute(wordId: String): String = "word_detail/$wordId"
    }
}

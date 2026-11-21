package com.hangulmate.ui.navigation

import android.net.Uri

/**
 * All navigation destinations. Five bottom tabs + word detail + collections
 * + add word + grammar list/detail.
 *
 * Contract (owned by the Home/Dictionary agent; other agents conform):
 * - [Screen.Dictionary] carries an optional `query` argument (default "").
 *   Use [Screen.Dictionary.createRoute] to build it; navigating to the bare
 *   "dictionary" route is the same as an empty query.
 * - [Screen.Collections] is implemented by the collections agent as
 *   `CollectionsScreen(onWordClick: (String) -> Unit)` in
 *   package `com.hangulmate.ui.collections`.
 * - [Screen.AddWord] carries an optional `prefillKorean` argument (default "").
 *   Use [Screen.AddWord.createRoute] to build it.
 * - [Screen.GrammarDetail] carries the grammar point id
 *   (e.g. "G-B1-U01-01"). Use [Screen.GrammarDetail.createRoute] to build it.
 *   Grammar is reached from the Home card, not from a bottom tab — the
 *   five tabs stay as they are.
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

    data object Grammar : Screen("grammar")

    data object GrammarDetail : Screen("grammar_detail/{pointId}") {
        fun createRoute(pointId: String): String = "grammar_detail/$pointId"
    }

    data object WordDetail : Screen("word_detail/{wordId}") {
        fun createRoute(wordId: String): String = "word_detail/$wordId"
    }

    data object AddWord : Screen("add_word?prefillKorean={prefillKorean}") {
        fun createRoute(prefillKorean: String = ""): String =
            if (prefillKorean.isBlank()) "add_word"
            else "add_word?prefillKorean=${Uri.encode(prefillKorean)}"
    }
}

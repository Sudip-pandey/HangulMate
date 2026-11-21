package com.hangulmate.domain.repository

import com.hangulmate.domain.model.GrammarPoint
import kotlinx.coroutines.flow.StateFlow

/** Load state of the bundled grammar seed (`assets/seed/grammar_seed.json`). */
sealed interface GrammarLoadState {
    data object Loading : GrammarLoadState

    /** All bundled points, in seed order; [skipped] counts malformed rows. */
    data class Ready(val points: List<GrammarPoint>, val skipped: Int) : GrammarLoadState

    data class Failed(val message: String) : GrammarLoadState
}

/**
 * Read-only access to the bundled EPS-TOPIK grammar points.
 *
 * Deliberately NOT a Room table: the 120 points are static reference
 * data with no user state attached (no favorites, no progress), so a
 * database migration would add regression risk for no benefit. The file
 * is parsed once, cached in memory, and served from there.
 */
interface GrammarRepository {

    val state: StateFlow<GrammarLoadState>

    /** Parses the bundled seed on first call; later calls are a no-op. */
    suspend fun loadIfNeeded()

    /** The point with [id], or null when not loaded / not present. */
    fun getById(id: String): GrammarPoint?
}

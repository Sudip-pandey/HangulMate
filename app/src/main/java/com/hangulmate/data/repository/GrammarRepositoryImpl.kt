package com.hangulmate.data.repository

import android.content.Context
import android.util.Log
import com.hangulmate.domain.model.GrammarPoint
import com.hangulmate.domain.repository.GrammarLoadState
import com.hangulmate.domain.repository.GrammarRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Loads the bundled grammar seed once and serves it from memory.
 *
 * - Asset: `seed/grammar_seed.json` (120 points, v0.2). Missing asset or
 *   malformed JSON reports [GrammarLoadState.Failed] — never a crash —
 *   and the next launch retries, because nothing is marked as loaded
 *   until a parse succeeds.
 * - Concurrent loads are harmless: the parse is idempotent and the last
 *   write wins with identical data.
 */
class GrammarRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : GrammarRepository {

    private val _state =
        MutableStateFlow<GrammarLoadState>(GrammarLoadState.Loading)
    override val state: StateFlow<GrammarLoadState> = _state.asStateFlow()

    override suspend fun loadIfNeeded() {
        if (_state.value is GrammarLoadState.Ready) return
        _state.value = GrammarLoadState.Loading
        withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open(ASSET_PATH)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsed = GrammarJsonParser.parse(json)
                _state.value = GrammarLoadState.Ready(parsed.points, parsed.skipped)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load grammar seed", e)
                _state.value = GrammarLoadState.Failed(
                    e.message ?: "Could not load grammar",
                )
            }
        }
    }

    override fun getById(id: String): GrammarPoint? {
        val ready = _state.value as? GrammarLoadState.Ready ?: return null
        return ready.points.find { it.id == id }
    }

    companion object {
        private const val TAG = "GrammarRepository"
        private const val ASSET_PATH = "seed/grammar_seed.json"
    }
}

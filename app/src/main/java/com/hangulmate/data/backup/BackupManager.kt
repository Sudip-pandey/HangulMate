package com.hangulmate.data.backup

import android.content.Context
import com.hangulmate.domain.model.ImportReport
import com.hangulmate.domain.repository.VocabularyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local backup and restore. Everything stays on the device: an export is
 * either written to a document the user picks (Storage Access Framework)
 * by the caller, or saved as a file in app-private storage. No network,
 * no account, no login.
 *
 * The backup format is the JSON produced by
 * [VocabularyRepository.exportAll] and consumed by
 * [VocabularyRepository.importJson].
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vocabularyRepository: VocabularyRepository,
) {

    /** The full export JSON string (words + progress + favorites). */
    suspend fun exportJson(): String = vocabularyRepository.exportAll()

    /** Imports a backup JSON string; returns added / skipped / error counts. */
    suspend fun importJson(json: String): ImportReport =
        vocabularyRepository.importJson(json)

    /**
     * Writes the current export to app-private storage at
     * `filesDir/backup/hangulmate-backup-YYYY-MM-DD.json` (overwrites
     * today's file when run twice) and returns the file.
     */
    suspend fun writeLocalBackup(): File {
        val dir = File(context.filesDir, BACKUP_DIR).apply { mkdirs() }
        val date = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val file = File(dir, "hangulmate-backup-$date.json")
        file.writeText(exportJson())
        return file
    }

    /** The most recently written local backup, or null when none exists. */
    fun latestLocalBackup(): File? {
        val dir = File(context.filesDir, BACKUP_DIR)
        if (!dir.isDirectory) return null
        return dir.listFiles()
            ?.filter { it.name.startsWith(BACKUP_PREFIX) && it.name.endsWith(".json") }
            ?.maxByOrNull { it.lastModified() }
    }

    companion object {
        const val BACKUP_DIR = "backup"
        const val BACKUP_PREFIX = "hangulmate-backup-"
    }
}

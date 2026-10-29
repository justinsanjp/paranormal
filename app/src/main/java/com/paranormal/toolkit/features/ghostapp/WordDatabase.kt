package com.paranormal.toolkit.features.ghostapp

import android.content.Context
import android.net.Uri
import com.paranormal.toolkit.data.documentDisplayName
import java.io.File

data class WordBankState(
    val entries: List<String>,
    val sourceName: String?,
    val importedAtMillis: Long,
) {
    val isLoaded: Boolean get() = entries.isNotEmpty()

    companion object {
        val EMPTY = WordBankState(entries = emptyList(), sourceName = null, importedAtMillis = 0L)
    }
}

/**
 * The GhostApp ships with no vocabulary at all. Its entry list is whatever
 * text file the user imports from their own storage - one entry per line.
 *
 * Nothing in here authors, curates, translates, weights or reorders those
 * entries, and duplicates are deliberately kept: if a file lists a word
 * twice, that is the user's own weighting and not ours to undo. The only
 * lines dropped are blank ones and '#' comments, and the only cap is a
 * memory guard against an accidentally picked huge file.
 */
class WordDatabase(private val context: Context) {

    private val dir: File
        get() = File(context.getExternalFilesDir(null), "ghostapp").apply { mkdirs() }

    private val entriesFile: File get() = File(dir, "wordbank.txt")
    private val sourceFile: File get() = File(dir, "wordbank.source")

    fun load(): WordBankState {
        val file = entriesFile
        if (!file.exists()) return WordBankState.EMPTY
        val entries = usableLines(runCatching { file.readLines() }.getOrDefault(emptyList()))
        if (entries.isEmpty()) return WordBankState.EMPTY
        val source = runCatching { sourceFile.readText().trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
        return WordBankState(entries = entries, sourceName = source, importedAtMillis = file.lastModified())
    }

    /** Copies the picked document in verbatim. Throws if it holds no usable lines. */
    fun import(uri: Uri): WordBankState {
        val lines = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readLines()
        } ?: throw IllegalArgumentException("Die Datei konnte nicht gelesen werden.")

        val entries = usableLines(lines)
        require(entries.isNotEmpty()) {
            "Die Datei enthält keine lesbaren Zeilen. Erwartet wird eine Textdatei mit einem Eintrag pro Zeile."
        }

        val name = documentDisplayName(context, uri)
        entriesFile.writeText(entries.joinToString("\n"))
        sourceFile.writeText(name)
        return WordBankState(entries = entries, sourceName = name, importedAtMillis = System.currentTimeMillis())
    }

    fun clear() {
        entriesFile.delete()
        sourceFile.delete()
    }

    private fun usableLines(lines: List<String>): List<String> =
        lines.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .take(MAX_ENTRIES)
            .toList()

    private companion object {
        const val MAX_ENTRIES = 100_000
    }
}

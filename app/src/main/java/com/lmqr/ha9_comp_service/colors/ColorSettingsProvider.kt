package com.lmqr.ha9_comp_service.colors

import android.content.ContentProvider
import android.content.ContentValues
import android.content.SharedPreferences
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.preference.PreferenceManager

class ColorSettingsProvider : ContentProvider() {
    private lateinit var sharedPreferences: SharedPreferences

    private val changeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || ColorKeys.affectsRecolor(key))
            context?.contentResolver?.notifyChange(BASE_URI, null)
    }

    override fun onCreate(): Boolean {
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context!!)
        sharedPreferences.registerOnSharedPreferenceChangeListener(changeListener)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val packageName = uri.lastPathSegment ?: return null
        return MatrixCursor(COLUMNS).apply {
            sharedPreferences.recolorConfigFor(packageName)?.let { addRow(it.toRow()) }
        }
    }

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException()

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = throw UnsupportedOperationException()

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val AUTHORITY = "com.lmqr.ha9_comp_service.colors"
        val BASE_URI: Uri = Uri.parse("content://$AUTHORITY/app")

        private val COLUMNS = arrayOf(
            "line_threshold", "fill_levels", "fill_dark_cutoff", "fill_light_cutoff", "light_gray"
        )

        fun appUri(packageName: String): Uri = BASE_URI.buildUpon().appendPath(packageName).build()

        fun recolorConfigFrom(cursor: Cursor): RecolorConfig? {
            if (!cursor.moveToFirst())
                return null
            return RecolorConfig(
                lineThreshold = cursor.getInt(0),
                fillLevels = FillLevels.entries[cursor.getInt(1)],
                fillDarkCutoff = cursor.getInt(2),
                fillLightCutoff = cursor.getInt(3),
                lightGray = cursor.getInt(4),
            )
        }

        private fun RecolorConfig.toRow() =
            arrayOf(lineThreshold, fillLevels.ordinal, fillDarkCutoff, fillLightCutoff, lightGray)
    }
}

package de.dvld.heartthrum

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

object Prefs {
    @Composable
    fun current(): SharedPreferences {
        val context = LocalContext.current
        return context.getSharedPreferences("heartthrum", Context.MODE_PRIVATE)
    }
}

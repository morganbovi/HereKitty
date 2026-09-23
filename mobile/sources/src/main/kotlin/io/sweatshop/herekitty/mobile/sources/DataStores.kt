package io.sweatshop.herekitty.mobile.sources

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.first

private val Context.deviceDataStore by preferencesDataStore(name = "device")
private val INSTALL_ID_KEY = stringPreferencesKey("installId")

/** Generated once on first call and persisted — not Android ID, which can change underneath an
 *  install. This is the stable identity a device keeps in Firestore/RTDB for its lifetime. */
internal suspend fun installId(context: Context): String {
    val prefs = context.deviceDataStore.data.first()
    prefs[INSTALL_ID_KEY]?.let { return it }
    val generated = UUID.randomUUID().toString()
    context.deviceDataStore.edit { it[INSTALL_ID_KEY] = generated }
    return generated
}

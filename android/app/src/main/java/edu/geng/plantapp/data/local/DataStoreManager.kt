package edu.geng.plantapp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {
    companion object {
        val JWT_TOKEN_KEY = stringPreferencesKey("jwt_token")
        val USERNAME_KEY = stringPreferencesKey("username")
        val PASSWORD_KEY = stringPreferencesKey("password")
        val REMEMBER_ME_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("remember_me")
        val HAS_SHOWN_WELCOME_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("has_shown_welcome")
        val BASE_URL_KEY = stringPreferencesKey("base_url")
    }

    // 保存自定义 Base URL
    suspend fun saveBaseUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[BASE_URL_KEY] = url
        }
    }

    // 获取 Base URL 流
    val baseUrlFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[BASE_URL_KEY]
    }

    // 保存 Token
    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[JWT_TOKEN_KEY] = token
        }
    }

    // 获取 Token 流
    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[JWT_TOKEN_KEY]
    }

    // 清除 Token
    suspend fun clearToken() {
        context.dataStore.edit { preferences ->
            preferences.remove(JWT_TOKEN_KEY)
            preferences.remove(HAS_SHOWN_WELCOME_KEY)
        }
    }

    // 保存记住密码选项
    suspend fun saveRememberMe(rememberMe: Boolean, username: String, pass: String) {
        context.dataStore.edit { preferences ->
            preferences[REMEMBER_ME_KEY] = rememberMe
            if (rememberMe) {
                preferences[USERNAME_KEY] = username
                preferences[PASSWORD_KEY] = pass
            } else {
                preferences.remove(USERNAME_KEY)
                preferences.remove(PASSWORD_KEY)
            }
        }
    }

    // 获取记住密码选项
    val rememberMeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[REMEMBER_ME_KEY] ?: false
    }

    // 获取保存的用户名和密码
    val savedCredentialsFlow: Flow<Pair<String, String>> = context.dataStore.data.map { preferences ->
        val user = preferences[USERNAME_KEY] ?: ""
        val pass = preferences[PASSWORD_KEY] ?: ""
        Pair(user, pass)
    }

    suspend fun setWelcomeShown() {
        context.dataStore.edit { preferences ->
            preferences[HAS_SHOWN_WELCOME_KEY] = true
        }
    }

    val hasShownWelcomeFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_SHOWN_WELCOME_KEY] ?: false
    }
}

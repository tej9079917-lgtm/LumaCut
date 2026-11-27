package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"
private const val FALLBACK_ASSET_NAME = "firebase_client_config.json"

private val firebaseInitLock = Any()
private val appCheckInstalled = AtomicBoolean(false)
@Volatile
private var cachedDebugToken: String? = null

/**
 * Configures Firebase and Firebase App Check from MainActivity.onCreate() before any UI,
 * ViewModel, or Firebase AI calls execute.
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    ensureFirebaseReady(context, intent)
}

/**
 * Guarantees a default [FirebaseApp] and App Check provider are initialized before any
 * Firebase AI Logic call runs. Falls back cleanly from standard google-services resource
 * lookup to direct package resource lookup and bundled build-time asset config if
 * FirebaseOptions.fromResource() returns null on OEM or exported APK builds.
 */
fun ensureFirebaseReady(context: Context, intent: Intent? = null): FirebaseApp? {
    val appContext = context.applicationContext ?: context
    intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        ?.takeIf { it.isNotBlank() }
        ?.let { cachedDebugToken = it }

    val app: FirebaseApp? = synchronized(firebaseInitLock) {
        try {
            val existing = FirebaseApp.getApps(appContext).firstOrNull()
            if (existing != null) {
                existing
            } else {
                // 1. Standard google-services resource initialization
                val defaultInit = runCatching { FirebaseApp.initializeApp(appContext) }.getOrNull()
                if (defaultInit != null) {
                    defaultInit
                } else {
                    // 2. Fallback: direct resource lookup + build-time bundled google-services asset
                    val fallbackOptions = resolveFallbackFirebaseOptions(appContext)
                    if (fallbackOptions != null) {
                        Log.i(TAG, "Initializing FirebaseApp via fallback FirebaseOptions.")
                        FirebaseApp.initializeApp(appContext, fallbackOptions)
                    } else {
                        Log.e(
                            TAG,
                            "Unable to resolve FirebaseOptions from resources or assets for ${appContext.packageName}."
                        )
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FirebaseApp", e)
            runCatching { FirebaseApp.getApps(appContext).firstOrNull() }.getOrNull()
        }
    }

    if (app != null) {
        installAppCheckOnce(appContext, app)
    }
    return app
}

private fun installAppCheckOnce(appContext: Context, app: FirebaseApp) {
    if (!appCheckInstalled.compareAndSet(false, true)) return
    try {
        if (BuildConfig.DEBUG) {
            val debugToken = cachedDebugToken
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = app.persistenceKey
                val prefs = appContext.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // CRITICAL: MUST use synchronous commit() before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val siteKey = lookupStringResource(appContext, RECAPTCHA_SITE_KEY_RES)
            if (!siteKey.isNullOrBlank()) {
                FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                )
                Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
            } else {
                Log.e(TAG, "recaptcha_site_key string resource not found or blank.")
            }
        }
    } catch (e: Exception) {
        appCheckInstalled.set(false)
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

private fun resolveFallbackFirebaseOptions(context: Context): FirebaseOptions? {
    // Attempt A: Direct string resource lookup across applicationId and namespace
    val resAppId = lookupStringResource(context, "google_app_id")
    val resApiKey = lookupStringResource(context, "google_api_key")
    if (!resAppId.isNullOrBlank() && !resApiKey.isNullOrBlank()) {
        val builder = FirebaseOptions.Builder()
            .setApplicationId(resAppId)
            .setApiKey(resApiKey)
        lookupStringResource(context, "project_id")?.takeIf { it.isNotBlank() }?.let {
            builder.setProjectId(it)
        }
        lookupStringResource(context, "gcm_defaultSenderId")?.takeIf { it.isNotBlank() }?.let {
            builder.setGcmSenderId(it)
        }
        lookupStringResource(context, "google_storage_bucket")?.takeIf { it.isNotBlank() }?.let {
            builder.setStorageBucket(it)
        }
        return builder.build()
    }

    // Attempt B: Parse bundled build-time mirror of google-services.json from assets
    return parseFirebaseOptionsFromAsset(context)
}

private fun lookupStringResource(context: Context, name: String): String? {
    val packages = linkedSetOf(context.packageName, "com.example")
    for (pkg in packages) {
        val resId = context.resources.getIdentifier(name, "string", pkg)
        if (resId != 0) {
            val value = runCatching { context.getString(resId) }.getOrNull()
            if (!value.isNullOrBlank()) return value
        }
    }
    return null
}

private fun parseFirebaseOptionsFromAsset(context: Context): FirebaseOptions? {
    val rawJson = runCatching {
        context.assets.open(FALLBACK_ASSET_NAME).bufferedReader().use { it.readText() }
    }.getOrNull() ?: return null

    return parseGoogleServicesJson(rawJson, context.packageName)
}

/**
 * Pure JVM-testable parser converting a standard google-services.json payload
 * into a [FirebaseOptions] instance matching [packageName] (or first client entry).
 */
internal fun parseGoogleServicesJson(rawJson: String, packageName: String): FirebaseOptions? = runCatching {
    val root = JSONObject(rawJson)
    val projectInfo = root.optJSONObject("project_info") ?: return null
    val projectId = projectInfo.optString("project_id", "").trim()
    val projectNumber = projectInfo.optString("project_number", "").trim()
    val storageBucket = projectInfo.optString("storage_bucket", "").trim()

    val clients = root.optJSONArray("client") ?: return null
    if (clients.length() == 0) return null

    var matchedClient: JSONObject? = null
    for (i in 0 until clients.length()) {
        val candidate = clients.optJSONObject(i) ?: continue
        val pkg = candidate.optJSONObject("client_info")
            ?.optJSONObject("android_client_info")
            ?.optString("package_name", "")
            ?.trim()
        if (pkg == packageName) {
            matchedClient = candidate
            break
        }
    }
    val client = matchedClient ?: clients.optJSONObject(0) ?: return null
    val appId = client.optJSONObject("client_info")
        ?.optString("mobilesdk_app_id", "")
        ?.trim()
        .orEmpty()
    val apiKey = client.optJSONArray("api_key")
        ?.optJSONObject(0)
        ?.optString("current_key", "")
        ?.trim()
        .orEmpty()

    if (appId.isBlank() || apiKey.isBlank()) return null

    val builder = FirebaseOptions.Builder()
        .setApplicationId(appId)
        .setApiKey(apiKey)
    if (projectId.isNotBlank()) builder.setProjectId(projectId)
    if (projectNumber.isNotBlank()) builder.setGcmSenderId(projectNumber)
    if (storageBucket.isNotBlank()) builder.setStorageBucket(storageBucket)
    builder.build()
}.getOrNull()

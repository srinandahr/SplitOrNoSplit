package com.srinandahr.splitornosplit.net

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/** Absolute URL builders. Pure functions so they can be unit tested without a device. */
object Endpoints {

    fun normalizeInstance(instanceUrl: String): String {
        val trimmed = instanceUrl.trim().trimEnd('/')
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "https://$trimmed"
    }

    fun projects(instance: String) = "${normalizeInstance(instance)}/api/projects"
    fun project(instance: String, id: String) = "${projects(instance)}/$id"
    fun members(instance: String, id: String) = "${project(instance, id)}/members"
    fun bills(instance: String, id: String) = "${project(instance, id)}/bills"
    fun statistics(instance: String, id: String) = "${project(instance, id)}/statistics"
}

/**
 * HTTP Basic credentials for a project.
 *
 * Hand-rolled Base64 rather than android.util.Base64 so this stays unit-testable on the
 * JVM, and rather than java.util.Base64 which needs API 26 (minSdk here is 24).
 */
internal fun base64(input: ByteArray): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val out = StringBuilder()
    var i = 0
    while (i + 2 < input.size) {
        val n = ((input[i].toInt() and 0xFF) shl 16) or
                ((input[i + 1].toInt() and 0xFF) shl 8) or
                (input[i + 2].toInt() and 0xFF)
        out.append(table[(n ushr 18) and 63]).append(table[(n ushr 12) and 63])
        out.append(table[(n ushr 6) and 63]).append(table[n and 63])
        i += 3
    }
    when (input.size - i) {
        1 -> {
            val n = (input[i].toInt() and 0xFF) shl 16
            out.append(table[(n ushr 18) and 63]).append(table[(n ushr 12) and 63]).append("==")
        }
        2 -> {
            val n = ((input[i].toInt() and 0xFF) shl 16) or ((input[i + 1].toInt() and 0xFF) shl 8)
            out.append(table[(n ushr 18) and 63]).append(table[(n ushr 12) and 63])
                .append(table[(n ushr 6) and 63]).append("=")
        }
    }
    return out.toString()
}

fun basicAuth(projectId: String, privateCode: String): String =
    "Basic " + base64("$projectId:$privateCode".toByteArray(Charsets.UTF_8))

object ApiClient {

    // Timeouts are deliberately short. The Split action runs inside a BroadcastReceiver's
    // goAsync() window, which the system only keeps alive for roughly ten seconds.
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    // baseUrl is required by Retrofit but never used — every call passes an absolute @Url.
    val api: IHateMoneyApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://ihatemoney.org/")
            .client(http)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IHateMoneyApi::class.java)
    }
}

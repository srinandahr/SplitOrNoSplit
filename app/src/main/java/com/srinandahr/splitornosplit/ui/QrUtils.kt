package com.srinandahr.splitornosplit.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.net.toUri
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.srinandahr.splitornosplit.data.Project

/**
 * Joining a project needs its instance, id and private code — three awkward strings to
 * retype. A link carries all three, and a QR carries the link.
 *
 * Note this puts the private code into whatever channel the link travels through. That is
 * unavoidable with I Hate Money's shared-secret model, and is called out in the UI.
 */
object JoinLink {

    const val SCHEME = "splitornosplit"
    const val HOST = "join"

    fun build(project: Project): String =
        Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendQueryParameter("instance", project.instanceUrl)
            .appendQueryParameter("id", project.projectId)
            .appendQueryParameter("code", project.privateCode)
            .build()
            .toString()

    /** Returns instance / id / code, or null if this isn't a join link we understand. */
    fun parse(raw: String): Triple<String, String, String>? {
        val uri = runCatching { raw.trim().toUri() }.getOrNull() ?: return null
        if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
        if (!uri.host.equals(HOST, ignoreCase = true)) return null
        val instance = uri.getQueryParameter("instance")?.takeIf { it.isNotBlank() }
            ?: Project.DEFAULT_INSTANCE
        val id = uri.getQueryParameter("id")?.takeIf { it.isNotBlank() } ?: return null
        val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() } ?: return null
        return Triple(instance, id, code)
    }
}

fun qrBitmap(content: String, sizePx: Int = 640): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    bitmap
}.getOrNull()

fun shareJoinLink(context: Context, project: Project) {
    val link = JoinLink.build(project)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(
            Intent.EXTRA_TEXT,
            "Join \"${project.name}\" on Split or No Split:\n$link\n\n" +
                "Anyone with this link can view and edit the group's expenses, " +
                "so only send it to people in the group.",
        )
    }
    context.startActivity(Intent.createChooser(intent, "Share group"))
}

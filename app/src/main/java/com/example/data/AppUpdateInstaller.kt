package com.smartprocurement.internal.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class AppUpdateInstaller(private val context: Context) {
    fun targetFile(release: AppUpdateRelease): File {
        val fileName = "app-${release.versionCode}-${release.releaseId.ifBlank { "update" }}.apk"
        return File(context.cacheDir, "app_updates/$fileName")
    }

    fun inspect(file: File): DownloadedApkInfo {
        val info = packageInfo(file)
        return DownloadedApkInfo(
            packageName = info?.packageName.orEmpty(),
            versionCode = info?.versionCodeCompat() ?: 0,
            versionName = info?.versionName.orEmpty(),
            sha256 = file.sha256(),
            signerSha256 = info?.signerSha256().orEmpty(),
            sizeBytes = file.length(),
            minSdk = info?.applicationInfo?.minSdkVersion ?: 0
        )
    }

    fun openSystemInstaller(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun requiresInstallPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(file: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode.toInt() else versionCode
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.signerSha256(): String {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            signingInfo?.apkContentsSigners
        } else {
            signatures
        }
        val first = signatures?.firstOrNull() ?: return ""
        return first.toByteArray().sha256()
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(this).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ByteArray.sha256(): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }
    }
}

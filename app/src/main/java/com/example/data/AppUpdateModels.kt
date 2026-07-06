package com.smartprocurement.internal.data

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

data class AppUpdateRelease(
    val releaseId: String = "",
    val packageName: String = "",
    val versionCode: Int = 0,
    val versionName: String = "",
    val channel: String = "",
    val minimumSupportedVersionCode: Int = 0,
    val updateType: String = "optional",
    val mandatory: Boolean = false,
    val title: String = "",
    val releaseNotes: List<String> = emptyList(),
    val apkSha256: String = "",
    val signerSha256: String = "",
    val sizeBytes: Long = 0,
    val minSdk: Int = 0,
    val downloadUrl: String = "",
    val downloadTicket: String = "",
    val manifestSignature: String = "",
    val manifestPublicKey: String = "",
    val manifestKeyId: String = "",
    val manifestSignatureAlgorithm: String = ""
)

data class AppUpdateCheckResult(
    val updateAvailable: Boolean = false,
    val mandatory: Boolean = false,
    val currentVersionBlocked: Boolean = false,
    val release: AppUpdateRelease? = null
)

data class DownloadedApkInfo(
    val packageName: String,
    val versionCode: Int,
    val versionName: String,
    val sha256: String,
    val signerSha256: String,
    val sizeBytes: Long,
    val minSdk: Int
)

data class AppUpdateVerificationResult(
    val isValid: Boolean,
    val failureCode: String = ""
)

object AppUpdatePolicy {
    fun channelForVariant(variantLabel: String): String {
        return if (variantLabel.isBlank()) "production" else "staging"
    }

    fun canEnterBusiness(release: AppUpdateRelease?): Boolean {
        return release?.mandatory != true && release?.updateType != "mandatory"
    }

    fun userFacingError(code: String): String = when (code) {
        "UPDATE_HASH_MISMATCH" -> "更新包校验失败，请重新下载"
        "UPDATE_PACKAGE_MISMATCH" -> "更新包不适用于当前应用"
        "UPDATE_SIGNER_MISMATCH" -> "更新包签名不一致，请联系管理员"
        "UPDATE_MANIFEST_SIGNATURE_INVALID" -> "更新清单签名无效，请联系管理员"
        "UPDATE_NOT_NEWER" -> "已是当前版本，请重新检查更新"
        "UPDATE_SDK_UNSUPPORTED" -> "当前设备不支持该版本"
        "UPDATE_SIZE_MISMATCH" -> "更新包下载不完整，请重新下载"
        "APP_UPDATE_REQUIRED" -> "当前版本已停用，请先更新应用"
        else -> "网络连接失败，请稍后重试"
    }
}

object AppUpdateVerifier {
    private val ed25519SpkiPrefix = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
    )

    fun canonicalManifestPayload(release: AppUpdateRelease): String {
        return buildString {
            append("{")
            append("\"apk_sha256\":").append(release.apkSha256.json())
            append(",\"apk_size_bytes\":").append(release.sizeBytes)
            append(",\"channel\":").append(release.channel.json())
            append(",\"min_sdk\":").append(release.minSdk)
            append(",\"minimum_supported_version_code\":").append(release.minimumSupportedVersionCode)
            append(",\"package_name\":").append(release.packageName.json())
            append(",\"release_id\":").append(release.releaseId.json())
            append(",\"release_notes\":").append(release.releaseNotes.joinToString(prefix = "[", postfix = "]") { it.json() })
            append(",\"schema_version\":1")
            append(",\"signer_sha256\":").append(release.signerSha256.json())
            append(",\"title\":").append(release.title.json())
            append(",\"update_type\":").append(release.updateType.json())
            append(",\"version_code\":").append(release.versionCode)
            append(",\"version_name\":").append(release.versionName.json())
            append("}")
        }
    }

    fun verifyManifest(release: AppUpdateRelease): AppUpdateVerificationResult {
        if (release.manifestSignatureAlgorithm != "Ed25519") {
            return AppUpdateVerificationResult(false, "UPDATE_MANIFEST_SIGNATURE_INVALID")
        }
        return runCatching {
            val signatureBytes = Base64.getDecoder().decode(release.manifestSignature)
            val rawPublicKey = Base64.getDecoder().decode(release.manifestPublicKey)
            if (signatureBytes.size != 64 || rawPublicKey.size != 32) {
                return AppUpdateVerificationResult(false, "UPDATE_MANIFEST_SIGNATURE_INVALID")
            }
            val publicKey = KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(ed25519SpkiPrefix + rawPublicKey))
            val verifier = Signature.getInstance("Ed25519")
            verifier.initVerify(publicKey)
            verifier.update(canonicalManifestPayload(release).toByteArray(Charsets.UTF_8))
            if (verifier.verify(signatureBytes)) {
                AppUpdateVerificationResult(true)
            } else {
                AppUpdateVerificationResult(false, "UPDATE_MANIFEST_SIGNATURE_INVALID")
            }
        }.getOrElse {
            AppUpdateVerificationResult(false, "UPDATE_MANIFEST_SIGNATURE_INVALID")
        }
    }

    fun verify(
        apk: DownloadedApkInfo,
        expected: AppUpdateRelease,
        currentVersionCode: Int,
        deviceSdk: Int
    ): AppUpdateVerificationResult {
        return when {
            apk.sha256.normalizeHex() != expected.apkSha256.normalizeHex() -> AppUpdateVerificationResult(false, "UPDATE_HASH_MISMATCH")
            expected.sizeBytes > 0 && apk.sizeBytes != expected.sizeBytes -> AppUpdateVerificationResult(false, "UPDATE_SIZE_MISMATCH")
            apk.packageName != expected.packageName -> AppUpdateVerificationResult(false, "UPDATE_PACKAGE_MISMATCH")
            apk.versionCode != expected.versionCode || apk.versionCode <= currentVersionCode -> AppUpdateVerificationResult(false, "UPDATE_NOT_NEWER")
            expected.versionName.isNotBlank() && apk.versionName != expected.versionName -> AppUpdateVerificationResult(false, "UPDATE_NOT_NEWER")
            expected.signerSha256.isNotBlank() && apk.signerSha256.normalizeHex() != expected.signerSha256.normalizeHex() -> AppUpdateVerificationResult(false, "UPDATE_SIGNER_MISMATCH")
            apk.minSdk > deviceSdk -> AppUpdateVerificationResult(false, "UPDATE_SDK_UNSUPPORTED")
            else -> AppUpdateVerificationResult(true)
        }
    }

    private fun String.normalizeHex(): String = trim().lowercase()

    private fun String.json(): String {
        val out = StringBuilder(length + 2)
        out.append('"')
        for (ch in this) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        out.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        out.append(ch)
                    }
                }
            }
        }
        out.append('"')
        return out.toString()
    }
}

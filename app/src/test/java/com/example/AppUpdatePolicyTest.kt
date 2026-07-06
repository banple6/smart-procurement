package com.smartprocurement.internal

import com.smartprocurement.internal.data.AppUpdatePolicy
import com.smartprocurement.internal.data.AppUpdateRelease
import com.smartprocurement.internal.data.AppUpdateVerifier
import com.smartprocurement.internal.data.DownloadedApkInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

class AppUpdatePolicyTest {
    @Test
    fun variant_label_maps_to_server_channel() {
        assertEquals("production", AppUpdatePolicy.channelForVariant(""))
        assertEquals("staging", AppUpdatePolicy.channelForVariant("开发版"))
        assertEquals("staging", AppUpdatePolicy.channelForVariant("测试版"))
    }

    @Test
    fun mandatory_update_blocks_business_entry_until_installed() {
        assertTrue(AppUpdatePolicy.canEnterBusiness(null))
        assertTrue(AppUpdatePolicy.canEnterBusiness(AppUpdateRelease(updateType = "recommended", mandatory = false)))
        assertFalse(AppUpdatePolicy.canEnterBusiness(AppUpdateRelease(updateType = "mandatory", mandatory = true)))
    }

    @Test
    fun verifier_rejects_tampered_or_wrong_apk_before_install() {
        val expected = AppUpdateRelease(
            packageName = "com.smartprocurement.internal",
            versionCode = 8,
            versionName = "1.2.0",
            channel = "production",
            apkSha256 = "abc123",
            signerSha256 = "signer",
            sizeBytes = 512
        )
        val validApk = DownloadedApkInfo(
            packageName = "com.smartprocurement.internal",
            versionCode = 8,
            versionName = "1.2.0",
            sha256 = "abc123",
            signerSha256 = "signer",
            sizeBytes = 512,
            minSdk = 24
        )

        assertTrue(AppUpdateVerifier.verify(validApk, expected, currentVersionCode = 7, deviceSdk = 30).isValid)
        assertEquals(
            "UPDATE_HASH_MISMATCH",
            AppUpdateVerifier.verify(validApk.copy(sha256 = "bad"), expected, currentVersionCode = 7, deviceSdk = 30).failureCode
        )
        assertEquals(
            "UPDATE_PACKAGE_MISMATCH",
            AppUpdateVerifier.verify(validApk.copy(packageName = "other"), expected, currentVersionCode = 7, deviceSdk = 30).failureCode
        )
        assertEquals(
            "UPDATE_NOT_NEWER",
            AppUpdateVerifier.verify(validApk.copy(versionCode = 7), expected, currentVersionCode = 7, deviceSdk = 30).failureCode
        )
        assertEquals(
            "UPDATE_SIGNER_MISMATCH",
            AppUpdateVerifier.verify(validApk.copy(signerSha256 = "other"), expected, currentVersionCode = 7, deviceSdk = 30).failureCode
        )
    }

    @Test
    fun internal_update_errors_are_shown_in_plain_chinese() {
        assertEquals("更新包校验失败，请重新下载", AppUpdatePolicy.userFacingError("UPDATE_HASH_MISMATCH"))
        assertEquals("当前设备不支持该版本", AppUpdatePolicy.userFacingError("UPDATE_SDK_UNSUPPORTED"))
        assertEquals("网络连接失败，请稍后重试", AppUpdatePolicy.userFacingError("HTTP 500 stack trace"))
    }

    @Test
    fun verifier_accepts_only_valid_ed25519_manifest_signature() {
        val keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        val release = AppUpdateRelease(
            releaseId = "rel-1",
            packageName = "com.smartprocurement.internal",
            versionCode = 8,
            versionName = "1.2.0",
            channel = "production",
            minimumSupportedVersionCode = 5,
            updateType = "mandatory",
            title = "正式更新",
            releaseNotes = listOf("修复订单问题"),
            apkSha256 = "abc123",
            signerSha256 = "signer",
            sizeBytes = 512,
            minSdk = 24,
            manifestSignatureAlgorithm = "Ed25519",
            manifestPublicKey = Base64.getEncoder().encodeToString(keyPair.public.encoded.takeLast(32).toByteArray()),
            manifestKeyId = "offline-key"
        )
        val signer = Signature.getInstance("Ed25519")
        signer.initSign(keyPair.private)
        signer.update(AppUpdateVerifier.canonicalManifestPayload(release).toByteArray(Charsets.UTF_8))
        val signed = release.copy(manifestSignature = Base64.getEncoder().encodeToString(signer.sign()))

        assertTrue(AppUpdateVerifier.verifyManifest(signed).isValid)
        assertEquals(
            "UPDATE_MANIFEST_SIGNATURE_INVALID",
            AppUpdateVerifier.verifyManifest(signed.copy(title = "被篡改")).failureCode
        )
    }
}

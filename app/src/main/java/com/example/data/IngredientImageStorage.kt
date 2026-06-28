package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

class IngredientImageStorage(private val context: Context) {
    fun createCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "ingredient-camera-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun saveImage(sourceUri: Uri): Result<String> = runCatching {
        val bytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
            input.readBytes()
        } ?: error("图片读取失败")

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        val sampleSize = calculateSampleSize(options.outWidth, options.outHeight, 1600)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
            ?: error("图片解码失败")

        val scaled = bitmap.scaleDownToMax(1600)
        val dir = File(context.filesDir, "ingredient_images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
        }
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        file.absolutePath
    }

    private fun calculateSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth / 2 >= maxSide || currentHeight / 2 >= maxSide) {
            sample *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sample
    }

    private fun Bitmap.scaleDownToMax(maxSide: Int): Bitmap {
        val side = max(width, height)
        if (side <= maxSide) return this
        val ratio = maxSide.toFloat() / side.toFloat()
        return Bitmap.createScaledBitmap(this, (width * ratio).roundToInt(), (height * ratio).roundToInt(), true)
    }
}

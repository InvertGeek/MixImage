package com.donut.miximage.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.donut.miximage.utils.objects.ProgressContent
import kotlin.math.abs

class XorRandom(var seed: Int) {

    fun nextInt(max: Int = Int.MAX_VALUE): Int {
        seed = seed xor (seed shl 13)
        seed = seed xor (seed shr 17)
        seed = seed xor (seed shl 5)
        return abs(seed) % max
    }

}


/**
 * 图像混淆器，负责对图像进行混淆和还原
 */
object ImageScrambler {
    private const val SEED = 1 // 固定种子确保混淆和还原一致


    // 反转颜色（255 - 原颜色值）
    private fun invertColor(rgb: Int): Int {
        val alpha = (rgb shr 24) and 0xFF // 保留 alpha 通道
        val red = 255 - ((rgb shr 16) and 0xFF)
        val green = 255 - ((rgb shr 8) and 0xFF)
        val blue = 255 - (rgb and 0xFF)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue // 重新组合 ARGB
    }

    // 计算块大小，基于图像尺寸动态调整
    private fun calculateBlockSize(width: Int, height: Int): Int {
        return (width + height) / 50
    }

    // 生成块坐标列表
    private fun generateBlockPositions(blocksX: Int, blocksY: Int): List<Pair<Int, Int>> {
        return (0 until blocksY).flatMap { y ->
            (0 until blocksX).map { x -> Pair(x, y) }
        }
    }

    // 使用 XorRandom 实现的 Fisher-Yates 洗牌
    private fun <T> shuffleList(list: List<T>, seed: Int): List<T> {
        val result = list.toMutableList()
        val random = XorRandom(seed)
        for (i in result.size - 1 downTo 1) {
            val j = random.nextInt(i + 1)
            result[i] = result[j].also { result[j] = result[i] }
        }
        return result
    }

    // 处理图像混淆或还原的核心逻辑
    private fun processImage(
        input: Bitmap,
        output: Bitmap,
        blockSize: Int,
        isScramble: Boolean,
        progress: ProgressContent
    ) {
        val width = input.width
        val height = input.height
        val blocksX = width / blockSize
        val blocksY = height / blockSize

        // 创建 Canvas 用于绘制到 output Bitmap
        val canvas = Canvas(output)

        // 生成并打乱块位置
        val positions = generateBlockPositions(blocksX, blocksY)
        val shuffledPositions = shuffleList(positions, SEED)

        // 根据混淆或还原决定源和目标位置的映射
        positions.forEachIndexed { index, pos ->
            progress.updateProgress(index.toLong(), positions.size.toLong())
            val srcPos = if (isScramble) pos else shuffledPositions[index]
            val destPos = if (isScramble) shuffledPositions[index] else pos

            val srcX = srcPos.first * blockSize
            val srcY = srcPos.second * blockSize
            val destX = destPos.first * blockSize
            val destY = destPos.second * blockSize

            // 定义源和目标区域
            val srcRect = Rect(srcX, srcY, srcX + blockSize, srcY + blockSize)
            val destRect = Rect(destX, destY, destX + blockSize, destY + blockSize)

            canvas.drawBitmap(input, srcRect, destRect, null)

        }
    }

    fun Bitmap.invertColors(): Bitmap {
        val bitmap = this
        // 创建输出 Bitmap
        val invertedBitmap = createBitmap(bitmap.width, bitmap.height, bitmap.config!!)

        // 设置 Canvas 和 Paint
        val canvas = Canvas(invertedBitmap)
        val paint = Paint()

        // 创建颜色反转的 ColorMatrix
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f, // 反转红色
                0f, -1f, 0f, 0f, 255f, // 反转绿色
                0f, 0f, -1f, 0f, 255f, // 反转蓝色
                0f, 0f, 0f, 1f, 0f      // 保留 Alpha
            )
        )

        // 应用 ColorMatrix
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)

        // 绘制反转后的图像
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return invertedBitmap
    }

    /**
     * 混淆图像
     * @param input 输入的 Bitmap
     * @return 混淆后的 Bitmap
     */
    fun scrambleImage(input: Bitmap, progress: ProgressContent): Bitmap {
        val output = createBitmap(input.width, input.height)
        val blockSize = calculateBlockSize(input.width, input.height)
        processImage(input, output, blockSize, isScramble = true, progress)
        return output.invertColors()
    }

    /**
     * 还原图像
     * @param input 混淆的 Bitmap
     * @return 还原后的 Bitmap
     */
    fun unscrambleImage(input: Bitmap, progress: ProgressContent): Bitmap {
        val output = createBitmap(input.width, input.height)
        val blockSize = calculateBlockSize(input.width, input.height)
        processImage(input, output, blockSize, isScramble = false, progress)
        return output.invertColors()
    }
}
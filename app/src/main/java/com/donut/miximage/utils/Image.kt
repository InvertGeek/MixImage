package com.example.imagescrambler

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.set
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

            // 复制并处理像素
            for (y in 0 until blockSize) {
                for (x in 0 until blockSize) {
                    if (srcX + x < width && srcY + y < height && destX + x < width && destY + y < height) {
                        val pixel = input[srcX + x, srcY + y]
                        val processedPixel = invertColor(pixel) // 可根据需求切换为 obfuscatePixel
                        output[destX + x, destY + y] = processedPixel
                    }
                }
            }
        }
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
        return output
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
        return output
    }
}
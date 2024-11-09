package com.csa6.devicadio

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ObjectDetector(context: Context) {
    private lateinit var interpreter: Interpreter
    private val inputSize = 224
    private val labelList: List<String> = loadLabelMap(context)

    init {
        interpreter = Interpreter(loadModelFile(context))
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd("mobilenet_v1_1.0_224.tflite")
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
    }

    fun detectObjects(bitmap: Bitmap): String {
        val inputBitmap = preprocessImage(bitmap)
        val inputBuffer = inputBitmap.toByteBuffer()

        // Output array: 1 batch, 1001 possible classes for MobileNet
        val outputArray = Array(1) { FloatArray(1001) }

        // Run inference
        interpreter.run(inputBuffer, outputArray)

        // Get objects with confidence greater than 70%
        val detectedObjects = mutableListOf<String>()
        outputArray[0].indices.sortedByDescending { outputArray[0][it] }
            .forEach { index ->
                val confidence = outputArray[0][index] * 100
                if (confidence >= 70) {
                    val objectName = if (index >= 0 && index < labelList.size) labelList[index] else "Unknown"
                    detectedObjects.add("$objectName ($confidence%)")
                }
            }

        return if (detectedObjects.isNotEmpty()) {
            detectedObjects.joinToString("\n")
        } else {
            "No objects detected with sufficient confidence"
        }
    }

    private fun loadLabelMap(context: Context): List<String> {
        val labelList = mutableListOf<String>()
        val inputStream = context.assets.open("mobilenet_v1_1.0_224.txt")
        inputStream.bufferedReader().useLines { lines ->
            lines.forEach {
                labelList.add(it.trim())  // Add each label (each line is a label)
            }
        }
        return labelList
    }

    private fun Bitmap.toByteBuffer(): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(inputSize * inputSize)
        this.getPixels(intValues, 0, this.width, 0, 0, this.width, this.height)
        for (pixelValue in intValues) {
            byteBuffer.putFloat(((pixelValue shr 16 and 0xFF) - 127.5f) / 127.5f) // Red
            byteBuffer.putFloat(((pixelValue shr 8 and 0xFF) - 127.5f) / 127.5f)  // Green
            byteBuffer.putFloat(((pixelValue and 0xFF) - 127.5f) / 127.5f)        // Blue
        }
        return byteBuffer
    }

    fun close() {
        interpreter.close()
    }
}


package com.duckduckgo.app.safegaze.ondeviceobjectdetection

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector

/**
 * Created by Asif Ahmed on 2/1/24.
 */

class ObjectDetectionHelper(val context: Context) {

    /**
     * ML Kit Object Detection function.
     */
    fun runObjectDetectionWithCustomModel(bitmap: Bitmap): MutableList<Detection> {

        // Step 1: create TFLite's TensorImage object
        val image = TensorImage.fromBitmap(bitmap)

        // Step 2: Initialize the detector object
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.5f)
            .build()
        val detector = ObjectDetector.createFromFileAndOptions(
            context, // the application context
            "detect_person.tflite", // must be same as the filename in assets folder
            options,
        )

        // Step 3: feed given image to the model and print the detection result
        val results = detector.detect(image)

        return results
    }

    fun isImageContainsHuman(bitmap: Bitmap): Boolean {
        return runObjectDetectionWithCustomModel(bitmap).any { detection ->
            detection.categories.any { category ->
                category.label.equals("person", ignoreCase = true)
            }
        }
    }
}

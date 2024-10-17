package com.ssafy.marusys.presentation.viewmodel

import android.content.Context
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.support.audio.TensorAudio
import org.tensorflow.lite.support.label.Category
import org.tensorflow.lite.task.audio.classifier.AudioClassifier
import org.tensorflow.lite.task.core.BaseOptions
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class HomeViewModel( ) : ViewModel() {

    private val _showBottomSheet = MutableStateFlow(false)
    val showBottomSheet: StateFlow<Boolean> = _showBottomSheet.asStateFlow()

//    private val _currentModel = MutableStateFlow(YAMNET_MODEL)
//    val currentModel: StateFlow<String> = _currentModel.asStateFlow()
//
//    private val _classificationThreshold = MutableStateFlow(DISPLAY_THRESHOLD)
//    val classificationThreshold: StateFlow<Float> = _classificationThreshold.asStateFlow()
//
//    private val _overlap = MutableStateFlow(DEFAULT_OVERLAP_VALUE)
//    val overlap: StateFlow<Float> = _overlap.asStateFlow()
//
//    private val _numOfResults = MutableStateFlow(DEFAULT_NUM_OF_RESULTS)
//    val numOfResults: StateFlow<Int> = _numOfResults.asStateFlow()

//    private val _currentDelegate = MutableStateFlow(0)
//    val currentDelegate: StateFlow<Int> = _currentDelegate.asStateFlow()
//
//    private val _numThreads = MutableStateFlow(2)
//    val numThreads: StateFlow<Int> = _numThreads.asStateFlow()
//
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _audioClassificationStatus = MutableStateFlow<AudioClassificationStatus>(AudioClassificationStatus.Unknown)
    val audioClassificationStatus: StateFlow<AudioClassificationStatus> = _audioClassificationStatus.asStateFlow()

    fun updateCategories(newCategories: List<Category>) {
        _categories.value = newCategories
    }

    fun openBottomSheet() {
        _showBottomSheet.value = true
    }

    fun closeBottomSheet() {
        _showBottomSheet.value = false
    }
}

sealed class AudioClassificationStatus {
    object Unknown : AudioClassificationStatus()
    data class onResult(val results: List<Category>, val inferenceTime: Long) : AudioClassificationStatus()
    data class Error(val error: String) : AudioClassificationStatus()
}

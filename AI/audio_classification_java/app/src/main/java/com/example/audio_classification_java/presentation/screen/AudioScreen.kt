package com.example.audio_classification_java.presentation.screen

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.audio_classification_java.presentation.viewmodel.MainViewModel

@Composable
fun AudioScreen(
    viewModel: MainViewModel,
    recordButtons: () -> Unit
) {
    val resultText by viewModel.resultText.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            // 버튼 클릭 리스너 설정 -> 버튼 클릭 시 작동
            onClick = { recordButtons() }
        ) {
            Text("녹음하기")
        }
        Text(resultText)
    }

}
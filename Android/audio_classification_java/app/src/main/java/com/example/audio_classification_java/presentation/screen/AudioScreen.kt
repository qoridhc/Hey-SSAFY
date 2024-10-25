package com.example.audio_classification_java.presentation.screen

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.audio_classification_java.R
import com.example.audio_classification_java.presentation.viewmodel.MainViewModel
import androidx.compose.runtime.LaunchedEffect


@Composable
fun AudioScreen(
    // 인자의 타입 지정
    viewModel: MainViewModel,
    recordButtons: () -> Unit,
    startService: () -> Unit,
) {
    val resultText by viewModel.resultText.collectAsState()
//    val errorText by viewModel.errorText.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity

//    이 화면에서 resultText의 값이 변경될 때 안의 함수를 작동 시킴
//    LaunchedEffect(resultText) {
//        recordButtons()
//    }

    // Column : 세로 div
    Column(
        // modifier : css 주는 것
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("모델 테스트 하기", style = MaterialTheme.typography.headlineLarge)
        // Spacer : 빈 공간 두는 것
        Spacer(modifier = Modifier.padding(16.dp))
        Button(
            // 버튼 클릭 리스너 설정 -> 버튼 클릭 시 작동
            onClick = { recordButtons() }, // 버튼 눌렀을 때 녹음 받는 기능
//            onClick = { startService() }, // 버튼 눌렀을 때 서비스를 시작시키려는 코드 ( 작동 안함...)
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3182F6)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("녹음하기")
        }
        Spacer(modifier = Modifier.padding(16.dp))
        Text(resultText, modifier = Modifier.weight(1f), fontSize = 32.sp)
//        Spacer(modifier = Modifier.padding(16.dp))
//        Text(errorText, modifier = Modifier.weight(1f), fontSize = 32.sp) //
    }
}
package com.marusys.hesap.presentation.screen

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marusys.hesap.R
import com.marusys.hesap.presentation.viewmodel.MainViewModel

@Composable
fun AudioScreen(
    // 인자의 타입 지정
    viewModel: MainViewModel,
) {
    val resultText by viewModel.resultText.collectAsState()
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val isAudioServiceRunning by viewModel.isAudioServiceRunning.collectAsState()

    val backgroundColor =
         if (isAudioServiceRunning) {
            Brush.verticalGradient(listOf(Color.White, Color.Gray)) // AudioService 실행 중일 때의 배경색
        } else {
            Brush.verticalGradient(listOf(Color.White, Color.White)) // 기본 배경색
        }

    // Column : 세로 div
    Column(
        // modifier : css 주는 것
        modifier = Modifier
            .background(backgroundColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painter= painterResource(R.drawable.marusys), contentDescription = "마르시스")
        Spacer(modifier = Modifier.padding(32.dp))
        Text("무엇을 도와드릴까요?", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight(1000))
        Spacer(modifier = Modifier.padding(8.dp))
        Text(resultText, fontSize = 32.sp, fontWeight = FontWeight(100))
    }
}
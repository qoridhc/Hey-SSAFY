package com.ssafy.marusys.presentation.screen

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavController
import com.ssafy.marusys.presentation.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(viewModel: HomeViewModel) {
    val showBottomSheet by viewModel.showBottomSheet.collectAsState()
    // 권한이 부여되면 아래와 같이 audio_fragment로 이동
    val sheetState = rememberModalBottomSheetState()

    Column {
        Text(text = "퍼미션 페이지")
        Button(onClick = {
            viewModel.openBottomSheet()
        }) {
            Text(text = "오디오 페이지로 가기")

        }
        if (showBottomSheet) {
            AudioScreen(
                sheetState = sheetState,
                viewModel = viewModel,
                selectedModel = "YAMNET",
                onModelSelected = {}
            )
        }
    }
}

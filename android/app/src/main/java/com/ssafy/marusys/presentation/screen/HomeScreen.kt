package com.ssafy.marusys.presentation.screen

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import com.marusys.common.R
import com.ssafy.marusys.presentation.viewmodel.HomeViewModel


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen (
    navController : NavController,
    context : Context
){
    val homeViewModel = HomeViewModel()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("AI 모델 돌리기 예제")
                },
                navigationIcon = {
                    IconButton(onClick = { /* Handle navigation icon click */ }) {
                        Icon(
                            painter = painterResource(id = com.marusys.common.R.drawable.tfl_logo),
                            contentDescription = "Menu"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Handle action click */ }) {
                        Icon(
                            painter = painterResource(id = R.drawable.icn_chevron_up),
                            contentDescription = "Search"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    ) { innerPadding ->
        // 여기에 메인 콘텐츠를 배치합니다
        Column(modifier = Modifier.padding(innerPadding)) {
            // NavHost 또는 다른 컨텐츠를 여기에 배치
            PermissionsScreen(viewModel = homeViewModel)
        }
    }
}
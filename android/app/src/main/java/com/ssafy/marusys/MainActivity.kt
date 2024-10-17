package com.ssafy.marusys

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import com.ssafy.common.ui.theme.MarusysTheme
import com.ssafy.marusys.navigation.AppNavGraph
import com.ssafy.marusys.presentation.screen.HomeScreen
import com.ssafy.marusys.presentation.viewmodel.HomeViewModel
import org.tensorflow.lite.support.label.Category

class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.Theme_Marusys)
        enableEdgeToEdge()
        setContent {
            MarusysTheme {
                val navController = rememberNavController()
                AppNavGraph(
                    navController,
                    applicationContext,
                )
            }
        }
    }
    // 데이터 업데이트 예시
    private fun updateCategories(newCategories: List<Category>) {
        viewModel.updateCategories(newCategories)
    }
}

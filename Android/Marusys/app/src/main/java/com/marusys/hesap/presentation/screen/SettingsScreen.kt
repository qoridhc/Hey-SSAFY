import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marusys.hesap.presentation.viewmodel.MainViewModel

@Composable
fun SettingScreen(
    viewModel: MainViewModel
) {
    val memoryText by viewModel.memoryText.collectAsState()
    val context = LocalContext.current  // Composable 함수 내에서 Context 가져오기

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "시스템 성능 모니터링",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        PerformanceCard(
            title = "앱 메모리 사용량",
            description = memoryText
        )

        Spacer(modifier = Modifier.height(16.dp))

        PerformanceCard(
            title = "CPU 사용량",
            description = "추후 CPU 사용량 데이터를 표시합니다."
        )

        Spacer(modifier = Modifier.height(16.dp))

        PerformanceCard(
            title = "RAM 사용량",
            description = "추후 RAM 사용량 데이터를 표시합니다."
        )
    }

    LaunchedEffect(Unit) {
        viewModel.updateMemoryUsage(context)
    }
}

@Composable
fun PerformanceCard(
    title: String,
    description: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.DarkGray,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 16.sp
            )
        }
    }
}

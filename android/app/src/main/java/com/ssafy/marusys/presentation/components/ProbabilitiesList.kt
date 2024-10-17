package com.ssafy.marusys.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ssafy.marusys.R
import com.ssafy.marusys.presentation.viewmodel.HomeViewModel
import org.tensorflow.lite.support.label.Category

@Composable
fun ProbabilitiesList(viewModel: HomeViewModel) {
    val categories by viewModel.categories.collectAsState()

    LazyColumn {
        items(categories) { category ->
            ProbabilityItem(category)
        }
    }
}

@Composable
fun ProbabilityItem(category: Category) {
    val primaryProgressColors = colorArrayResource(id = R.array.colors_progress_primary)
    val backgroundProgressColors = colorArrayResource(id = R.array.colors_progress_background)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.label,
            modifier = Modifier.weight(1f)
        )
        LinearProgressIndicator(
            progress = category.score,
            modifier = Modifier.height(16.dp)
                .background(backgroundProgressColors[category.index % backgroundProgressColors.size]),
            color = primaryProgressColors[category.index % primaryProgressColors.size],
        )
        Text(
            text = "${(category.score * 100).toInt()}%",
            modifier = Modifier.width(50.dp),
            textAlign = TextAlign.End
        )
    }
}
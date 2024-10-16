package com.ssafy.marusys.presentation.screen

import android.bluetooth.BluetoothManager
import android.content.Context
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.marusys.common.R
import com.ssafy.marusys.presentation.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlin.math.round

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioScreen(
    sheetState: SheetState,
    viewModel: HomeViewModel,
    selectedModel: String,
    onModelSelected: (String) -> Unit
) {
    ModalBottomSheet(
        modifier = Modifier
            .fillMaxHeight(),
        onDismissRequest = {
            viewModel.closeBottomSheet()
        },
        sheetState = sheetState,
        containerColor = Color(0xFFF2F4F6),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(text = "바텀 모달 시트 ( 오디오 페이지)")
            Box{
                Image(painter = painterResource(id = R.drawable.icn_chevron_up), contentDescription = "Bottom sheet expandable indicator")
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Inference Time",
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "0ms",
                    color = Color.Black,
                    textAlign = TextAlign.End
                )
            }
            //
            val radioOptions = listOf("YAMNET", "Speech Command")
            Column(
                modifier = Modifier
                    .selectableGroup()
                    .padding(top = 16.dp)
            ) {
                radioOptions.forEach { text ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .selectable(
                                selected = (text == selectedModel),
                                onClick = { onModelSelected(text) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (text == selectedModel),
                            onClick = null // null because we're handling the click on the parent
                        )
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            //
            DelegateSelector()

            OverlapSelector()
            MaxResultsSelector(
                initialValue = 2,
                onValueChange = { newValue ->
                    // 여기서 새로운 값을 처리합니다.
                    println("New max results value: $newValue")
                }
            )
            ConfidenceThresholdSelector(
                initialValue = 0.30f,
                onValueChange = { newValue ->
                    // 여기서 새로운 값을 처리합니다.
                    println("New confidence threshold: $newValue")
                }
            )
            ThreadsSelector(
                initialValue = 2,
                onValueChange = { newValue ->
                    // 여기서 새로운 값을 처리합니다.
                    println("New threads count: $newValue")
                }
            )
        }
    }
}

@Composable
fun DelegateSelector() {
    var expanded by remember { mutableStateOf(false) }
    val items = stringArrayResource(id = R.array.delegate_spinner_titles)
    var selectedIndex by remember { mutableStateOf(0) }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.label_delegate),
                fontSize = 18.sp,
                color = Color.Black
            )

            Box {
                Row(
                    modifier = Modifier
                        .clickable { expanded = true }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = items[selectedIndex],
                        modifier = Modifier.padding(end = 8.dp),
                        fontSize = 16.sp
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = "Dropdown Arrow"
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(160.dp)
                ) {
                    items.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            text = { Text(text = item) },
                            onClick = {
                                selectedIndex = index
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }

@Composable
fun OverlapSelector() {
    var expanded by remember { mutableStateOf(false) }
    val items = stringArrayResource(id = R.array.overlap_spinner_titles)
    var selectedIndex by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.label_overlap),
            fontSize = 16.sp,
            color = Color.Black
        )

        Box {
            Row(
                modifier = Modifier
                    .clickable { expanded = true }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = items[selectedIndex],
                    modifier = Modifier.padding(end = 8.dp),
                    fontSize = 16.sp
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Dropdown Arrow"
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.width(160.dp)
            ) {
                items.forEachIndexed { index, item ->
                    DropdownMenuItem(
                        text = { Text(text = item) },
                        onClick = {
                            selectedIndex = index
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
@Composable
fun MaxResultsSelector(
    initialValue: Int = 2,
    onValueChange: (Int) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.label_max_results),
            fontSize = 16.sp,
            color = Color.Black
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (value > 1) {
                        value--
                        onValueChange(value)
                    }
                },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Decrease")
            }

            Text(
                text = value.toString(),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(min = 48.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = Color.Black
            )

            IconButton(
                onClick = {
                    value++
                    onValueChange(value)
                },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
    }
}
@Composable
fun ConfidenceThresholdSelector(
    initialValue: Float = 0.30f,
    onValueChange: (Float) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.label_confidence_threshold),
            fontSize = 16.sp,
            color = Color.Black
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (value > 0.01f) {
                        value = round((value - 0.01f) * 100) / 100
                        onValueChange(value)
                    }
                },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(id = R.string.alt_threshold_button_minus)
                )
            }

            Text(
                text = String.format("%.2f", value),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(min = 48.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = Color.Black
            )

            IconButton(
                onClick = {
                    if (value < 1.00f) {
                        value = round((value + 0.01f) * 100) / 100
                        onValueChange(value)
                    }
                },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(id = R.string.alt_threshold_button_plus)
                )
            }
        }
    }
}
@Composable
fun ThreadsSelector(
    initialValue: Int = 2,
    onValueChange: (Int) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = R.string.label_threads),
            fontSize = 16.sp,
            color = Color.Black
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (value > 1) {
                        value--
                        onValueChange(value)
                    }
                },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(id = R.string.alt_bottom_sheet_thread_button_minus)
                )
            }

            Text(
                text = value.toString(),
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .widthIn(min = 48.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                color = Color.Black
            )

            IconButton(
                onClick = {
                    value++
                    onValueChange(value)
                },
                modifier = Modifier.size(16.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(id = R.string.alt_bottom_sheet_thread_button_plus)
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
fun SimpleComposablePreview() {
    val sheetState = rememberModalBottomSheetState()
    val viewModel =  HomeViewModel()
    val selectedModel = ""
    val onModelSelected: (String) -> Unit = { text -> print(text) }
    AudioScreen(sheetState, viewModel, selectedModel, {onModelSelected("hi")})

}
package com.ssafy.marusys.presentation.components

import androidx.annotation.ArrayRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.integerArrayResource

@Composable
fun colorArrayResource(@ArrayRes id: Int): List<Color> {
    val intArray = integerArrayResource(id)
    return intArray.map { Color(it) }
}
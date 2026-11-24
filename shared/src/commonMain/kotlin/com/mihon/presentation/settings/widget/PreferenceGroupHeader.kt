package com.mihon.presentation.settings.widget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PreferenceGroupHeader(title: String) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, top = 16.dp),
    ) {
        Text(
            text = title.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = PrefsHorizontalPadding),
            style = MaterialTheme.typography.labelLarge,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

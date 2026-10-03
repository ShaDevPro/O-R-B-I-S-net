package com.sha.orbis.ui.social.feed

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FeedInterestChipRow(
    categories: List<Pair<String, String>>,
    selectedCategoryKey: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(
                horizontal = FeedDesignTokens.ContentPaddingHorizontal,
                vertical = FeedDesignTokens.SectionVerticalGap
            ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { (catKey, catLabel) ->
            val isSelected = selectedCategoryKey == catKey
            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelected(catKey) },
                label = {
                    Text(
                        text = catLabel,
                        fontSize = FeedDesignTokens.HintSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

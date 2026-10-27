package com.nuvio.app.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class NuvioSelectOption(
    val key: String,
    val label: String,
)

/**
 * A field that opens a list of [options]. With [multiSelect] each option has a checkbox and the
 * list stays open while choosing; otherwise picking an option closes it.
 */
@Composable
fun NuvioDropdownField(
    valueLabel: String,
    options: List<NuvioSelectOption>,
    selectedKeys: Set<String>,
    onSelect: (NuvioSelectOption) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    multiSelect: Boolean = false,
) {
    val tokens = MaterialTheme.nuvio
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
            shape = RoundedCornerShape(NuvioTokens.Radius.lg),
            color = tokens.colors.surfaceCard,
            border = BorderStroke(NuvioTokens.Border.thin, tokens.colors.borderSubtle),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (label != null) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = tokens.colors.textMuted,
                        )
                    }
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = tokens.colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = tokens.colors.textMuted,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 240.dp)
                .heightIn(max = 420.dp),
        ) {
            options.forEach { option ->
                val selected = option.key in selectedKeys
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = if (multiSelect) {
                        { Checkbox(checked = selected, onCheckedChange = null) }
                    } else {
                        null
                    },
                    trailingIcon = if (!multiSelect && selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = tokens.colors.accent) }
                    } else {
                        null
                    },
                    onClick = {
                        if (!multiSelect) expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

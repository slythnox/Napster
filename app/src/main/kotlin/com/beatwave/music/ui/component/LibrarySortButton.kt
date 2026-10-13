/**
 * Napster (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.beatwave.music.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatwave.music.R
import com.beatwave.music.constants.LibraryViewType

@Composable
inline fun <reified T : Enum<T>> LibrarySortButton(
    viewType: LibraryViewType? = null,
    crossinline onViewTypeChange: (LibraryViewType) -> Unit = {},
    sortType: T,
    sortDescending: Boolean,
    crossinline onSortTypeChange: (T) -> Unit,
    crossinline onSortDescendingChange: (Boolean) -> Unit,
    crossinline sortTypeText: (T) -> Int,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { menuExpanded = !menuExpanded },
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E2634),
            border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.55f)),
            modifier = Modifier.height(38.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.tune),
                    contentDescription = null,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.sort_by),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    ),
                )
                Icon(
                    painter = painterResource(R.drawable.arrow_downward),
                    contentDescription = null,
                    tint = Color(0xFF9EACB9),
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer { rotationZ = if (sortDescending) 0f else 180f },
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier
                .widthIn(min = 190.dp)
                .background(Color(0xFF16181D)),
        ) {
            if (viewType != null) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "List view",
                            color = if (viewType == LibraryViewType.LIST) Color(0xFF60A5FA) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (viewType == LibraryViewType.LIST) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.list),
                            contentDescription = null,
                            tint = if (viewType == LibraryViewType.LIST) Color(0xFF60A5FA) else Color(0xFF9EACB9),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (viewType == LibraryViewType.LIST) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        onViewTypeChange(LibraryViewType.LIST)
                        menuExpanded = false
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Grid view",
                            color = if (viewType == LibraryViewType.GRID) Color(0xFF60A5FA) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (viewType == LibraryViewType.GRID) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.grid_view),
                            contentDescription = null,
                            tint = if (viewType == LibraryViewType.GRID) Color(0xFF60A5FA) else Color(0xFF9EACB9),
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    trailingIcon = {
                        if (viewType == LibraryViewType.GRID) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        onViewTypeChange(LibraryViewType.GRID)
                        menuExpanded = false
                    },
                )
                HorizontalDivider(
                    color = Color(0xFF252932),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            enumValues<T>().forEach { type ->
                val isSelected = sortType == type
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(sortTypeText(type)),
                            color = if (isSelected) Color(0xFF60A5FA) else Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        onSortTypeChange(type)
                        menuExpanded = false
                    },
                )
            }

            HorizontalDivider(
                color = Color(0xFF252932),
                modifier = Modifier.padding(vertical = 4.dp),
            )

            DropdownMenuItem(
                text = {
                    Text(
                        text = if (sortDescending) stringResource(R.string.sort_descending) else "Ascending",
                        color = Color.White,
                        fontSize = 14.sp,
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.arrow_downward),
                        contentDescription = null,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer { rotationZ = if (sortDescending) 0f else 180f },
                    )
                },
                onClick = {
                    onSortDescendingChange(!sortDescending)
                },
            )
        }
    }
}

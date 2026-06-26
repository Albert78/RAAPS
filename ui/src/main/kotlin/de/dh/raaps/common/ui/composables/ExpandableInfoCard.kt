package de.dh.raaps.common.ui.composables

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.R
import de.dh.raaps.common.ui.icons.Icon_Clear
import de.dh.raaps.common.ui.icons.Icon_Info
import de.dh.raaps.common.ui.theme.AppTheme

@Composable
fun ExpandableInfoCard(
    modifier: Modifier = Modifier,
    infoText: String,
    detailText: String,
    infoTextColor: Color = MaterialTheme.colorScheme.onSurface,
    imageVector: ImageVector = Icon_Info,
    actionButtonText: String? = null,
    onActionButtonClick: (() -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    expandable: Boolean = true,
    collapsible: Boolean = true
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }

    Column() {
        if (!expanded) {
            Row(modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .clickable(onClick = {
                    if (expandable) {
                        expanded = !expanded;
                    }
                }),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = imageVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = infoText,
                    color = infoTextColor,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        } else {
            Card(
                modifier = modifier,
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(), // Default elevation for cards
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.Top) {
                                Text(
                                    text = detailText,
                                    modifier = Modifier.weight(1f)
                                )

                                if (collapsible) {
                                    Icon(
                                        imageVector = Icon_Clear,
                                        contentDescription = stringResource(R.string.cd_close),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .clickable { expanded = false }
                                            .padding(4.dp)
                                    )
                                }
                            }
                            if (actionButtonText != null && onActionButtonClick != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Text(
                                        text = actionButtonText,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .padding(end = 16.dp)
                                            .clickable {
                                                expanded = false
                                                onActionButtonClick()
                                            },
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Collapsed State")
@Preview(showBackground = true, name = "Collapsed State (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpandableInfoCardPreview() {
    AppTheme {
        Surface {
            ExpandableInfoCard(
                imageVector = Icon_Info,
                infoText = "Short Text",
                detailText = "This is the invisible detail text.",
                actionButtonText = "Action",
                onActionButtonClick = {}
            )
        }
    }
}

@Preview(showBackground = true, name = "Expanded State")
@Preview(showBackground = true, name = "Expanded State (Dark)", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ExpandableInfoCardExpandedDarkPreview() {
    AppTheme {
        ExpandableInfoCard(
            imageVector = Icon_Info,
            infoText = "Invisible Short Text",
            detailText = "This is the detail text that appears when the card is expanded. It can contain multiple lines of important information.",
            actionButtonText = "Action",
            onActionButtonClick = {},
            initiallyExpanded = true
        )
    }
}
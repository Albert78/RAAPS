package de.dh.raaps.ui.controls.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.TARGET_MAX
import de.dh.raaps.common.model.TARGET_MIN
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.composables.TimeHourSelector
import de.dh.raaps.ui.R
import de.dh.raaps.ui.screens.insulinprofile.InsertButton
import de.dh.raaps.ui.screens.insulinprofile.ValueAdjuster
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgEditorDialog(
    initialBlocks: List<BgBlock>,
    onSave: (List<BgBlock>) -> Unit,
    onDismiss: () -> Unit
) {
    var blocks by remember { mutableStateOf(initialBlocks) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.bg_editor_title)) },
        text = {
            Column(modifier = Modifier.height(400.dp)) {
                Text(
                    text = stringResource(id = R.string.bg_editor_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                BgBlockList(
                    blocks = blocks,
                    onBlocksChanged = { blocks = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(blocks) }) {
                Text(stringResource(id = de.dh.raaps.common.R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun BgBlockList(
    blocks: List<BgBlock>,
    onBlocksChanged: (List<BgBlock>) -> Unit
) {
    val startHours = remember(blocks) {
        var currentHour = 0
        blocks.map { block ->
            val hour = currentHour
            currentHour += block.duration.value / 60
            hour
        }
    }

    fun updateHour(index: Int, newHour: Int) {
        val newHours = startHours.toMutableList()
        newHours[index] = newHour

        val newBlocks = mutableListOf<BgBlock>()
        for (i in 0 until newHours.size) {
            val durationHours = if (i < newHours.size - 1) newHours[i+1] - newHours[i] else 24 - newHours[i]
            newBlocks.add(blocks[i].copy(duration = Minutes.ofHours(durationHours)))
        }
        onBlocksChanged(newBlocks)
    }

    fun addBlock(atIndex: Int) {
        if (atIndex == 0) return
        val splitIndex = atIndex - 1
        val splitBlock = blocks[splitIndex]
        val splitDuration = splitBlock.duration.value / 60

        val newBlocks = blocks.toMutableList()
        newBlocks[splitIndex] = splitBlock.copy(duration = Minutes.ofHours(1))
        newBlocks.add(atIndex, splitBlock.copy(duration = Minutes.ofHours(splitDuration - 1)))
        onBlocksChanged(newBlocks)
    }

    fun removeBlock(index: Int) {
        if (blocks.size <= 1) return
        val newBlocks = blocks.toMutableList()
        val removed = newBlocks.removeAt(index)
        val targetIndex = if (index > 0) index - 1 else 0
        val target = newBlocks[targetIndex]
        newBlocks[targetIndex] = target.copy(duration = Minutes((target.duration.value + removed.duration.value).toShort()))
        onBlocksChanged(newBlocks)
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val currentHour = startHours[index]
            val prevHour = if (index > 0) startHours[index - 1] else -1
            val nextHour = if (index < blocks.size - 1) startHours[index + 1] else 24

            item(key = "bg_$index") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TimeHourSelector(
                            hour = currentHour,
                            enabled = index > 0,
                            minHour = prevHour + 1,
                            maxHour = nextHour - 1,
                            onHourChanged = { updateHour(index, it) },
                            modifier = Modifier.width(70.dp)
                        )

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ValueAdjuster(
                                    value = block.target.mgdl.toDouble(),
                                    onValueChanged = { newVal ->
                                        val updated = blocks.toMutableList()
                                        updated[index] = block.copy(target = BgValue(newVal.roundToInt().toShort()))
                                        onBlocksChanged(updated)
                                    },
                                    step = 5.0,
                                    format = "%.0f",
                                    modifier = Modifier.weight(1f),
                                    minValue = TARGET_MIN.toDouble(),
                                    maxValue = TARGET_MAX.toDouble()
                                )
                                Icon(
                                    imageVector = Icons.Default.Adjust,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).padding(start = 2.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ValueAdjuster(
                                    value = block.lowThreshold.mgdl.toDouble(),
                                    onValueChanged = { newVal ->
                                        val updated = blocks.toMutableList()
                                        updated[index] = block.copy(lowThreshold = BgValue(newVal.roundToInt().toShort()))
                                        onBlocksChanged(updated)
                                    },
                                    step = 5.0,
                                    format = "%.0f",
                                    modifier = Modifier.weight(1f),
                                    minValue = TARGET_MIN.toDouble(),
                                    maxValue = TARGET_MAX.toDouble()
                                )
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignBottom,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).padding(start = 2.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (index > 0) {
                            IconButton(onClick = { removeBlock(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(id = de.dh.raaps.common.R.string.action_delete))
                            }
                        }
                    }
                }
            }

            item(key = "insert_$index") {
                InsertButton(canInsert = nextHour - currentHour > 1) { addBlock(index + 1) }
            }
        }
    }
}
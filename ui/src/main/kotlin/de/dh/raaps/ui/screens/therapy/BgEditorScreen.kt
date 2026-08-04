package de.dh.raaps.ui.screens.therapy

import androidx.activity.compose.BackHandler
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.TARGET_MAX
import de.dh.raaps.common.model.TARGET_MIN
import de.dh.raaps.common.model.data.BgBlock
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.DefaultSteppingStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.composables.StepperDefaults
import de.dh.raaps.common.ui.composables.TimeHourSelector
import de.dh.raaps.common.ui.composables.contentScrollIndicator
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.ui.R
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.controls.profile.CurrentTherapyViewModel
import de.dh.raaps.ui.screens.insulinprofile.InsertButton
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgEditorScreen(
    viewModel: CurrentTherapyViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var blocks by remember(uiState.defaultBgBlocks) { mutableStateOf(uiState.defaultBgBlocks) }

    BgEditorContent(
        blocks = blocks,
        onBlocksChanged = { blocks = it },
        onSave = {
            viewModel.updateDefaultBgBlocks(blocks)
            onNavigateUp()
        },
        onNavigateUp = onNavigateUp,
        originalBlocks = uiState.defaultBgBlocks
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BgEditorContent(
    blocks: List<BgBlock>,
    onBlocksChanged: (List<BgBlock>) -> Unit,
    onSave: () -> Unit,
    onNavigateUp: () -> Unit,
    originalBlocks: List<BgBlock> = emptyList()
) {
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    val hasChanges = remember(blocks, originalBlocks) { blocks != originalBlocks && originalBlocks.isNotEmpty() }

    fun handleBack() {
        if (hasChanges) {
            showDiscardConfirmation = true
        } else {
            onNavigateUp()
        }
    }

    BackHandler(onBack = ::handleBack)

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumTopAppBar(
                title = screenTitle(stringResource(id = R.string.bg_editor_title)),
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(
                            imageVector = if (hasChanges) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                id = if (hasChanges) de.dh.raaps.common.R.string.cd_cancel
                                else de.dh.raaps.common.R.string.cd_navigate_up
                            )
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.action_save)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.bg_editor_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            BgBlockList(
                blocks = blocks,
                onBlocksChanged = onBlocksChanged,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(id = R.string.bg_editor_discard_title)) },
            text = { Text(stringResource(id = R.string.bg_editor_discard_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    onSave()
                }) {
                    Text(stringResource(id = de.dh.raaps.common.R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscardConfirmation = false
                    onNavigateUp()
                }) {
                    Text(stringResource(id = R.string.discard_confirm_button))
                }
            }
        )
    }
}

@Composable
private fun BgBlockList(
    blocks: List<BgBlock>,
    onBlocksChanged: (List<BgBlock>) -> Unit,
    modifier: Modifier = Modifier
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

    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().contentScrollIndicator(listState),
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
                    Column(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TimeHourSelector(
                                hour = currentHour,
                                enabled = index > 0,
                                minHour = prevHour + 1,
                                maxHour = nextHour - 1,
                                onHourChanged = { updateHour(index, it) },
                                modifier = Modifier.width(150.dp)
                            )

                            if (index > 0) {
                                IconButton(onClick = { removeBlock(index) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(id = de.dh.raaps.common.R.string.action_delete)
                                    )
                                }
                            }
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Adjust,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 8.dp, end = 16.dp)
                                        .size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                EditableValueStepper(
                                    currentValue = block.target.mgdl.toDouble(),
                                    onValueChange = { newVal ->
                                        val updated = blocks.toMutableList()
                                        updated[index] = block.copy(target = BgValue(newVal.roundToInt().toShort()))
                                        onBlocksChanged(updated)
                                    },
                                    modifier = Modifier.weight(1f),
                                    minValue = TARGET_MIN.toDouble(),
                                    maxValue = TARGET_MAX.toDouble(),
                                    steppingStrategy = DefaultSteppingStrategy(step = 5.0),
                                    style = StepperDefaults.mediumStyle()
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignBottom,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 8.dp, end = 16.dp)
                                        .size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                EditableValueStepper(
                                    currentValue = block.lowThreshold.mgdl.toDouble(),
                                    onValueChange = { newVal ->
                                        val updated = blocks.toMutableList()
                                        updated[index] = block.copy(lowThreshold = BgValue(newVal.roundToInt().toShort()))
                                        onBlocksChanged(updated)
                                    },
                                    modifier = Modifier.weight(1f),
                                    minValue = TARGET_MIN.toDouble(),
                                    maxValue = TARGET_MAX.toDouble(),
                                    steppingStrategy = DefaultSteppingStrategy(step = 5.0),
                                    style = StepperDefaults.mediumStyle()
                                )
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

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
private fun BgEditorPreview() {
    val mockBlocks = listOf(
        BgBlock(
            duration = Minutes.ofHours(8),
            target = BgValue(100),
            lowThreshold = BgValue(70)
        ),
        BgBlock(
            duration = Minutes.ofHours(16),
            target = BgValue(110),
            lowThreshold = BgValue(80)
        )
    )
    AppTheme {
        Surface {
            BgEditorContent(
                blocks = mockBlocks,
                onBlocksChanged = {},
                onSave = {},
                onNavigateUp = {}
            )
        }
    }
}
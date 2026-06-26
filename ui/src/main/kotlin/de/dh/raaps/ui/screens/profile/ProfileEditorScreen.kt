package de.dh.raaps.ui.screens.profile

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.ui.R
import de.dh.raaps.common.model.BASAL_MAX
import de.dh.raaps.common.model.BASAL_MIN
import de.dh.raaps.common.model.DEFAULT_BASAL_UNITS_PER_HOUR
import de.dh.raaps.common.model.DEFAULT_IC_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_TARGET_HIGH_MGDL
import de.dh.raaps.common.model.DEFAULT_TARGET_LOW_MGDL
import de.dh.raaps.common.model.IC_MAX
import de.dh.raaps.common.model.IC_MIN
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.ISF_MAX
import de.dh.raaps.common.model.ISF_MIN
import de.dh.raaps.common.model.TARGET_MAX
import de.dh.raaps.common.model.TARGET_MIN
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.TargetBlock
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.ui.composables.TimeHourSelector
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.controls.profile.ProfileSettingsUiState
import de.dh.raaps.ui.controls.profile.ProfileSettingsViewModel
import kotlin.math.roundToInt

@Composable
fun ProfileEditorScreen(
    viewModel: ProfileSettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.editingProfile != null) {
        ProfileDetailEditor(
            profile = uiState.editingProfile!!,
            onSave = { viewModel.saveProfile(it) },
            onCancel = { viewModel.stopEditing() },
            isNameUnique = { name, id -> viewModel.isNameUnique(name, id) }
        )
    } else {
        ProfileList(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onAddProfile = {
                viewModel.startEditing(
                    Profile(
                        name = "",
                        therapyData = TherapyData(
                            basalBlocks = listOf(Block(
                                Minutes.ofHours(24),
                                DEFAULT_BASAL_UNITS_PER_HOUR
                            )),
                            isfBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_ISF_MGDL_PER_UNIT)),
                            icBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_IC_GRAM_PER_UNIT)),
                            targetBlocks = listOf(TargetBlock(
                                Minutes.ofHours(24),
                                BgValue(DEFAULT_TARGET_LOW_MGDL),
                                BgValue(DEFAULT_TARGET_HIGH_MGDL)))
                        )
                    )
                )
            },
            onEditProfile = { viewModel.startEditing(it) },
            onDeleteProfile = { viewModel.confirmDelete(it) }
        )
    }

    uiState.showDeleteConfirmation?.let { profile ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(id = R.string.delete_profile_title)) },
            text = { Text(stringResource(id = R.string.delete_profile_message, profile.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProfile(profile) }) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileList(
    uiState: ProfileSettingsUiState,
    onNavigateUp: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (Profile) -> Unit,
    onDeleteProfile: (Profile) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.profile_editor_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_navigate_up)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddProfile) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.cd_add_profile)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.profiles.size) { index ->
                        val profile = uiState.profiles[index]
                        ListItem(
                            headlineContent = { Text(profile.name) },
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { onEditProfile(profile) },
                            trailingContent = {
                                IconButton(onClick = { onDeleteProfile(profile) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(id = R.string.cd_delete_profile)
                                    )
                                }
                            },
                            tonalElevation = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailEditor(
    profile: Profile,
    onSave: (Profile) -> Unit,
    onCancel: () -> Unit,
    isNameUnique: (String, Long) -> Boolean = { _, _ -> true }
) {
    var name by remember { mutableStateOf(profile.name) }
    var therapyData by remember { mutableStateOf(profile.therapyData) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        stringResource(id = R.string.profile_editor_tab_basal),
        stringResource(id = R.string.profile_editor_tab_isf),
        stringResource(id = R.string.profile_editor_tab_ic),
        stringResource(id = R.string.profile_editor_tab_target)
    )

    val isNameValid = name.trim().isNotBlank() && isNameUnique(name.trim(), profile.id)

    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(if (profile.id == ID_UNDEFINED) stringResource(id = R.string.profile_editor_new_profile) else stringResource(id = R.string.profile_editor_edit_profile)),
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = de.dh.raaps.common.R.string.cd_cancel)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onSave(profile.copy(name = name.trim(), therapyData = therapyData)) },
                        enabled = isNameValid
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(id = R.string.cd_save_profile)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(id = R.string.profile_editor_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                isError = !isNameValid
            )

            SecondaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> TherapyBlockListEditor(
                        title = stringResource(id = R.string.profile_editor_basal_title),
                        description = stringResource(id = R.string.profile_editor_basal_desc),
                        blocks = therapyData.basalBlocks,
                        onBlocksChanged = { therapyData = therapyData.copy(basalBlocks = it) },
                        step = 0.05,
                        format = "%.2f",
                        minValue = BASAL_MIN,
                        maxValue = BASAL_MAX
                    )
                    1 -> TherapyBlockListEditor(
                        title = stringResource(id = R.string.profile_editor_isf_title),
                        description = stringResource(id = R.string.profile_editor_isf_desc),
                        blocks = therapyData.isfBlocks,
                        onBlocksChanged = { therapyData = therapyData.copy(isfBlocks = it) },
                        step = 1.0,
                        format = "%.0f",
                        minValue = ISF_MIN,
                        maxValue = ISF_MAX
                    )
                    2 -> TherapyBlockListEditor(
                        title = stringResource(id = R.string.profile_editor_ic_title),
                        description = stringResource(id = R.string.profile_editor_ic_desc),
                        blocks = therapyData.icBlocks,
                        onBlocksChanged = { therapyData = therapyData.copy(icBlocks = it) },
                        step = 0.1,
                        format = "%.1f",
                        minValue = IC_MIN,
                        maxValue = IC_MAX
                    )
                    3 -> TargetBlockListEditor(
                        title = stringResource(id = R.string.profile_editor_target_title),
                        description = stringResource(id = R.string.profile_editor_target_desc),
                        blocks = therapyData.targetBlocks,
                        onBlocksChanged = { therapyData = therapyData.copy(targetBlocks = it) }
                    )
                }
            }
        }
    }
}

@Composable
fun TherapyBlockListEditor(
    title: String,
    description: String,
    blocks: List<Block>,
    onBlocksChanged: (List<Block>) -> Unit,
    step: Double,
    format: String,
    minValue: Double,
    maxValue: Double
) {
    var showHelpDialog by remember { mutableStateOf(false) }

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

        val newBlocks = mutableListOf<Block>()
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(id = R.string.cd_help),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        blocks.forEachIndexed { index, block ->
            val currentHour = startHours[index]
            val prevHour = if (index > 0) startHours[index - 1] else -1
            val nextHour = if (index < blocks.size - 1) startHours[index + 1] else 24

            item(key = "block_$index") {
                BlockRow(
                    hour = currentHour,
                    minHour = prevHour + 1,
                    maxHour = nextHour - 1,
                    value = block.amount,
                    onHourChanged = { updateHour(index, it) },
                    onValueChanged = { newVal ->
                        val updated = blocks.toMutableList()
                        updated[index] = block.copy(amount = newVal)
                        onBlocksChanged(updated)
                    },
                    onDelete = if (index > 0) { { removeBlock(index) } } else null,
                    step = step,
                    format = format,
                    isFixed = index == 0,
                    minValue = minValue,
                    maxValue = maxValue
                )
            }

            item(key = "insert_$index") {
                InsertButton(canInsert = nextHour - currentHour > 1) { addBlock(index + 1) }
            }
        }
    }

    if (showHelpDialog) {
        ProfileHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun TargetBlockListEditor(
    title: String,
    description: String,
    blocks: List<TargetBlock>,
    onBlocksChanged: (List<TargetBlock>) -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }

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

        val newBlocks = mutableListOf<TargetBlock>()
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
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(id = R.string.cd_help),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        blocks.forEachIndexed { index, block ->
            val currentHour = startHours[index]
            val prevHour = if (index > 0) startHours[index - 1] else -1
            val nextHour = if (index < blocks.size - 1) startHours[index + 1] else 24

            item(key = "target_$index") {
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
                            modifier = Modifier.width(100.dp)
                        )

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ValueAdjuster(
                                value = block.lowTarget.mgdl.toDouble(),
                                onValueChanged = { newVal ->
                                    val updated = blocks.toMutableList()
                                    updated[index] = block.copy(lowTarget = BgValue(newVal.roundToInt().toShort()))
                                    onBlocksChanged(updated)
                                },
                                step = 5.0,
                                format = "%.0f",
                                modifier = Modifier.fillMaxWidth(),
                                minValue = TARGET_MIN.toDouble(),
                                maxValue = TARGET_MAX.toDouble()
                            )
                            ValueAdjuster(
                                value = block.highTarget.mgdl.toDouble(),
                                onValueChanged = { newVal ->
                                    val updated = blocks.toMutableList()
                                    updated[index] = block.copy(highTarget = BgValue(newVal.roundToInt().toShort()))
                                    onBlocksChanged(updated)
                                },
                                step = 5.0,
                                format = "%.0f",
                                modifier = Modifier.fillMaxWidth(),
                                minValue = TARGET_MIN.toDouble(),
                                maxValue = TARGET_MAX.toDouble()
                            )
                        }

                        if (index > 0) {
                            IconButton(onClick = { removeBlock(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(id = de.dh.raaps.common.R.string.action_delete))
                            }
                        } else {
                            Box(modifier = Modifier.size(48.dp))
                        }
                    }
                }
            }

            item(key = "insert_$index") {
                InsertButton(canInsert = nextHour - currentHour > 1) { addBlock(index + 1) }
            }
        }
    }

    if (showHelpDialog) {
        ProfileHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun ProfileHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.profile_editor_help_title)) },
        text = { Text(stringResource(id = R.string.profile_editor_help_message)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = android.R.string.ok))
            }
        }
    )
}

@Composable
fun BlockRow(
    hour: Int,
    minHour: Int,
    maxHour: Int,
    value: Double,
    onHourChanged: (Int) -> Unit,
    onValueChanged: (Double) -> Unit,
    onDelete: (() -> Unit)?,
    step: Double,
    format: String,
    isFixed: Boolean,
    minValue: Double,
    maxValue: Double
) {
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
                hour = hour,
                enabled = !isFixed,
                minHour = minHour,
                maxHour = maxHour,
                onHourChanged = onHourChanged,
                modifier = Modifier.width(100.dp)
            )

            ValueAdjuster(
                value = value,
                onValueChanged = onValueChanged,
                step = step,
                format = format,
                modifier = Modifier.weight(1f),
                minValue = minValue,
                maxValue = maxValue
            )

            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(id = de.dh.raaps.common.R.string.action_delete))
                }
            } else {
                Box(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
fun InsertButton(canInsert: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        contentAlignment = Alignment.Center
    ) {
        val backgroundColor = if (canInsert) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
        val iconTint = if (canInsert) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        }

        Box(
            modifier = Modifier
                .width(80.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(backgroundColor)
                .then(if (canInsert) Modifier.clickable { onClick() } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = if (canInsert) stringResource(id = R.string.cd_insert_block) else null,
                modifier = Modifier.size(16.dp),
                tint = iconTint
            )
        }
    }
}

@Composable
fun ValueAdjuster(
    value: Double,
    onValueChanged: (Double) -> Unit,
    step: Double,
    format: String,
    modifier: Modifier = Modifier,
    minValue: Double,
    maxValue: Double
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        IconButton(
            onClick = { onValueChanged((value - step).coerceIn(minValue, maxValue)) },
            enabled = value > minValue
        ) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(id = R.string.cd_decrease_value))
        }

        Text(
            text = String.format(LocalLocale.current.platformLocale, format, value),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = { onValueChanged((value + step).coerceIn(minValue, maxValue)) },
            enabled = value < maxValue
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(id = R.string.cd_increase_value))
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun ProfileEditorPreview() {
    AppTheme {
        ProfileList(
            uiState = ProfileSettingsUiState(
                profiles = listOf(
                    Profile(name = "Normal", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList())),
                    Profile(name = "Sport", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList())),
                    Profile(name = "Illness", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList()))
                ),
                isLoading = false
            ),
            onNavigateUp = {},
            onAddProfile = {},
            onEditProfile = {},
            onDeleteProfile = {}
        )
    }
}

@Preview(showBackground = true, name = "Detail Editor Preview")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun ProfileDetailEditorPreview() {
    AppTheme {
        ProfileDetailEditor(
            profile = Profile(name = "Normal", therapyData = TherapyData(
                basalBlocks = listOf(Block(Minutes.ofHours(24), 1.0)),
                isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
                icBlocks = listOf(Block(Minutes.ofHours(24), 10.0)),
                targetBlocks = listOf(TargetBlock(Minutes.ofHours(24), BgValue(100), BgValue(120)))
            )),
            onSave = {},
            onCancel = {}
        )
    }
}
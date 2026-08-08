package de.dh.raaps.ui.screens.insulinprofile

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.BASAL_MAX
import de.dh.raaps.common.model.BASAL_MIN
import de.dh.raaps.common.model.CR_MAX
import de.dh.raaps.common.model.CR_MIN
import de.dh.raaps.common.model.DEFAULT_BASAL_UNITS_PER_HOUR
import de.dh.raaps.common.model.DEFAULT_CR_GRAM_PER_UNIT
import de.dh.raaps.common.model.DEFAULT_ISF_MGDL_PER_UNIT
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.ISF_MAX
import de.dh.raaps.common.model.ISF_MIN
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.InsulinProfile
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.ui.DefaultSteppingStrategy
import de.dh.raaps.common.ui.ValueDisplayStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.composables.NormalTextButton
import de.dh.raaps.common.ui.composables.StepperDefaults
import de.dh.raaps.common.ui.composables.TimeHourSelector
import de.dh.raaps.common.ui.composables.contentScrollIndicator
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R
import de.dh.raaps.ui.controls.profile.InsulinProfileSettingsUiState
import de.dh.raaps.ui.controls.profile.InsulinProfileSettingsViewModel

@Composable
fun InsulinProfileEditorScreen(
    viewModel: InsulinProfileSettingsViewModel,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val copyNameFormat = stringResource(R.string.insulin_profile_editor_copy_name_format)

    if (uiState.editingProfile != null) {
        InsulinProfileDetailEditor(
            profile = uiState.editingProfile!!,
            insulinTypes = uiState.insulinTypes,
            onSave = { viewModel.saveInsulinProfile(it) },
            onCancel = { viewModel.stopEditing() },
            isNameUnique = { name, id -> viewModel.isNameUnique(name, id) }
        )
    } else {
        InsulinProfileList(
            uiState = uiState,
            onNavigateUp = onNavigateUp,
            onAddProfile = {
                val defaultInsulinType = uiState.insulinTypes.firstOrNull()
                if (defaultInsulinType != null) {
                    viewModel.startEditing(
                        InsulinProfile(
                            name = "",
                            basalBlocks = listOf(Block(
                                Minutes.ofHours(24),
                                DEFAULT_BASAL_UNITS_PER_HOUR
                            )),
                            isfBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_ISF_MGDL_PER_UNIT)),
                            crBlocks = listOf(Block(Minutes.ofHours(24), DEFAULT_CR_GRAM_PER_UNIT)),
                            insulinType = defaultInsulinType,
                            dia = defaultInsulinType.dia,
                            peak = defaultInsulinType.peak
                        )
                    )
                }
            },
            onEditProfile = { viewModel.startEditing(it) },
            onDeleteProfile = { viewModel.confirmDelete(it) },
            onCopyProfile = { profile ->
                viewModel.copyInsulinProfile(profile, copyNameFormat.format(profile.name))
            }
        )
    }

    uiState.showDeleteConfirmation?.let { profile ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(stringResource(id = R.string.delete_profile_title)) },
            text = { Text(stringResource(id = R.string.delete_profile_message, profile.name)) },
            confirmButton = {
                NormalTextButton(onClick = { viewModel.deleteInsulinProfile(profile) }) {
                    Text(stringResource(id = android.R.string.ok))
                }
            },
            dismissButton = {
                NormalTextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinProfileList(
    uiState: InsulinProfileSettingsUiState,
    onNavigateUp: () -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (InsulinProfile) -> Unit,
    onDeleteProfile: (InsulinProfile) -> Unit,
    onCopyProfile: (InsulinProfile) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.insulin_profile_editor_screen_title)),
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
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().contentScrollIndicator(listState)
                ) {
                    items(uiState.profiles.size) { index ->
                        val profile = uiState.profiles[index]
                        ListItem(
                            headlineContent = { Text(profile.name) },
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable { onEditProfile(profile) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { onCopyProfile(profile) }) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(id = R.string.cd_copy_profile)
                                        )
                                    }
                                    IconButton(onClick = { onDeleteProfile(profile) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(id = R.string.cd_delete_profile)
                                        )
                                    }
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
fun InsulinProfileDetailEditor(
    profile: InsulinProfile,
    insulinTypes: List<InsulinType>,
    onSave: (InsulinProfile) -> Unit,
    onCancel: () -> Unit,
    isNameUnique: (String, Long) -> Boolean = { _, _ -> true }
) {
    var name by remember { mutableStateOf(profile.name) }
    var basalBlocks by remember { mutableStateOf(profile.basalBlocks) }
    var isfBlocks by remember { mutableStateOf(profile.isfBlocks) }
    var crBlocks by remember { mutableStateOf(profile.crBlocks) }
    var insulinType by remember { mutableStateOf(profile.insulinType) }
    var dia by remember { mutableStateOf(profile.dia.value.toString()) }
    var peak by remember { mutableStateOf(profile.peak.value.toString()) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }

    val tabs = listOf(
        stringResource(id = R.string.insulin_profile_editor_tab_insulin),
        stringResource(id = R.string.insulin_profile_editor_tab_basal),
        stringResource(id = R.string.insulin_profile_editor_tab_isf),
        stringResource(id = R.string.insulin_profile_editor_tab_cr)
    )

    val isNameValid = name.trim().isNotBlank() && isNameUnique(name.trim(), profile.id)
    val diaValue = dia.toIntOrNull() ?: 0
    val peakValue = peak.toIntOrNull() ?: 0

    val hasChanges = name != profile.name ||
            basalBlocks != profile.basalBlocks ||
            isfBlocks != profile.isfBlocks ||
            crBlocks != profile.crBlocks ||
            insulinType != profile.insulinType ||
            diaValue != profile.dia.value.toInt() ||
            peakValue != profile.peak.value.toInt()

    fun handleBack() {
        if (hasChanges) {
            showDiscardConfirmation = true
        } else {
            onCancel()
        }
    }

    fun saveChanges() {
        onSave(profile.copy(
            name = name.trim(),
            basalBlocks = basalBlocks,
            isfBlocks = isfBlocks,
            crBlocks = crBlocks,
            insulinType = insulinType,
            dia = Minutes(diaValue.toShort()),
            peak = Minutes(peakValue.toShort())
        ))
    }

    BackHandler(onBack = ::handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(if (profile.id == ID_UNDEFINED) stringResource(id = R.string.insulin_profile_editor_new_profile) else stringResource(id = R.string.insulin_profile_editor_edit_profile)),
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
                    IconButton(
                        onClick = ::saveChanges,
                        enabled = isNameValid && diaValue > 0 && peakValue > 0
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
                label = { Text(stringResource(id = R.string.insulin_profile_editor_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true,
                isError = !isNameValid,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
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
                    0 -> InsulinSettingsEditor(
                        insulinTypes = insulinTypes,
                        selectedInsulinType = insulinType,
                        onInsulinTypeSelected = {
                            insulinType = it
                            dia = it.dia.value.toString()
                            peak = it.peak.value.toString()
                        },
                        dia = dia,
                        onDiaChanged = { dia = it },
                        peak = peak,
                        onPeakChanged = { peak = it }
                    )
                    1 -> TherapyBlockListEditor(
                        title = stringResource(id = R.string.insulin_profile_editor_basal_title),
                        description = stringResource(id = R.string.insulin_profile_editor_basal_desc),
                        blocks = basalBlocks,
                        onBlocksChanged = { basalBlocks = it },
                        step = 0.05,
                        format = "%.2f",
                        minValue = BASAL_MIN,
                        maxValue = BASAL_MAX
                    )
                    2 -> TherapyBlockListEditor(
                        title = stringResource(id = R.string.insulin_profile_editor_isf_title),
                        description = stringResource(id = R.string.insulin_profile_editor_isf_desc),
                        blocks = isfBlocks,
                        onBlocksChanged = { isfBlocks = it },
                        step = 1.0,
                        format = "%.0f",
                        minValue = ISF_MIN,
                        maxValue = ISF_MAX
                    )
                    3 -> TherapyBlockListEditor(
                        title = stringResource(id = R.string.insulin_profile_editor_cr_title),
                        description = stringResource(id = R.string.insulin_profile_editor_cr_desc),
                        blocks = crBlocks,
                        onBlocksChanged = { crBlocks = it },
                        step = 0.1,
                        format = "%.1f",
                        minValue = CR_MIN,
                        maxValue = CR_MAX
                    )
                }
            }
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(id = R.string.insulin_profile_editor_discard_title)) },
            text = { Text(stringResource(id = R.string.insulin_profile_editor_discard_message)) },
            confirmButton = {
                NormalTextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        saveChanges()
                    },
                    enabled = isNameValid && diaValue > 0 && peakValue > 0
                ) {
                    Text(stringResource(id = de.dh.raaps.common.R.string.action_save))
                }
            },
            dismissButton = {
                NormalTextButton(onClick = {
                    showDiscardConfirmation = false
                    onCancel()
                }) {
                    Text(stringResource(id = R.string.discard_confirm_button))
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinSettingsEditor(
    insulinTypes: List<InsulinType>,
    selectedInsulinType: InsulinType,
    onInsulinTypeSelected: (InsulinType) -> Unit,
    dia: String,
    onDiaChanged: (String) -> Unit,
    peak: String,
    onPeakChanged: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .contentScrollIndicator(scrollState)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(id = R.string.insulin_profile_editor_insulin_settings_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selectedInsulinType.name,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(id = R.string.insulin_profile_editor_insulin_type_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                insulinTypes.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.name) },
                        onClick = {
                            onInsulinTypeSelected(type)
                            expanded = false
                        }
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = dia,
                onValueChange = { onDiaChanged(it) },
                label = { Text(stringResource(id = R.string.insulin_profile_editor_dia_label)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = peak,
                onValueChange = { onPeakChanged(it) },
                label = { Text(stringResource(id = R.string.insulin_profile_editor_peak_label)) },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
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

    val listState = rememberLazyListState()

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
        state = listState,
        modifier = Modifier.fillMaxSize().contentScrollIndicator(listState),
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
        InsulinProfileHelpDialog(onDismiss = { showHelpDialog = false })
    }
}

@Composable
fun InsulinProfileHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.insulin_profile_editor_help_title)) },
        text = {
            Text(
                stringResource(id = R.string.help_24h_profile_general) +
                        "\n\n" +
                        stringResource(id = R.string.insulin_profile_editor_help_specific)
            )
        },
        confirmButton = {
            NormalTextButton(onClick = onDismiss) {
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

            val locale = LocalLocale.current
            EditableValueStepper(
                currentValue = value,
                onValueChange = onValueChanged,
                modifier = Modifier.weight(1f),
                minValue = minValue,
                maxValue = maxValue,
                steppingStrategy = DefaultSteppingStrategy(step = step),
                displayStrategy = object : ValueDisplayStrategy {
                    override fun format(value: Double): String = String.format(locale.platformLocale, format, value)
                    override fun color(value: Double): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified
                },
                style = StepperDefaults.compactStyle().copy(suffixBelowValue = false, valueWidth = 64.dp)
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

@Preview(showBackground = true)
@Composable
private fun InsulinProfileListPreview() {
    val sampleInsulinType = InsulinType(name = "Humalog", dia = Minutes.ofHours(5), peak = Minutes.ofHours(1))
    val sampleProfiles = listOf(
        InsulinProfile(
            id = 1,
            name = "Normal",
            basalBlocks = listOf(Block(Minutes.ofHours(24), 0.8)),
            isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
            crBlocks = listOf(Block(Minutes.ofHours(24), 10.0)),
            insulinType = sampleInsulinType,
            dia = sampleInsulinType.dia,
            peak = sampleInsulinType.peak
        ),
        InsulinProfile(
            id = 2,
            name = "Sport",
            basalBlocks = listOf(Block(Minutes.ofHours(24), 0.5)),
            isfBlocks = listOf(Block(Minutes.ofHours(24), 80.0)),
            crBlocks = listOf(Block(Minutes.ofHours(24), 15.0)),
            insulinType = sampleInsulinType,
            dia = sampleInsulinType.dia,
            peak = sampleInsulinType.peak
        )
    )

    AppTheme {
        InsulinProfileList(
            uiState = InsulinProfileSettingsUiState(profiles = sampleProfiles),
            onNavigateUp = {},
            onAddProfile = {},
            onEditProfile = {},
            onDeleteProfile = {},
            onCopyProfile = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun InsulinProfileDetailEditorPreview() {
    val sampleInsulinType = InsulinType(name = "Humalog", dia = Minutes.ofHours(5), peak = Minutes.ofHours(1))
    val sampleProfile = InsulinProfile(
        id = 1,
        name = "Normal",
        basalBlocks = listOf(
            Block(Minutes.ofHours(8), 0.8),
            Block(Minutes.ofHours(16), 1.0)
        ),
        isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
        crBlocks = listOf(Block(Minutes.ofHours(24), 10.0)),
        insulinType = sampleInsulinType,
        dia = sampleInsulinType.dia,
        peak = sampleInsulinType.peak
    )

    AppTheme {
        InsulinProfileDetailEditor(
            profile = sampleProfile,
            insulinTypes = listOf(sampleInsulinType, InsulinType(name = "Novorapid", dia = Minutes.ofHours(5), peak = Minutes.ofHours(1))),
            onSave = {},
            onCancel = {}
        )
    }
}

package de.dh.raaps.ui.screens.profile

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.R
import de.dh.raaps.common.model.MINUTES_PER_DAY
import de.dh.raaps.common.model.data.BgValue
import de.dh.raaps.common.model.data.Block
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Profile
import de.dh.raaps.common.model.data.TargetBlock
import de.dh.raaps.common.model.data.TherapyData
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.controls.profile.ProfileSettingsUiState
import de.dh.raaps.ui.controls.profile.ProfileSettingsViewModel

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
            onCancel = { viewModel.stopEditing() }
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
                            basalBlocks = listOf(Block(Minutes.ofHours(24), 1.0)),
                            isfBlocks = listOf(Block(Minutes.ofHours(24), 50.0)),
                            icBlocks = listOf(Block(Minutes.ofHours(24), 10.0)),
                            targetBlocks = listOf(TargetBlock(Minutes.ofHours(24), BgValue(100), BgValue(120)))
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
                    items(uiState.profiles) { profile ->
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
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(profile.name) }
    var therapyData by remember { mutableStateOf(profile.therapyData) }

    val basalTotal = therapyData.basalBlocks.sumOf { it.duration.value.toInt() }
    val isfTotal = therapyData.isfBlocks.sumOf { it.duration.value.toInt() }
    val icTotal = therapyData.icBlocks.sumOf { it.duration.value.toInt() }
    val targetTotal = therapyData.targetBlocks.sumOf { it.duration.value.toInt() }

    val isValid = name.isNotBlank() && 
            basalTotal == MINUTES_PER_DAY && 
            isfTotal == MINUTES_PER_DAY && 
            icTotal == MINUTES_PER_DAY && 
            targetTotal == MINUTES_PER_DAY

    BackHandler(onBack = onCancel)

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(if (profile.id == -1L) "Neues Profil" else "Profil bearbeiten"),
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
                        onClick = { onSave(profile.copy(name = name, therapyData = therapyData)) },
                        enabled = isValid
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
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profilname") },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                singleLine = true
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    BlockSectionHeader("Basalraten (U/h)", basalTotal)
                    therapyData.basalBlocks.forEachIndexed { index, block ->
                        BlockItem(
                            block = block,
                            onChanged = { newBlock ->
                                val newList = therapyData.basalBlocks.toMutableList()
                                newList[index] = newBlock
                                therapyData = therapyData.copy(basalBlocks = newList)
                            },
                            onDelete = if (therapyData.basalBlocks.size > 1) {
                                {
                                    val newList = therapyData.basalBlocks.toMutableList()
                                    newList.removeAt(index)
                                    therapyData = therapyData.copy(basalBlocks = newList)
                                }
                            } else null
                        )
                    }
                    AddBlockButton {
                        val newList = therapyData.basalBlocks.toMutableList()
                        newList.add(Block(Minutes(60), 1.0))
                        therapyData = therapyData.copy(basalBlocks = newList)
                    }
                }

                item {
                    BlockSectionHeader("ISF (mg/dL/U)", isfTotal)
                    therapyData.isfBlocks.forEachIndexed { index, block ->
                        BlockItem(
                            block = block,
                            onChanged = { newBlock ->
                                val newList = therapyData.isfBlocks.toMutableList()
                                newList[index] = newBlock
                                therapyData = therapyData.copy(isfBlocks = newList)
                            },
                            onDelete = if (therapyData.isfBlocks.size > 1) {
                                {
                                    val newList = therapyData.isfBlocks.toMutableList()
                                    newList.removeAt(index)
                                    therapyData = therapyData.copy(isfBlocks = newList)
                                }
                            } else null
                        )
                    }
                    AddBlockButton {
                        val newList = therapyData.isfBlocks.toMutableList()
                        newList.add(Block(Minutes(60), 50.0))
                        therapyData = therapyData.copy(isfBlocks = newList)
                    }
                }

                item {
                    BlockSectionHeader("I:C (g/U)", icTotal)
                    therapyData.icBlocks.forEachIndexed { index, block ->
                        BlockItem(
                            block = block,
                            onChanged = { newBlock ->
                                val newList = therapyData.icBlocks.toMutableList()
                                newList[index] = newBlock
                                therapyData = therapyData.copy(icBlocks = newList)
                            },
                            onDelete = if (therapyData.icBlocks.size > 1) {
                                {
                                    val newList = therapyData.icBlocks.toMutableList()
                                    newList.removeAt(index)
                                    therapyData = therapyData.copy(icBlocks = newList)
                                }
                            } else null
                        )
                    }
                    AddBlockButton {
                        val newList = therapyData.icBlocks.toMutableList()
                        newList.add(Block(Minutes(60), 10.0))
                        therapyData = therapyData.copy(icBlocks = newList)
                    }
                }

                item {
                    BlockSectionHeader("Zielbereich (mg/dL)", targetTotal)
                    therapyData.targetBlocks.forEachIndexed { index, block ->
                        TargetBlockItem(
                            block = block,
                            onChanged = { newBlock ->
                                val newList = therapyData.targetBlocks.toMutableList()
                                newList[index] = newBlock
                                therapyData = therapyData.copy(targetBlocks = newList)
                            },
                            onDelete = if (therapyData.targetBlocks.size > 1) {
                                {
                                    val newList = therapyData.targetBlocks.toMutableList()
                                    newList.removeAt(index)
                                    therapyData = therapyData.copy(targetBlocks = newList)
                                }
                            } else null
                        )
                    }
                    AddBlockButton {
                        val newList = therapyData.targetBlocks.toMutableList()
                        newList.add(TargetBlock(Minutes(60), BgValue(100), BgValue(120)))
                        therapyData = therapyData.copy(targetBlocks = newList)
                    }
                }
                
                item {
                    Box(modifier = Modifier.padding(bottom = 32.dp))
                }
            }
        }
    }
}

@Composable
fun BlockSectionHeader(title: String, currentTotal: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (currentTotal != MINUTES_PER_DAY) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 4.dp)
                )
                Text(
                    text = "$currentTotal / $MINUTES_PER_DAY min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Text(
                text = "24h vollständig",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AddBlockButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null)
        Text("Block hinzufügen")
    }
}

@Composable
fun BlockItem(
    block: Block,
    onChanged: (Block) -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = block.duration.value.toString(),
            onValueChange = {
                val min = it.toShortOrNull() ?: 0
                onChanged(block.copy(duration = Minutes(min)))
            },
            label = { Text("Dauer (min)") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = block.amount.toString(),
            onValueChange = {
                val amount = it.toDoubleOrNull() ?: 0.0
                onChanged(block.copy(amount = amount))
            },
            label = { Text("Wert") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen")
            }
        }
    }
}

@Composable
fun TargetBlockItem(
    block: TargetBlock,
    onChanged: (TargetBlock) -> Unit,
    onDelete: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = block.duration.value.toString(),
            onValueChange = {
                val min = it.toShortOrNull() ?: 0
                onChanged(block.copy(duration = Minutes(min)))
            },
            label = { Text("Dauer") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = block.lowTarget.mgdl.toString(),
            onValueChange = {
                val valMgdl = it.toShortOrNull() ?: 0
                onChanged(block.copy(lowTarget = BgValue(valMgdl)))
            },
            label = { Text("Min") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            value = block.highTarget.mgdl.toString(),
            onValueChange = {
                val valMgdl = it.toShortOrNull() ?: 0
                onChanged(block.copy(highTarget = BgValue(valMgdl)))
            },
            label = { Text("Max") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Löschen")
            }
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
@Composable
fun ProfileDetailEditorPreview() {
    AppTheme {
        ProfileDetailEditor(
            profile = Profile(name = "Normal", therapyData = TherapyData(basalBlocks = emptyList(), isfBlocks = emptyList(), icBlocks = emptyList(), targetBlocks = emptyList())),
            onSave = {},
            onCancel = {}
        )
    }
}
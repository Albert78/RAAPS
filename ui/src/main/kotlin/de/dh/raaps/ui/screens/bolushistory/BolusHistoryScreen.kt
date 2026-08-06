package de.dh.raaps.ui.screens.bolushistory

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.BOLUS_MAX
import de.dh.raaps.common.model.BOLUS_MIN
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.common.ui.DefaultSteppingStrategy
import de.dh.raaps.common.ui.ValueDisplayStrategy
import de.dh.raaps.common.ui.composables.EditableValueStepper
import de.dh.raaps.common.ui.composables.contentScrollIndicator
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R
import de.dh.raaps.common.R as CommonR
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BolusHistoryScreen(
    viewModel: BolusHistoryViewModel,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    BolusHistoryContent(
        uiState = uiState,
        onAddManualBolus = { amount, type -> viewModel.addManualBolus(amount, type) },
        onUpdateManualBolus = { app, amount, type -> viewModel.updateManualBolus(app, amount, type) },
        onDeleteBolus = { viewModel.deleteBolus(it) },
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BolusHistoryContent(
    uiState: BolusHistoryUiState,
    onAddManualBolus: (Double, InsulinType) -> Unit,
    onUpdateManualBolus: (InsulinApplication, Double, InsulinType) -> Unit,
    onDeleteBolus: (InsulinApplication) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingBolus by remember { mutableStateOf<InsulinApplication?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.bolus_history_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = CommonR.string.cd_navigate_up)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.cd_add_meal) // reuse for now or add new cd
                )
            }
        }
    ) { innerPadding ->
        if (showAddDialog) {
            AddManualBolusDialog(
                availableInsulinTypes = uiState.insulinTypes,
                defaultInsulinType = uiState.defaultInsulinType,
                onDismiss = { showAddDialog = false },
                onConfirm = { amount, type ->
                    onAddManualBolus(amount, type)
                    showAddDialog = false
                }
            )
        }

        editingBolus?.let { bolus ->
            AddManualBolusDialog(
                availableInsulinTypes = uiState.insulinTypes,
                defaultInsulinType = bolus.insulinType,
                initialAmount = bolus.amount,
                isEditMode = true,
                onDismiss = { editingBolus = null },
                onConfirm = { amount, type ->
                    onUpdateManualBolus(bolus, amount, type)
                    editingBolus = null
                }
            )
        }

        if (uiState.bolusEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.bolus_history_empty_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .contentScrollIndicator(listState)
            ) {
                items(uiState.bolusEntries) { entry ->
                    val isEditable = remember(entry.timestamp, entry.origin, uiState.editThresholdHours) {
                        (entry.origin == InsulinOrigin.Manual) && (entry.timestamp >= Timestamp.now().minusHours(uiState.editThresholdHours))
                    }

                    BolusItem(
                        entry = entry,
                        isEditable = isEditable,
                        onEditClick = { editingBolus = entry },
                        onDeleteClick = { onDeleteBolus(entry) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun BolusItem(
    entry: InsulinApplication,
    isEditable: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault()) }

    val timeString = remember(entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp.ms)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
    }
    val dateString = remember(entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp.ms)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)
    }

    val originString = when (entry.origin) {
        InsulinOrigin.Pump -> stringResource(id = R.string.bolus_origin_pump)
        InsulinOrigin.Manual -> stringResource(id = R.string.bolus_origin_manual)
    }

    ListItem(
        modifier = if (isEditable) Modifier.clickable(onClick = onEditClick) else Modifier,
        headlineContent = {
            Text(text = stringResource(id = R.string.insulin_unit_label_format, entry.amount))
        },
        supportingContent = {
            Row {
                Text(text = originString)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.insulinType.name,
                    color = Color.DarkGray
                )
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
                if (isEditable) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.cd_edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.cd_delete_profile),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualBolusDialog(
    availableInsulinTypes: List<InsulinType>,
    defaultInsulinType: InsulinType?,
    initialAmount: Double = 0.0,
    isEditMode: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Double, InsulinType) -> Unit
) {
    var amount by remember { mutableDoubleStateOf(initialAmount) }
    var selectedInsulinType by remember { mutableStateOf(defaultInsulinType ?: availableInsulinTypes.firstOrNull()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditMode) stringResource(id = R.string.bolus_history_edit_manual_title)
                else stringResource(id = R.string.bolus_history_add_manual_title)
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.bolus_history_add_manual_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (availableInsulinTypes.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedInsulinType?.name ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(id = R.string.insulin_profile_editor_insulin_type_label)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableInsulinTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.name) },
                                    onClick = {
                                        selectedInsulinType = type
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EditableValueStepper(
                        currentValue = amount,
                        onValueChange = { amount = it },
                        minValue = BOLUS_MIN,
                        maxValue = BOLUS_MAX,
                        steppingStrategy = DefaultSteppingStrategy(0.5),
                        displayStrategy = object : ValueDisplayStrategy {
                            override fun format(value: Double): String = String.format(Locale.getDefault(), "%.2f", value)
                            override fun color(value: Double): Color =
                                Color.Unspecified
                        },
                        suffix = " U"
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val type = selectedInsulinType
                    if (amount > 0 && type != null) {
                        onConfirm(amount, type)
                    }
                },
                enabled = amount > 0 && selectedInsulinType != null
            ) {
                Text(text = stringResource(id = CommonR.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = CommonR.string.cd_cancel))
            }
        }
    )
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun BolusHistoryPreview() {
    AppTheme {
        BolusHistoryContent(
            uiState = BolusHistoryUiState(),
            onAddManualBolus = { _, _ -> },
            onUpdateManualBolus = { _, _, _ -> },
            onDeleteBolus = {},
            onNavigateUp = {}
        )
    }
}

@Preview(showBackground = true, name = "With Data")
@Composable
fun BolusHistoryWithDataPreview() {
    val sampleInsulinType = InsulinType(name = "Fiasp", peak = Minutes(50.toShort()), dia = Minutes(300.toShort()))
    val sampleEntries = listOf(
        InsulinApplication(id = 1, timestamp = Timestamp.now().minusHours(8), amount = 5.0, insulinType = sampleInsulinType, origin = InsulinOrigin.Pump),
        InsulinApplication(id = 2, timestamp = Timestamp.now().minusHours(5), amount = 2.5, insulinType = sampleInsulinType, origin = InsulinOrigin.Manual),
        InsulinApplication(id = 3, timestamp = Timestamp.now().minusHours(1), amount = 3.0, insulinType = sampleInsulinType, origin = InsulinOrigin.Manual)
    )

    AppTheme {
        BolusHistoryContent(
            uiState = BolusHistoryUiState(bolusEntries = sampleEntries),
            onAddManualBolus = { _, _ -> },
            onUpdateManualBolus = { _, _, _ -> },
            onDeleteBolus = {},
            onNavigateUp = {}
        )
    }
}

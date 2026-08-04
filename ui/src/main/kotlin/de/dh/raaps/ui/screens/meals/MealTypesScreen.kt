package de.dh.raaps.ui.screens.meals

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.ui.composables.screenTitle
import de.dh.raaps.common.ui.theme.AppTheme
import de.dh.raaps.ui.R

@Composable
fun MealTypesScreen(
    viewModel: MealTypesViewModel,
    onNavigateToEditor: (String?) -> Unit,
    onNavigateUp: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    MealTypesContent(
        uiState = uiState,
        onDeleteMealType = { viewModel.deleteMealType(it) },
        onAddMealType = { onNavigateToEditor(null) },
        onEditMealType = { onNavigateToEditor(it.id) },
        onNavigateUp = onNavigateUp
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealTypesContent(
    uiState: MealTypesUiState,
    onDeleteMealType: (MealType) -> Unit,
    onAddMealType: () -> Unit,
    onEditMealType: (MealType) -> Unit,
    onNavigateUp: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.meal_types_screen_title)),
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
            FloatingActionButton(onClick = onAddMealType) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.cd_add_meal_type)
                )
            }
        }
    ) { innerPadding ->
        if (uiState.mealTypes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(text = "Keine Mahlzeitentypen definiert", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(uiState.mealTypes) { mealType ->
                    MealTypeItem(
                        mealType = mealType,
                        onDelete = { onDeleteMealType(mealType) },
                        onClick = { onEditMealType(mealType) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun MealTypeItem(mealType: MealType, onDelete: () -> Unit, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(mealType.name) },
        supportingContent = { Text("${mealType.cat.value} Min.") },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(imageVector = Icons.Default.Delete, contentDescription = "Löschen")
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun MealTypesPreview() {
    AppTheme {
        MealTypesContent(
            uiState = MealTypesUiState(),
            onDeleteMealType = {},
            onAddMealType = {},
            onEditMealType = {},
            onNavigateUp = {}
        )
    }
}
package de.dh.raaps.ui.screens.meals

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.dh.raaps.common.model.CarbCurveComponentData
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.ui.R
import de.dh.raaps.ui.common.composables.contentScrollIndicator
import de.dh.raaps.ui.common.composables.screenTitle
import de.dh.raaps.ui.common.icons.Icon_Menu_Meal_Types
import de.dh.raaps.ui.common.theme.AppTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import de.dh.raaps.common.R as CommonR

@Composable
fun MealsScreen(
    viewModel: MealsViewModel,
    onNavigateToMealTypes: () -> Unit,
    onNavigateToMealBolus: () -> Unit,
    onEditMeal: (MealEntry) -> Unit,
    onDeleteMeal: (MealEntry) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    MealsContent(
        uiState = uiState,
        onNavigateToMealTypes = onNavigateToMealTypes,
        onNavigateToMealBolus = onNavigateToMealBolus,
        onEditMeal = onEditMeal,
        onDeleteMeal = onDeleteMeal,
        onNavigateUp = onNavigateUp,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsContent(
    uiState: MealsUiState,
    onNavigateToMealTypes: () -> Unit,
    onNavigateToMealBolus: () -> Unit,
    onEditMeal: (MealEntry) -> Unit,
    onDeleteMeal: (MealEntry) -> Unit,
    onNavigateUp: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = screenTitle(stringResource(id = R.string.meals_screen_title)),
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = CommonR.string.cd_navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = !showMenu }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(id = CommonR.string.cd_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(id = R.string.menu_meal_types_label)) },
                            leadingIcon = { Icon(imageVector = Icon_Menu_Meal_Types, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onNavigateToMealTypes()
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToMealBolus) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.cd_add_meal)
                )
            }
        }
    ) { innerPadding ->
        if (uiState.meals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(id = R.string.meals_empty_list),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                items(uiState.meals.sortedByDescending { it.timestamp }) { meal ->
                    val isEditable = remember(meal.timestamp, uiState.editThresholdHours) {
                        meal.timestamp >= Timestamp.now().minusHours(uiState.editThresholdHours)
                    }

                    MealItem(
                        meal = meal,
                        isEditable = isEditable,
                        onEditClick = { onEditMeal(meal) },
                        onDeleteClick = { onDeleteMeal(meal) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun MealItem(
    meal: MealEntry,
    isEditable: Boolean,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val timeFormatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val timeString = remember(meal.timestamp) {
        Instant.ofEpochMilli(meal.timestamp.ms)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(timeFormatter)
    }

    ListItem(
        modifier = if (isEditable) Modifier.clickable(onClick = onEditClick) else Modifier,
        headlineContent = {
            Text(text = stringResource(id = R.string.meal_entry_grams_format, meal.carbGrams))
        },
        supportingContent = {
            Text(text = meal.mealType.name)
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isEditable) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onEditClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(id = R.string.cd_edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(id = R.string.cd_delete_profile),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    )
}

@Preview(showBackground = true, name = "Light Mode")
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, name = "Dark Mode")
@Composable
fun MealsPreview() {
    AppTheme {
        MealsContent(
            uiState = MealsUiState(),
            onNavigateToMealTypes = {},
            onNavigateToMealBolus = {},
            onEditMeal = {},
            onDeleteMeal = {},
            onNavigateUp = {}
        )
    }
}

@Preview(showBackground = true, name = "With Data")
@Composable
fun MealsWithDataPreview() {
    val sampleMealType = MealType(
        name = "Normal",
        components = listOf(CarbCurveComponentData(100, Minutes(45.toShort()))),
        cat = Minutes(180.toShort())
    )
    val sampleMeals = listOf(
        MealEntry(id = 1, timestamp = Timestamp.now().minusHours(8), carbGrams = 45.0, mealType = sampleMealType),
        MealEntry(id = 2, timestamp = Timestamp.now().minusHours(5), carbGrams = 15.0, mealType = sampleMealType),
        MealEntry(id = 3, timestamp = Timestamp.now().minusHours(1), carbGrams = 60.0, mealType = sampleMealType)
    )

    AppTheme {
        MealsContent(
            uiState = MealsUiState(meals = sampleMeals),
            onNavigateToMealTypes = {},
            onNavigateToMealBolus = {},
            onEditMeal = {},
            onDeleteMeal = {},
            onNavigateUp = {}
        )
    }
}

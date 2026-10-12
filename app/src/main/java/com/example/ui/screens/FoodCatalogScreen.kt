package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.calculation.PortionEngine
import com.example.calculation.PortionUnit
import com.example.data.model.FoodItem
import com.example.viewmodel.MealMindViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodCatalogScreen(
    viewModel: MealMindViewModel,
    modifier: Modifier = Modifier
) {
    val foods by viewModel.allFoods.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var foodToLog by remember { mutableStateOf<FoodItem?>(null) }
    var showAddCustomFoodDialog by remember { mutableStateOf(false) }

    val categories = listOf(
        "All",
        "Favorites",
        "Rice & Grains",
        "Meat & Poultry",
        "Fish & Seafood",
        "Bangladeshi Specialties",
        "Dal & Lentils",
        "Vegetables",
        "Fruits",
        "Eggs & Dairy",
        "Snacks & Fast Food",
        "Bakery & Sweets",
        "Beverages & Drinks",
        "Asian Special"
    )

    val filteredFoods = foods.filter { food ->
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Favorites" -> food.isFavorite
            else -> food.category.equals(selectedCategory, ignoreCase = true)
        }

        val matchesQuery = if (searchQuery.isBlank()) true
        else food.foodName.contains(searchQuery, ignoreCase = true) ||
                food.alternativeNames.contains(searchQuery, ignoreCase = true) ||
                food.cuisine.contains(searchQuery, ignoreCase = true)

        matchesCategory && matchesQuery
    }

    Box(modifier = modifier.fillMaxSize().testTag("food_catalog_screen")) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search 500+ items (e.g. Biryani, Ilish, Rice)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_search_bar")
            )

            // Category Filter Pills
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { viewModel.setSelectedCategory(cat) },
                        label = { Text(cat) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Results count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${filteredFoods.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Reference: 100g per item",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Foods List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(filteredFoods) { food ->
                    FoodCatalogItemCard(
                        food = food,
                        onFavoriteToggle = { viewModel.toggleFoodFavorite(food) },
                        onClick = { foodToLog = food },
                        onDeleteCustom = if (food.isCustom) {
                            { viewModel.deleteCustomFood(food) }
                        } else null
                    )
                }
            }
        }

        // Add Custom Food FAB
        FloatingActionButton(
            onClick = { showAddCustomFoodDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("add_custom_food_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Food")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Food", fontWeight = FontWeight.Bold)
            }
        }
    }

    // Modal to calculate portion and log directly
    if (foodToLog != null) {
        PortionCalculatorDialog(
            food = foodToLog!!,
            onDismiss = { foodToLog = null },
            onLog = { mealType, unit, quantity ->
                viewModel.logMealDirect(foodToLog!!, mealType, unit, quantity)
                foodToLog = null
            }
        )
    }

    // Modal to create custom food item
    if (showAddCustomFoodDialog) {
        AddCustomFoodDialog(
            onDismiss = { showAddCustomFoodDialog = false },
            onSave = { customFood ->
                viewModel.addCustomFood(customFood)
                showAddCustomFoodDialog = false
            }
        )
    }
}

@Composable
fun FoodCatalogItemCard(
    food: FoodItem,
    onFavoriteToggle: () -> Unit,
    onClick: () -> Unit,
    onDeleteCustom: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("food_item_${food.foodId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = food.foodName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (food.isCustom) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (food.alternativeNames.isNotBlank()) {
                        Text(
                            text = food.alternativeNames,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = food.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = food.cuisine,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onFavoriteToggle) {
                        Icon(
                            imageVector = if (food.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (food.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (onDeleteCustom != null) {
                        IconButton(onClick = onDeleteCustom) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Custom Food",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Nutrition values per 100g
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${food.caloriesPer100g.toInt()} kcal / 100g",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("P: ${food.proteinGPer100g}g", style = MaterialTheme.typography.labelSmall)
                    Text("C: ${food.carbsGPer100g}g", style = MaterialTheme.typography.labelSmall)
                    Text("F: ${food.fatGPer100g}g", style = MaterialTheme.typography.labelSmall)
                    Text("Fiber: ${food.fiberGPer100g}g", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortionCalculatorDialog(
    food: FoodItem,
    onDismiss: () -> Unit,
    onLog: (mealType: String, unit: PortionUnit, quantity: Double) -> Unit
) {
    var mealType by remember { mutableStateOf("Lunch") }
    var unit by remember { mutableStateOf(PortionUnit.GRAMS) }
    var quantityText by remember { mutableStateOf(food.defaultPortionGrams.toInt().toString()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val qty = quantityText.toDoubleOrNull() ?: 100.0
    val grams = PortionEngine.calculateGrams(food, unit, qty)
    val calculated = PortionEngine.calculateNutrition(food, grams)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Log ${food.foodName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Reference: ${food.caloriesPer100g.toInt()} kcal per 100g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Meal Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { type ->
                        FilterChip(
                            selected = mealType == type,
                            onClick = { mealType = type },
                            label = { Text(type, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Quantity and Unit Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Portion") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it },
                        modifier = Modifier.weight(1.2f)
                    ) {
                        OutlinedTextField(
                            value = unit.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.menuAnchor(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            PortionUnit.values().forEach { u ->
                                DropdownMenuItem(
                                    text = { Text(u.displayName) },
                                    onClick = {
                                        unit = u
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Live calculated result card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Calculated Weight:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "${calculated.portionGrams.toInt()}g",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Total Energy:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${calculated.calories.toInt()} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Protein: ${calculated.proteinG}g", style = MaterialTheme.typography.labelSmall)
                            Text("Carbs: ${calculated.carbsG}g", style = MaterialTheme.typography.labelSmall)
                            Text("Fat: ${calculated.fatG}g", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onLog(mealType, unit, qty) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Log to $mealType")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddCustomFoodDialog(
    onDismiss: () -> Unit,
    onSave: (FoodItem) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var altNames by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Rice & Grains") }
    var cuisine by remember { mutableStateOf("Bangladeshi") }
    var calories by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    var fiber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Custom Food to Database") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Enter standard nutritional values per 100 grams.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Food Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = altNames,
                        onValueChange = { altNames = it },
                        label = { Text("Alternative / Local Names") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = cuisine,
                            onValueChange = { cuisine = it },
                            label = { Text("Cuisine") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = calories,
                            onValueChange = { calories = it },
                            label = { Text("Calories (kcal) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = protein,
                            onValueChange = { protein = it },
                            label = { Text("Protein (g) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = carbs,
                            onValueChange = { carbs = it },
                            label = { Text("Carbs (g) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = fat,
                            onValueChange = { fat = it },
                            label = { Text("Fat (g) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = fiber,
                        onValueChange = { fiber = it },
                        label = { Text("Fiber (g)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && calories.isNotBlank()) {
                        val food = FoodItem(
                            foodName = name.trim(),
                            alternativeNames = altNames.trim(),
                            category = category.trim().ifBlank { "Custom" },
                            cuisine = cuisine.trim().ifBlank { "Common" },
                            caloriesPer100g = calories.toDoubleOrNull() ?: 100.0,
                            proteinGPer100g = protein.toDoubleOrNull() ?: 5.0,
                            carbsGPer100g = carbs.toDoubleOrNull() ?: 15.0,
                            fatGPer100g = fat.toDoubleOrNull() ?: 2.0,
                            fiberGPer100g = fiber.toDoubleOrNull() ?: 1.0,
                            isCustom = true
                        )
                        onSave(food)
                    }
                },
                enabled = name.isNotBlank() && calories.isNotBlank()
            ) {
                Text("Save to Database")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

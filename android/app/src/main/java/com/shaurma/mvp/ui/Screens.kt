package com.shaurma.mvp.ui

import android.view.ViewGroup
import android.widget.EditText
import android.widget.NumberPicker
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shaurma.mvp.data.local.CartItemEntity
import com.shaurma.mvp.data.local.MenuItemEntity
import com.shaurma.mvp.data.local.OrderEntity
import com.shaurma.mvp.data.repository.totalPrice
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.launch

private val DateTimeFormatterRu: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

@Composable
fun StartupScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(message)
    }
}

@Composable
fun BlockedScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Доступ заблокирован", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Обратитесь к администратору, чтобы разблокировать устройство.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onCategoryClick: (Int) -> Unit,
    onMissingCategoryClick: (String) -> Unit,
    onCartClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val menuButtons = remember {
        listOf("ШАУРМА", "ГРИЛЬ НА УГЛЯХ", "КАРТОШКА & СНЕКИ", "НАПИТКИ", "МОИ ЗАКАЗЫ")
    }
    val sharedFontSize = remember { mutableStateOf(28.sp) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ShaurmaBlack),
    ) constraints@ {
        val screenWidth = this@constraints.maxWidth
        val screenHeight = this@constraints.maxHeight
        val topZoneHeight = screenHeight * 0.15f
        val bottomZoneHeight = screenHeight * 0.85f
        val buttonWidth = screenWidth * 0.8f
        val buttonHeight = bottomZoneHeight * 0.08f
        val buttonGap = bottomZoneHeight * 0.03f

        Column(modifier = Modifier.fillMaxSize()) {
            ShaurmaTopZone(
                topZoneHeight = topZoneHeight,
                leftIconName = "ic_profile",
                leftContentDescription = "Личный кабинет",
                onLeftClick = onProfileClick,
                rightIconName = "ic_cart",
                rightContentDescription = "Корзина",
                onRightClick = onCartClick,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomZoneHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(buttonGap),
                ) {
                    menuButtons.forEach { title ->
                        ShaurmaOutlinedMenuButton(
                            text = title,
                            width = buttonWidth,
                            height = buttonHeight,
                            fontSize = sharedFontSize.value,
                            onFontOverflow = { sharedFontSize.value = (sharedFontSize.value.value * 0.9f).sp },
                            onClick = {
                                if (title == "МОИ ЗАКАЗЫ") {
                                    onOrdersClick()
                                } else {
                                    val category = categories.firstOrNull { it.name.normalizedMenuName() == title.normalizedMenuName() }
                                    if (category == null) onMissingCategoryClick(title) else onCategoryClick(category.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MissingCategoryScreen(
    title: String,
    onHomeClick: () -> Unit,
    onCartClick: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ShaurmaBlack),
    ) constraints@ {
        val screenWidth = this@constraints.maxWidth
        val screenHeight = this@constraints.maxHeight
        val topZoneHeight = screenHeight * 0.15f
        val bottomZoneHeight = screenHeight * 0.85f
        val fontFamily = rememberShaurmaFontFamily()

        Column(modifier = Modifier.fillMaxSize()) {
            ShaurmaTopZone(
                topZoneHeight = topZoneHeight,
                leftIconName = "home",
                leftContentDescription = "Главное меню",
                onLeftClick = onHomeClick,
                rightIconName = "ic_cart",
                rightContentDescription = "Корзина",
                onRightClick = onCartClick,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomZoneHeight)
                    .padding(horizontal = screenWidth * 0.1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title.uppercase(),
                        color = ShaurmaWhite,
                        fontFamily = fontFamily,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(bottomZoneHeight * 0.03f))
                    Text(
                        text = "Раздел скоро появится",
                        color = ShaurmaTextGray,
                        fontFamily = fontFamily,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onProductClick: (Int) -> Unit,
    viewModel: CategoryViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Категория") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ProductListCard(item = item, onClick = { onProductClick(item.id) })
            }
        }
    }
}

@Composable
private fun ProductListCard(item: MenuItemEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            item.description?.let { Text(it) }
            Spacer(Modifier.height(8.dp))
            Text("${formatMoney(item.price)} · ${item.weight} г · ${item.cookingTime} мин")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: ProductViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val showOptions = remember { mutableStateOf(false) }
    val item = state.item

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Карточка товара") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        if (item == null) {
            StartupScreen("Загружаем товар...")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(item.name, style = MaterialTheme.typography.headlineSmall)
                item.description?.let { Text(it) }
                Text("${item.weight} г · готовится ${item.cookingTime} мин")
                Text("Цена: ${formatMoney(productPrice(state))}", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = { showOptions.value = true }) {
                    Text("Дополнительно / убрать ингредиенты")
                }
                Button(
                    enabled = !state.isAdding,
                    onClick = {
                        scope.launch {
                            if (viewModel.addToCart()) onAdded()
                        }
                    },
                ) {
                    Text(if (state.isAdding) "Добавляем..." else "Добавить в корзину")
                }
            }
        }
    }

    if (showOptions.value) {
        ProductOptionsDialog(
            state = state,
            onToggleAddition = viewModel::toggleAddition,
            onToggleRemoval = viewModel::toggleRemoval,
            onDismiss = { showOptions.value = false },
        )
    }
}

@Composable
private fun ProductOptionsDialog(
    state: ProductUiState,
    onToggleAddition: (Int) -> Unit,
    onToggleRemoval: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка товара") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Text("Добавить", fontWeight = FontWeight.Bold) }
                items(state.additions, key = { it.id }) { addition ->
                    SelectableRow(
                        checked = addition.id in state.selectedAdditionIds,
                        label = "${addition.name} +${formatMoney(addition.price)}",
                        onClick = { onToggleAddition(addition.id) },
                    )
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Не класть", fontWeight = FontWeight.Bold)
                }
                items(state.removals, key = { it.id }) { removal ->
                    SelectableRow(
                        checked = removal.id in state.selectedRemovalIds,
                        label = removal.name,
                        onClick = { onToggleRemoval(removal.id) },
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } },
    )
}

@Composable
private fun SelectableRow(checked: Boolean, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Checkbox(checked = checked, onCheckedChange = { onClick() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onHomeClick: () -> Unit,
    onProfileClick: () -> Unit,
    onEditItem: (Int) -> Unit,
    onOrderCreated: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val deleteCandidate = remember { mutableStateOf<CartItemEntity?>(null) }
    val editCandidate = remember { mutableStateOf<CartItemEntity?>(null) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ShaurmaBlack),
    ) constraints@ {
        val screenWidth = this@constraints.maxWidth
        val screenHeight = this@constraints.maxHeight
        val topZoneHeight = screenHeight * 0.15f
        val bottomZoneHeight = screenHeight * 0.85f
        val contentWidth = screenWidth * 0.9f
        val narrowButtonWidth = contentWidth * 0.47f
        val fullButtonWidth = screenWidth * 0.8f
        val buttonHeight = bottomZoneHeight * 0.075f
        val itemGap = bottomZoneHeight * 0.02f
        val fontFamily = rememberShaurmaFontFamily()
        val sharedButtonFontSize = remember { mutableStateOf(22.sp) }

        Column(modifier = Modifier.fillMaxSize()) {
            ShaurmaTopZone(
                topZoneHeight = topZoneHeight,
                leftIconName = "home",
                leftContentDescription = "Главное меню",
                onLeftClick = onHomeClick,
                rightIconName = "ic_profile",
                rightContentDescription = "Личный кабинет",
                onRightClick = onProfileClick,
            )

            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomZoneHeight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Корзина пустая",
                        color = ShaurmaWhite,
                        fontFamily = fontFamily,
                        fontSize = 26.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomZoneHeight)
                        .padding(horizontal = screenWidth * 0.05f),
                    verticalArrangement = Arrangement.spacedBy(itemGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(state.items, key = { it.id }) { item ->
                        CartItemCard(
                            item = item,
                            width = contentWidth,
                            editButtonWidth = narrowButtonWidth,
                            buttonHeight = buttonHeight,
                            buttonFontSize = sharedButtonFontSize.value,
                            onFontOverflow = { sharedButtonFontSize.value = (sharedButtonFontSize.value.value * 0.9f).sp },
                            onEdit = { editCandidate.value = item },
                            onDelete = { deleteCandidate.value = item },
                        )
                    }
                    item {
                        Text(
                            text = "Итого: ${formatMoney(state.totalPrice)}",
                            color = ShaurmaWhite,
                            fontFamily = fontFamily,
                            fontSize = 22.sp,
                            modifier = Modifier.width(contentWidth),
                        )
                    }
                    item {
                        CartCommentField(
                            value = state.generalComment,
                            width = contentWidth,
                            height = bottomZoneHeight * 0.12f,
                            onValueChange = viewModel::updateComment,
                        )
                    }
                    item {
                        CartDateTimeSelection(
                            state = state,
                            viewModel = viewModel,
                            width = contentWidth,
                            wheelHeight = bottomZoneHeight * 0.11f,
                        )
                    }
                    if (state.hasDifferentCookingTimes) {
                        item {
                            Text(
                                "Ваш заказ будет готов через ${state.items.maxOf { it.cookingTime }} минут. Если вы хотите получить часть заказа раньше, оформите два заказа отдельно.",
                                color = ShaurmaTextGray,
                                fontFamily = fontFamily,
                                fontSize = 14.sp,
                                modifier = Modifier.width(contentWidth),
                            )
                        }
                    }
                    state.error?.let {
                        item {
                            Text(
                                text = it,
                                color = Color.Red,
                                fontFamily = fontFamily,
                                fontSize = 14.sp,
                                modifier = Modifier.width(contentWidth),
                            )
                        }
                    }
                    item {
                        ShaurmaOutlinedMenuButton(
                            text = "ДОБАВИТЬ К ЗАКАЗУ",
                            width = fullButtonWidth,
                            height = buttonHeight,
                            fontSize = sharedButtonFontSize.value,
                            onFontOverflow = { sharedButtonFontSize.value = (sharedButtonFontSize.value.value * 0.9f).sp },
                            onClick = onHomeClick,
                        )
                    }
                    item {
                        ShaurmaOutlinedMenuButton(
                            text = if (state.isSubmitting) "ОТПРАВЛЯЕМ..." else "ОПЛАТИТЬ",
                            width = fullButtonWidth,
                            height = buttonHeight,
                            fontSize = sharedButtonFontSize.value,
                            onFontOverflow = { sharedButtonFontSize.value = (sharedButtonFontSize.value.value * 0.9f).sp },
                            enabled = state.items.isNotEmpty() && state.isTimeValid && !state.isSubmitting,
                            onClick = {
                                scope.launch {
                                    if (viewModel.submit()) onOrderCreated()
                                }
                            },
                        )
                    }
                    item { Spacer(Modifier.height(itemGap)) }
                }
            }
        }
    }

    deleteCandidate.value?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate.value = null },
            title = { Text("Удалить позицию?") },
            text = { Text(item.name) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        deleteCandidate.value = null
                    },
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate.value = null }) { Text("Отмена") } },
        )
    }

    editCandidate.value?.let { item ->
        AlertDialog(
            onDismissRequest = { editCandidate.value = null },
            title = { Text("Изменить позицию?") },
            text = { Text("Текущая позиция будет удалена, затем можно добавить обновлённую.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        editCandidate.value = null
                        onEditItem(item.menuItemId)
                    },
                ) { Text("Изменить") }
            },
            dismissButton = { TextButton(onClick = { editCandidate.value = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun CartItemCard(
    item: CartItemEntity,
    width: Dp,
    editButtonWidth: Dp,
    buttonHeight: Dp,
    buttonFontSize: androidx.compose.ui.unit.TextUnit,
    onFontOverflow: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val fontFamily = rememberShaurmaFontFamily()
    Column(
        modifier = Modifier
            .width(width)
            .border(2.dp, ShaurmaWhite, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(item.name, color = ShaurmaWhite, fontFamily = fontFamily, fontSize = 20.sp)
        if (item.additionNames.isNotBlank()) {
            Text("Добавки: ${item.additionNames}", color = ShaurmaWhite, fontFamily = fontFamily, fontSize = 15.sp)
        }
        if (item.removalNames.isNotBlank()) {
            Text("Не класть: ${item.removalNames}", color = ShaurmaWhite, fontFamily = fontFamily, fontSize = 15.sp)
        }
        Text(
            text = "Вес: ${item.weight} г · Цена: ${formatMoney(item.totalPrice)}",
            color = ShaurmaWhite,
            fontFamily = fontFamily,
            fontSize = 15.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ShaurmaOutlinedMenuButton(
                text = "РЕДАКТИРОВАТЬ",
                width = editButtonWidth,
                height = buttonHeight,
                fontSize = buttonFontSize,
                onFontOverflow = onFontOverflow,
                onClick = onEdit,
            )
            ShaurmaOutlinedMenuButton(
                text = "УДАЛИТЬ",
                width = editButtonWidth,
                height = buttonHeight,
                fontSize = buttonFontSize,
                onFontOverflow = onFontOverflow,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun CartCommentField(
    value: String,
    width: Dp,
    height: Dp,
    onValueChange: (String) -> Unit,
) {
    val fontFamily = rememberShaurmaFontFamily()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .width(width)
            .height(height),
        textStyle = TextStyle(
            color = ShaurmaWhite,
            fontFamily = fontFamily,
            fontSize = 16.sp,
        ),
        placeholder = {
            Text(
                text = "Комментарий к заказу",
                color = ShaurmaPlaceholderGray,
                fontFamily = fontFamily,
                fontSize = 16.sp,
            )
        },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ShaurmaWhite,
            unfocusedTextColor = ShaurmaWhite,
            cursorColor = ShaurmaWhite,
            focusedBorderColor = ShaurmaWhite,
            unfocusedBorderColor = ShaurmaWhite,
            focusedContainerColor = ShaurmaBlack,
            unfocusedContainerColor = ShaurmaBlack,
            disabledContainerColor = ShaurmaBlack,
        ),
        minLines = 2,
        maxLines = 3,
    )
}

@Composable
private fun CartDateTimeSelection(
    state: CartUiState,
    viewModel: CartViewModel,
    width: Dp,
    wheelHeight: Dp,
) {
    val fontFamily = rememberShaurmaFontFamily()
    val selected = state.requestedTimeMillis.toMoscowDateTime()
    val minDateTime = state.minTimeMillis.toMoscowDateTime()
    val maxDateTime = state.maxTimeMillis.toMoscowDateTime()
    val allowedDates = remember(state.minTimeMillis, state.maxTimeMillis) {
        val days = ChronoUnit.DAYS.between(minDateTime.toLocalDate(), maxDateTime.toLocalDate()).toInt()
        (0..days).map { minDateTime.toLocalDate().plusDays(it.toLong()) }
    }
    val years = allowedDates.map { it.year }.distinct()
    val selectedYear = selected.year.takeIf { it in years } ?: years.first()
    val months = allowedDates.filter { it.year == selectedYear }.map { it.monthValue }.distinct()
    val selectedMonth = selected.monthValue.takeIf { it in months } ?: months.first()
    val days = allowedDates
        .filter { it.year == selectedYear && it.monthValue == selectedMonth }
        .map { it.dayOfMonth }
        .distinct()
    val selectedDay = selected.dayOfMonth.takeIf { it in days } ?: days.first()
    val selectedDate = LocalDate.of(selectedYear, selectedMonth, selectedDay)
    val hourRange = allowedHourRange(selectedDate, minDateTime, maxDateTime)
    val selectedHour = selected.hour.takeIf { it in hourRange } ?: hourRange.first
    val minuteRange = allowedMinuteRange(selectedDate, selectedHour, minDateTime, maxDateTime)
    val selectedMinute = selected.minute.takeIf { it in minuteRange } ?: minuteRange.first

    Column(
        modifier = Modifier.width(width),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Выберите дату и время когда должен быть готов заказ",
            color = ShaurmaWhite,
            fontFamily = fontFamily,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShaurmaWheelPicker(
                values = days.map { it.toString().padStart(2, '0') },
                selectedIndex = days.indexOf(selectedDay).coerceAtLeast(0),
                width = width * 0.25f,
                height = wheelHeight,
                onSelectedIndex = { index ->
                    val day = days[index]
                    viewModel.setRequestedDate(selectedYear, selectedMonth - 1, day)
                },
            )
            ShaurmaWheelPicker(
                values = months.map { monthNameRu(it) },
                selectedIndex = months.indexOf(selectedMonth).coerceAtLeast(0),
                width = width * 0.35f,
                height = wheelHeight,
                onSelectedIndex = { index ->
                    val month = months[index]
                    val validDays = allowedDates.filter { it.year == selectedYear && it.monthValue == month }.map { it.dayOfMonth }
                    val minDay = validDays.minOrNull() ?: selectedDay
                    val maxDay = validDays.maxOrNull() ?: selectedDay
                    viewModel.setRequestedDate(selectedYear, month - 1, selectedDay.coerceIn(minDay, maxDay))
                },
            )
            ShaurmaWheelPicker(
                values = years.map { it.toString() },
                selectedIndex = years.indexOf(selectedYear).coerceAtLeast(0),
                width = width * 0.25f,
                height = wheelHeight,
                onSelectedIndex = { index ->
                    val year = years[index]
                    val validDates = allowedDates.filter { it.year == year }
                    val target = validDates.firstOrNull { it.monthValue == selectedMonth && it.dayOfMonth == selectedDay }
                        ?: validDates.first()
                    viewModel.setRequestedDate(target.year, target.monthValue - 1, target.dayOfMonth)
                },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShaurmaWheelPicker(
                values = hourRange.toList().map { it.toString().padStart(2, '0') },
                selectedIndex = (selectedHour - hourRange.first).coerceAtLeast(0),
                width = width * 0.28f,
                height = wheelHeight,
                onSelectedIndex = { index ->
                    val hour = hourRange.first + index
                    val minutes = allowedMinuteRange(selectedDate, hour, minDateTime, maxDateTime)
                    viewModel.setRequestedTime(hour, selectedMinute.coerceIn(minutes.first, minutes.last))
                },
            )
            Text(
                text = ":",
                color = ShaurmaWhite,
                fontFamily = fontFamily,
                fontSize = 28.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(width * 0.08f),
            )
            ShaurmaWheelPicker(
                values = minuteRange.toList().map { it.toString().padStart(2, '0') },
                selectedIndex = (selectedMinute - minuteRange.first).coerceAtLeast(0),
                width = width * 0.28f,
                height = wheelHeight,
                onSelectedIndex = { index -> viewModel.setRequestedTime(selectedHour, minuteRange.first + index) },
            )
        }
        Text(
            text = "Минимум: ${formatMillis(state.minTimeMillis)}",
            color = ShaurmaTextGray,
            fontFamily = fontFamily,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (!state.isTimeValid) {
            Text(
                "Выберите время не раньше минимального и не позже 23:50 в пределах трёх дней.",
                color = Color.Red,
                fontFamily = fontFamily,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ShaurmaWheelPicker(
    values: List<String>,
    selectedIndex: Int,
    width: Dp,
    height: Dp,
    onSelectedIndex: (Int) -> Unit,
) {
    AndroidView(
        modifier = Modifier
            .width(width)
            .height(height),
        factory = { context ->
            NumberPicker(context).apply {
                wrapSelectorWheel = false
                descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                styleShaurmaNumberPicker()
            }
        },
        update = { picker ->
            picker.displayedValues = null
            picker.minValue = 0
            picker.maxValue = (values.size - 1).coerceAtLeast(0)
            picker.displayedValues = values.toTypedArray()
            picker.value = selectedIndex.coerceIn(0, (values.size - 1).coerceAtLeast(0))
            picker.setOnValueChangedListener { _, _, newValue -> onSelectedIndex(newValue) }
            picker.styleShaurmaNumberPicker()
        },
    )
}

private fun NumberPicker.styleShaurmaNumberPicker() {
    setBackgroundColor(android.graphics.Color.BLACK)
    for (index in 0 until childCount) {
        (getChildAt(index) as? EditText)?.apply {
            setTextColor(android.graphics.Color.WHITE)
            setHintTextColor(android.graphics.Color.GRAY)
            textSize = 18f
        }
    }
    try {
        val paintField = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
        paintField.isAccessible = true
        val paint = paintField.get(this) as android.graphics.Paint
        paint.color = android.graphics.Color.WHITE
    } catch (_: ReflectiveOperationException) {
        // Some Android versions hide NumberPicker internals; EditText styling above still applies.
    }
    invalidate()
}

private fun allowedHourRange(date: LocalDate, minDateTime: java.time.LocalDateTime, maxDateTime: java.time.LocalDateTime): IntRange {
    val minHour = if (date == minDateTime.toLocalDate()) minDateTime.hour else 0
    val maxHour = if (date == maxDateTime.toLocalDate()) maxDateTime.hour else 23
    return minHour..maxHour
}

private fun allowedMinuteRange(
    date: LocalDate,
    hour: Int,
    minDateTime: java.time.LocalDateTime,
    maxDateTime: java.time.LocalDateTime,
): IntRange {
    val minMinute = if (date == minDateTime.toLocalDate() && hour == minDateTime.hour) minDateTime.minute else 0
    val maxMinute = if (date == maxDateTime.toLocalDate() && hour == maxDateTime.hour) maxDateTime.minute else 59
    return minMinute..maxMinute
}

private fun monthNameRu(month: Int): String =
    when (month) {
        1 -> "Янв"
        2 -> "Фев"
        3 -> "Мар"
        4 -> "Апр"
        5 -> "Май"
        6 -> "Июн"
        7 -> "Июл"
        8 -> "Авг"
        9 -> "Сен"
        10 -> "Окт"
        11 -> "Ноя"
        else -> "Дек"
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onBack: () -> Unit,
    onMainMenu: () -> Unit,
    viewModel: OrdersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои заказы") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Button(onClick = onMainMenu) { Text("Главное меню") }
                if (state.isRefreshing) Text("Обновляем...")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            if (state.orders.isEmpty()) {
                item { Text("Заказов пока нет") }
            }
            items(state.orders, key = { it.publicId }) { order ->
                OrderCard(order)
            }
        }
    }
}

@Composable
private fun OrderCard(order: OrderEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("№ ${order.publicId}", fontWeight = FontWeight.Bold)
                AssistChip(
                    onClick = {},
                    label = { Text(statusLabel(order.status)) },
                    leadingIcon = {
                        Text("●", color = statusColor(order.status))
                    },
                )
            }
            Text("Создан: ${formatMillis(order.createdAt)}")
            Text("К получению: ${formatMillis(order.requestedTime)}")
            Text("Сумма: ${formatMoney(order.totalPrice)}")
            order.generalComment?.takeIf { it.isNotBlank() }?.let { Text("Комментарий: $it") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onHomeClick: () -> Unit,
    onCartClick: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharedFontSize = remember { mutableStateOf(28.sp) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ShaurmaBlack),
    ) constraints@ {
        val screenWidth = this@constraints.maxWidth
        val screenHeight = this@constraints.maxHeight
        val topZoneHeight = screenHeight * 0.15f
        val bottomZoneHeight = screenHeight * 0.85f
        val contentWidth = screenWidth * 0.8f
        val buttonHeight = bottomZoneHeight * 0.08f
        val fieldHeight = buttonHeight * 1.4f
        val verticalGap = bottomZoneHeight * 0.035f
        val fontFamily = rememberShaurmaFontFamily()

        Column(modifier = Modifier.fillMaxSize()) {
            ShaurmaTopZone(
                topZoneHeight = topZoneHeight,
                leftIconName = "home",
                leftContentDescription = "Главное меню",
                onLeftClick = onHomeClick,
                rightIconName = "ic_cart",
                rightContentDescription = "Корзина",
                onRightClick = onCartClick,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomZoneHeight),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.width(contentWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(verticalGap),
                ) {
                    val clientNumber = state.profile?.clientNumber
                    Text(
                        text = if (clientNumber == null) {
                            "Ваш внутренний номер № загружается"
                        } else {
                            "Ваш внутренний номер № $clientNumber"
                        },
                        color = if (clientNumber == null) ShaurmaTextGray else ShaurmaWhite,
                        fontFamily = fontFamily,
                        fontSize = 20.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Вы можете не указывать Ваши номер телефона и имя. Но если у нас будет вопрос по вашему заказу, мы не сможем с вами связаться и уточнить детали. В этом случае мы будем делать заказ по своим стандартам и претензии не принимаются.",
                        color = ShaurmaTextGray,
                        fontFamily = fontFamily,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ShaurmaProfileTextField(
                        value = state.name,
                        placeholder = "Имя",
                        width = contentWidth,
                        height = fieldHeight,
                        onValueChange = viewModel::updateName,
                    )
                    ShaurmaProfileTextField(
                        value = state.phone,
                        placeholder = "Номер телефона",
                        width = contentWidth,
                        height = fieldHeight,
                        onValueChange = viewModel::updatePhone,
                        keyboardType = KeyboardType.Phone,
                    )
                    state.error?.let {
                        Text(
                            text = it,
                            color = Color.Red,
                            fontFamily = fontFamily,
                            fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    ShaurmaOutlinedMenuButton(
                        text = if (state.isSaving) "СОХРАНЯЕМ..." else "СОХРАНИТЬ",
                        width = contentWidth,
                        height = buttonHeight,
                        fontSize = sharedFontSize.value,
                        onFontOverflow = { sharedFontSize.value = (sharedFontSize.value.value * 0.9f).sp },
                        onClick = viewModel::save,
                        enabled = !state.isSaving,
                    )
                }
            }
        }
    }
}

private fun productPrice(state: ProductUiState): Int =
    (state.item?.price ?: 0) + state.additions
        .filter { it.id in state.selectedAdditionIds }
        .sumOf { it.price }

private fun String.normalizedMenuName(): String =
    trim().replace(Regex("\\s+"), " ").uppercase()

private fun formatMoney(value: Int): String = "$value ₽"

private fun formatMillis(value: Long): String =
    Instant.ofEpochMilli(value).atZone(MoscowZone).toLocalDateTime().format(DateTimeFormatterRu)

private fun statusLabel(status: String): String =
    when (status) {
        "NEW" -> "Новый"
        "CONFIRMED" -> "Подтверждён"
        "COOKING" -> "Готовится"
        "READY" -> "Готов"
        "COMPLETED" -> "Завершён"
        else -> status
    }

private fun statusColor(status: String): Color =
    when (status) {
        "NEW" -> Color(0xFF1565C0)
        "CONFIRMED" -> Color(0xFF6A1B9A)
        "COOKING" -> Color(0xFFE65100)
        "READY" -> Color(0xFF2E7D32)
        "COMPLETED" -> Color(0xFF546E7A)
        else -> Color.Gray
    }

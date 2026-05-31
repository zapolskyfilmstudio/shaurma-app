package com.shaurma.mvp.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shaurma.mvp.data.local.CartItemEntity
import com.shaurma.mvp.data.local.MenuItemEntity
import com.shaurma.mvp.data.local.OrderEntity
import com.shaurma.mvp.data.repository.totalPrice
import java.time.Instant
import java.time.format.DateTimeFormatter
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
    onBack: () -> Unit,
    onEditItem: (Int) -> Unit,
    onOrderCreated: () -> Unit,
    viewModel: CartViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val deleteCandidate = remember { mutableStateOf<CartItemEntity?>(null) }
    val editCandidate = remember { mutableStateOf<CartItemEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Корзина") },
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
            if (state.items.isEmpty()) {
                item { Text("Корзина пуста") }
            }
            items(state.items, key = { it.id }) { item ->
                CartItemCard(
                    item = item,
                    onEdit = { editCandidate.value = item },
                    onDelete = { deleteCandidate.value = item },
                )
            }
            item {
                OutlinedTextField(
                    value = state.generalComment,
                    onValueChange = viewModel::updateComment,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Комментарий к заказу") },
                    minLines = 2,
                )
            }
            item { TimeSelection(state = state, viewModel = viewModel) }
            if (state.hasDifferentCookingTimes) {
                item {
                    Text(
                        "Внимание: в корзине товары с разным временем приготовления.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Text("Итого: ${formatMoney(state.totalPrice)}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.items.isNotEmpty() && state.isTimeValid && !state.isSubmitting,
                    onClick = {
                        scope.launch {
                            if (viewModel.submit()) onOrderCreated()
                        }
                    },
                ) {
                    Text(if (state.isSubmitting) "Отправляем..." else "Оплатить")
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
private fun CartItemCard(item: CartItemEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium)
            if (item.additionNames.isNotBlank()) Text("Добавки: ${item.additionNames}")
            if (item.removalNames.isNotBlank()) Text("Не класть: ${item.removalNames}")
            Text("${item.weight} г · ${item.cookingTime} мин · ${formatMoney(item.totalPrice)}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("Редактировать") }
                OutlinedButton(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun TimeSelection(state: CartUiState, viewModel: CartViewModel) {
    val context = LocalContext.current
    val selected = state.requestedTimeMillis.toMoscowDateTime()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Время получения: ${selected.format(DateTimeFormatterRu)}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        context,
                        { _, year, month, day -> viewModel.setRequestedDate(year, month, day) },
                        selected.year,
                        selected.monthValue - 1,
                        selected.dayOfMonth,
                    ).apply {
                        datePicker.minDate = state.minTimeMillis
                        datePicker.maxDate = state.maxTimeMillis
                    }.show()
                },
            ) { Text("Дата") }
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> viewModel.setRequestedTime(hour, minute) },
                        selected.hour,
                        selected.minute,
                        true,
                    ).show()
                },
            ) { Text("Время") }
        }
        Text("Минимум: ${formatMillis(state.minTimeMillis)} · максимум: ${formatMillis(state.maxTimeMillis)}")
        if (!state.isTimeValid) {
            Text(
                "Выберите время не раньше минимального и не позже 23:50 в пределах трёх дней.",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
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

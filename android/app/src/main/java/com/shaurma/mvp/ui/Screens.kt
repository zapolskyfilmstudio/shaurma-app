package com.shaurma.mvp.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shaurma.mvp.data.local.CartItemEntity
import com.shaurma.mvp.data.local.MenuCategoryEntity
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
    onCartClick: () -> Unit,
    onOrdersClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Главное меню") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOrdersClick) { Text("Мои заказы") }
                    OutlinedButton(onClick = onProfileClick) { Text("Профиль") }
                    OutlinedButton(onClick = onCartClick) { Text("Корзина ($cartCount)") }
                }
            }
            items(categories, key = { it.id }) { category ->
                CategoryCard(category = category, onClick = { onCategoryClick(category.id) })
            }
        }
    }
}

@Composable
private fun CategoryCard(category: MenuCategoryEntity, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(category.name, style = MaterialTheme.typography.titleLarge)
            Text(if (category.isGrill) "Гриль" else "Без приготовления на гриле")
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
    var showOptions by remember { mutableStateOf(false) }
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
                OutlinedButton(onClick = { showOptions = true }) {
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

    if (showOptions) {
        ProductOptionsDialog(
            state = state,
            onToggleAddition = viewModel::toggleAddition,
            onToggleRemoval = viewModel::toggleRemoval,
            onDismiss = { showOptions = false },
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
    var deleteCandidate by remember { mutableStateOf<CartItemEntity?>(null) }
    var editCandidate by remember { mutableStateOf<CartItemEntity?>(null) }

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
                    onEdit = { editCandidate = item },
                    onDelete = { deleteCandidate = item },
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

    deleteCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Удалить позицию?") },
            text = { Text(item.name) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        deleteCandidate = null
                    },
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Отмена") } },
        )
    }

    editCandidate?.let { item ->
        AlertDialog(
            onDismissRequest = { editCandidate = null },
            title = { Text("Изменить позицию?") },
            text = { Text("Текущая позиция будет удалена, затем можно добавить обновлённую.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item.id)
                        editCandidate = null
                        onEditItem(item.menuItemId)
                    },
                ) { Text("Изменить") }
            },
            dismissButton = { TextButton(onClick = { editCandidate = null }) { Text("Отмена") } },
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
    onBack: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профиль") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Назад") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Клиент № ${state.profile?.clientNumber ?: "..." }")
            Text("Device ID: ${state.profile?.deviceId.orEmpty()}")
            HorizontalDivider()
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Имя") },
            )
            OutlinedTextField(
                value = state.phone,
                onValueChange = viewModel::updatePhone,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Телефон") },
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !state.isSaving, onClick = viewModel::save) {
                Text(if (state.isSaving) "Сохраняем..." else "Сохранить")
            }
        }
    }
}

private fun productPrice(state: ProductUiState): Int =
    (state.item?.price ?: 0) + state.additions
        .filter { it.id in state.selectedAdditionIds }
        .sumOf { it.price }

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

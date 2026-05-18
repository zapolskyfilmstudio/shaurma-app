package com.shaurma.mvp.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaurma.mvp.data.local.AdditionEntity
import com.shaurma.mvp.data.local.CartItemEntity
import com.shaurma.mvp.data.local.ClientProfile
import com.shaurma.mvp.data.local.DevicePreferences
import com.shaurma.mvp.data.local.MenuCategoryEntity
import com.shaurma.mvp.data.local.MenuItemEntity
import com.shaurma.mvp.data.local.OrderEntity
import com.shaurma.mvp.data.local.RemovalEntity
import com.shaurma.mvp.data.repository.CartRepository
import com.shaurma.mvp.data.repository.MenuRepository
import com.shaurma.mvp.data.repository.OrderRepository
import com.shaurma.mvp.data.repository.ProfileRepository
import com.shaurma.mvp.data.repository.totalPrice
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppUiState(
    val isLoading: Boolean = true,
    val profile: ClientProfile? = null,
    val error: String? = null,
) {
    val isBlocked: Boolean get() = profile?.isBlocked == true
}

data class ProductUiState(
    val item: MenuItemEntity? = null,
    val additions: List<AdditionEntity> = emptyList(),
    val removals: List<RemovalEntity> = emptyList(),
    val selectedAdditionIds: Set<Int> = emptySet(),
    val selectedRemovalIds: Set<Int> = emptySet(),
    val isAdding: Boolean = false,
)

data class CartUiState(
    val items: List<CartItemEntity> = emptyList(),
    val totalPrice: Int = 0,
    val generalComment: String = "",
    val requestedTimeMillis: Long = 0,
    val minTimeMillis: Long = 0,
    val maxTimeMillis: Long = 0,
    val isTimeValid: Boolean = true,
    val hasDifferentCookingTimes: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
)

data class OrdersUiState(
    val orders: List<OrderEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

data class ProfileUiState(
    val profile: ClientProfile? = null,
    val name: String = "",
    val phone: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

private data class ProductBaseState(
    val item: MenuItemEntity?,
    val additions: List<AdditionEntity>,
    val removals: List<RemovalEntity>,
)

private data class CartBaseState(
    val items: List<CartItemEntity>,
    val selectedTime: Long?,
    val comment: String,
    val profile: ClientProfile,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val menuRepository: MenuRepository,
) : ViewModel() {
    private val isLoading = MutableStateFlow(true)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<AppUiState> = combine(
        profileRepository.profile,
        isLoading,
        error,
    ) { profile, loading, error ->
        AppUiState(isLoading = loading, profile = profile, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUiState())

    init {
        viewModelScope.launch {
            runCatching {
                profileRepository.initDevice()
                menuRepository.refresh()
            }.onFailure {
                error.value = it.localizedMessage ?: "Не удалось загрузить данные"
            }
            isLoading.value = false
        }
    }
}

@HiltViewModel
class MenuViewModel @Inject constructor(
    private val menuRepository: MenuRepository,
    cartRepository: CartRepository,
) : ViewModel() {
    val categories: StateFlow<List<MenuCategoryEntity>> = menuRepository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val cartCount: StateFlow<Int> = cartRepository.cart.map { it.size }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        0,
    )

    fun refresh() {
        viewModelScope.launch { runCatching { menuRepository.refresh() } }
    }
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    menuRepository: MenuRepository,
) : ViewModel() {
    private val categoryId: Int = checkNotNull(savedStateHandle["categoryId"])
    val items: StateFlow<List<MenuItemEntity>> = menuRepository.itemsForCategory(categoryId).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
}

@HiltViewModel
class ProductViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val menuRepository: MenuRepository,
    private val cartRepository: CartRepository,
) : ViewModel() {
    private val itemId: Int = checkNotNull(savedStateHandle["itemId"])
    private val selectedAdditions = MutableStateFlow<Set<Int>>(emptySet())
    private val selectedRemovals = MutableStateFlow<Set<Int>>(emptySet())
    private val isAdding = MutableStateFlow(false)

    private val baseState = combine(
        menuRepository.item(itemId),
        menuRepository.additions(itemId),
        menuRepository.removals(itemId),
    ) { item, additions, removals ->
        ProductBaseState(item = item, additions = additions, removals = removals)
    }

    val state: StateFlow<ProductUiState> = combine(
        baseState,
        selectedAdditions,
        selectedRemovals,
        isAdding,
    ) { base, selectedAdditionIds, selectedRemovalIds, adding ->
        ProductUiState(
            item = base.item,
            additions = base.additions,
            removals = base.removals,
            selectedAdditionIds = selectedAdditionIds,
            selectedRemovalIds = selectedRemovalIds,
            isAdding = adding,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductUiState())

    fun toggleAddition(id: Int) {
        selectedAdditions.value = selectedAdditions.value.toggle(id)
    }

    fun toggleRemoval(id: Int) {
        selectedRemovals.value = selectedRemovals.value.toggle(id)
    }

    suspend fun addToCart(): Boolean {
        val item = menuRepository.item(itemId).first() ?: return false
        isAdding.value = true
        return runCatching {
            val additions = menuRepository.additions(itemId).first()
                .filter { it.id in selectedAdditions.value }
            val removals = menuRepository.removals(itemId).first()
                .filter { it.id in selectedRemovals.value }
            cartRepository.addItem(item, additions, removals)
        }.also { isAdding.value = false }.isSuccess
    }

    private fun Set<Int>.toggle(id: Int): Set<Int> =
        if (id in this) this - id else this + id
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    devicePreferences: DevicePreferences,
) : ViewModel() {
    private val requestedTime = MutableStateFlow<Long?>(null)
    private val comment = MutableStateFlow("")
    private val isSubmitting = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val baseState = combine(
        cartRepository.cart,
        requestedTime,
        comment,
        devicePreferences.profileFlow,
    ) { items, selectedTime, comment, profile ->
        CartBaseState(items = items, selectedTime = selectedTime, comment = comment, profile = profile)
    }

    val state: StateFlow<CartUiState> = combine(
        baseState,
        isSubmitting,
        error,
    ) { base, submitting, error ->
        val items = base.items
        val serverNow = System.currentTimeMillis() + base.profile.serverTimeOffsetMillis
        val minTime = minimumRequestedTime(
            serverNowMillis = serverNow,
            maxCookingMinutes = items.maxOfOrNull { it.cookingTime } ?: 0,
        )
        val maxTime = maximumRequestedTime(serverNow)
        val effectiveTime = base.selectedTime ?: minTime
        val localTime = effectiveTime.toMoscowDateTime().toLocalTime()
        CartUiState(
            items = items,
            totalPrice = items.sumOf { it.totalPrice },
            generalComment = base.comment,
            requestedTimeMillis = effectiveTime,
            minTimeMillis = minTime,
            maxTimeMillis = maxTime,
            isTimeValid = effectiveTime in minTime..maxTime && localTime.hour < 24 &&
                (localTime.hour < 23 || localTime.minute <= 50),
            hasDifferentCookingTimes = items.map { it.cookingTime }.toSet().size > 1,
            isSubmitting = submitting,
            error = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CartUiState())

    fun updateComment(value: String) {
        comment.value = value
    }

    fun setRequestedDate(year: Int, zeroBasedMonth: Int, day: Int) {
        val current = state.value.requestedTimeMillis.toMoscowDateTime()
        requestedTime.value = LocalDateTime.of(
            LocalDate.of(year, zeroBasedMonth + 1, day),
            current.toLocalTime(),
        ).toEpochMillisMoscow()
    }

    fun setRequestedTime(hour: Int, minute: Int) {
        val current = state.value.requestedTimeMillis.toMoscowDateTime()
        requestedTime.value = current.withHour(hour).withMinute(minute).toEpochMillisMoscow()
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch { cartRepository.delete(id) }
    }

    suspend fun submit(): Boolean {
        val snapshot = state.value
        if (snapshot.items.isEmpty() || !snapshot.isTimeValid || snapshot.isSubmitting) return false
        isSubmitting.value = true
        error.value = null
        val result = runCatching {
            orderRepository.createOrder(snapshot.requestedTimeMillis, snapshot.generalComment)
        }
        isSubmitting.value = false
        result.exceptionOrNull()?.let {
            error.value = it.localizedMessage ?: "Не удалось создать заказ"
        }
        return result.isSuccess
    }
}

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private val isRefreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<OrdersUiState> = combine(
        orderRepository.orders,
        isRefreshing,
        error,
    ) { orders, refreshing, error ->
        OrdersUiState(orders = orders, isRefreshing = refreshing, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    init {
        viewModelScope.launch {
            while (true) {
                isRefreshing.value = true
                runCatching { orderRepository.syncOrders() }
                    .onFailure { error.value = it.localizedMessage ?: "Не удалось обновить заказы" }
                isRefreshing.value = false
                delay(10_000)
            }
        }
    }
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val name = MutableStateFlow("")
    private val phone = MutableStateFlow("")
    private val isSaving = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val state: StateFlow<ProfileUiState> = combine(
        profileRepository.profile,
        name,
        phone,
        isSaving,
        error,
    ) { profile, name, phone, saving, error ->
        ProfileUiState(profile = profile, name = name, phone = phone, isSaving = saving, error = error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    init {
        viewModelScope.launch {
            val profile = profileRepository.profile.first()
            name.value = profile.name.orEmpty()
            phone.value = profile.phone.orEmpty()
        }
    }

    fun updateName(value: String) {
        name.value = value
    }

    fun updatePhone(value: String) {
        phone.value = value
    }

    fun save() {
        viewModelScope.launch {
            isSaving.value = true
            error.value = null
            runCatching { profileRepository.updateProfile(name.value, phone.value) }
                .onFailure { error.value = it.localizedMessage ?: "Не удалось сохранить профиль" }
            isSaving.value = false
        }
    }
}

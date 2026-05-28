package com.example.cleancity.ui.feature.notifications

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import com.example.cleancity.data.network.NotificationsApiContract
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.domain.AuthState
import com.example.cleancity.domain.NotificationEventBus
import com.example.cleancity.domain.UnreadCountStore
import com.example.cleancity.shared.models.NotificationResponse
import com.example.cleancity.ui.util.listErrorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NotificationsState {
    data object Initial : NotificationsState
    data object Loading : NotificationsState
    data object GuestPrompt : NotificationsState
    data object Empty : NotificationsState
    data class Error(val message: String) : NotificationsState
    data class Loaded(
        val items: List<NotificationResponse>,
        val isRefreshing: Boolean = false,
        val transientError: String? = null,
    ) : NotificationsState {
        val unreadCount: Int get() = items.count { it.readAt == null }
    }
}

private const val LIST_LIMIT = 50

class NotificationsScreenModel(
    private val api: NotificationsApiContract,
    private val unreadCountStore: UnreadCountStore,
    private val authRepo: AuthRepository,
    private val bus: NotificationEventBus,
) : ScreenModel {

    private val _state = MutableStateFlow<NotificationsState>(NotificationsState.Initial)
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init {
        // Push-канал замечает новое ANNOUNCEMENT — подтягиваем список без
        // ожидания pull-to-refresh, иначе элемент появляется только после
        // того как юзер сам обновит экран.
        screenModelScope.launch {
            bus.newAnnouncements.collect {
                if (authRepo.state.value is AuthState.Authenticated) load()
            }
        }
    }

    /** Грузит список. Вызывается при открытии экрана; повторно — через pull-to-refresh. */
    fun load() {
        if (authRepo.state.value !is AuthState.Authenticated) {
            _state.value = NotificationsState.GuestPrompt
            return
        }
        if (_state.value is NotificationsState.Loading) return  // уже загружается — не дублировать запрос
        if (_state.value !is NotificationsState.Loaded) {
            _state.value = NotificationsState.Loading
        }
        screenModelScope.launch {
            runCatching { api.list(limit = LIST_LIMIT) }
                .onSuccess { resp ->
                    _state.value = if (resp.items.isEmpty()) {
                        NotificationsState.Empty
                    } else {
                        NotificationsState.Loaded(items = resp.items)
                    }
                }
                .onFailure { e ->
                    if (_state.value !is NotificationsState.Loaded) {
                        _state.value = NotificationsState.Error(listErrorMessage(e))
                    } else {
                        _state.update { s ->
                            (s as? NotificationsState.Loaded)?.copy(isRefreshing = false) ?: s
                        }
                    }
                }
        }
    }

    fun refresh() {
        _state.update { s ->
            (s as? NotificationsState.Loaded)?.copy(isRefreshing = true) ?: s
        }
        load()
    }

    /** Оптимистично помечает уведомление прочитанным и синхронизирует бейдж. */
    fun markRead(id: Long) {
        val loaded = _state.value as? NotificationsState.Loaded ?: return
        val target = loaded.items.firstOrNull { it.id == id } ?: return
        if (target.readAt != null) return  // уже прочитано — ничего не делаем

        _state.value = loaded.copy(items = loaded.items.markRead(setOf(id)))
        unreadCountStore.decrement(1)
        screenModelScope.launch {
            runCatching { api.markRead(id) }
                .onFailure {
                    // откат: возвращаем элемент в непрочитанное и восстанавливаем бейдж
                    unreadCountStore.increment(1)
                    _state.update { s ->
                        val l = s as? NotificationsState.Loaded ?: return@update s
                        l.copy(
                            items = l.items.map { if (it.id == id) it.copy(readAt = null) else it },
                            transientError = "Не удалось отметить уведомление прочитанным",
                        )
                    }
                }
        }
    }

    /** Оптимистично помечает все уведомления прочитанными. */
    fun markAllRead() {
        val loaded = _state.value as? NotificationsState.Loaded ?: return
        val unread = loaded.unreadCount
        if (unread == 0) return

        _state.value = loaded.copy(
            items = loaded.items.markRead(loaded.items.map { it.id }.toSet())
        )
        unreadCountStore.decrement(unread)
        screenModelScope.launch {
            runCatching { api.markAllRead() }
                .onFailure {
                    // откат к снимку до отметки и восстановление бейджа
                    unreadCountStore.increment(unread)
                    _state.update { s ->
                        if (s is NotificationsState.Loaded) {
                            loaded.copy(transientError = "Не удалось отметить уведомления прочитанными")
                        } else s
                    }
                }
        }
    }

    fun clearTransientError() {
        _state.update { s ->
            if (s is NotificationsState.Loaded) s.copy(transientError = null) else s
        }
    }

    private fun List<NotificationResponse>.markRead(ids: Set<Long>): List<NotificationResponse> =
        map { n ->
            if (n.id in ids && n.readAt == null) {
                n.copy(readAt = "1970-01-01T00:00:00Z")  // маркер «прочитано», точное время неважно
            } else n
        }
}

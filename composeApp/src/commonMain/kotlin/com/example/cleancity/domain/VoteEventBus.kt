package com.example.cleancity.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class VoteEvent(
    val complaintId: Long,
    val votesCount: Int,
    val userVoted: Boolean,
)

// Лента и Detail живут в разных Voyager-навигаторах, и LaunchedEffect(Unit)
// в FeedScreen не срабатывает при возврате с Detail — карточка в ленте остаётся
// со старым счётчиком. Detail после успешного toggleVote эмитит событие,
// FeedScreenModel слушает и обновляет конкретную карточку в state.
object VoteEventBus {
    private val _events = MutableSharedFlow<VoteEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun emit(event: VoteEvent) {
        _events.tryEmit(event)
    }
}

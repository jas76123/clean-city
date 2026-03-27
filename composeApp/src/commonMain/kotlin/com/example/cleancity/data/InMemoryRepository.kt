package com.example.cleancity.data

import com.example.cleancity.model.*
import com.example.cleancity.platform.currentTimeMillis
import com.example.cleancity.platform.randomUUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object InMemoryRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser

    private val _problems = MutableStateFlow<List<Problem>>(emptyList())
    val problems: StateFlow<List<Problem>> = _problems

    private val _events = MutableStateFlow<List<CleanupEvent>>(emptyList())
    val events: StateFlow<List<CleanupEvent>> = _events

    private val _notifications = MutableStateFlow<List<Notification>>(emptyList())
    val notifications: StateFlow<List<Notification>> = _notifications

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun register(name: String, email: String, password: String) {
        _currentUser.value = User(id = randomUUID(), name = name, email = email, password = password)
    }

    fun login(email: String, password: String): Boolean {
        _currentUser.value = User(id = randomUUID(), name = email.substringBefore('@'), email = email, password = password)
        return true
    }

    fun logout() { _currentUser.value = null }

    fun addProblem(title: String, description: String, type: ProblemType, latitude: Double, longitude: Double, address: String, photoUrl: String? = null) {
        val user = _currentUser.value ?: return
        val now = currentTimeMillis()
        val problem = Problem(
            id = randomUUID(), authorId = user.id, authorName = user.name,
            title = title, description = description, type = type,
            photoUrl = photoUrl, latitude = latitude, longitude = longitude, address = address,
            statusHistory = listOf(StatusChange(ProblemStatus.NEW, now, "Создана")),
            createdAt = now,
        )
        _problems.value = _problems.value + problem
    }

    fun voteProblem(problemId: String, voteYes: Boolean) {
        _problems.value = _problems.value.map { p ->
            if (p.id == problemId) {
                if (voteYes) p.copy(votesYes = p.votesYes + 1) else p.copy(votesNo = p.votesNo + 1)
            } else p
        }
    }

    fun verifyProblem(problemId: String) {
        val user = _currentUser.value ?: return
        _problems.value = _problems.value.map { p ->
            if (p.id == problemId && p.verifications.none { it.userId == user.id }) {
                val newV = p.verifications + Verification(user.id, user.name, currentTimeMillis())
                val newStatus = if (newV.size >= 3) ProblemStatus.VERIFIED else p.status
                p.copy(verifications = newV, status = newStatus)
            } else p
        }
    }

    fun createEvent(name: String, description: String, dateTime: Long, location: String, latitude: Double, longitude: Double, photoUrl: String? = null) {
        val user = _currentUser.value ?: return
        val event = CleanupEvent(
            id = randomUUID(), authorId = user.id, name = name, description = description,
            dateTime = dateTime, location = location, latitude = latitude, longitude = longitude,
            photoUrl = photoUrl, participants = listOf(user.id), chatId = randomUUID(),
        )
        _events.value = _events.value + event
    }

    fun joinEvent(eventId: String) {
        val userId = _currentUser.value?.id ?: return
        _events.value = _events.value.map { e ->
            if (e.id == eventId && userId !in e.participants) e.copy(participants = e.participants + userId) else e
        }
    }

    fun sendMessage(eventId: String, text: String) {
        val user = _currentUser.value ?: return
        val msg = ChatMessage(id = randomUUID(), eventId = eventId, authorId = user.id, authorName = user.name, text = text, timestamp = currentTimeMillis())
        _messages.value = _messages.value + msg
    }

    fun markNotificationRead(notificationId: String) {
        _notifications.value = _notifications.value.map { n ->
            if (n.id == notificationId) n.copy(isRead = true) else n
        }
    }

    fun unreadNotificationCount(): Int = _notifications.value.count { !it.isRead }

    fun loadSampleData() {
        _problems.value = SampleData.problems
        _events.value = SampleData.events
        _notifications.value = SampleData.notifications
        _messages.value = SampleData.messages
    }
}

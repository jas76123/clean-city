package com.example.cleancity.model

enum class TrustLevel(val displayName: String, val requiredXp: Int) {
    NOVICE("Новичок", 0),
    RESIDENT("Житель", 100),
    ACTIVIST("Активист", 200),
    HERO("Герой", 300)
}

enum class ProblemType(val displayName: String, val emoji: String) {
    DUMP("Свалки", "🗑"),
    HOLES("Ямы", "⚠️"),
    LIGHTING("Освещение", "💡"),
    GREENERY("Озеленение", "🌳")
}

enum class ProblemStatus(val displayName: String) {
    NEW("Новая"),
    VERIFIED("Проверена"),
    SENT("Отправлена"),
    IN_WORK("В работе"),
    SOLVED("Решена")
}

enum class EventStatus(val displayName: String) {
    UPCOMING("Скоро"),
    ACTIVE("Активен"),
    COMPLETED("Завершён")
}

enum class NotificationType { STATUS, VERIFY, BADGE, SYSTEM, CHAT, LOCATION }

data class User(
    val id: String,
    val name: String,
    val email: String,
    val password: String,
    val trustLevel: TrustLevel = TrustLevel.NOVICE,
    val xp: Int = 0,
    val achievements: List<Achievement> = emptyList(),
    val createdCount: Int = 0,
    val solvedCount: Int = 0,
    val complaintsCount: Int = 0,
    val inProgressCount: Int = 0,
)

data class Problem(
    val id: String,
    val authorId: String,
    val authorName: String,
    val title: String,
    val description: String,
    val type: ProblemType,
    val photoUrl: String? = null,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val status: ProblemStatus = ProblemStatus.NEW,
    val statusHistory: List<StatusChange> = emptyList(),
    val verifications: List<Verification> = emptyList(),
    val votesYes: Int = 0,
    val votesNo: Int = 0,
    val signatories: List<String> = emptyList(),
    val createdAt: Long,
)

data class StatusChange(
    val status: ProblemStatus,
    val timestamp: Long,
    val description: String,
)

data class Verification(
    val userId: String,
    val userName: String,
    val timestamp: Long,
)

data class CleanupEvent(
    val id: String,
    val authorId: String,
    val name: String,
    val description: String,
    val dateTime: Long,
    val location: String,
    val latitude: Double,
    val longitude: Double,
    val photoUrl: String? = null,
    val participants: List<String> = emptyList(),
    val status: EventStatus = EventStatus.UPCOMING,
    val chatId: String,
)

data class Notification(
    val id: String,
    val type: NotificationType,
    val title: String,
    val description: String,
    val isRead: Boolean = false,
    val createdAt: Long,
)

data class ChatMessage(
    val id: String,
    val eventId: String,
    val authorId: String,
    val authorName: String,
    val text: String,
    val timestamp: Long,
)

data class Achievement(
    val id: String,
    val title: String,
    val icon: String,
    val description: String,
    val isUnlocked: Boolean = false,
)

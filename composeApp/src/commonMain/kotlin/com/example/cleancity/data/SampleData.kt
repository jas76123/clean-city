package com.example.cleancity.data

import com.example.cleancity.model.*

object SampleData {
    val problems = listOf(
        Problem(id = "p1", authorId = "u1", authorName = "Иван Петров",
            title = "Свалка мусора на ул. Ленина", description = "Большая куча строительного мусора у дома 15.",
            type = ProblemType.DUMP, latitude = 43.585472, longitude = 39.723089, address = "ул. Ленина, 15, Сочи",
            status = ProblemStatus.IN_WORK,
            statusHistory = listOf(
                StatusChange(ProblemStatus.NEW, 1711000000000L, "Создана"),
                StatusChange(ProblemStatus.VERIFIED, 1711100000000L, "Проверена жителями"),
                StatusChange(ProblemStatus.SENT, 1711200000000L, "Отправлена в администрацию"),
                StatusChange(ProblemStatus.IN_WORK, 1711300000000L, "Принята в работу"),
            ),
            verifications = listOf(
                Verification("u2", "Анна Сидорова", 1711050000000L),
                Verification("u3", "Пётр Козлов", 1711060000000L),
                Verification("u4", "Мария Иванова", 1711070000000L),
            ),
            votesYes = 12, votesNo = 1, signatories = listOf("u1", "u2", "u3", "u4"), createdAt = 1711000000000L),
        Problem(id = "p2", authorId = "u2", authorName = "Анна Сидорова",
            title = "Яма на дороге по ул. Навагинской", description = "Глубокая яма в дорожном покрытии.",
            type = ProblemType.HOLES, latitude = 43.590000, longitude = 39.725000, address = "ул. Навагинская, 8, Сочи",
            status = ProblemStatus.NEW,
            verifications = listOf(Verification("u1", "Иван Петров", 1711150000000L), Verification("u3", "Пётр Козлов", 1711160000000L)),
            votesYes = 4, votesNo = 0, createdAt = 1711100000000L),
        Problem(id = "p3", authorId = "u3", authorName = "Пётр Козлов",
            title = "Не работает фонарь в парке Ривьера", description = "Фонарь у центрального входа не горит уже неделю.",
            type = ProblemType.LIGHTING, latitude = 43.592000, longitude = 39.720000, address = "Парк Ривьера, Сочи",
            status = ProblemStatus.SOLVED, votesYes = 8, votesNo = 0, createdAt = 1710900000000L),
    )

    val events = listOf(
        CleanupEvent(id = "e1", authorId = "u1", name = "Субботник в парке Ривьера",
            description = "Убираем территорию парка. Приносите перчатки!",
            dateTime = 1711500000000L, location = "Парк Ривьера, главный вход",
            latitude = 43.592000, longitude = 39.720000, participants = listOf("u1", "u2", "u3"),
            status = EventStatus.ACTIVE, chatId = "c1"),
        CleanupEvent(id = "e2", authorId = "u2", name = "Уборка пляжа Приморский",
            description = "Собираем мусор вдоль береговой линии.",
            dateTime = 1711800000000L, location = "Пляж Приморский",
            latitude = 43.580000, longitude = 39.718000, participants = listOf("u2", "u4"),
            status = EventStatus.UPCOMING, chatId = "c2"),
        CleanupEvent(id = "e3", authorId = "u3", name = "Озеленение двора на ул. Горького",
            description = "Высадка деревьев и кустарников.",
            dateTime = 1710600000000L, location = "ул. Горького, 22",
            latitude = 43.588000, longitude = 39.730000, participants = listOf("u1", "u2", "u3", "u4", "u5"),
            status = EventStatus.COMPLETED, chatId = "c3"),
    )

    val notifications = listOf(
        Notification("n1", NotificationType.STATUS, "Статус изменён", "Проблема «Свалка на ул. Ленина» принята в работу", false, 1711300000000L),
        Notification("n2", NotificationType.VERIFY, "Подтверждение получено", "Ваша проблема получила 3/3 подтверждения", false, 1711100000000L),
        Notification("n3", NotificationType.BADGE, "Новое достижение!", "Вы получили значок «Активист»", false, 1711200000000L),
        Notification("n4", NotificationType.SYSTEM, "Email подтверждён", "Ваш email успешно верифицирован", true, 1710900000000L),
        Notification("n5", NotificationType.CHAT, "Новое сообщение", "Анна написала в чат «Субботник в парке Ривьера»", false, 1711350000000L),
        Notification("n6", NotificationType.LOCATION, "Проблема рядом", "Новая проблема обнаружена в 200м от вас", true, 1711250000000L),
    )

    val messages = listOf(
        ChatMessage("m1", "c1", "u1", "Иван Петров", "Всем привет! Завтра в 10:00 у главного входа", 1711400000000L),
        ChatMessage("m2", "c1", "u2", "Анна Сидорова", "Отлично! Я буду с перчатками и мешками", 1711401000000L),
        ChatMessage("m3", "c1", "u3", "Пётр Козлов", "Подскажите, нужно ли брать грабли?", 1711402000000L),
        ChatMessage("m4", "c1", "u1", "Иван Петров", "Да, грабли пригодятся! И лопаты тоже", 1711403000000L),
    )
}

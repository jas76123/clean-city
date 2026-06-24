package com.example.cleancity.ui.feature.map.components

import com.example.cleancity.shared.models.ComplaintStatus
import kotlin.math.roundToLong

internal fun formatCoord(value: Double): String {
    val rounded = (value * 10000).roundToLong()
    val whole = rounded / 10000
    val frac = (rounded % 10000).let { if (it < 0) -it else it }
    val fracStr = frac.toString().padStart(4, '0')
    return "$whole.$fracStr"
}

internal fun ComplaintStatus.localizedLabel(): String = when (this) {
    ComplaintStatus.NEW -> "Новая"
    ComplaintStatus.IN_PROGRESS -> "В работе"
    ComplaintStatus.RESOLVED -> "Решено"
    ComplaintStatus.REJECTED -> "Отклонено"
    ComplaintStatus.DUPLICATE -> "Дубликат"
}

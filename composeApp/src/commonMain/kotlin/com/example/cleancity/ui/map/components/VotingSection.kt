package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.ui.theme.*

@Composable
fun VotingSection(
    votesYes: Int,
    votesNo: Int,
    onVoteYes: () -> Unit,
    onVoteNo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = votesYes + votesNo
    val yesPercent = if (total > 0) (votesYes * 100 / total) else 0
    val noPercent = if (total > 0) (votesNo * 100 / total) else 0

    Column(
        modifier = modifier
            .background(Green50, RoundedCornerShape(12.dp))
            .border(1.dp, Green100, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(
            "ПРОБЛЕМА РЕШЕНА?",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Green800,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onVoteYes,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (yesPercent >= 50) Green600 else Color.White,
                    contentColor = if (yesPercent >= 50) Color.White else Gray600,
                ),
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    "✓ Да · ${if (total > 0) "$yesPercent%" else "—"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            OutlinedButton(
                onClick = onVoteNo,
                shape = CircleShape,
                modifier = Modifier.weight(1f).height(34.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    "Нет · ${if (total > 0) "$noPercent%" else "—"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Gray600,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${if (total > 0) "$total голосов · " else "Нет голосов · "}70%+ «Да» → статус Решена",
            style = MaterialTheme.typography.labelSmall,
            color = Gray500,
        )
    }
}

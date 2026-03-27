package com.example.cleancity.ui.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cleancity.model.Problem
import com.example.cleancity.model.ProblemStatus
import com.example.cleancity.ui.theme.*

@Composable
fun ProblemBottomSheet(
    problem: Problem,
    onVerify: () -> Unit,
    onVoteYes: () -> Unit,
    onVoteNo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 16.dp,
        color = Color.White,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 20.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gray200)
            )
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        problem.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Gray900,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(problem.status)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "📍 ${problem.address} · Автор: ${problem.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Gray500,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    problem.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Gray600,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Green50, RoundedCornerShape(12.dp))
                        .border(1.dp, Green100, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val verCount = problem.verifications.size
                    val isOfficial = verCount >= 3
                    Text(
                        "✅ $verCount подтверждений${if (isOfficial) " · Официальная" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Green700,
                    )
                    Button(
                        onClick = onVerify,
                        colors = ButtonDefaults.buttonColors(containerColor = Green600),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(30.dp),
                    ) {
                        Text("+ Подтвердить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))
                VotingSection(
                    votesYes = problem.votesYes,
                    votesNo = problem.votesNo,
                    onVoteYes = onVoteYes,
                    onVoteNo = onVoteNo,
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { /* navigate to complaint detail — future */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Green900),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text("Подробнее", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: ProblemStatus) {
    val (bg, text) = when (status) {
        ProblemStatus.NEW -> Blue.copy(alpha = 0.9f) to Color.White
        ProblemStatus.VERIFIED -> Accent.copy(alpha = 0.9f) to Green900
        ProblemStatus.SENT -> Amber.copy(alpha = 0.9f) to Color.White
        ProblemStatus.IN_WORK -> Amber.copy(alpha = 0.9f) to Color.White
        ProblemStatus.SOLVED -> Green600.copy(alpha = 0.9f) to Color.White
    }
    Surface(color = bg, shape = CircleShape) {
        Text(
            status.displayName,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

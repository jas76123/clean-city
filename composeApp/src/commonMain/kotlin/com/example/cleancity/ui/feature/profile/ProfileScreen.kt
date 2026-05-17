package com.example.cleancity.ui.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.shared.models.UserResponse
import com.example.cleancity.ui.feature.auth.LoginScreen
import com.example.cleancity.ui.feature.auth.RegisterScreen
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.AmberLight
import com.example.cleancity.ui.theme.Blue
import com.example.cleancity.ui.theme.BlueLight
import com.example.cleancity.ui.theme.Gray100
import com.example.cleancity.ui.theme.Gray500
import com.example.cleancity.ui.theme.Gray600
import com.example.cleancity.ui.theme.Gray700
import com.example.cleancity.ui.theme.Gray900
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Green50
import com.example.cleancity.ui.theme.Green600
import com.example.cleancity.ui.theme.Green700
import com.example.cleancity.ui.theme.Green900
import com.example.cleancity.ui.theme.Purple
import com.example.cleancity.ui.theme.Red
import com.example.cleancity.ui.theme.RedLight
import kotlinx.coroutines.launch

class ProfileScreen : Screen {
    @Composable
    override fun Content() {
        val model: ProfileScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHost = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) { model.load() }

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val s = state) {
                ProfileState.Loading -> CenteredSpinner()
                ProfileState.GuestPrompt -> GuestPromptView(
                    onLoginClick = { navigator.push(LoginScreen()) },
                    onRegisterClick = { navigator.push(RegisterScreen()) },
                )
                is ProfileState.Error -> ErrorView(s.message) { model.load() }
                is ProfileState.Loaded -> LoadedView(
                    user = s.user,
                    stats = s.stats,
                    onMyComplaintsClick = { /* shell-level переключение таба — Day 13 */ },
                    onSettingsClick = {
                        scope.launch {
                            snackbarHost.showSnackbar("Настройки уведомлений появятся в Day 12 (push)")
                        }
                    },
                    onAboutClick = { navigator.push(AboutScreen()) },
                    onLogoutClick = { model.logout() },
                )
            }
            SnackbarHost(
                hostState = snackbarHost,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) { data -> Snackbar(snackbarData = data) }
        }
    }
}

@Composable
private fun CenteredSpinner() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = Gray600, textAlign = TextAlign.Center)
            Button(onClick = onRetry) { Text("Повторить") }
        }
    }
}

@Composable
private fun GuestPromptView(onLoginClick: () -> Unit, onRegisterClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Accent, Green400))),
            contentAlignment = Alignment.Center,
        ) {
            Text("🛡", style = MaterialTheme.typography.headlineMedium, color = Green900)
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Войдите, чтобы видеть свои жалобы",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "В профиле — статистика по поданным жалобам и подтверждениям, настройки уведомлений и выход.",
            style = MaterialTheme.typography.bodySmall,
            color = Gray500,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onLoginClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green700, contentColor = Color.White),
        ) { Text("Войти") }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onRegisterClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green50, contentColor = Green700),
        ) { Text("Зарегистрироваться") }
    }
}

@Composable
private fun LoadedView(
    user: UserResponse,
    stats: ProfileStats,
    onMyComplaintsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ProfileHeader(user = user)
        Spacer(Modifier.height(16.dp))
        ProfileStatsGrid(stats = stats)
        Spacer(Modifier.height(16.dp))
        ProfileMenu(
            onMyComplaintsClick = onMyComplaintsClick,
            onSettingsClick = onSettingsClick,
            onAboutClick = onAboutClick,
            onLogoutClick = onLogoutClick,
        )
    }
}

@Composable
private fun ProfileHeader(user: UserResponse) {
    val initials = remember(user) {
        val name = user.fullName?.trim().orEmpty()
        if (name.isNotEmpty()) {
            name.split(" ").filter { it.isNotBlank() }.take(2)
                .map { it.first().uppercaseChar() }
                .joinToString("")
        } else {
            user.email.take(1).uppercase()
        }
    }
    val displayName = user.fullName?.takeIf { it.isNotBlank() } ?: user.email.substringBefore('@')
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Green700, Green900)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(Accent, Green400))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials.ifBlank { "?" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = Green900,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                )
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun ProfileStatsGrid(stats: ProfileStats) {
    val cards = listOf(
        StatCard("В обработке", stats.inProgress, Icons.Default.AccessTime, AmberLight, com.example.cleancity.ui.theme.Amber),
        StatCard("В работе", stats.inWork, Icons.Default.HourglassEmpty, BlueLight, Blue),
        StatCard("Решено", stats.resolved, Icons.Default.CheckCircle, Green50, Green600),
        StatCard("Подтверждено", stats.confirmed, Icons.Default.ThumbUp, RedLight.copy(alpha = 0.5f), Purple),
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
        modifier = Modifier.height(220.dp),
    ) {
        items(items = cards) { card -> StatCardView(card) }
    }
}

private data class StatCard(
    val label: String,
    val value: Int,
    val icon: ImageVector,
    val iconBg: Color,
    val iconFg: Color,
)

@Composable
private fun StatCardView(card: StatCard) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Gray100, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(card.iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(card.icon, contentDescription = null, tint = card.iconFg, modifier = Modifier.size(18.dp))
        }
        Text(
            card.value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Gray900,
        )
        Text(
            card.label,
            style = MaterialTheme.typography.bodySmall,
            color = Gray500,
        )
    }
}

@Composable
private fun ProfileMenu(
    onMyComplaintsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAboutClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MenuItemRow(icon = Icons.Default.ListAlt, label = "Мои жалобы", onClick = onMyComplaintsClick)
        MenuItemRow(icon = Icons.Default.NotificationsActive, label = "Настройки уведомлений", onClick = onSettingsClick)
        MenuItemRow(icon = Icons.Default.Info, label = "О приложении", onClick = onAboutClick)
        MenuItemRow(
            icon = Icons.AutoMirrored.Default.Logout,
            label = "Выйти",
            tint = Red,
            iconBg = RedLight,
            onClick = onLogoutClick,
        )
    }
}

@Composable
private fun MenuItemRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Gray700,
    iconBg: Color = Green50,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Gray100, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.size(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = tint,
            modifier = Modifier.weight(1f),
        )
        if (tint != Red) {
            Icon(
                imageVector = Icons.AutoMirrored.Default.ArrowForwardIos,
                contentDescription = null,
                tint = Gray500,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

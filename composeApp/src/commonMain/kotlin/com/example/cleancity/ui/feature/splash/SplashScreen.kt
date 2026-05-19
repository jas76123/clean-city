package com.example.cleancity.ui.feature.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import cleancity.composeapp.generated.resources.Res
import cleancity.composeapp.generated.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.cleancity.data.repository.AuthRepository
import com.example.cleancity.ui.components.PrimaryButton
import com.example.cleancity.ui.components.SecondaryButton
import com.example.cleancity.ui.feature.auth.LoginScreen
import com.example.cleancity.ui.feature.auth.RegisterScreen
import com.example.cleancity.ui.theme.Accent
import com.example.cleancity.ui.theme.Green400
import com.example.cleancity.ui.theme.Green800
import com.example.cleancity.ui.theme.Green900
import org.koin.compose.koinInject

class SplashScreen : Screen {
    @Composable
    override fun Content() {
        val nav = LocalNavigator.currentOrThrow
        val authRepo: AuthRepository = koinInject()
        SplashContent(
            onLoginClick = { nav.push(LoginScreen()) },
            onRegisterClick = { nav.push(RegisterScreen()) },
            onGuestClick = { authRepo.continueAsGuest() },
        )
    }
}

class SplashLoaderScreen : Screen {
    @Composable
    override fun Content() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Green800, Green900))),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Accent)
        }
    }
}

@Composable
private fun SplashContent(
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onGuestClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Green800, Green900)))
            .safeDrawingPadding()
            .padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(20.dp))
        // Logo
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(Res.drawable.app_logo),
                contentDescription = "Чистый Город",
                modifier = Modifier.size(120.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = buildAnnotatedString {
                    append("Чистый ")
                    withStyle(SpanStyle(color = Accent)) { append("Город") }
                },
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Сообщайте о проблемах\nВлияйте на свой город",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
        }
        // Actions
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryButton(text = "Войти", onClick = onLoginClick, backgroundColor = Accent, contentColor = Green900)
            SecondaryButton(text = "Регистрация", onClick = onRegisterClick)
            Text(
                text = "Зайти как гость",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .clickable(onClick = onGuestClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

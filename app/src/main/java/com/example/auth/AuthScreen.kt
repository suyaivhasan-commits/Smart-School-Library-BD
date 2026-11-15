package com.example.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.components.AppLogoBadge
import com.example.data.AuthRepository
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.classTitle
import com.example.ui.i18n.streamTitle
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthScreen(
    authRepository: AuthRepository,
    onAuthSuccess: () -> Unit,
    onToggleLanguage: () -> Unit
) {
    val strings = LocalAppStrings.current
    val lang = strings.language
    val isBn = lang == AppLanguage.BN
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var selectedClass by remember { mutableIntStateOf(5) }
    var selectedStream by remember { mutableStateOf("SCIENCE") }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var infoMessage by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    // Silent auto sign-in check on startup per Credential Manager guidelines
    LaunchedEffect(Unit) {
        authRepository.attemptSilentGoogleSignIn(context).onSuccess { user ->
            if (user != null) onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(NavyDeep, NavyPrimary, MaterialTheme.colorScheme.background)
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("auth_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onToggleLanguage,
                    modifier = Modifier.testTag("auth_language_toggle")
                ) {
                    Text(
                        text = if (isBn) "English" else "বাংলা",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            AppLogoBadge(size = 76.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Smart School Library BD",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = strings.appTagline,
                style = MaterialTheme.typography.bodyMedium,
                color = EmeraldPrimary
            )

            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    TabRow(
                        selectedTabIndex = if (isRegisterMode) 1 else 0,
                        containerColor = Color.Transparent
                    ) {
                        Tab(
                            selected = !isRegisterMode,
                            onClick = {
                                isRegisterMode = false
                                errorMessage = null
                            },
                            text = { Text(strings.loginTitle, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("auth_tab_login")
                        )
                        Tab(
                            selected = isRegisterMode,
                            onClick = {
                                isRegisterMode = true
                                errorMessage = null
                            },
                            text = { Text(strings.registerTitle, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.testTag("auth_tab_register")
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (infoMessage != null) {
                        Surface(
                            color = EmeraldPrimary.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = infoMessage!!,
                                color = EmeraldDark,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (isRegisterMode) {
                        OutlinedTextField(
                            value = fullName,
                            onValueChange = { fullName = it },
                            label = { Text(strings.fullNameLabel) },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("register_name_input")
                        )
                        Spacer(Modifier.height(10.dp))
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(strings.emailLabel) },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_input")
                    )

                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(strings.passwordLabel) },
                        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Toggle password visibility"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_password_input")
                    )

                    if (isRegisterMode) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = strings.classesHeader,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (5..14).forEach { cls ->
                                FilterChip(
                                    selected = selectedClass == cls,
                                    onClick = { selectedClass = cls },
                                    label = { Text(classTitle(cls, lang)) }
                                )
                            }
                        }

                        if (selectedClass in 9..10) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = strings.selectStreamTitle,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("SCIENCE", "HUMANITIES", "BUSINESS").forEach { code ->
                                    FilterChip(
                                        selected = selectedStream == code,
                                        onClick = { selectedStream = code },
                                        label = { Text(streamTitle(code, lang)) }
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    resetEmail = email
                                    showResetDialog = true
                                },
                                modifier = Modifier.testTag("forgot_password_button")
                            ) {
                                Text(strings.forgotPassword)
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            errorMessage = null
                            infoMessage = null
                            if (email.isBlank() || password.isBlank()) {
                                errorMessage = if (isBn) "ইমেইল এবং পাসওয়ার্ড পূরণ করুন।" else "Please enter email and password."
                                return@Button
                            }
                            if (isRegisterMode && fullName.isBlank()) {
                                errorMessage = if (isBn) "শিক্ষার্থীর পূর্ণ নাম লিখুন।" else "Please enter student full name."
                                return@Button
                            }
                            isLoading = true
                            scope.launch {
                                val res = if (isRegisterMode) {
                                    authRepository.registerWithEmail(
                                        name = fullName,
                                        email = email,
                                        password = password,
                                        selectedClass = selectedClass,
                                        selectedStream = if (selectedClass in 9..10) selectedStream else "NONE"
                                    )
                                } else {
                                    authRepository.signInWithEmail(email, password)
                                }
                                isLoading = false
                                res.onSuccess {
                                    onAuthSuccess()
                                }.onFailure { err ->
                                    errorMessage = AuthRepository.translateAuthError(err, lang)
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RoyalBlue,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("auth_primary_submit_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Text(
                                text = if (isRegisterMode) strings.registerButton else strings.loginButton,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = if (isBn) " অথবা " else " OR ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }

                    Spacer(Modifier.height(14.dp))

                    // Interactive Google Sign-In via Jetpack Credential Manager
                    OutlinedButton(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity == null) {
                                errorMessage = if (isBn) "Google সাইন-ইন শুরু করা যায়নি।" else "Unable to launch Google Sign-In."
                                return@OutlinedButton
                            }
                            errorMessage = null
                            isLoading = true
                            scope.launch {
                                val res = authRepository.signInWithGoogleInteractive(
                                    activityContext = activity,
                                    onCancelled = { isLoading = false }
                                )
                                isLoading = false
                                res.onSuccess { user ->
                                    if (user != null) onAuthSuccess()
                                }.onFailure { err ->
                                    errorMessage = AuthRepository.translateAuthError(err, lang)
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = EmeraldDark
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = strings.googleSignIn,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (isBn) {
                    "নিরাপদ ক্লাউড সিঙ্ক • ব্যক্তিগত বুকমার্ক ও নোট • এনসিটিবি পাঠ্যবই"
                } else {
                    "Secure Cloud Sync • Private Notes & Bookmarks • NCTB Textbooks"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    if (isBn) "পাসওয়ার্ড রিসেট করুন" else "Reset Password",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (isBn) {
                            "আপনার নিবন্ধিত ইমেইল ঠিকানা দিন। পাসওয়ার্ড রিসেট লিংক পাঠানো হবে।"
                        } else {
                            "Enter your registered email address to receive a password reset link."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(strings.emailLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetEmail.isBlank()) return@Button
                        scope.launch {
                            authRepository.sendPasswordReset(resetEmail)
                                .onSuccess {
                                    showResetDialog = false
                                    infoMessage = if (isBn) {
                                        "পাসওয়ার্ড রিসেট ইমেইল পাঠানো হয়েছে। ইনবক্স পরীক্ষা করুন।"
                                    } else {
                                        "Password reset email sent. Please check your inbox."
                                    }
                                }
                                .onFailure { err ->
                                    showResetDialog = false
                                    errorMessage = AuthRepository.translateAuthError(err, lang)
                                }
                        }
                    }
                ) {
                    Text(strings.confirm)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

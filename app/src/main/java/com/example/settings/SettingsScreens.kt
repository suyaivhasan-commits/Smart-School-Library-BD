package com.example.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.components.AppLogoBadge
import com.example.components.DestructiveConfirmDialog
import com.example.components.NavyScreenTopBar
import com.example.data.ThemeMode
import com.example.data.UserPreferencesState
import com.example.model.AppSettings
import com.example.model.UserProfile
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.EmeraldDark
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.RoyalBlue

@Composable
fun SettingsScreen(
    preferences: UserPreferencesState,
    appSettings: AppSettings,
    userProfile: UserProfile?,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetLanguage: (AppLanguage) -> Unit,
    onSetNotifications: (Boolean) -> Unit,
    onOpenLegalDoc: (String) -> Unit,
    onRequestAccountDeletion: () -> Unit,
    onLogout: () -> Unit,
    onOpenWelcomeIntro: (() -> Unit)? = null
) {
    val strings = LocalAppStrings.current
    val isBn = strings.language == AppLanguage.BN

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDownloadPolicyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }

    val themeLabel = when (preferences.themeMode) {
        ThemeMode.LIGHT -> "Light Mode"
        ThemeMode.DARK -> "Dark Mode"
        ThemeMode.SYSTEM -> if (isBn) "সিস্টেম ডিফল্ট" else "System Default"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_screen")
    ) {
        NavyScreenTopBar(
            title = strings.featureSettings,
            subtitle = if (isBn) "থিম, ভাষা, নোটিফিকেশন ও নীতিমালা" else "Theme, Language, Notifications & Legal",
            onBack = onBack
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsActionRow(
                            icon = Icons.Filled.DarkMode,
                            title = if (isBn) "থিম নির্বাচন" else "App Theme",
                            valueText = themeLabel,
                            testTag = "settings_theme_row",
                            onClick = { showThemeDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = RoyalBlue)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = if (isBn) "নোটিফিকেশন" else "Notifications",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = preferences.notificationsEnabled,
                                onCheckedChange = onSetNotifications,
                                modifier = Modifier.testTag("settings_notification_switch")
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        SettingsActionRow(
                            icon = Icons.Filled.Language,
                            title = if (isBn) "ভাষা / Language" else "Language / ভাষা",
                            valueText = if (preferences.language == AppLanguage.BN) "বাংলা" else "English",
                            testTag = "settings_language_row",
                            onClick = { showLanguageDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        SettingsActionRow(
                            icon = Icons.Filled.Download,
                            title = if (isBn) "অফলাইন ডাউনলোড নীতি" else "Offline Download Policy",
                            valueText = if (appSettings.downloadsEnabled) {
                                if (isBn) "অনুমোদিত" else "Enabled"
                            } else {
                                if (isBn) "নিয়ন্ত্রিত" else "Restricted"
                            },
                            testTag = "settings_download_policy_row",
                            onClick = { showDownloadPolicyDialog = true }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        if (onOpenWelcomeIntro != null) {
                            SettingsActionRow(
                                icon = Icons.Filled.Info,
                                title = if (isBn) "অ্যাপ পরিচিতি / স্বাগতম পর্দা" else "About / Introduction",
                                testTag = "settings_welcome_intro_row",
                                onClick = onOpenWelcomeIntro
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        }

                        SettingsActionRow(
                            icon = Icons.Filled.PrivacyTip,
                            title = if (isBn) "গোপনীয়তা নীতি" else "Privacy Policy",
                            testTag = "settings_privacy_row",
                            onClick = { onOpenLegalDoc("PRIVACY") }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        SettingsActionRow(
                            icon = Icons.Filled.Description,
                            title = if (isBn) "শর্তাবলী" else "Terms & Conditions",
                            testTag = "settings_terms_row",
                            onClick = { onOpenLegalDoc("TERMS") }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                        SettingsActionRow(
                            icon = Icons.Filled.Info,
                            title = if (isBn) "সম্পর্কে ও যোগাযোগ" else "About & Contact",
                            testTag = "settings_about_row",
                            onClick = { onOpenLegalDoc("ABOUT") }
                        )
                    }
                }

                // Account Info & Deletion Request Section
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isBn) "অ্যাকাউন্ট তথ্য" else "Account Information",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = userProfile?.email?.ifBlank { "-" } ?: "-",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        if (userProfile?.accountDeletionRequested == true) {
                            Text(
                                text = if (isBn) {
                                    "আপনার অ্যাকাউন্ট মুছে ফেলার অনুরোধ প্রক্রিয়াধীন রয়েছে।"
                                } else {
                                    "Your account deletion request has been recorded and is pending review."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ErrorRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            TextButton(
                                onClick = { showDeleteAccountConfirm = true },
                                modifier = Modifier.testTag("request_account_deletion_button")
                            ) {
                                Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = ErrorRed)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isBn) "অ্যাকাউন্ট মুছে ফেলার অনুরোধ করুন" else "Request Account Deletion",
                                    color = ErrorRed
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("settings_logout_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = ErrorRed)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.logoutButton, color = ErrorRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text(if (isBn) "থিম নির্বাচন করুন" else "Select Theme", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to (if (isBn) "সিস্টেম থিম (System)" else "System Default"),
                        ThemeMode.LIGHT to (if (isBn) "লাইট থিম (Light Mode)" else "Light Mode"),
                        ThemeMode.DARK to (if (isBn) "ডার্ক থিম (Dark Mode)" else "Dark Mode")
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.themeMode == mode,
                                onClick = {
                                    onSetThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(if (isBn) "ভাষা নির্বাচন করুন" else "Select Language", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    listOf(
                        AppLanguage.BN to "বাংলা (প্রাথমিক ভাষা)",
                        AppLanguage.EN to "English (Secondary)"
                    ).forEach { (langChoice, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetLanguage(langChoice)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = preferences.language == langChoice,
                                onClick = {
                                    onSetLanguage(langChoice)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showDownloadPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadPolicyDialog = false },
            title = { Text(if (isBn) "কেন্দ্রীয় ডাউনলোড নিয়ন্ত্রণ নীতি" else "Download Policy", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (appSettings.downloadsEnabled) {
                        if (isBn) {
                            "অ্যাডমিন অনুমোদিত পাঠ্যবই ও গাইডের জন্য ডিভাইসে পিডিএফ ডাউনলোড সক্রিয় রয়েছে। রিডারের ভেতরে অনুমোদিত বইয়ে ডাউনলোড বাটন দেখা যাবে।"
                        } else {
                            "Device PDF downloads are enabled for items permitted by the Super Admin."
                        }
                    } else {
                        if (isBn) {
                            "কপিরাইট সুরক্ষার স্বার্থে বর্তমানে অ্যাপের বাইরে পিডিএফ ডাউনলোড বন্ধ রয়েছে। আপনি অ্যাপের ভেতরেই নির্বিঘ্নে সকল বই পড়তে পারবেন।"
                        } else {
                            "External PDF file downloads are currently disabled by the Super Admin. All books can be read seamlessly inside the built-in reader."
                        }
                    }
                )
            },
            confirmButton = {
                Button(onClick = { showDownloadPolicyDialog = false }) { Text(strings.close) }
            }
        )
    }

    if (showDeleteAccountConfirm) {
        DestructiveConfirmDialog(
            title = if (isBn) "অ্যাকাউন্ট মুছে ফেলার অনুরোধ?" else "Request Account Deletion?",
            message = if (isBn) {
                "আপনার অ্যাকাউন্ট ও ব্যক্তিগত তথ্য মুছে ফেলার অনুরোধ অ্যাডমিনের কাছে নথিভুক্ত করা হবে। আপনি কি নিশ্চিত?"
            } else {
                "Your request to delete your account and personal study records will be submitted. Continue?"
            },
            onConfirm = onRequestAccountDeletion,
            onDismiss = { showDeleteAccountConfirm = false }
        )
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    valueText: String? = null,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (!valueText.isNullOrBlank()) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun LegalInfoScreen(
    docType: String,
    appSettings: AppSettings,
    onBack: () -> Unit
) {
    val strings = LocalAppStrings.current
    val isBn = strings.language == AppLanguage.BN

    val screenTitle = when (docType) {
        "PRIVACY" -> if (isBn) "গোপনীয়তা নীতি" else "Privacy Policy"
        "TERMS" -> if (isBn) "শর্তাবলী" else "Terms & Conditions"
        else -> if (isBn) "সম্পর্কে" else "About"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("legal_info_screen_$docType")
    ) {
        NavyScreenTopBar(
            title = screenTitle,
            onBack = onBack
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AppLogoBadge(size = 76.dp)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = NavyPrimary, fontWeight = FontWeight.ExtraBold)) {
                                    append("Smart School\n")
                                }
                                withStyle(SpanStyle(color = EmeraldDark, fontWeight = FontWeight.ExtraBold)) {
                                    append("Library BD")
                                }
                            },
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Version 1.0.0",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isBn) {
                                "শিক্ষার জন্য একটি স্মার্ট ডিজিটাল স্কুল লাইব্রেরি অ্যাপ।\nপঞ্চম থেকে দশম শ্রেণির শিক্ষার্থীদের বিশ্বস্ত সহায়ক।"
                            } else {
                                "A smart digital school library application for Classes 5 to 10."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (docType == "ABOUT") {
                            Spacer(Modifier.height(18.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(14.dp))
                            ContactInfoRow(Icons.Filled.Email, if (isBn) "ইমেইল" else "Email", appSettings.contactEmail)
                            Spacer(Modifier.height(10.dp))
                            ContactInfoRow(Icons.Filled.Public, if (isBn) "ওয়েবসাইট" else "Website", appSettings.websiteUrl)
                            Spacer(Modifier.height(10.dp))
                            ContactInfoRow(Icons.Filled.Call, if (isBn) "সাপোর্ট" else "Support", appSettings.contactPhone)
                        } else {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(14.dp))
                            Text(
                                text = if (docType == "PRIVACY") appSettings.privacyPolicyBn else appSettings.termsAndConditionsBn,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Text(
                    text = "© Smart School Library BD",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ContactInfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = RoyalBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

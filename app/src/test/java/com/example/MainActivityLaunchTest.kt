package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp-xxhdpi")
class MainActivityLaunchTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_firstLaunchShowsAuthScreenDirectly() {
        composeRule.waitUntil(timeoutMillis = 5000L) {
            composeRule.onAllNodes(hasTestTag("auth_screen")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("auth_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email_input").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_primary_submit_button").assertIsDisplayed()
    }
}

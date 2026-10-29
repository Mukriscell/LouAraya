package cl.duoc.louaraya.presentation

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.ext.junit.rules.ActivityScenarioRule
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import kotlinx.coroutines.runBlocking


internal fun AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>.signInDemo(alias: String) {
    val cases = (activity.application as LouArayaApplication).container.localUseCases
    runBlocking { cases.initialize(); cases.signIn(alias, "LouDemo2026!") }
    activityRule.scenario.recreate()

    waitUntil(15_000) { onAllNodes(hasText("Escanear QR") or hasTestTag("tab-home")).fetchSemanticsNodes().isNotEmpty() }
    if (onAllNodes(hasTestTag("tab-home")).fetchSemanticsNodes().isNotEmpty()) onNodeWithTag("tab-home").performClick()
    waitUntil(15_000) { onAllNodes(hasText("Escanear QR")).fetchSemanticsNodes().isNotEmpty() }
}

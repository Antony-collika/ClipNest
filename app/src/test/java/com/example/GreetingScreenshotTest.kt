package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.data.model.ClipboardCardProjection
import com.example.data.model.ContentType
import com.example.ui.theme.ClipboardManagerTheme
import com.example.ui.vault.ClipboardCardItem
import com.example.ui.vault.VaultTopBar
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun vault_card_screenshot() {
    val sampleCard = ClipboardCardProjection(
      id = 1L,
      preview = "Secure offline-first clipboard card preview.\nSecond line details.",
      createdAtMillis = 1700000000000L,
      sortOrder = 1000L,
      sourceApp = "Manual Entry",
      contentType = ContentType.TEXT,
      pinned = true,
      isSensitive = false
    )

    composeTestRule.setContent {
      ClipboardManagerTheme {
        ClipboardCardItem(
          card = sampleCard,
          isSelected = false,
          isSensitiveRevealed = false,
          isMaskingEnabled = true,
          canMoveUp = false,
          canMoveDown = true,
          onToggleSelect = {},
          onLongPress = {},
          onCopy = {},
          onToggleRevealSensitive = {},
          onMoveUp = {},
          onMoveDown = {}
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/vault_card.png")
  }
}

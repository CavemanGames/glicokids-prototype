package com.glicokids.prototype.util

import android.app.Application
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.glicokids.prototype.R
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import kotlin.math.pow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Module 6 — locks in WCAG 2.1 AA text contrast (>= 4.5:1 for normal text, the threshold
 * from handoff-android.md §10.5: "Contraste AA (4.5:1)") after an audit found ~30 contrast
 * failures across 6 of the Module 6 screens, traced to two distinct defects:
 *
 * 1. `text_muted_light` (colors.xml) is too light against every real light background it is
 *    used on in this app (white, the parent-area `#F5F3FA`, and `lavender_bg`).
 * 2. `Theme.GlicoKids` never overrides `colorSurface`/`colorOnSurface`, so on Android 12+ the
 *    unstyled `tgInputMode` toggle buttons in `activity_glucose_log.xml` resolve those roles
 *    from dynamic (Material You) color instead of the project palette — measured on-device as
 *    `#938F99` text on `#4A4458`/`#EDE7F6`, neither of which exists anywhere in colors.xml.
 *
 * Colors under test are read from the real resources/theme/layout, not hardcoded here, so a
 * regression that reintroduces either defect fails this suite again.
 */
@RunWith(RobolectricTestRunner::class)
class ContrastAccessibilityTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    // WCAG 2.1 AA threshold for normal-size text (handoff-android.md §10.5).
    private val MIN_CONTRAST_NORMAL_TEXT = 4.5

    // Module `app/` is the working directory for the `testDebugUnitTest` task, so these
    // relative paths match the ones used to build the app (same files that ship on-device).
    private val themesXml = File("src/main/res/values/themes.xml").readText()
    private val colorsXml = File("src/main/res/values/colors.xml").readText()
    private val glucoseLogLayoutXml = File("src/main/res/layout/activity_glucose_log.xml").readText()
    private val toggleBgColorXml = File("src/main/res/color/toggle_mode_bg_color.xml").readText()
    private val toggleTextColorXml = File("src/main/res/color/toggle_mode_text_color.xml").readText()

    // --- WCAG 2.1 relative-luminance contrast ratio --------------------------------------
    // https://www.w3.org/TR/WCAG21/#contrast-minimum — proper sRGB linearization, not a
    // perceived-brightness approximation.

    private fun srgbChannelToLinear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Int): Double {
        val r = srgbChannelToLinear(Color.red(color))
        val g = srgbChannelToLinear(Color.green(color))
        val b = srgbChannelToLinear(Color.blue(color))
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(foreground: Int, background: Int): Double {
        val fgLum = relativeLuminance(foreground)
        val bgLum = relativeLuminance(background)
        val lighter = maxOf(fgLum, bgLum)
        val darker = minOf(fgLum, bgLum)
        return (lighter + 0.05) / (darker + 0.05)
    }

    // --- Defect 1: text_muted_light is too light on the light backgrounds it is used on ---

    @Test
    fun `text_muted_light meets AA contrast on white`() {
        val foreground = ContextCompat.getColor(context, R.color.text_muted_light)
        val ratio = contrastRatio(foreground, Color.WHITE)
        assertWithMessage("text_muted_light on #FFFFFF").that(ratio).isAtLeast(MIN_CONTRAST_NORMAL_TEXT)
    }

    @Test
    fun `text_muted_light meets AA contrast on the parent area background`() {
        val foreground = ContextCompat.getColor(context, R.color.text_muted_light)
        // #F5F3FA is used directly as an android:background literal (fragment_parent_area.xml
        // and others) rather than through a named color token.
        val ratio = contrastRatio(foreground, Color.parseColor("#F5F3FA"))
        assertWithMessage("text_muted_light on #F5F3FA").that(ratio).isAtLeast(MIN_CONTRAST_NORMAL_TEXT)
    }

    @Test
    fun `text_muted_light meets AA contrast on lavender_bg`() {
        val foreground = ContextCompat.getColor(context, R.color.text_muted_light)
        val background = ContextCompat.getColor(context, R.color.lavender_bg)
        val ratio = contrastRatio(foreground, background)
        assertWithMessage("text_muted_light on lavender_bg").that(ratio).isAtLeast(MIN_CONTRAST_NORMAL_TEXT)
    }

    // handoff-android.md §10.5 also says "cinza #8A80A8 só sobre fundo claro." A cheap
    // source-grep guard for that was tried here and dropped: matching `@color/<dark-token>"`
    // anywhere in a layout also matches unrelated attributes like `android:textColor` or
    // `app:hintTextColor` that legitimately use a dark tone as a foreground on a light
    // screen (e.g. activity_new_meal.xml's `android:textColor="@color/primary_dark"`), which
    // produced false failures. Catching only true background usage would need a real XML
    // parser scoped to `android:background`/`app:cardBackgroundColor` next to the muted
    // token's element — left out rather than ship a check that fails for the wrong reason.

    // --- Defect 2: Theme.GlicoKids must pin colorSurface/colorOnSurface itself ------------
    // Robolectric does not simulate Android 12+ dynamic (Material You) color extraction from
    // the device wallpaper, so a runtime theme-attribute resolution here would resolve to the
    // Material3 library's own static baseline and pass whether or not Theme.GlicoKids
    // overrides anything — it would not reproduce the on-device defect. These tests instead
    // check the theme's own source declaration, which is exactly what the fix must add and
    // what a future regression (someone deleting the override) would remove.

    private fun assertThemeDeclaresPaletteColor(attrName: String) {
        val item = Regex("""<item name="$attrName">([^<]+)</item>""").find(themesXml)
        assertWithMessage(
            "Theme.GlicoKids must declare `$attrName` explicitly — without it, Android 12+ " +
                "resolves it from dynamic (Material You) color instead of the project palette"
        ).that(item).isNotNull()

        val colorRef = item!!.groupValues[1].trim()
        assertWithMessage("$attrName must reference a @color/ token, not a raw literal")
            .that(colorRef).matches("""@color/\w+""")

        val colorName = colorRef.removePrefix("@color/")
        assertWithMessage("$attrName references @color/$colorName, which must be declared in colors.xml")
            .that(colorsXml).contains("name=\"$colorName\"")
    }

    @Test
    fun `Theme_GlicoKids declares colorSurface from the project palette`() {
        assertThemeDeclaresPaletteColor("colorSurface")
    }

    @Test
    fun `Theme_GlicoKids declares colorOnSurface from the project palette`() {
        assertThemeDeclaresPaletteColor("colorOnSurface")
    }

    // --- Defect 2b: the b16 toggle buttons must declare their own colors ------------------
    // Same Robolectric limitation as above: resolving btnModeManual/btnModeSensor's current
    // text color under Robolectric would not reproduce the dynamic-color leak either, since
    // there is no wallpaper-derived palette to inject in the JVM. So this checks the layout
    // source instead: the two toggle buttons must stop relying on the theme default and
    // declare android:textColor (and ideally backgroundTint) themselves.

    private fun toggleButtonTag(id: String): String {
        val match = Regex(
            """<Button\b[^>]*android:id="@\+id/$id"[^>]*/>""",
            RegexOption.DOT_MATCHES_ALL
        ).find(glucoseLogLayoutXml)
        assertWithMessage("could not find Button with id $id in activity_glucose_log.xml")
            .that(match).isNotNull()
        return match!!.value
    }

    @Test
    fun `glucose log toggle button btnModeManual declares its own text color`() {
        assertWithMessage("btnModeManual must not rely on the theme's colorOnSurface default")
            .that(toggleButtonTag("btnModeManual")).contains("android:textColor")
    }

    @Test
    fun `glucose log toggle button btnModeSensor declares its own text color`() {
        assertWithMessage("btnModeSensor must not rely on the theme's colorOnSurface default")
            .that(toggleButtonTag("btnModeSensor")).contains("android:textColor")
    }

    // --- Defect 2c: the b16 toggle selectors must use the tokens the design spec calls for ---
    // GlicoKids Design.dc.html:464 (b16) pins the toggle to @color/primary (checked
    // background) + white (checked text) and text_muted_light (unchecked text) on a white
    // container. A prior contrast fix picked working-but-off-spec tokens (primary_dark, ink)
    // instead — it passed the text-declared-a-color checks above without matching the design.
    // These tests read the actual selector files so a return to the off-spec tokens fails here,
    // even though the color itself might still pass WCAG.

    private fun selectorColorRef(xml: String, checked: Boolean): String {
        val pattern = if (checked) {
            Regex("""<item\s+android:color="([^"]+)"\s+android:state_checked="true"\s*/>""")
        } else {
            Regex("""<item\s+android:color="([^"]+)"\s*/>""")
        }
        val match = pattern.find(xml)
        assertWithMessage(
            "could not find the ${if (checked) "checked" else "default"} <item> in the selector"
        ).that(match).isNotNull()
        return match!!.groupValues[1]
    }

    // Resolves a selector's `android:color` reference to an actual ARGB int, the same way the
    // OS would, so the contrast math below runs against the resolved color rather than a
    // hardcoded hex — a token swap changes what this resolves to, not just a string match.
    private fun resolveColorRef(ref: String): Int {
        if (ref == "@android:color/white") return Color.WHITE
        val name = ref.removePrefix("@color/")
        val resId = context.resources.getIdentifier(name, "color", context.packageName)
        assertWithMessage("could not resolve color resource $ref").that(resId).isNotEqualTo(0)
        return ContextCompat.getColor(context, resId)
    }

    @Test
    fun `toggle background selector matches the b16 design spec tokens`() {
        assertWithMessage("checked background must be @color/primary (GlicoKids Design.dc.html:464)")
            .that(selectorColorRef(toggleBgColorXml, checked = true)).isEqualTo("@color/primary")
        assertWithMessage("default background must be white (GlicoKids Design.dc.html:464)")
            .that(selectorColorRef(toggleBgColorXml, checked = false)).isEqualTo("@android:color/white")
    }

    @Test
    fun `toggle text selector matches the b16 design spec tokens`() {
        assertWithMessage("checked text must be white (GlicoKids Design.dc.html:464)")
            .that(selectorColorRef(toggleTextColorXml, checked = true)).isEqualTo("@android:color/white")
        assertWithMessage("default text must be @color/text_muted_light (GlicoKids Design.dc.html:464)")
            .that(selectorColorRef(toggleTextColorXml, checked = false)).isEqualTo("@color/text_muted_light")
    }

    @Test
    fun `toggle resolved colors meet AA contrast in both checked and unchecked states`() {
        val checkedText = resolveColorRef(selectorColorRef(toggleTextColorXml, checked = true))
        val checkedBg = resolveColorRef(selectorColorRef(toggleBgColorXml, checked = true))
        val uncheckedText = resolveColorRef(selectorColorRef(toggleTextColorXml, checked = false))
        val uncheckedBg = resolveColorRef(selectorColorRef(toggleBgColorXml, checked = false))

        val checkedRatio = contrastRatio(checkedText, checkedBg)
        val uncheckedRatio = contrastRatio(uncheckedText, uncheckedBg)

        assertWithMessage("checked toggle state (white text on @color/primary): ratio $checkedRatio")
            .that(checkedRatio).isAtLeast(MIN_CONTRAST_NORMAL_TEXT)
        assertWithMessage("unchecked toggle state (text_muted_light on white): ratio $uncheckedRatio")
            .that(uncheckedRatio).isAtLeast(MIN_CONTRAST_NORMAL_TEXT)
    }

    @Test
    fun `the on-device measured toggle contrast violates AA in both checked and unchecked states`() {
        // Characterization test: documents the exact pair the human audit measured on a real
        // device (Android 12+, dynamic color active). Neither #938F99 nor #4A4458 exist in
        // colors.xml — they come from the OS, not the project — so they cannot be read from a
        // resource; they are recorded here as evidence that the WCAG math above correctly
        // flags them, not as a check against the app's own resources. This test does not
        // re-verify a fix (a real device must confirm the toggle is fixed on-device, per the
        // adb screencap step in the project's verification routine) — it only pins the math
        // against the reported failure so the ratio computation itself cannot silently regress.
        val measuredText = Color.parseColor("#938F99")
        val checkedBackground = Color.parseColor("#4A4458")
        val uncheckedBackground = Color.parseColor("#EDE7F6") // == lavender_bg

        assertThat(contrastRatio(measuredText, checkedBackground)).isLessThan(MIN_CONTRAST_NORMAL_TEXT)
        assertThat(contrastRatio(measuredText, uncheckedBackground)).isLessThan(MIN_CONTRAST_NORMAL_TEXT)
    }
}

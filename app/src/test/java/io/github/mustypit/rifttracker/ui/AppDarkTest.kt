package io.github.mustypit.rifttracker.ui

import org.robolectric.annotation.Config

/** Every AppTest flow again with the phone in dark mode; screenshots are saved as dark_*.png. */
@Config(qualifiers = "+night")
class AppDarkTest : AppTest()

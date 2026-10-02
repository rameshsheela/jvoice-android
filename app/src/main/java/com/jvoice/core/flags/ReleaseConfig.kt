package com.jvoice.core.flags

/**
 * Build-time switches for the Play Store build (the `play-release` branch).
 *
 * The Play build is the reader app only: no staff sign-in, no desk, no AI
 * Shorts studio, no Study module and no comments. Unlike [FeatureFlags], these
 * cannot be turned on from the server - they are compile-time constants, so
 * R8 drops the code behind them from the release APK altogether.
 *
 * The full app, with every desk feature, lives on the `desk-features` branch.
 */
object ReleaseConfig {
    /** True for the Play build: the reader experience and nothing else. */
    const val READER_ONLY = true
}

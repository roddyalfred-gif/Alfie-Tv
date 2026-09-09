package com.alfietv.player

/**
 * Compatibility hook for the player activity's channel-zap flow.
 * The visual overlay is refreshed by MainActivity's periodic overlay renderer.
 */
fun MainActivity.showZapOverlay() {
    // MainActivity owns the overlay views; keep this hook safe when no channel metadata is available.
}

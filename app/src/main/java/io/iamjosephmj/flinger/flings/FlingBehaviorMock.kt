package io.iamjosephmj.flinger.flings

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.iamjosephmj.flinger.FlingConfiguration

@Composable
fun flingBehavior(
    scrollConfiguration: FlingConfiguration = FlingConfiguration()
): FlingBehavior {
    return ScrollableDefaults.flingBehavior()
}

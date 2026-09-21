package com.kashif_e.backdrop

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class BackdropEffectScopeTest {

    private fun newScope(): BackdropEffectScopeImpl = object : BackdropEffectScopeImpl() {
        override val shape: Shape get() = RectangleShape
    }

    /**
     * The whole point of [BackdropEffectScopeImpl.obtainPlatformShader]: `effects.lens()`/
     * `effects.frostedGrain()` call this every `updateEffects()` run - including every animation
     * frame during a BackdropInteractionController-driven hover/press transition - and must not
     * recompile the same constant SKSL source each time. A second call with the same key must
     * return the exact same [PlatformRuntimeShader] instance, not a fresh compile.
     */
    @Test
    fun sameKeyReturnsTheSameCachedShaderInstance() {
        val scope = newScope()
        val source = "half4 main(float2 coord) { return half4(1); }"

        val first = scope.obtainPlatformShader("test-shader", source)
        val second = scope.obtainPlatformShader("test-shader", source)

        assertSame(first, second, "obtainPlatformShader must cache by key, not recompile every call")
    }

    @Test
    fun differentKeysReturnDifferentShaderInstances() {
        val scope = newScope()
        val source = "half4 main(float2 coord) { return half4(1); }"

        val a = scope.obtainPlatformShader("shader-a", source)
        val b = scope.obtainPlatformShader("shader-b", source)

        assertNotSame(a, b)
    }

    @Test
    fun resetClearsTheShaderCache() {
        val scope = newScope()
        val source = "half4 main(float2 coord) { return half4(1); }"

        val beforeReset = scope.obtainPlatformShader("test-shader", source)
        scope.reset()
        val afterReset = scope.obtainPlatformShader("test-shader", source)

        assertNotSame(
            beforeReset,
            afterReset,
            "reset() (called from DrawBackdropNode.onDetach()) must drop cached shaders so a " +
                "detached-then-reattached node doesn't hold stale compiled state",
        )
    }
}

package com.mongodb.jbplugin.i18n

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import java.util.function.Supplier

object TelemetryMessages {
    @NonNls
    private const val BUNDLE = "messages.TelemetryBundle"
    private val instance = DynamicBundle(TelemetryMessages::class.java, BUNDLE)

    fun message(
        key:
            @PropertyKey(resourceBundle = BUNDLE)
            String,
        vararg params: Any,
    ): @Nls String = instance.getMessage(key, *params)

    fun lazyMessage(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): Supplier<@Nls String> = instance.getLazyMessage(key, *params)
}

/**
 * Provides functions to simplify assertions.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Waits until the block function finishes successfully up to 1 second (or the provided timeout).
 *
 * Example usages:
 *
 * ```kt
 * eventually {
 *    verify(mock).myFunction()
 * }
 * // with custom timeout
 * eventually(timeout = Duration.ofSeconds(5)) {
 *    verify(mock).myFunction()
 * }
 * ```
 *
 * @param timeout
 * @param fn
 */
fun eventually(timeout: Duration = Duration.ofSeconds(1), fn: () -> Unit) {
    waitFor(timeout, Duration.ofMillis(50)) {
        runCatching {
            fn()
            true
        }.getOrDefault(false)
    }
}
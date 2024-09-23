/**
 * Provides functions to simplify assertions.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import java.time.Duration

/**
 * Closes all open modals by using the ESC key. This should be enough for recovering on a lot of different test cases.
 */
fun RemoteRobot.closeAllOpenModals() {
    // just do it a few times, to ensure we closed all modals
    for (i in 0..5) {
        keyboard {
            escape()
        }
    }
}

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
 * @param recovery
 */
fun eventually(
    timeout: Duration = Duration.ofSeconds(1),
    recovery: () -> Unit = {},
    fn: () -> Unit
) {
    waitFor(timeout, Duration.ofMillis(50)) {
        val result = runCatching {
            fn()
            true
        }.getOrDefault(false)

        if (!result) {
            recovery()
        }

        result
    }
}

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
 * @return
 */
fun <T> eventually(
    timeout: Duration = Duration.ofSeconds(1),
    fn: () -> T
): T? = waitFor<T?>(
    timeout,
    Duration.ofMillis(
        50
    )
) {
    val result = runCatching {
        fn()
    }

    result.isSuccess to result.getOrNull()
}

/**
 * Provides functions to simplify assertions.
 */

package com.mongodb.jbplugin.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.stepsProcessing.step
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
 * eventually("Doing something") {
 *    verify(mock).myFunction()
 * }
 * // with custom timeout
 * eventually(description = "Doing something", timeout = Duration.ofSeconds(5)) {
 *    verify(mock).myFunction()
 * }
 * ```
 *
 * @param timeout
 * @param fn
 * @param recovery
 */
fun eventually(
    description: String,
    timeout: Duration = Duration.ofSeconds(1),
    recovery: () -> Unit = {},
    fn: (Int) -> Unit
) {
    eventually(timeout, recovery) { attempt ->
        step("$description, attempt=$attempt") {
            fn(attempt)
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
    fn: (Int) -> Unit
) {
    var attempt = 1
    waitFor(timeout, Duration.ofMillis(50)) {
        val result = runCatching {
            fn(attempt++)
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
    fn: (Int) -> T
): T? {
    var attempt = 1
    return waitFor<T?>(
        timeout,
        Duration.ofMillis(
            50
        )
    ) {
        val result = runCatching {
            fn(attempt++)
        }

        result.isSuccess to result.getOrNull()
    }
}

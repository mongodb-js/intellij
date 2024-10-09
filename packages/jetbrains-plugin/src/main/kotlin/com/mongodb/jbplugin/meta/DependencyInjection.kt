package com.mongodb.jbplugin.meta

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.rd.util.launchChildBackground
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.reflect.KProperty

class DependencyInjection<out T>(private val cm: ComponentManager, private val javaClass: Class<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return cm.getService<T>(javaClass)
    }
}

/**
 * Injects an application service into a val.
 *
 * ```kt
 *  val pluginActivated by service<PluginActivatedProbe>()
 * ```
 */
inline fun <reified T> service(): DependencyInjection<T> {
    return DependencyInjection(ApplicationManager.getApplication(), T::class.java)
}

/**
 * Injects a project-wide service into a val.
 *
 * ```kt
 * val readModelProvider by project.service<DataGripBasedReadModelProvider>()
 * ```
 */

inline fun <reified T> Project.service(): DependencyInjection<T> {
    return DependencyInjection(this, T::class.java)
}

// We probably don't want to use a lot of threads for this. First because updating the state should
// be straightforward (to avoid lag) and because state updates should be sequential.
private val asyncStatusRefreshScope = Dispatchers.IO.limitedParallelism(1)

class AsyncState<E : Any, T : Any>(
    parent: Disposable,
    private val sharedFlow: SharedFlow<E>,
    private val accessor: E.() -> T
) : Disposable {
    private lateinit var state: T
    private val scope: CoroutineScope = CoroutineScope(asyncStatusRefreshScope)

    init {
        scope.launchChildBackground {
            sharedFlow.collectLatest {
                state = it.accessor()
            }
        }

        Disposer.register(parent, this)
    }

    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>
    ): T {
        return state
    }

    override fun dispose() {
        scope.cancel()
    }
}

/**
 * We will need to synchronise the state from a SharedFlow when we are doing work in the background.
 * However, we want to make it seamless, and we don't want to by coupled to the shared flow syntax.
 * This delegate should synchronise the variable with the latest event of the SharedFlow. For example:
 *
 * ```kt
 * class EventNumber(val numberInEvent: Int)
 * val sharedFlow: SharedFlow<EventNumber>
 * val lastNumber by sharedFlow.latest { numberInEvent }
 * ```
 *
 * It can also be used to compute an expression across properties in an event:
 * ```kt
 * class EventNumber(val numberInEvent: Int, val otherNumber: Int)
 * val sharedFlow: SharedFlow<EventNumber>
 * val computedNumber by sharedFlow.latest { numberInEvent * otherNumber }
 * ```
 *
 * It will be closed when the current project is closed. If no projects are available,
 * it will be closed when the IDE is closed.
 */
fun <E : Any, T : Any> SharedFlow<E>.latest(
    parent: Disposable = ProjectManager.getInstance().openProjects.firstOrNull()
        ?: ApplicationManager.getApplication(),
    prop: E.() -> T
): AsyncState<E, T> =
    AsyncState(
        parent,
        this,
        prop
    )

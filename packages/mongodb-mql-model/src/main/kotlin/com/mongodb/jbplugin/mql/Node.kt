/**
 * Node and components are the main building blocks of the query model.
 */

package com.mongodb.jbplugin.mql

import com.mongodb.jbplugin.mql.components.HasCollectionReference

/** A component represents the semantics of a Node. When a Node has some special meaning, we will attach a component
 * that adds that specific meaning. For example, take into consideration the following Java Query:
 * ```java
 * Filters.eq("myField", 42)
 * ```
 * This query contains three semantic units:
 * * Named: The operation that is executing has a name.
 * * HasFieldReference: It refers to a field in a document.
 * * HasValueReference: It refers to a value in code.
 *
 * @see Node
 * @see com.mongodb.jbplugin.mql.components.Named
 * @see com.mongodb.jbplugin.mql.components.HasFieldReference
 * @see com.mongodb.jbplugin.mql.components.HasValueReference
 */
interface Component

/**
 * Represents the building block of a query in this model. Nodes don't have any semantic per se, but they can
 * hold Components that will give them specific meaning.
 *
 * @see Component
 *
 * @param S
 * @property source
 * @property components
 */
data class Node<S>(
    val source: S,
    val components: List<Component>,
) {
    inline fun <reified C : Component> component(): C? = components.firstOrNull { it is C } as C?

    fun <C : Component> component(withClass: Class<C>): C? = components.firstOrNull { withClass.isInstance(it) } as C?

    inline fun <reified C : Component> components(): List<C> = components.filterIsInstance<C>()

    inline fun <reified C : Component> hasComponent(): Boolean = component<C>() != null

    /**
     * Creates a copy of the query and modifies the database reference in every HasCollectionReference component
     * with the provided database
     *
     * @param database
     * @return
     */
    fun queryWithOverwrittenDatabase(database: String) = this.copy { component ->
        if (component is HasCollectionReference<*>) {
            component.copy(database = database)
        } else {
            component
        }
    }

    /**
     * Creates a copy of the Node by modifying the underlying component list
     *
     * @param componentModifier A mapper function that is provided with a component (one at a time) from the Node's
     * component list and is expected to either provide a modified component or the same component. The return value of
     * this function is used to create a new component list for the copied Node
     */
    fun copy(componentModifier: (component: Component) -> Component): Node<S> =
        copy(source = source, components = components.map(componentModifier))
}

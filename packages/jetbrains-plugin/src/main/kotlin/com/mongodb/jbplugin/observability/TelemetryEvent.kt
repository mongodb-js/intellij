/**
 * This file defines all the properties and events that can be sent to
 * Segment. New properties (or fields) will be added in the TelemetryProperty
 * enum class. New events will be added inside the TelemetryEvent sealed class.
 */

package com.mongodb.jbplugin.observability

/**
 * Represents a field in Segment. New fields will be added here, where the
 * publicName is how it will look like in Segment.
 *
 * @property publicName Name of the field in Segment.
 */
internal enum class TelemetryProperty(val publicName: String)

/**
 * Represents an event that will be sent to Segment. Essentially, all
 * events will be sent as Track events to Segment but PluginActivated,
 * that will be sent as an identify event. This logic is in the
 * TelemetryService.
 *
 * @property name Name of the event
 * @property userId Identifier of the user emitting an event
 * @property properties Map of fields sent to Segment.
 * @see TelemetryService
 */
internal sealed class TelemetryEvent(
    internal val name: String,
    internal val userId: String,
    internal val properties: Map<TelemetryProperty, Any>
) {
    /**
     * @property jbId
     */
    internal data class PluginActivated(val jbId: String) : TelemetryEvent(
        name = "plugin-activated",
        userId = jbId,
        properties = emptyMap()
    )
}

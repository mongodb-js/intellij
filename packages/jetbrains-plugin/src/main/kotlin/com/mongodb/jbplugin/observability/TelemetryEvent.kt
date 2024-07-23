/**
 * This file defines all the properties and events that can be sent to
 * Segment. New properties (or fields) will be added in the TelemetryProperty
 * enum class. New events will be added inside the TelemetryEvent sealed class.
 */

package com.mongodb.jbplugin.observability

import com.mongodb.jbplugin.dialects.Dialect

/**
 * Represents a field in Segment. New fields will be added here, where the
 * publicName is how it will look like in Segment.
 *
 * @property publicName Name of the field in Segment.
 */
internal enum class TelemetryProperty(
    val publicName: String,
) {
    IS_ATLAS("is_atlas"),
    IS_LOCAL_ATLAS("is_local_atlas"),
    IS_LOCALHOST("is_localhost"),
    IS_ENTERPRISE("is_enterprise"),
    IS_GENUINE("is_genuine"),
    NON_GENUINE_SERVER_NAME("non_genuine_server_name"),
    SERVER_OS_FAMILY("server_os_family"),
    VERSION("version"),
    ERROR_CODE("error_code"),
    ERROR_NAME("error_name"),
    DIALECT("dialect"),
    AUTOCOMPLETE_TYPE("ac_type"),
    AUTOCOMPLETE_COUNT("ac_count"),
;
}

/**
 * Represents an event that will be sent to Segment. Essentially, all
 * events will be sent as Track events to Segment but PluginActivated,
 * that will be sent as an identify event. This logic is in the
 * TelemetryService.
 *
 * @property name Name of the event
 * @property properties Map of fields sent to Segment.
 * @see TelemetryService
 */
internal sealed class TelemetryEvent(
    internal val name: String,
    internal val properties: Map<TelemetryProperty, Any>,
) {
    /**
     * Represents the event that is emitted when the plugin is started.
     */
    data object PluginActivated : TelemetryEvent(
        name = "plugin-activated",
        properties = emptyMap(),
    )

    /**
     * Represents the event that is emitted when the plugin connects
     * to a cluster.
     *
     * @param isAtlas
     * @param isLocalhost
     * @param isEnterprise
     * @param isGenuine
     * @param nonGenuineServerName
     * @param serverOsFamily
     * @param version
     * @param isLocalAtlas
     */
    class NewConnection(
        isAtlas: Boolean,
        isLocalAtlas: Boolean,
        isLocalhost: Boolean,
        isEnterprise: Boolean,
        isGenuine: Boolean,
        nonGenuineServerName: String?,
        serverOsFamily: String?,
        version: String?,
    ) : TelemetryEvent(
            name = "new-connection",
            properties =
                mapOf(
                    TelemetryProperty.IS_ATLAS to isAtlas,
                    TelemetryProperty.IS_LOCAL_ATLAS to isLocalAtlas,
                    TelemetryProperty.IS_LOCALHOST to isLocalhost,
                    TelemetryProperty.IS_ENTERPRISE to isEnterprise,
                    TelemetryProperty.IS_GENUINE to isGenuine,
                    TelemetryProperty.NON_GENUINE_SERVER_NAME to (nonGenuineServerName ?: ""),
                    TelemetryProperty.SERVER_OS_FAMILY to (serverOsFamily ?: ""),
                    TelemetryProperty.VERSION to (version ?: ""),
                ),
        )

    /**
     * Represents the event that is emitted when the there is an error
     * during the connection to a MongoDB Cluster.
     *
     * @param isAtlas
     * @param isLocalhost
     * @param isEnterprise
     * @param isGenuine
     * @param nonGenuineServerName
     * @param serverOsFamily
     * @param version
     * @param isLocalAtlas
     * @param errorCode
     * @param errorName
     */
    class ConnectionError(
        errorCode: String,
        errorName: String,
        isAtlas: Boolean?,
        isLocalAtlas: Boolean?,
        isLocalhost: Boolean?,
        isEnterprise: Boolean?,
        isGenuine: Boolean?,
        nonGenuineServerName: String?,
        serverOsFamily: String?,
        version: String?,
    ) : TelemetryEvent(
            name = "connection-error",
            properties =
                mapOf(
                    TelemetryProperty.IS_ATLAS to (isAtlas ?: ""),
                    TelemetryProperty.IS_LOCAL_ATLAS to (isLocalAtlas ?: ""),
                    TelemetryProperty.IS_LOCALHOST to (isLocalhost ?: ""),
                    TelemetryProperty.IS_ENTERPRISE to (isEnterprise ?: ""),
                    TelemetryProperty.IS_GENUINE to (isGenuine ?: ""),
                    TelemetryProperty.NON_GENUINE_SERVER_NAME to (nonGenuineServerName ?: ""),
                    TelemetryProperty.SERVER_OS_FAMILY to (serverOsFamily ?: ""),
                    TelemetryProperty.VERSION to (version ?: ""),
                    TelemetryProperty.ERROR_CODE to errorCode,
                    TelemetryProperty.ERROR_NAME to errorName,
                ),
        )

    /**
     * Aggregated count of events of the same autocomplete type, sent to Segment
     * every hour if not empty.
     *
 * @param dialect
 * @param autocompleteType
 * @param count
 */
    class AutocompleteGroupEvent(
        dialect: Dialect<*>,
        autocompleteType: String,
        count: Int,
    ) : TelemetryEvent(
            name = "autocomplete-selected",
            properties =
                mapOf(
                    TelemetryProperty.DIALECT to dialect.javaClass.simpleName,
                    TelemetryProperty.AUTOCOMPLETE_TYPE to autocompleteType,
                    TelemetryProperty.AUTOCOMPLETE_COUNT to count,
                ),
        )
}

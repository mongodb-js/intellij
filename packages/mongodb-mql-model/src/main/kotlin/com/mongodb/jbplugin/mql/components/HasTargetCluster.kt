package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component
import io.github.z4kn4fein.semver.Version

/**
 * Represents the target cluster metadata that the query is designed for. This information is not gathered from the
 * query itself, but from the connection that is attached when processing the query. We are storing this information
 * here as some functionalities will depend on the cluster version or environment.
 *
 * @property version
 */
data class HasTargetCluster(
    val version: Version
) : Component {
    val majorVersion: Version = Version(version.major, 0)
}

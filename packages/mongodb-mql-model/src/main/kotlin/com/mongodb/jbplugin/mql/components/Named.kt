/**
 * The named component represents metadata of the name of the operation that a specific
 * node represents.
 */

package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Component

/**
 * A canonical representation of operations. All operations relevant in the driver
 * are listed here.
 *
 * @property canonical
 */
enum class Name(val canonical: String) {
    ALL("all"),
    AND("and"),
    BITS_ALL_CLEAR("bitsAllClear"),
    BITS_ALL_SET("bitsAllSet"),
    BITS_ANY_CLEAR("bitsAnyClear"),
    BITS_ANY_SET("bitsAnySet"),
    COMBINE("combine"),
    ELEM_MATCH("elementMatch"),
    EQ("eq"),
    EXISTS("exists"),
    GEO_INTERSECTS("geoIntersects"),
    GEO_WITHIN("geoWithin"),
    GEO_WITHIN_BOX("geoWithinBox"),
    GEO_WITHIN_CENTER("geoWithinCenter"),
    GEO_WITHIN_CENTER_SPHERE("geoWithinCenterSphere"),
    GEO_WITHIN_POLYGON("geoWithinPolygon"),
    GT("gt"),
    GTE("gte"),
    IN("in"),
    LT("lt"),
    LTE("lte"),
    NE("ne"),
    NEAR("near"),
    NEAR_SPHERE("nearSphere"),
    NIN("nin"),
    NOR("nor"),
    NOT("not"),
    OR("or"),
    REGEX("regex"),
    SET("set"),
    SIZE("size"),
    TEXT("text"),
    TYPE("type"),
    UNSET("unset"),
    MATCH("match"),
    PROJECT("project"),
    INCLUDE("include"),
    EXCLUDE("exclude"),
    GROUP("group"),
    SUM("sum"),
    AVG("avg"),
    FIRST("first"),
    LAST("last"),
    TOP("top"),
    BOTTOM("bottom"),
    MAX("max"),
    MIN("min"),
    PUSH("push"),
    ADD_TO_SET("addToSet"),
    SORT("sort"),
    ASCENDING("ascending"),
    DESCENDING("descending"),
    UNKNOWN("<unknown operator>"),
    ;

    override fun toString(): String = canonical

    companion object {
        fun from(canonical: String): Name =
            entries.firstOrNull { it.canonical == canonical } ?: UNKNOWN
    }
}

/**
 * Represents an operation that has a name. Usually most operations will have a name.
 *
 * @property name
 */
data class Named(
    val name: Name,
) : Component

/**
 * File containing all the inspection bridge implementations
 */

package com.mongodb.jbplugin.inspections.bridge

import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.impl.FieldCheckLinterInspection

/**
 * This is the bridge implementation that connects our query linter for JavaDriver to IntelliJ's inspections.
 */
class JavaDriverFieldCheckInspectionBridge :
    AbstractMongoDbInspectionBridge(
        JavaDriverDialect,
        FieldCheckLinterInspection,
    )

/**
 * This is the bridge implementation that connects our query linter for SpringCriteria to IntelliJ's inspections.
 */
class SpringCriteriaFieldCheckInspectionBridge :
    AbstractMongoDbInspectionBridge(
        SpringCriteriaDialect,
        FieldCheckLinterInspection,
    )

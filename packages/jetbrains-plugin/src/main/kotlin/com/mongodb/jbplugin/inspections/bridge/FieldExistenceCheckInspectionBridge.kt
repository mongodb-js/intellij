package com.mongodb.jbplugin.inspections.bridge

import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.inspections.AbstractMongoDbInspectionBridge
import com.mongodb.jbplugin.inspections.impl.FieldCheckLinterInspection

/**
 * This is the bridge implementation that connects our query linter to IntelliJ's inspections.
 */
class FieldExistenceCheckInspectionBridge : AbstractMongoDbInspectionBridge(JavaDriverDialect,
 FieldCheckLinterInspection)

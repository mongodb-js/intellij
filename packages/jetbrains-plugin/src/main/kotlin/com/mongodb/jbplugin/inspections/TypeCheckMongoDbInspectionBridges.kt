package com.mongodb.jbplugin.inspections

import com.mongodb.jbplugin.dialects.javadriver.JavaDriverDialect
import com.mongodb.jbplugin.dialects.springdata.queryannotation.SpringDataQueryAnnotationDialect
import com.mongodb.jbplugin.inspections.mongodb.TypeCheckMongoDbInspection

class JavaDriverTypeCheckMongoDbInspectionBridge : AbstractMongoDbInspectionBridge(
    JavaDriverDialect,
    TypeCheckMongoDbInspection,
)

class SpringQueryAnnotationTypeCheckMongoDbInspectionBridge : AbstractMongoDbInspectionBridge(
    SpringDataQueryAnnotationDialect,
    TypeCheckMongoDbInspection,
)

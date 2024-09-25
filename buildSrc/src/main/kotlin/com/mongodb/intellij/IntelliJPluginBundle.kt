package com.mongodb.intellij

import org.gradle.api.provider.Property

interface IntelliJPluginBundle {
    val enableBundle: Property<Boolean>
}
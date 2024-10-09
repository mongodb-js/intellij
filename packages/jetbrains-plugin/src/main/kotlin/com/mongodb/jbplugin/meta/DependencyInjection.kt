package com.mongodb.jbplugin.meta

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.project.Project
import kotlin.reflect.KProperty

class DependencyInjection<out T>(private val cm: ComponentManager, private val javaClass: Class<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return cm.getService<T>(javaClass)
    }
}

inline fun <reified T> service(): DependencyInjection<T> {
    return DependencyInjection(ApplicationManager.getApplication(), T::class.java)
}

inline fun <reified T> Project.service(): DependencyInjection<T> {
    return DependencyInjection(this, T::class.java)
}

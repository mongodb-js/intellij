package com.mongodb.jbplugin.accessadapter.datagrip

import com.intellij.database.dataSource.LocalDataSource
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.mongodb.jbplugin.accessadapter.MongoDBReadModelProvider
import com.mongodb.jbplugin.accessadapter.Slice
import com.mongodb.jbplugin.accessadapter.datagrip.adapter.DataGripMongoDBDriver
import com.mongodb.jbplugin.accessadapter.slice.BuildInfoSlice
import kotlinx.coroutines.runBlocking

@Service(Service.Level.PROJECT)
class DataGripBasedReadModelProvider(
    private val project: Project,
) : MongoDBReadModelProvider<LocalDataSource> {
    private val cachedValues: MutableMap<String, CachedValue<*>> = mutableMapOf()

    override fun <T : Any> slice(dataSource: LocalDataSource, slice: Slice<T>): T {
        return cachedValues
            .computeIfAbsent(slice.javaClass.canonicalName, fromSlice(dataSource, BuildInfoSlice))
            .value as T
    }

    private inline fun <reified T : Any> fromSlice(dataSource: LocalDataSource, slice: Slice<T>): (String) -> CachedValue<T> {
        val cacheManager = CachedValuesManager.getManager(project)
        return {
            cacheManager.createCachedValue {
                runBlocking {
                    val driver = DataGripMongoDBDriver(project, dataSource)
                    val sliceData = slice.queryUsingDriver(driver)

                    CachedValueProvider.Result.create(sliceData, dataSource)
                }
            }
        }
    }
}
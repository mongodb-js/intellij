package com.mongodb.jbplugin.accessadapter

interface Slice<State: Any> {
    suspend fun queryUsingDriver(from: MongoDBDriver): State
}

interface MongoDBReadModelProvider<DataSource> {
    fun <T: Any> slice(dataSource: DataSource, slice: Slice<T>): T
}
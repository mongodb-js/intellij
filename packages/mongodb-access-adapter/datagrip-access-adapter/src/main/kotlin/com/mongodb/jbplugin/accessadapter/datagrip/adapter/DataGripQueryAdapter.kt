package com.mongodb.jbplugin.accessadapter.datagrip.adapter

import com.google.gson.Gson
import com.intellij.database.dataSource.DatabaseConnectionCore
import com.intellij.database.datagrid.DataRequest

class DataGripQueryAdapter<out T: Any>(
    private val queryScript: String,
    private val resultClass: Class<T>,
    private val gson: Gson,
    ownerEx: OwnerEx,
    private val continuation: (List<T>) -> Unit,
): DataRequest.RawRequest(ownerEx) {
    override fun processRaw(p0: Context?, p1: DatabaseConnectionCore?) {
        val remoteConnection = p1!!.remoteConnection
        val statement = remoteConnection.prepareStatement(queryScript.trimIndent())

        val listOfResults = mutableListOf<T>()
        val resultSet = statement.executeQuery()

        if (resultClass.isPrimitive || resultClass == String::class.java) {
            while (resultSet.next()) {
                listOfResults.add(resultSet.getObject(1) as T)
            }
        } else {
            while (resultSet.next()) {
                val hashMap = resultSet.getObject(1) as Map<String, Any>
                val result = gson.fromJson(gson.toJson(hashMap), resultClass)
                listOfResults.add(result)
            }
        }

        continuation(listOfResults)
    }
}
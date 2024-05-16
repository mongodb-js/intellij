package com.mongodb.accessadapter

data class Group(
    val id: String,
    val name: String
)
data class Cluster(
    val groupId: String?,
    val groupName: String?,
    val organizationId: String?,
    val organizationName: String?,
    val clusterName: String,
    val clusterUrl: String,
)

interface ClusterProvider {
    suspend fun getClusters(): List<Cluster>
    suspend fun listOfGroups(): List<Group>
    suspend fun createFreeCluster(name: String, groupId: String?, local: Boolean, sampleDataset: Boolean): Cluster
}
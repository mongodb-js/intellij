package com.mongodb.accessadapter.atlas

import com.mongodb.accessadapter.Cluster
import com.mongodb.accessadapter.ClusterProvider
import com.mongodb.accessadapter.Group
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class AtlasClusterProvider(
    private val publicKey: String,
    private val privateKey: String,
): ClusterProvider {
    private val client = HttpClient(CIO) {
        install(Auth) {
            digest {
                credentials {
                    DigestAuthCredentials(publicKey, privateKey)
                }
            }
        }

        install(ContentNegotiation) {
            gson()
        }

        defaultRequest {
            url("https://cloud.mongodb.com/api/atlas/")
            contentType(ContentType.Application.Json)
            header("Accept", "application/vnd.atlas.2023-01-01+json")
        }
    }


    override suspend fun getClusters(): List<Cluster> = withContext(Dispatchers.IO) {
        val response = client.get("v2/clusters")
        when (response.status.value) {
            200 ->
                response.body<GetClusterListBody>()
                    .results.flatMap { group ->
                        group.clusters.flatMap { cluster ->
                            getClusterByGroupAndName(
                                group.groupId,
                                group.groupName,
                                group.orgId,
                                group.orgName,
                                cluster.name
                            )
                        }
                    }
            else -> emptyList()
        }
    }

    override suspend fun listOfGroups(): List<Group> {
        val response = client.get("v2/groups")
        return if (response.status.value == 200) {
            response.body<GroupResponse>().results.map {
                Group(it.id, it.name)
            }
        } else {
            emptyList()
        }
    }

    override suspend fun createFreeCluster(name: String, groupId: String?, local: Boolean, sampleDataset: Boolean): Cluster {
        val response = client.post("v1.0/groups/${groupId}/clusters") {
            setBody(ClusterDefinitionRequest(
                clusterType = "REPLICASET",
                name = name,
                providerSettings = ClusterDefinitionRequestProviderSettings(
                    providerName = "TENANT",
                    backingProviderName = "AWS",
                    regionName = "EU_WEST_1",
                    instanceSizeName = "M0"
                )
            ))
        }

        if (response.status.value == 201) {
            val id = response.body<ClusterDefinitionResponse>()
            val startTime = Instant.now()

            val myIp = HttpClient(CIO).get("http://ipecho.net/plain").bodyAsText()
            client.post("v2/groups/${groupId}/accessList") {
                setBody(listOf(IpAddress(myIp)))
            }

            while (true) {
                val status = getClusterStatusByGroupAndName(id.groupId, id.name)
                if (status == "IDLE") {
                    break
                }

                delay(5.seconds)

                val currentTime = Instant.now()
                if (Duration.between(startTime, currentTime) > Duration.ofMinutes(10)) {
                    break
                }
            }

            if (sampleDataset) {
                client.post("v2/groups/${groupId}/sampleDatasetLoad/${name}")
            }

            return getClusterByGroupAndName(id.groupId, "<to fill>", "<to fill>", "<to fill>", id.name)[0]
        }

        throw RuntimeException("Could not work")
    }

    suspend fun getClusterByGroupAndName(
        groupId: String,
        groupName: String,
        organizationId: String,
        organizationName: String,
        clusterName: String
    ): List<Cluster> = withContext(Dispatchers.IO) {
        val response = client.get("v2/groups/${groupId}/clusters/${clusterName}")
        when (response.status.value) {
            200 -> {
                val body = response.body<ClusterDefinitionResponse>()
                if (body.stateName != "IDLE") {
                    emptyList()
                } else {
                    listOf(Cluster(groupId, groupName, organizationId, organizationName, body.name, body.connectionStrings.standardSrv))
                }
            }
            else -> listOf(Cluster(groupId, groupName, organizationId, organizationName, "<could not load>", "<could not load>"))
        }
    }

    suspend fun getClusterStatusByGroupAndName(
        groupId: String,
        clusterName: String,
    ): String = withContext(Dispatchers.IO) {
        val response = client.get("v2/groups/${groupId}/clusters/${clusterName}")
        if (response.status.value !== 200) {
            return@withContext "CREATING"
        }

        val body = response.body<ClusterDefinitionResponse>()
        return@withContext body.stateName
    }
}

private data class ClusterDefinitionRequestProviderSettings(
    val backingProviderName: String,
    val providerName: String,
    val regionName: String,
    val instanceSizeName: String,
)

private data class ClusterDefinitionRequest(
    val clusterType: String,
    val name: String,
    val providerSettings: ClusterDefinitionRequestProviderSettings
)

private data class IpAddress(val ipAddress: String)
private data class GroupResponseGroup(val id: String, val name: String)
private data class GroupResponse(val results: List<GroupResponseGroup>)
private data class ClusterDefinitionConnectionStrings(val standardSrv: String)
private data class ClusterDefinitionResponse(val groupId: String, val name: String, val connectionStrings: ClusterDefinitionConnectionStrings, val stateName: String)
private data class GetClusterListCluster(val name: String)
private data class GetClusterListResults(val groupId: String, val groupName: String, val orgId: String, val orgName: String, val clusters: List<GetClusterListCluster>)
private data class GetClusterListBody(val results: List<GetClusterListResults>)
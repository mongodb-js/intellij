package com.mongodb.accessadapter.atlas

import com.mongodb.accessadapter.SearchIndexProvider
import com.mongodb.accessadapter.VectorSearchDefinition
import io.ktor.client.*
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
import kotlinx.coroutines.withContext

class AtlasSearchIndexProvider(
    private val publicKey: String,
    private val privateKey: String,
): SearchIndexProvider {
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

    override suspend fun createVectorSearchIndex(
        groupId: String,
        clusterName: String,
        index: VectorSearchDefinition
    ) = withContext(Dispatchers.IO) {
        val response = client.post("v2/groups/${groupId}/clusters/${clusterName}/fts/indexes") {
            setBody(index)
        }

        assert(response.status.isSuccess()) { response.bodyAsText() }
    }
}
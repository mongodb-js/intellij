package com.mongodb.accessadapter.atlas

import com.mongodb.accessadapter.DatabaseUser
import com.mongodb.accessadapter.DatabaseUserProvider
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.gson.*

class AtlasDatabaseUserProvider(
    private val publicKey: String,
    private val privateKey: String,
): DatabaseUserProvider {
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

    override suspend fun createUser(groupId: String, username: String, password: CharSequence): DatabaseUser {
        client.post("v2/groups/${groupId}/databaseUsers") {
            setBody(CreateUserRequest("admin", username, password, listOf(CreateUserRole("admin", "atlasAdmin"))))
        }

        return DatabaseUser(groupId, username, password)
    }
}

data class CreateUserRole(
    val databaseName: String,
    val roleName: String,
)
data class CreateUserRequest(
    val databaseName: String,
    val username: String,
    val password: CharSequence,
    val roles: List<CreateUserRole>,
)
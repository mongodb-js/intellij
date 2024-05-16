package com.mongodb.accessadapter

data class DatabaseUser(
    val groupId: String,
    val username: String,
    val password: CharSequence
)

interface DatabaseUserProvider {
    suspend fun createUser(groupId: String, username: String, password: CharSequence): DatabaseUser
}
package com.mongodb.jbplugin.mql.components

import com.mongodb.jbplugin.mql.Namespace
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HasCollectionReferenceTest {
    @Test
    fun `when the underlying reference is Known, it creates a copy with the database modified`() {
        val collectionReference = HasCollectionReference(
            HasCollectionReference.Known(
                Namespace("foo", "bar"),
            )
        )

        val modifiedReference = collectionReference.copy("goo")
        // original is not modified
        assertEquals((collectionReference.reference as HasCollectionReference.Known).namespace.database, "foo")
        assertEquals((modifiedReference.reference as HasCollectionReference.Known).namespace.database, "goo")
    }

    @Test
    fun `when the underlying reference is OnlyCollection, it converts it to Known`() {
        val collectionReference = HasCollectionReference(
            HasCollectionReference.OnlyCollection(
                "bar",
            )
        )

        val modifiedReference = collectionReference.copy("foo")
        // original is not modified
        assertTrue(collectionReference.reference is HasCollectionReference.OnlyCollection)

        assertTrue(modifiedReference.reference is HasCollectionReference.Known)
        assertEquals((modifiedReference.reference as HasCollectionReference.Known).namespace.database, "foo")
    }

    @Test
    fun `when the underlying reference is Unknown, it does nothing`() {
        val collectionReference = HasCollectionReference(
            HasCollectionReference.Unknown
        )

        val modifiedReference = collectionReference.copy("foo")
        // original is not modified
        assertTrue(collectionReference.reference is HasCollectionReference.Unknown)

        assertTrue(modifiedReference.reference is HasCollectionReference.Unknown)
    }
}
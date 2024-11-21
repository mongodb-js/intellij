package com.mongodb.jbplugin.dialects.javadriver.glossary.aggregationparser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialectParser
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.IsCommand
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class AggregationParserTest {

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
      """
    )
    fun `should identify an aggregation as a valid candidate for parsing`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        // The entire aggregation is not a valid candidate
        assertFalse(JavaDriverDialectParser.isCandidateForQuery(query))

        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        // Only the collection call is the valid query
        assertTrue(JavaDriverDialectParser.isCandidateForQuery(actualQuery))
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
      """
    )
    fun `should consider the MongoCollection#aggregate as the attachment for query`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }

        assertEquals(actualQuery, JavaDriverDialectParser.attachment(actualQuery))
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
        """,
    )
    fun `can extract the namespace of an aggregate`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        val parsedAggregate = JavaDriverDialect.parser.parse(actualQuery)

        val knownReference = parsedAggregate.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Known
        val command = parsedAggregate.component<IsCommand>()
        val dialect = parsedAggregate.component<HasSourceDialect>()
        val namespace = knownReference.namespace

        assertEquals(HasSourceDialect.DialectName.JAVA_DRIVER, dialect?.name)
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
        assertEquals(IsCommand.CommandType.AGGREGATE, command?.type)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(List.of()).first();
    }
}
        """,
    )
    fun `should be able to parse an empty aggregation built with List#of method`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("of())") }
        val parsedAggregate = JavaDriverDialect.parser.parse(actualQuery)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()

        assertNotNull(hasAggregation)
        assertEquals(hasAggregation?.children?.isEmpty(), true)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document findBookById(ObjectId id) {
        return this.collection.aggregate(Arrays.asList()).first();
    }
}
        """,
    )
    fun `should be able to parse an empty aggregation built with Arrays#asList method`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val actualQuery = PsiTreeUtil
            .findChildrenOfType(query, PsiMethodCallExpression::class.java)
            .first { it.text.endsWith("asList())") }
        val parsedAggregate = JavaDriverDialect.parser.parse(actualQuery)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()

        assertNotNull(hasAggregation)
        assertEquals(hasAggregation?.children?.isEmpty(), true)
    }
}

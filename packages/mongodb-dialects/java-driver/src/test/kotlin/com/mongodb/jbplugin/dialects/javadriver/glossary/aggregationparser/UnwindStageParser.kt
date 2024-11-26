package com.mongodb.jbplugin.dialects.javadriver.glossary.aggregationparser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class UnwindStageParser {
    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.unwind()
        ));
    }
}
      """
    )
    fun `should be able to parse an empty unwind call`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val unwindStageNode = hasAggregation?.children?.get(0)!!
        assertEquals(1, unwindStageNode.components.size)

        val named = unwindStageNode.component<Named>()!!
        assertEquals(Name.UNWIND, named.name)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.unwind("${'$'}name")
        ));
    }
}
      """
    )
    fun `should be able to parse an unwind call with fieldName`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val unwindStageNode = hasAggregation?.children?.get(0)!!
        assertEquals(2, unwindStageNode.components.size)

        val named = unwindStageNode.component<Named>()!!
        assertEquals(Name.UNWIND, named.name)

        val fieldReference = unwindStageNode.component<HasFieldReference<PsiElement>>()!!.reference
            as HasFieldReference.FromSchema<PsiElement>
        assertEquals("name", fieldReference.fieldName)
        assertEquals("${'$'}name", fieldReference.displayName)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String fieldName = "${'$'}name";
        return this.collection.aggregate(List.of(
            Aggregates.unwind(fieldName)
        ));
    }
}
      """
    )
    fun `should be able to parse an unwind call with fieldName where fieldName is variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val unwindStageNode = hasAggregation?.children?.get(0)!!
        assertEquals(2, unwindStageNode.components.size)

        val named = unwindStageNode.component<Named>()!!
        assertEquals(Name.UNWIND, named.name)

        val fieldReference = unwindStageNode.component<HasFieldReference<PsiElement>>()!!.reference
            as HasFieldReference.FromSchema<PsiElement>
        assertEquals("name", fieldReference.fieldName)
        assertEquals("${'$'}name", fieldReference.displayName)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getUnwindField() {
        return "${'$'}name";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.unwind(getUnwindField())
        ));
    }
}
      """
    )
    fun `should be able to parse an unwind call with fieldName where fieldName is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val unwindStageNode = hasAggregation?.children?.get(0)!!
        assertEquals(2, unwindStageNode.components.size)

        val named = unwindStageNode.component<Named>()!!
        assertEquals(Name.UNWIND, named.name)

        val fieldReference = unwindStageNode.component<HasFieldReference<PsiElement>>()!!.reference
            as HasFieldReference.FromSchema<PsiElement>
        assertEquals("name", fieldReference.fieldName)
        assertEquals("${'$'}name", fieldReference.displayName)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.unwind("${'$'}name", new UnwindOptions())
        ));
    }
}
      """
    )
    fun `should be able to parse an unwind call with fieldName while ignoring the UnwindOptions`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val unwindStageNode = hasAggregation?.children?.get(0)!!
        assertEquals(2, unwindStageNode.components.size)

        val named = unwindStageNode.component<Named>()!!
        assertEquals(Name.UNWIND, named.name)

        val fieldReference = unwindStageNode.component<HasFieldReference<PsiElement>>()!!.reference
            as HasFieldReference.FromSchema<PsiElement>
        assertEquals("name", fieldReference.fieldName)
        assertEquals("${'$'}name", fieldReference.displayName)
    }
}

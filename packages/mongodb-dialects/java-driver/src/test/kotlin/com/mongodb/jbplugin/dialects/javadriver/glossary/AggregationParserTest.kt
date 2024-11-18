package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFieldReference.FromSchema
import com.mongodb.jbplugin.mql.components.HasFilter
import com.mongodb.jbplugin.mql.components.HasSourceDialect
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.HasValueReference.Constant
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
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

    // Assuming that the Filters remains the same in the provided test file
    private fun commonAssertionsOnMatchStageNode(matchStageNode: Node<PsiElement>) {
        val named = matchStageNode.component<Named>()!!
        assertEquals(Name.MATCH, named.name)

        val hasFilters = matchStageNode.component<HasFilter<PsiElement>>()!!
        assertEquals(hasFilters.children.size, 1)

        val filterNode = hasFilters.children[0]

        val filterNameComponent = filterNode.component<Named>()!!
        assertEquals(Name.EQ, filterNameComponent.name)

        val fieldReferenceComponent = filterNode.component<HasFieldReference<PsiElement>>()!!
        assertEquals((fieldReferenceComponent.reference as FromSchema).fieldName, "name")

        val valueReferenceComponent = filterNode.component<HasValueReference<PsiElement>>()!!
        assertEquals((valueReferenceComponent.reference as Constant).value, "MongoDB")
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;import com.mongodb.client.MongoClient;
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

    public AggregateIterable<Document> findBookById(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.match(
                Filters.eq("name", "MongoDB")
            )
        ));
    }
}
        """,
    )
    fun `(Aggregates#match call) should be able to parse a simple call`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(hasAggregation?.children?.size, 1)
        commonAssertionsOnMatchStageNode(hasAggregation?.children?.get(0)!!)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;import com.mongodb.client.MongoClient;
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

    public AggregateIterable<Document> findBookById(ObjectId id) {
        Bson filters = Filters.eq("name", "MongoDB");
        return this.collection.aggregate(List.of(
            Aggregates.match(filters)
        ));
    }
}
        """,
    )
    fun `(Aggregates#match call) should be able to parse when filters are referenced as a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(hasAggregation?.children?.size, 1)
        commonAssertionsOnMatchStageNode(hasAggregation?.children?.get(0)!!)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;import com.mongodb.client.MongoClient;
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
    
    private Bson getFilters() {
        return Filters.eq("name", "MongoDB");
    }

    public AggregateIterable<Document> findBookById(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.match(getFilters())
        ));
    }
}
        """,
    )
    fun `(Aggregates#match call) should be able to parse when filters are retrieved from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(hasAggregation?.children?.size, 1)
        commonAssertionsOnMatchStageNode(hasAggregation?.children?.get(0)!!)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;import com.mongodb.client.MongoClient;
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

    public AggregateIterable<Document> findBookById(ObjectId id) {
        Bson filters = Filters.eq("name", "MongoDB");
        Bson matchStage = Aggregates.match(filters);
        return this.collection.aggregate(List.of(
            matchStage
        ));
    }
}
        """,
    )
    fun `(Aggregates#match call) should be able to parse when match stage is referenced as variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(hasAggregation?.children?.size, 1)
        commonAssertionsOnMatchStageNode(hasAggregation?.children?.get(0)!!)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;import com.mongodb.client.MongoClient;
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
    
    private Bson getMatchStage() {
        Bson filters = Filters.eq("name", "MongoDB");
        return Aggregates.match(filters);
    }

    public AggregateIterable<Document> findBookById(ObjectId id) {
        return this.collection.aggregate(List.of(
            getMatchStage()
        ));
    }
}
        """,
    )
    fun `(Aggregates#match call) should be able to parse when match stage is retrieved from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(hasAggregation?.children?.size, 1)
        commonAssertionsOnMatchStageNode(hasAggregation?.children?.get(0)!!)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;import com.mongodb.client.MongoClient;
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
    
    private Bson getMatchStage() {
        Bson filters = Filters.eq("name", "MongoDB");
        return Aggregates.match(filters);
    }

    public AggregateIterable<Document> findBookById(ObjectId id) {
        List<Bson> pipeline = List.of(
            getMatchStage()
        );
        return this.collection.aggregate(pipeline);
    }
}
        """,
    )
    fun `(Aggregates#match call) should be able to parse when pipeline is referenced as variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(hasAggregation?.children?.size, 1)
        commonAssertionsOnMatchStageNode(hasAggregation?.children?.get(0)!!)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;import com.mongodb.client.MongoClient;
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
    
    private Bson getMatchStage() {
        Bson filters = Filters.eq("name", "MongoDB");
        return Aggregates.match(filters);
    }
    
    private Bson getPipeline() {
        return List.of(
            getMatchStage()
        );
    }

    public AggregateIterable<Document> findBookById(ObjectId id) {
        return this.collection.aggregate(getPipeline());
    }
}
        """,
    )
    fun `(Aggregates#match call) should be able to parse when pipeline is retrieved from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "findBookById")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(hasAggregation?.children?.size, 1)
        commonAssertionsOnMatchStageNode(hasAggregation?.children?.get(0)!!)
    }
}

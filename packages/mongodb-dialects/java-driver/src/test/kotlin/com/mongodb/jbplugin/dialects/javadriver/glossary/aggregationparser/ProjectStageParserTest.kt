package com.mongodb.jbplugin.dialects.javadriver.glossary.aggregationparser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.mql.Node
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasFieldReference.FromSchema
import com.mongodb.jbplugin.mql.components.HasProjections
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.HasValueReference.Inferred
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class ProjectStageParserTest {
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
            Aggregates.project()
        ));
    }
}
      """
    )
    fun `should be able to parse an empty project call`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        val named = projectStageNode.component<Named>()!!
        assertEquals(Name.PROJECT, named.name)

        assertEquals(0, projectStageNode.component<HasProjections<PsiElement>>()!!.children.size)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year"; 
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.include("title", yearField, getAuthorField())
            )
        ));
    }
}
      """
    )
    fun `Projection#include - should be able to parse with varargs`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForIncludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.include(
                    List.of("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#include - should be able to parse with List#of`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForIncludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        List<String> projectedFields = List.of("title", yearField, getAuthorField());
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.include(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#include - should be able to parse with List#of when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForIncludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private List<String> getProjectedFields() {
        String yearField = "year";
        return List.of("title", yearField, getAuthorField());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.include(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#include - should be able to parse with List#of when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForIncludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Arrays;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.include(
                    Arrays.asList("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#include - should be able to parse with Arrays#asList`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForIncludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        List<String> projectedFields = Arrays.asList("title", yearField, getAuthorField());
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.include(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#include - should be able to parse with Arrays#asList when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForIncludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private List<String> getProjectedFields() {
        String yearField = "year";
        return Arrays.asList("title", yearField, getAuthorField());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.include(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#include - should be able to parse with Arrays#asList when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForIncludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year"; 
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.exclude("title", yearField, getAuthorField())
            )
        ));
    }
}
      """
    )
    fun `Projection#exclude - should be able to parse with varargs`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForExcludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.exclude(
                    List.of("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#exclude - should be able to parse with List#of`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForExcludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        List<String> projectedFields = List.of("title", yearField, getAuthorField());
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.exclude(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#exclude - should be able to parse with List#of when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForExcludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private List<String> getProjectedFields() {
        String yearField = "year";
        return List.of("title", yearField, getAuthorField());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.exclude(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#exclude - should be able to parse with List#of when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForExcludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Arrays;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.exclude(
                    Arrays.asList("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#exclude - should be able to parse with Arrays#asList`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForExcludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        List<String> projectedFields = Arrays.asList("title", yearField, getAuthorField());
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.exclude(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#exclude - should be able to parse with Arrays#asList when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForExcludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private List<String> getProjectedFields() {
        String yearField = "year";
        return Arrays.asList("title", yearField, getAuthorField());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.exclude(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#exclude - should be able to parse with Arrays#asList when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForExcludeProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private Bson getThirdProjection() {
        return Projections.exclude(Arrays.asList("published", getAuthorField()));
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        Bson secondProjection = Projections.include(List.of(yearField));
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.fields(
                    Projections.include("title"),
                    secondProjection,
                    getThirdProjection()
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#fields - should be able to parse with varargs`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForFieldsProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private Bson getThirdProjection() {
        return Projections.exclude(Arrays.asList("published", getAuthorField()));
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        Bson secondProjection = Projections.include(List.of(yearField));
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.fields(
                    List.of(
                        Projections.include("title"),
                        secondProjection,
                        getThirdProjection()
                    )
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#fields - should be able to parse with List#of`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForFieldsProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private Bson getThirdProjection() {
        return Projections.exclude(Arrays.asList("published", getAuthorField()));
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        Bson secondProjection = Projections.include(List.of(yearField));
        List<Bson> projections = List.of(
            Projections.include("title"),
            secondProjection,
            getThirdProjection()
        );
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.fields(
                    projections
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#fields - should be able to parse with List#of when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForFieldsProjection(projectStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.bson.conversions.Bson;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private String getAuthorField() {
        return "author";
    }
    
    private Bson getThirdProjection() {
        return Projections.exclude(Arrays.asList("published", getAuthorField()));
    }
    
    private List<Bson> getProjections() {
        String yearField = "year";
        Bson secondProjection = Projections.include(List.of(yearField));
        return List.of(
            Projections.include("title"),
            secondProjection,
            getThirdProjection()
        );
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.project(
                Projections.fields(
                    getProjections()
                )
            )
        ));
    }
}
      """
    )
    fun `Projection#fields - should be able to parse with List#of when the list comes from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val projectStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForFieldsProjection(projectStageNode)
    }

    companion object {
        fun commonAssertionsForIncludeProjection(projectStageNode: Node<PsiElement>) {
            val named = projectStageNode.component<Named>()!!
            assertEquals(Name.PROJECT, named.name)

            val projections = projectStageNode.component<HasProjections<PsiElement>>()!!
            assertEquals(3, projections.children.size)

            val titleProjection = projections.children[0]
            assertEquals(Name.INCLUDE, titleProjection.component<Named>()!!.name)

            val titleProjectionFieldRef =
                (titleProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "title",
                titleProjectionFieldRef.fieldName
            )

            val titleProjectionValueRef =
                (titleProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                titleProjectionValueRef.value
            )

            val yearProjection = projections.children[1]
            assertEquals(Name.INCLUDE, yearProjection.component<Named>()!!.name)

            val yearProjectionFieldRef =
                (yearProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "year",
                yearProjectionFieldRef.fieldName
            )

            val yearProjectionValueRef =
                (yearProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                yearProjectionValueRef.value
            )

            val authorProjection = projections.children[2]
            assertEquals(Name.INCLUDE, authorProjection.component<Named>()!!.name)

            val authorProjectionFieldRef =
                (authorProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "author",
                authorProjectionFieldRef.fieldName
            )

            val authorProjectionValueRef =
                (authorProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                authorProjectionValueRef.value
            )
        }

        fun commonAssertionsForExcludeProjection(projectStageNode: Node<PsiElement>) {
            val named = projectStageNode.component<Named>()!!
            assertEquals(Name.PROJECT, named.name)

            val projections = projectStageNode.component<HasProjections<PsiElement>>()!!
            assertEquals(3, projections.children.size)

            val titleProjection = projections.children[0]
            assertEquals(Name.EXCLUDE, titleProjection.component<Named>()!!.name)

            val titleProjectionFieldRef =
                (titleProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "title",
                titleProjectionFieldRef.fieldName
            )

            val titleProjectionValueRef =
                (titleProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                titleProjectionValueRef.value
            )

            val yearProjection = projections.children[1]
            assertEquals(Name.EXCLUDE, yearProjection.component<Named>()!!.name)

            val yearProjectionFieldRef =
                (yearProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "year",
                yearProjectionFieldRef.fieldName
            )

            val yearProjectionValueRef =
                (yearProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                yearProjectionValueRef.value
            )

            val authorProjection = projections.children[2]
            assertEquals(Name.EXCLUDE, authorProjection.component<Named>()!!.name)

            val authorProjectionFieldRef =
                (authorProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "author",
                authorProjectionFieldRef.fieldName
            )

            val authorProjectionValueRef =
                (authorProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                authorProjectionValueRef.value
            )
        }

        fun commonAssertionsForFieldsProjection(projectStageNode: Node<PsiElement>) {
            val named = projectStageNode.component<Named>()!!
            assertEquals(Name.PROJECT, named.name)

            val projections = projectStageNode.component<HasProjections<PsiElement>>()!!
            assertEquals(4, projections.children.size)

            val titleProjection = projections.children[0]
            assertEquals(Name.INCLUDE, titleProjection.component<Named>()!!.name)

            val titleProjectionFieldRef =
                (titleProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "title",
                titleProjectionFieldRef.fieldName
            )

            val titleProjectionValueRef =
                (titleProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                titleProjectionValueRef.value
            )

            val yearProjection = projections.children[1]
            assertEquals(Name.INCLUDE, yearProjection.component<Named>()!!.name)

            val yearProjectionFieldRef =
                (yearProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "year",
                yearProjectionFieldRef.fieldName
            )

            val yearProjectionValueRef =
                (yearProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                yearProjectionValueRef.value
            )

            val publishedProjection = projections.children[2]
            assertEquals(Name.EXCLUDE, publishedProjection.component<Named>()!!.name)

            val publishedProjectionFieldRef =
                (publishedProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "published",
                publishedProjectionFieldRef.fieldName
            )

            val publishedProjectionValueRef =
                (publishedProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                publishedProjectionValueRef.value
            )

            val authorProjection = projections.children[3]
            assertEquals(Name.EXCLUDE, authorProjection.component<Named>()!!.name)

            val authorProjectionFieldRef =
                (authorProjection.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "author",
                authorProjectionFieldRef.fieldName
            )

            val authorProjectionValueRef =
                (authorProjection.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                authorProjectionValueRef.value
            )
        }
    }
}

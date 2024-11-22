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
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.HasValueReference.Inferred
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class SortStageParserTest {
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
            Aggregates.sort()
        ));
    }
}
      """
    )
    fun `should be able to parse an empty sort call`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        val named = sortStageNode.component<Named>()!!
        assertEquals(Name.SORT, named.name)

        assertEquals(0, sortStageNode.component<HasSorts<PsiElement>>()!!.children.size)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.ascending("title", yearField, getAuthorField())
            )
        ));
    }
}
      """
    )
    fun `Sorts#ascending - should be able to parse with varargs`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForAscendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.ascending(
                    List.of("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#ascending - should be able to parse with List#of`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForAscendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.ascending(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#ascending - should be able to parse with List#of when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForAscendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.ascending(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#ascending - should be able to parse with List#of when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForAscendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.ascending(
                    Arrays.asList("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#ascending - should be able to parse with Arrays#asList`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForAscendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.ascending(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#ascending - should be able to parse with Arrays#asList when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForAscendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.ascending(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#ascending - should be able to parse with Arrays#asList when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForAscendingSort(sortStageNode)
    }

    // ////////////////////////////////////////////////////////////////////////

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.descending("title", yearField, getAuthorField())
            )
        ));
    }
}
      """
    )
    fun `Sorts#descending - should be able to parse with varargs`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForDescendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.descending(
                    List.of("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#descending - should be able to parse with List#of`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForDescendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.descending(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#descending - should be able to parse with List#of when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForDescendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.descending(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#descending - should be able to parse with List#of when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForDescendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.descending(
                    Arrays.asList("title", yearField, getAuthorField())
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#descending - should be able to parse with Arrays#asList`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForDescendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.descending(
                    projectedFields
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#descending - should be able to parse with Arrays#asList when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForDescendingSort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
            Aggregates.sort(
                Sorts.descending(
                    getProjectedFields()
                )
            )
        ));
    }
}
      """
    )
    fun `Sorts#descending - should be able to parse with Arrays#asList when the list is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForDescendingSort(sortStageNode)
    }

    // ////////////////////////////////////////////////////

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
    
    private Bson getThirdSort() {
        return Sorts.descending(Arrays.asList("published", getAuthorField()));
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        Bson secondSort = Sorts.ascending(List.of(yearField));
        return this.collection.aggregate(List.of(
            Aggregates.sort(
                Sorts.orderBy(
                    Sorts.ascending("title"),
                    secondSort,
                    getThirdSort()
                )
            )
        ));
    }
}
      """
    )
    fun `Sort#orderBy - should be able to parse with varargs`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForOrderBySort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
    
    private Bson getThirdSort() {
        return Sorts.descending(Arrays.asList("published", getAuthorField()));
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        Bson secondSort = Sorts.ascending(List.of(yearField));
        return this.collection.aggregate(List.of(
            Aggregates.sort(
                Sorts.orderBy(
                    List.of(
                        Sorts.ascending("title"),
                        secondSort,
                        getThirdSort()
                    )
                )
            )
        ));
    }
}
      """
    )
    fun `Sort#orderBy - should be able to parse with List#of`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForOrderBySort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
    
    private Bson getThirdSort() {
        return Sorts.descending(Arrays.asList("published", getAuthorField()));
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String yearField = "year";
        Bson secondSort = Sorts.ascending(List.of(yearField));
        List<Bson> sorts = List.of(
            Sorts.ascending("title"),
            secondSort,
            getThirdSort()
        );
        return this.collection.aggregate(List.of(
            Aggregates.sort(
                Sorts.orderBy(
                    sorts
                )
            )
        ));
    }
}
      """
    )
    fun `Sort#orderBy - should be able to parse with List#of when the list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForOrderBySort(sortStageNode)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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
    
    private Bson getThirdSort() {
        return Sorts.descending(Arrays.asList("published", getAuthorField()));
    }
    
    private List<Bson> getSorts() {
        String yearField = "year";
        Bson secondSort = Sorts.ascending(List.of(yearField));
        return List.of(
            Sorts.ascending("title"),
            secondSort,
            getThirdSort()
        );
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.sort(
                Sorts.orderBy(
                    getSorts()
                )
            )
        ));
    }
}
      """
    )
    fun `Sort#orderBy - should be able to parse with List#of when the list comes from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod(
            "Aggregation",
            "getAllBookTitles"
        )
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val sortStageNode = hasAggregation?.children?.get(0)!!

        commonAssertionsForOrderBySort(sortStageNode)
    }

    companion object {
        fun commonAssertionsForAscendingSort(sortStageNode: Node<PsiElement>) {
            val named = sortStageNode.component<Named>()!!
            assertEquals(Name.SORT, named.name)

            val sorts = sortStageNode.component<HasSorts<PsiElement>>()!!
            assertEquals(3, sorts.children.size)

            val titleSort = sorts.children[0]
            assertEquals(Name.ASCENDING, titleSort.component<Named>()!!.name)

            val titleSortFieldRef =
                (titleSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "title",
                titleSortFieldRef.fieldName
            )

            val titleSortValueRef =
                (titleSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                titleSortValueRef.value
            )

            val yearSort = sorts.children[1]
            assertEquals(Name.ASCENDING, yearSort.component<Named>()!!.name)

            val yearSortFieldRef =
                (yearSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "year",
                yearSortFieldRef.fieldName
            )

            val yearSortValueRef =
                (yearSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                yearSortValueRef.value
            )

            val authorSort = sorts.children[2]
            assertEquals(Name.ASCENDING, authorSort.component<Named>()!!.name)

            val authorSortFieldRef =
                (authorSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "author",
                authorSortFieldRef.fieldName
            )

            val authorSortValueRef =
                (authorSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                authorSortValueRef.value
            )
        }

        fun commonAssertionsForDescendingSort(sortStageNode: Node<PsiElement>) {
            val named = sortStageNode.component<Named>()!!
            assertEquals(Name.SORT, named.name)

            val sorts = sortStageNode.component<HasSorts<PsiElement>>()!!
            assertEquals(3, sorts.children.size)

            val titleSort = sorts.children[0]
            assertEquals(Name.DESCENDING, titleSort.component<Named>()!!.name)

            val titleSortFieldRef =
                (titleSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "title",
                titleSortFieldRef.fieldName
            )

            val titleSortValueRef =
                (titleSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                titleSortValueRef.value
            )

            val yearSort = sorts.children[1]
            assertEquals(Name.DESCENDING, yearSort.component<Named>()!!.name)

            val yearSortFieldRef =
                (yearSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "year",
                yearSortFieldRef.fieldName
            )

            val yearSortValueRef =
                (yearSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                yearSortValueRef.value
            )

            val authorSort = sorts.children[2]
            assertEquals(Name.DESCENDING, authorSort.component<Named>()!!.name)

            val authorSortFieldRef =
                (authorSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "author",
                authorSortFieldRef.fieldName
            )

            val authorSortValueRef =
                (authorSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                authorSortValueRef.value
            )
        }

        fun commonAssertionsForOrderBySort(sortStageNode: Node<PsiElement>) {
            val named = sortStageNode.component<Named>()!!
            assertEquals(Name.SORT, named.name)

            val sorts = sortStageNode.component<HasSorts<PsiElement>>()!!
            assertEquals(4, sorts.children.size)

            val titleSort = sorts.children[0]
            assertEquals(Name.ASCENDING, titleSort.component<Named>()!!.name)

            val titleSortFieldRef =
                (titleSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "title",
                titleSortFieldRef.fieldName
            )

            val titleSortValueRef =
                (titleSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                titleSortValueRef.value
            )

            val yearSort = sorts.children[1]
            assertEquals(Name.ASCENDING, yearSort.component<Named>()!!.name)

            val yearSortFieldRef =
                (yearSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "year",
                yearSortFieldRef.fieldName
            )

            val yearSortValueRef =
                (yearSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                1,
                yearSortValueRef.value
            )

            val publishedSort = sorts.children[2]
            assertEquals(Name.DESCENDING, publishedSort.component<Named>()!!.name)

            val publishedSortFieldRef =
                (publishedSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "published",
                publishedSortFieldRef.fieldName
            )

            val publishedSortValueRef =
                (publishedSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                publishedSortValueRef.value
            )

            val authorSort = sorts.children[3]
            assertEquals(Name.DESCENDING, authorSort.component<Named>()!!.name)

            val authorSortFieldRef =
                (authorSort.component<HasFieldReference<PsiElement>>()!!.reference) as FromSchema
            assertEquals(
                "author",
                authorSortFieldRef.fieldName
            )

            val authorSortValueRef =
                (authorSort.component<HasValueReference<PsiElement>>()!!.reference) as Inferred
            assertEquals(
                -1,
                authorSortValueRef.value
            )
        }
    }
}

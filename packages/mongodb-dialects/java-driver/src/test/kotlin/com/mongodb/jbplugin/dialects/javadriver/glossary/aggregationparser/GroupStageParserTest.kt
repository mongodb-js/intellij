package com.mongodb.jbplugin.dialects.javadriver.glossary.aggregationparser

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.WithFile
import com.mongodb.jbplugin.dialects.javadriver.caret
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.mql.components.HasAccumulatedFields
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasLimit
import com.mongodb.jbplugin.mql.components.HasSorts
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@IntegrationTest
class GroupStageParserTest {
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
            Aggregates.group(null)
        ));
    }
}
      """
    )
    fun `should be able to parse a group stage with null _id`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val groupStage = hasAggregation?.children?.get(0)!!

        val named = groupStage.component<Named>()!!
        assertEquals(Name.GROUP, named.name)

        val idFieldRef = groupStage.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Inferred<PsiElement>
        val constantValueRef = groupStage.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant<PsiElement>
        val accumulatedFields = groupStage.component<HasAccumulatedFields<PsiElement>>()!!

        assertEquals("_id", idFieldRef.fieldName)
        assertEquals(0, accumulatedFields.children.size)
        assertEquals(null, constantValueRef.value)
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
            Aggregates.group("${'$'}myField")
        ));
    }
}
      """
    )
    fun `should be able to parse a group stage without accumulators`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val groupStage = hasAggregation?.children?.get(0)!!

        val named = groupStage.component<Named>()!!
        assertEquals(Name.GROUP, named.name)

        val idFieldRef = groupStage.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Inferred<PsiElement>
        val computedValueRef = groupStage.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Computed<PsiElement>
        val accumulatedFields = groupStage.component<HasAccumulatedFields<PsiElement>>()!!

        assertEquals("_id", idFieldRef.fieldName)
        assertEquals(0, accumulatedFields.children.size)

        val computedExpression = computedValueRef.type.expression
        val fieldUsedForComputation = computedExpression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>

        assertEquals("myField", fieldUsedForComputation.fieldName)
        assertEquals("${'$'}myField", fieldUsedForComputation.displayName)
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
        String _idField = "${'$'}myField";
        return this.collection.aggregate(List.of(
            Aggregates.group(_idField)
        ));
    }
}
      """
    )
    fun `should be able to parse a group stage without accumulators, when _id is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val groupStage = hasAggregation?.children?.get(0)!!

        val named = groupStage.component<Named>()!!
        assertEquals(Name.GROUP, named.name)

        val idFieldRef = groupStage.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Inferred<PsiElement>
        val computedValueRef = groupStage.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Computed<PsiElement>
        val accumulatedFields = groupStage.component<HasAccumulatedFields<PsiElement>>()!!

        assertEquals("_id", idFieldRef.fieldName)
        assertEquals(0, accumulatedFields.children.size)

        val computedExpression = computedValueRef.type.expression
        val fieldUsedForComputation = computedExpression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>

        assertEquals("myField", fieldUsedForComputation.fieldName)
        assertEquals("${'$'}myField", fieldUsedForComputation.displayName)
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
    
    private String getIdField() {
        return "${'$'}myField";
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.group(getIdField())
        ));
    }
}
      """
    )
    fun `should be able to parse a group stage without accumulators, when _id is from a method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val groupStage = hasAggregation?.children?.get(0)!!

        val named = groupStage.component<Named>()!!
        assertEquals(Name.GROUP, named.name)

        val idFieldRef = groupStage.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Inferred<PsiElement>
        val computedValueRef = groupStage.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Computed<PsiElement>
        val accumulatedFields = groupStage.component<HasAccumulatedFields<PsiElement>>()!!

        assertEquals("_id", idFieldRef.fieldName)
        assertEquals(0, accumulatedFields.children.size)

        val computedExpression = computedValueRef.type.expression
        val fieldUsedForComputation = computedExpression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>

        assertEquals("myField", fieldUsedForComputation.fieldName)
        assertEquals("${'$'}myField", fieldUsedForComputation.displayName)
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
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
            Aggregates.group("${'$'}myField", Accumulators."|"("myKey", "myVal"))
        ));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "sum;;SUM",
            "avg;;AVG",
            "first;;FIRST",
            "last;;LAST",
            "max;;MAX",
            "min;;MIN",
            "push;;PUSH",
            "addToSet;;ADD_TO_SET",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant key-value accumulators from the driver`(
        method: String,
        expected: Name,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
            val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
            val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
            assertEquals(1, hasAggregation?.children?.size)

            val groupStage = hasAggregation?.children?.get(0)!!
            val named = groupStage.component<Named>()!!
            assertEquals(Name.GROUP, named.name)

            val accumulator = groupStage.component<HasAccumulatedFields<PsiElement>>()!!.children[0]
            val accumulatorName = accumulator.component<Named>()!!
            assertEquals(expected, accumulatorName.name)

            val accumulatorField = accumulator.component<HasFieldReference<PsiElement>>()?.reference as HasFieldReference.Computed<PsiElement>
            assertEquals("myKey", accumulatorField.fieldName)

            val accumulatorComputed = accumulator.component<HasValueReference<PsiElement>>()?.reference as HasValueReference.Computed<PsiElement>
            val accumulatorComputedFieldValue = accumulatorComputed.type.expression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("myVal", accumulatorComputedFieldValue.fieldName)
        }
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.BsonField;
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
        BsonField accumulatedExpr = Accumulators."|"("myKey", "myVal");
        return this.collection.aggregate(List.of(
            Aggregates.group("${'$'}myField", accumulatedExpr)
        ));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "sum;;SUM",
            "avg;;AVG",
            "first;;FIRST",
            "last;;LAST",
            "max;;MAX",
            "min;;MIN",
            "push;;PUSH",
            "addToSet;;ADD_TO_SET",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant key-value accumulators from the driver, when accumulated expression is a variable`(
        method: String,
        expected: Name,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
            val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
            val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
            assertEquals(1, hasAggregation?.children?.size)

            val groupStage = hasAggregation?.children?.get(0)!!
            val named = groupStage.component<Named>()!!
            assertEquals(Name.GROUP, named.name)

            val accumulator = groupStage.component<HasAccumulatedFields<PsiElement>>()!!.children[0]
            val accumulatorName = accumulator.component<Named>()!!
            assertEquals(expected, accumulatorName.name)

            val accumulatorField = accumulator.component<HasFieldReference<PsiElement>>()?.reference as HasFieldReference.Computed<PsiElement>
            assertEquals("myKey", accumulatorField.fieldName)

            val accumulatorComputed = accumulator.component<HasValueReference<PsiElement>>()?.reference as HasValueReference.Computed<PsiElement>
            val accumulatorComputedFieldValue = accumulatorComputed.type.expression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("myVal", accumulatorComputedFieldValue.fieldName)
        }
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.BsonField;
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
    
    private BsonField getAccumulatedExpr() {
        return Accumulators."|"("myKey", "myVal");
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.group("${'$'}myField", getAccumulatedExpr())
        ));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "sum;;SUM",
            "avg;;AVG",
            "first;;FIRST",
            "last;;LAST",
            "max;;MAX",
            "min;;MIN",
            "push;;PUSH",
            "addToSet;;ADD_TO_SET",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant key-value accumulators from the driver, when accumulated expression is from a method call`(
        method: String,
        expected: Name,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
            val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
            val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
            assertEquals(1, hasAggregation?.children?.size)

            val groupStage = hasAggregation?.children?.get(0)!!
            val named = groupStage.component<Named>()!!
            assertEquals(Name.GROUP, named.name)

            val accumulator = groupStage.component<HasAccumulatedFields<PsiElement>>()!!.children[0]
            val accumulatorName = accumulator.component<Named>()!!
            assertEquals(expected, accumulatorName.name)

            val accumulatorField = accumulator.component<HasFieldReference<PsiElement>>()?.reference as HasFieldReference.Computed<PsiElement>
            assertEquals("myKey", accumulatorField.fieldName)

            val accumulatorComputed = accumulator.component<HasValueReference<PsiElement>>()?.reference as HasValueReference.Computed<PsiElement>
            val accumulatorComputedFieldValue = accumulatorComputed.type.expression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("myVal", accumulatorComputedFieldValue.fieldName)
        }
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Accumulators;
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
            Aggregates.group("${'$'}myField", Accumulators.sum("totalCount", 1))
        ));
    }
}
      """
    )
    fun `supports accumulators also expecting a constant argument`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val groupStage = hasAggregation?.children?.get(0)!!

        val named = groupStage.component<Named>()!!
        assertEquals(Name.GROUP, named.name)

        val idFieldRef = groupStage.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Inferred<PsiElement>
        val computedValueRef = groupStage.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Computed<PsiElement>
        val accumulatedFields = groupStage.component<HasAccumulatedFields<PsiElement>>()!!

        assertEquals("_id", idFieldRef.fieldName)
        val computedExpression = computedValueRef.type.expression
        val fieldUsedForComputation = computedExpression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>

        assertEquals("myField", fieldUsedForComputation.fieldName)
        assertEquals("${'$'}myField", fieldUsedForComputation.displayName)

        assertEquals(1, accumulatedFields.children.size)
        val totalCountNode = accumulatedFields.children.first()
        val totalCountFieldRef =
            totalCountNode.component<HasFieldReference<PsiElement>>()!!.reference
                as HasFieldReference.Computed<PsiElement>
        val totalCountValueRef =
            totalCountNode.component<HasValueReference<PsiElement>>()!!.reference
                as HasValueReference.Constant<PsiElement>

        assertEquals("totalCount", totalCountFieldRef.fieldName)
        assertEquals(1, totalCountValueRef.value)
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;import org.bson.Document;
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
            Aggregates.group("${'$'}myField", Accumulators."|"("myKey", Sorts.ascending("mySort"), "myVal"))
        ));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "top;;TOP",
            "bottom;;BOTTOM",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant key-value accumulators with sorting criteria from the driver`(
        method: String,
        expected: Name,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
            val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
            val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
            assertEquals(1, hasAggregation?.children?.size)

            val groupStage = hasAggregation?.children?.get(0)!!
            val named = groupStage.component<Named>()!!
            assertEquals(Name.GROUP, named.name)

            val accumulator = groupStage.component<HasAccumulatedFields<PsiElement>>()!!.children[0]
            val accumulatorName = accumulator.component<Named>()!!
            assertEquals(expected, accumulatorName.name)

            val accumulatorField = accumulator.component<HasFieldReference<PsiElement>>()?.reference as HasFieldReference.Computed<PsiElement>
            assertEquals("myKey", accumulatorField.fieldName)

            val accumulatorComputed = accumulator.component<HasValueReference<PsiElement>>()?.reference as HasValueReference.Computed<PsiElement>
            val accumulatorComputedFieldValue = accumulatorComputed.type.expression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("myVal", accumulatorComputedFieldValue.fieldName)

            val accumulatorSorting = accumulator.component<HasSorts<PsiElement>>()!!.children[0]
            val sortingField = accumulatorSorting.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("mySort", sortingField.fieldName)
        }
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;import org.bson.Document;
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
            Aggregates.group(
                "${'$'}myField",
                Accumulators."|"("myKey", Sorts.ascending("mySort"), "myVal", 3)
            )
        ));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "topN;;TOP_N",
            "bottomN;;BOTTOM_N",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant key-value accumulators with sorting criteria and n value`(
        method: String,
        expected: Name,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
            val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
            val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
            assertEquals(1, hasAggregation?.children?.size)

            val groupStage = hasAggregation?.children?.get(0)!!
            val named = groupStage.component<Named>()!!
            assertEquals(Name.GROUP, named.name)

            val accumulator = groupStage.component<HasAccumulatedFields<PsiElement>>()!!.children[0]
            val accumulatorName = accumulator.component<Named>()!!
            assertEquals(expected, accumulatorName.name)

            val accumulatorField = accumulator.component<HasFieldReference<PsiElement>>()?.reference as HasFieldReference.Computed<PsiElement>
            assertEquals("myKey", accumulatorField.fieldName)

            val accumulatorComputed = accumulator.component<HasValueReference<PsiElement>>()?.reference as HasValueReference.Computed<PsiElement>
            val accumulatorComputedFieldValue = accumulatorComputed.type.expression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("myVal", accumulatorComputedFieldValue.fieldName)

            val accumulatorSorting = accumulator.component<HasSorts<PsiElement>>()!!.children[0]
            val sortingField = accumulatorSorting.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("mySort", sortingField.fieldName)

            val accumulatorLimit = accumulator.component<HasLimit>()!!
            assertEquals(3, accumulatorLimit.limit)
        }
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        Number nValue = 3;
        return this.collection.aggregate(List.of(
            Aggregates.group(
                "${'$'}myField",
                Accumulators."|"("myKey", Sorts.ascending("mySort"), "myVal", nValue)
            )
        ));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "topN;;TOP_N",
            "bottomN;;BOTTOM_N",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant key-value accumulators with sorting criteria and n value as variable`(
        method: String,
        expected: Name,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
            val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
            val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
            assertEquals(1, hasAggregation?.children?.size)

            val groupStage = hasAggregation?.children?.get(0)!!
            val named = groupStage.component<Named>()!!
            assertEquals(Name.GROUP, named.name)

            val accumulator = groupStage.component<HasAccumulatedFields<PsiElement>>()!!.children[0]
            val accumulatorName = accumulator.component<Named>()!!
            assertEquals(expected, accumulatorName.name)

            val accumulatorField = accumulator.component<HasFieldReference<PsiElement>>()?.reference as HasFieldReference.Computed<PsiElement>
            assertEquals("myKey", accumulatorField.fieldName)

            val accumulatorComputed = accumulator.component<HasValueReference<PsiElement>>()?.reference as HasValueReference.Computed<PsiElement>
            val accumulatorComputedFieldValue = accumulatorComputed.type.expression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("myVal", accumulatorComputedFieldValue.fieldName)

            val accumulatorSorting = accumulator.component<HasSorts<PsiElement>>()!!.children[0]
            val sortingField = accumulatorSorting.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("mySort", sortingField.fieldName)

            val accumulatorLimit = accumulator.component<HasLimit>()!!
            assertEquals(3, accumulatorLimit.limit)
        }
    }

    @WithFile(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    private Number getNValue() {
        return 3;
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.group(
                "${'$'}myField",
                Accumulators."|"("myKey", Sorts.ascending("mySort"), "myVal", getNValue())
            )
        ));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "topN;;TOP_N",
            "bottomN;;BOTTOM_N",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant key-value accumulators with sorting criteria and n value from method call`(
        method: String,
        expected: Name,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
            val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
            val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
            assertEquals(1, hasAggregation?.children?.size)

            val groupStage = hasAggregation?.children?.get(0)!!
            val named = groupStage.component<Named>()!!
            assertEquals(Name.GROUP, named.name)

            val accumulator = groupStage.component<HasAccumulatedFields<PsiElement>>()!!.children[0]
            val accumulatorName = accumulator.component<Named>()!!
            assertEquals(expected, accumulatorName.name)

            val accumulatorField = accumulator.component<HasFieldReference<PsiElement>>()?.reference as HasFieldReference.Computed<PsiElement>
            assertEquals("myKey", accumulatorField.fieldName)

            val accumulatorComputed = accumulator.component<HasValueReference<PsiElement>>()?.reference as HasValueReference.Computed<PsiElement>
            val accumulatorComputedFieldValue = accumulatorComputed.type.expression.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("myVal", accumulatorComputedFieldValue.fieldName)

            val accumulatorSorting = accumulator.component<HasSorts<PsiElement>>()!!.children[0]
            val sortingField = accumulatorSorting.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.FromSchema<PsiElement>
            assertEquals("mySort", sortingField.fieldName)

            val accumulatorLimit = accumulator.component<HasLimit>()!!
            assertEquals(3, accumulatorLimit.limit)
        }
    }
}

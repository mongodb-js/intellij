package com.mongodb.jbplugin.dialects.javadriver.glossary.aggregationparser

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.components.HasAddedFields
import com.mongodb.jbplugin.mql.components.HasAggregation
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.Name
import com.mongodb.jbplugin.mql.components.Named
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull

@IntegrationTest
class AddFieldsParserTest {
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
            Aggregates.addFields()
        ));
    }
}
      """
    )
    fun `should be able to parse an empty addFields call`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        assertEquals(0, addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!.children.size)
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, valueForFieldName2);
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                new Field<>("field1", "Value1"),
                field2,
                getField3()
            )
        ));
    }
}
      """
    )
    fun `should be able to parse addFields call with var args of added fields`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        commonAssertionsOnAddedFieldsComponent(
            addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, valueForFieldName2);
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                List.of(
                    new Field<>("field1", "Value1"),
                    field2,
                    getField3()
                )
            )
        ));
    }
}
      """
    )
    fun `should be able to parse addFields call with List#of of added fields`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        commonAssertionsOnAddedFieldsComponent(
            addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, valueForFieldName2);
        List<Field<?>> addedFields = List.of(
            new Field<>("field1", "Value1"),
            field2,
            getField3()
        );
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                addedFields
            )
        ));
    }
}
      """
    )
    fun `should be able to parse addFields call with List#of of added fields, when list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        commonAssertionsOnAddedFieldsComponent(
            addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }
    
    private List<Field<?>> getAddedFields() {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, valueForFieldName2);
        return List.of(
            new Field<>("field1", "Value1"),
            field2,
            getField3()
        );
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                getAddedFields()
            )
        ));
    }
}
      """
    )
    fun `should be able to parse addFields call with List#of of added fields, when list is retrieved from method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        commonAssertionsOnAddedFieldsComponent(
            addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
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

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, valueForFieldName2);
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                Arrays.asList(
                    new Field<>("field1", "Value1"),
                    field2,
                    getField3()
                )
            )
        ));
    }
}
      """
    )
    fun `should be able to parse addFields call with Arrays#asList of added fields`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        commonAssertionsOnAddedFieldsComponent(
            addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
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

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, valueForFieldName2);
        List<Field<?>> addedFields = Arrays.asList(
            new Field<>("field1", "Value1"),
            field2,
            getField3()
        );
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                addedFields
            )
        ));
    }
}
      """
    )
    fun `should be able to parse addFields call with Arrays#asList of added fields, when list is a variable`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        commonAssertionsOnAddedFieldsComponent(
            addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
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

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }
    
    private List<Field<?>> getAddedFields() {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, valueForFieldName2);
        return Arrays.asList(
            new Field<>("field1", "Value1"),
            field2,
            getField3()
        );
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                getAddedFields()
            )
        ));
    }
}
      """
    )
    fun `should be able to parse addFields call with Arrays#asList of added fields, when list is retrieved from method call`(
        psiFile: PsiFile
    ) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        commonAssertionsOnAddedFieldsComponent(
            addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        )
    }

    @ParsingTest(
        fileName = "Aggregation.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class Aggregation {
    private final MongoCollection<Document> collection;

    public Aggregation(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    private String getFieldName3() {
        return "field3";
    }

    private String getValueForFieldName3() {
        return "Value3";
    }
    
    private Field<String> getField3() {
        return new Field<>(getFieldName3(), getValueForFieldName3());
    }

    public AggregateIterable<Document> getAllBookTitles(ObjectId id) {
        String fieldName2 = "field2";
        String valueForFieldName2 = "Value2";
        Field<String> field2 = new Field<>(fieldName2, Filters.eq("asd"));
        return this.collection.aggregate(List.of(
            Aggregates.addFields(
                new Field<>("field1", "Value1"),
                field2,
                getField3()
            )
        ));
    }
}
      """
    )
    fun `should parse Fields call with an expression as an Unknown value`(psiFile: PsiFile) {
        val aggregate = psiFile.getQueryAtMethod("Aggregation", "getAllBookTitles")
        val parsedAggregate = JavaDriverDialect.parser.parse(aggregate)
        val hasAggregation = parsedAggregate.component<HasAggregation<PsiElement>>()
        assertEquals(1, hasAggregation?.children?.size)

        val addFieldsStageNode = hasAggregation?.children?.get(0)!!

        val named = addFieldsStageNode.component<Named>()!!
        assertEquals(Name.ADD_FIELDS, named.name)

        val addedFields = addFieldsStageNode.component<HasAddedFields<PsiElement>>()!!
        assertEquals(3, addedFields.children.size)

        val field1Node = addedFields.children[0]
        val field1FieldReference = (field1Node.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Computed)
        assertEquals("field1", field1FieldReference.fieldName)
        val field1ValueReference = (field1Node.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant)
        assertEquals("Value1", field1ValueReference.value)
        assertEquals(BsonAnyOf(BsonString, BsonNull), field1ValueReference.type)

        val field2Node = addedFields.children[1]
        val field2FieldReference = (field2Node.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Computed)
        assertEquals("field2", field2FieldReference.fieldName)
        val field2ValueReference = field2Node.component<HasValueReference<PsiElement>>()!!.reference
        assertNotNull(field2ValueReference as? HasValueReference.Unknown)

        val field3Node = addedFields.children[2]
        val field3FieldReference = (field3Node.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Computed)
        assertEquals("field3", field3FieldReference.fieldName)
        val field3ValueReference = (field3Node.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant)
        assertEquals("Value3", field3ValueReference.value)
        assertEquals(BsonAnyOf(BsonString, BsonNull), field3ValueReference.type)
    }

    companion object {
        fun commonAssertionsOnAddedFieldsComponent(addedFields: HasAddedFields<PsiElement>) {
            assertEquals(3, addedFields.children.size)

            val field1Node = addedFields.children[0]
            val field1FieldReference = (field1Node.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Computed)
            assertEquals("field1", field1FieldReference.fieldName)
            val field1ValueReference = (field1Node.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant)
            assertEquals("Value1", field1ValueReference.value)
            assertEquals(BsonAnyOf(BsonString, BsonNull), field1ValueReference.type)

            val field2Node = addedFields.children[1]
            val field2FieldReference = (field2Node.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Computed)
            assertEquals("field2", field2FieldReference.fieldName)
            val field2ValueReference = (field2Node.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant)
            assertEquals("Value2", field2ValueReference.value)
            assertEquals(BsonAnyOf(BsonString, BsonNull), field2ValueReference.type)

            val field3Node = addedFields.children[2]
            val field3FieldReference = (field3Node.component<HasFieldReference<PsiElement>>()!!.reference as HasFieldReference.Computed)
            assertEquals("field3", field3FieldReference.fieldName)
            val field3ValueReference = (field3Node.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant)
            assertEquals("Value3", field3ValueReference.value)
            assertEquals(BsonAnyOf(BsonString, BsonNull), field3ValueReference.type)
        }
    }
}

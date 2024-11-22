package com.mongodb.jbplugin.inspections.impl

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.CodeInsightTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.mql.BsonDouble
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@CodeInsightTest
class JavaDriverFieldCheckLinterInspectionTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return <warning descr="No connection available to run this check.">client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find()</warning>;
    }
}
        """,
    )
    fun `shows an inspection when there is no connection attached to the current editor`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123"));
    }
}
        """,
    )
    fun `shows an inspection when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("thisIsDouble", <warning descr="A \"String\"(type of provided value) cannot be assigned to \"double\"(type of \"thisIsDouble\")">"123"</warning>));
    }
}
        """,
    )
    fun `shows an inspection when a provided value cannot be assigned to a field because of detected type mismatch`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("myDatabase", "myCollection"),
                    BsonObject(mapOf("thisIsDouble" to BsonDouble))
                )
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(
                    and(
                        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123")
                    )
                );
    }
}
        """,
    )
    fun `shows an inspection when a field, referenced in a nested $and query, does not exists in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public AggregateIterable<Document> exampleAggregate() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.match(
                        eq(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>, "123")
                    )
                ));
    }
}
        """,
    )
    fun `shows an inspection for Aggregates#match call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public AggregateIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.match(
                        eq("thisIsDouble", <warning descr="A \"String\"(type of provided value) cannot be assigned to \"double\"(type of \"thisIsDouble\")">"123"</warning>)
                    )
                ));
    }
}
        """,
    )
    fun `shows an inspection for Aggregates#match call when a provided value cannot be assigned to a field because of detected type mismatch`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("myDatabase", "myCollection"),
                    BsonObject(mapOf("thisIsDouble" to BsonDouble))
                )
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public AggregateIterable<Document> exampleAggregateInclude() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.include(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                    )
                ));
    }
    
    public AggregateIterable<Document> exampleAggregateExclude() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.exclude(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                    )
                ));
    }
    
    private Bson getAnotherProjection() {
        return Projections.exclude(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
    }
    
    public AggregateIterable<Document> exampleAggregateFields() {
        Bson includeProject = Projections.include(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.project(
                        Projections.fields(
                            Projections.exclude(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>),
                            includeProject,
                            getAnotherProjection()
                        )
                    )
                ));
    }
}
        """,
    )
    fun `shows an inspection for Aggregates#project call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public AggregateIterable<Document> exampleAggregateAscending() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.ascending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                    )
                ));
    }
    
    public AggregateIterable<Document> exampleAggregateDescending() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.descending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>)
                    )
                ));
    }
    
    private Bson getAnotherSort() {
        return Sorts.descending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
    }
    
    public AggregateIterable<Document> exampleAggregateOrderBy() {
        Bson ascendingSort = Sorts.ascending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>);
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.sort(
                        Sorts.orderBy(
                            Sorts.descending(<warning descr="Field \"nonExistingField\" does not exist in collection \"myDatabase.myCollection\"">"nonExistingField"</warning>),
                            ascendingSort,
                            getAnotherSort()
                        )
                    )
                ));
    }
}
        """,
    )
    fun `shows an inspection for Aggregates#sort call when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import java.util.List;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public AggregateIterable<Document> exampleAggregateOrderBy() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of(
                    Aggregates.addFields(
                        new Field<>("nonExistingField", "nonExistingField")
                    )
                ));
    }
}
        """,
    )
    fun `does not show any inspection for Aggregates#addFields call even when the field does not exist in the current namespace`(
        fixture: CodeInsightTestFixture,
    ) {
        val (dataSource, readModelProvider) = fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(Namespace("myDatabase", "myCollection"), BsonObject(emptyMap()))
            ),
        )

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}

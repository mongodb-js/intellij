package com.mongodb.jbplugin.inspections.impl

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.dialects.springcriteria.SpringCriteriaDialect
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.fixtures.mockDatabaseConnection
import com.mongodb.jbplugin.mql.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

// Suppressing
// - LONG_LINE because the complaint is about the templated error description which needs to be in the same line for the
// match to happen correctly
// - TOO_LONG_FUNCTION because it is better to keep test logic within the tests and not make them "too smart" otherwise
// reading through them becomes a task in its own
@Suppress("LONG_LINE", "TOO_LONG_FUNCTION")
@CodeInsightTest
class SpringCriteriaFieldCheckLinterInspectionTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(
            query(
            <warning descr="No connection available to run this check.">where("released")</warning>
            // TODO: (INTELLIJ-62) The Java SDK is not available in the test class path which is why there is
            // an error in the .is block and hence expected.
            .is<error descr="'is(java.lang.Object)' in 'org.springframework.data.mongodb.core.query.Criteria' cannot be applied to '(boolean)'">(true)</error>),
            Book.class
        );
    }
}
        """,
    )
    fun `shows an inspection when there is no connection attached to the current editor`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(SpringCriteriaDialect)
        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(
                // TODO: (INTELLIJ-62) The Java SDK is not available in the test class path which is why there is
                // an error in the .is block and hence expected.
                query(<warning descr="No database selected to run this check.">where("released")</warning>.is<error descr="'is(java.lang.Object)' in 'org.springframework.data.mongodb.core.query.Criteria' cannot be applied to '(boolean)'">(true)</error>),
                Book.class
        );
    }
}
        """,
    )
    fun `shows an inspection when there is a connection but no database attached to the current editor`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val dbPsiFacade = mock<DbPsiFacade>()
        val dbDataSource = mock<DbDataSource>()
        val dataSource = mockDataSource()
        val application = ApplicationManager.getApplication()
        val realConnectionManager = DatabaseConnectionManager.getInstance()
        val dbConnectionManager =
            mock<DatabaseConnectionManager>().also { cm ->
                `when`(cm.build(any(), any())).thenAnswer {
                    realConnectionManager.build(
                        it.arguments[0] as Project,
                        it.arguments[1] as DatabaseConnectionPoint
                    )
                }
            }
        val connection = mockDatabaseConnection(dataSource)
        val readModelProvider = mock<DataGripBasedReadModelProvider>()

        `when`(dbDataSource.localDataSource).thenReturn(dataSource)
        `when`(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)
        `when`(dbConnectionManager.activeConnections).thenReturn(listOf(connection))

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
            dataSource,
        )

        fixture.specifyDialect(SpringCriteriaDialect)

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(CollectionSchema(Namespace("", ""), BsonObject(emptyMap()))),
        )

        application.withMockedService(dbConnectionManager)
        project.withMockedService(readModelProvider)
        project.withMockedService(dbPsiFacade)

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(
                // TODO: (INTELLIJ-62) The Java SDK is not available in the test class path which is why there is
                // an error in the .is block and hence expected.
                query(where(<warning descr="Field \"released\" does not exist in collection \"bad_db.book\"">"released"</warning>).is<error descr="'is(java.lang.Object)' in 'org.springframework.data.mongodb.core.query.Criteria' cannot be applied to '(boolean)'">(true)</error>),
                Book.class
        );
    }
}
        """,
    )
    fun `shows an inspection when the field does not exist in the current namespace`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val dbPsiFacade = mock<DbPsiFacade>()
        val dbDataSource = mock<DbDataSource>()
        val dataSource = mockDataSource()
        val application = ApplicationManager.getApplication()
        val realConnectionManager = DatabaseConnectionManager.getInstance()
        val dbConnectionManager =
            mock<DatabaseConnectionManager>().also { cm ->
                `when`(cm.build(any(), any())).thenAnswer {
                    realConnectionManager.build(
                        it.arguments[0] as Project,
                        it.arguments[1] as DatabaseConnectionPoint
                    )
                }
            }
        val connection = mockDatabaseConnection(dataSource)
        val readModelProvider = mock<DataGripBasedReadModelProvider>()

        `when`(dbDataSource.localDataSource).thenReturn(dataSource)
        `when`(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)
        `when`(dbConnectionManager.activeConnections).thenReturn(listOf(connection))

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
            dataSource,
        )

        fixture.specifyDialect(SpringCriteriaDialect)

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDatabase,
            "bad_db",
        )

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(CollectionSchema(Namespace("", ""), BsonObject(emptyMap()))),
        )

        application.withMockedService(dbConnectionManager)
        project.withMockedService(readModelProvider)
        project.withMockedService(dbPsiFacade)

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(
                // TODO: (INTELLIJ-62) The Java SDK is not available in the test class path which is why there is
                // an error in the .is block and hence expected.
                query(<warning descr="A \"String\"(type of provided value) can not be assigned to \"boolean\"(type of \"released\")">where("released").is<error descr="'is(java.lang.Object)' in 'org.springframework.data.mongodb.core.query.Criteria' cannot be applied to '(java.lang.String)'">("true")</error></warning>),
                Book.class
        );
    }
}
        """,
    )
    fun `shows an inspection when a provided value cannot be assigned to a field because of detected type mismatch`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val dbPsiFacade = mock<DbPsiFacade>()
        val dbDataSource = mock<DbDataSource>()
        val dataSource = mockDataSource()
        val application = ApplicationManager.getApplication()
        val realConnectionManager = DatabaseConnectionManager.getInstance()
        val dbConnectionManager =
            mock<DatabaseConnectionManager>().also { cm ->
                `when`(cm.build(any(), any())).thenAnswer {
                    realConnectionManager.build(
                        it.arguments[0] as Project,
                        it.arguments[1] as DatabaseConnectionPoint
                    )
                }
            }
        val connection = mockDatabaseConnection(dataSource)
        val readModelProvider = mock<DataGripBasedReadModelProvider>()

        `when`(dbDataSource.localDataSource).thenReturn(dataSource)
        `when`(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)
        `when`(dbConnectionManager.activeConnections).thenReturn(listOf(connection))

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
            dataSource,
        )

        fixture.specifyDialect(SpringCriteriaDialect)

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDatabase,
            "sample_books",
        )

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("sample_books", "book"),
                    BsonObject(mapOf("released" to BsonBoolean))
                )
            ),
        )

        application.withMockedService(dbConnectionManager)
        project.withMockedService(readModelProvider)
        project.withMockedService(dbPsiFacade)

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;

import static org.springframework.data.mongodb.core.query.Query.query;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class BookRepository {
    private final MongoTemplate template;

    public BookRepository(MongoTemplate template) {
        this.template = template;
    }

    public void allReleasedBooks() {
        template.find(
                // TODO: (INTELLIJ-62) The Java SDK is not available in the test class path which is why there is
                // an error in the .is block and hence expected.
                query(where("released").is<error descr="'is(java.lang.Object)' in 'org.springframework.data.mongodb.core.query.Criteria' cannot be applied to '(java.lang.String)'">("true")</error>),
                Book.class
        );
    }
}
        """,
    )
    fun `shows no inspection when a provided value can be assigned to a field`(
        project: Project,
        fixture: CodeInsightTestFixture,
    ) {
        val dbPsiFacade = mock<DbPsiFacade>()
        val dbDataSource = mock<DbDataSource>()
        val dataSource = mockDataSource()
        val application = ApplicationManager.getApplication()
        val realConnectionManager = DatabaseConnectionManager.getInstance()
        val dbConnectionManager =
            mock<DatabaseConnectionManager>().also { cm ->
                `when`(cm.build(any(), any())).thenAnswer {
                    realConnectionManager.build(
                        it.arguments[0] as Project,
                        it.arguments[1] as DatabaseConnectionPoint
                    )
                }
            }
        val connection = mockDatabaseConnection(dataSource)
        val readModelProvider = mock<DataGripBasedReadModelProvider>()

        `when`(dbDataSource.localDataSource).thenReturn(dataSource)
        `when`(dbPsiFacade.findDataSource(any())).thenReturn(dbDataSource)
        `when`(dbConnectionManager.activeConnections).thenReturn(listOf(connection))

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDataSource,
            dataSource,
        )

        fixture.specifyDialect(SpringCriteriaDialect)

        fixture.file.virtualFile.putUserData(
            MongoDbVirtualFileDataSourceProvider.Keys.attachedDatabase,
            "sample_books",
        )

        `when`(
            readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())
        ).thenReturn(
            GetCollectionSchema(
                CollectionSchema(
                    Namespace("sample_books", "book"),
                    BsonObject(mapOf("released" to BsonAnyOf(BsonString, BsonNull)))
                )
            ),
        )

        application.withMockedService(dbConnectionManager)
        project.withMockedService(readModelProvider)
        project.withMockedService(dbPsiFacade)

        fixture.enableInspections(FieldCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}

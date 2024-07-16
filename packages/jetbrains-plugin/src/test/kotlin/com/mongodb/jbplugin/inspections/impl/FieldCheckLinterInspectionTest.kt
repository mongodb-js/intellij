package com.mongodb.jbplugin.inspections.impl

import com.intellij.database.dataSource.DatabaseConnectionManager
import com.intellij.database.dataSource.DatabaseConnectionPoint
import com.intellij.database.dataSource.localDataSource
import com.intellij.database.psi.DbDataSource
import com.intellij.database.psi.DbPsiFacade
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.accessadapter.datagrip.DataGripBasedReadModelProvider
import com.mongodb.jbplugin.accessadapter.slice.GetCollectionSchema
import com.mongodb.jbplugin.editor.MongoDbVirtualFileDataSourceProvider
import com.mongodb.jbplugin.fixtures.*
import com.mongodb.jbplugin.fixtures.mockDataSource
import com.mongodb.jbplugin.inspections.bridge.FieldExistenceCheckInspectionBridge
import com.mongodb.jbplugin.mql.BsonObject
import com.mongodb.jbplugin.mql.CollectionSchema
import com.mongodb.jbplugin.mql.Namespace
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

@CodeInsightTest
@Suppress("TOO_LONG_FUNCTION", "LONG_LINE")
class FieldCheckLinterInspectionTest {
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
    fun `shows an intention when there is no connection attached to the current editor`(
        psiFile: PsiFile,
        fixture: CodeInsightTestFixture,
    ) {
        fixture.enableInspections(FieldExistenceCheckInspectionBridge::class.java)
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
    fun `shows an intention when the field does not exist in the current namespace`(
        project: Project,
        psiFile: PsiFile,
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
                    realConnectionManager.build(it.arguments[0] as Project, it.arguments[1] as DatabaseConnectionPoint)
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

        `when`(readModelProvider.slice(eq(dataSource), any<GetCollectionSchema.Slice>())).thenReturn(
            GetCollectionSchema(CollectionSchema(Namespace("", ""), BsonObject(emptyMap()))),
        )

        application.withMockedService(dbConnectionManager)
        project.withMockedService(readModelProvider)
        project.withMockedService(dbPsiFacade)

        fixture.enableInspections(FieldExistenceCheckInspectionBridge::class.java)
        fixture.testHighlighting()
    }
}

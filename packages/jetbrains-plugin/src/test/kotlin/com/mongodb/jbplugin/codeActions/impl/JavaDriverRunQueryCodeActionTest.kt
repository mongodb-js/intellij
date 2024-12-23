package com.mongodb.jbplugin.codeActions.impl

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.dialects.javadriver.glossary.JavaDriverDialect
import com.mongodb.jbplugin.fixtures.CodeInsightTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import com.mongodb.jbplugin.fixtures.setupConnection
import com.mongodb.jbplugin.fixtures.specifyDialect
import com.mongodb.jbplugin.i18n.CodeActionsMessages
import com.mongodb.jbplugin.i18n.Icons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

@CodeInsightTest
class JavaDriverRunQueryCodeActionTest {
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
                .find();
    }
}
        """,
    )
    fun `does show a gutter icon if not connected`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.specifyDialect(JavaDriverDialect)
        val gutters = fixture.findAllGutters()
        assertTrue(gutters.isNotEmpty())
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
                .find();
    }
}
        """,
    )
    fun `does show a gutter icon if connected`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        val gutters = fixture.findAllGutters()
        assertEquals(1, gutters.size)

        val gutter = gutters.first()
        assertEquals(Icons.runQueryGutter, gutter.icon)
        assertEquals(CodeActionsMessages.message("code.action.run.query"), gutter.tooltipText)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import java.util.List;import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .aggregate(List.of()).first();
    }
}
        """,
    )
    fun `does not show a gutter icon for aggregates`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.setupConnection()
        fixture.specifyDialect(JavaDriverDialect)

        val gutters = fixture.findAllGutters()
        assertEquals(0, gutters.size)
    }
}

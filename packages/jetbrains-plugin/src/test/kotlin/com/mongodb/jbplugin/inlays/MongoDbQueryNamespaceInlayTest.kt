package com.mongodb.jbplugin.inlays

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.mongodb.jbplugin.fixtures.CodeInsightTest
import com.mongodb.jbplugin.fixtures.ParsingTest
import org.junit.jupiter.api.Assertions.*

@CodeInsightTest
class MongoDbQueryNamespaceInlayTest {
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
        return client.getDatabase(<hint text="s:"/>"myDatabase")
                .getCollection(<hint text="s:"/>"myCollection")
                .find();
    }
}
        """,
    )
    fun `shows a inlay hint when the namespace is resolved`(
        fixture: CodeInsightTestFixture,
    ) {
        fixture.testInlays()
    }
}

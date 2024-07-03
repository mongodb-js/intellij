package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class DriverInFactoryMethodAbstractionTest {
    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;

public abstract class AbstractRepository {
    private final MongoClient client;
    
    protected MongoClient getClient() {
        return client;
    }
}

public class Repository extends AbstractRepository {
    public Repository(MongoClient client) {
        super(client);
    }
    
    public Document findAll() {
        return getClient().getDatabase("my").getCollection("coll").find().toList();
    }
}
        """,
    )
    fun `does detect a factory method for a client`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findAll")
        assertTrue(DriverInFactoryMethodAbstraction.isIn(query))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoDatabase;

public abstract class AbstractRepository {
    private final MongoDatabase db;
    
    protected MongoDatabase getDatabase() {
        return db;
    }
}

public class Repository extends AbstractRepository {
    public Repository(MongoDatabase db) {
        super(db);
    }
    
    public Document findAll() {
        return getDatabase().getCollection("coll").find().toList();
    }
}
        """,
    )
    fun `does detect a factory method for a database`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findAll")
        assertTrue(DriverInFactoryMethodAbstraction.isIn(query))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoCollection;

public abstract class AbstractRepository {
    private final MongoCollection collection;
    
    protected MongoCollection getCollection() {
        return collection;
    }
}

public class Repository extends AbstractRepository {
    public Repository(MongoCollection collection) {
        super(collection);
    }
    
    public Document findAll() {
        return getCollection().find().toList();
    }
}
        """,
    )
    fun `does detect a factory method for a collection`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findAll")
        assertTrue(DriverInFactoryMethodAbstraction.isIn(query))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;


public class Repository extends AbstractRepository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public MongoCollection getCollection() {
        return client.getDatabase("myDb").getCollection("myColl");
    }
    
    public Document findAll() {
        return getCollection().find().toList();
    }
}
        """,
    )
    fun `does detect a factory method in the same class`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findAll")
        assertTrue(DriverInFactoryMethodAbstraction.isIn(query))
    }
}

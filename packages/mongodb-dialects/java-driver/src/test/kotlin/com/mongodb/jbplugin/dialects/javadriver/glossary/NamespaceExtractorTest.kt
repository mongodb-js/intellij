package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class NamespaceExtractorTest {
    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoCollection<T> collection;
    
    protected AbstractRepository(MongoCollection<T> collection) {
        this.collection = collection;
    }
    
    protected final T findById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}

public final class UserRepository extends AbstractRepository<User> {
    public UserRepository(MongoClient client) {
        super(client.getDatabase("production").getCollection("users", User.class));
    }
    
    public User findUserById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("UserRepository", "findUserById")
        val namespace = NamespaceExtractor.extractNamespace(methodToAnalyse)!!
        assertEquals("production", namespace.database)
        assertEquals("users", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoCollection<T> collection;
    
    protected AbstractRepository(MongoClient client, String database, String collection) {
        this.collection = client.getDatabase(database).getCollection(collection);
    }
    
    protected final T findById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}

public final class BookRepository extends AbstractRepository<Book> {
    public BookRepository(MongoClient client) {
        super(client, "staging", "books");
    }
    
    public User findBookById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection with different arguments`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace = NamespaceExtractor.extractNamespace(methodToAnalyse)!!
        assertEquals("staging", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public abstract class AbstractRepository<T> {
    private final MongoClient<T> client;
    private final String database;
    private final String collection;
    
    protected AbstractRepository(MongoClient client, String database, String collection) {
        this.client = client;
        this.database = database;
        this.collection = collection;
    }
    
    protected final T findById(ObjectId id) {
        return this.getCollection().find(eq("_id", id)).first();
    }
    
    protected final MongoCollection<T> getCollection() {
        return this.client.getDatabase(database).getCollection(collection);
    }
}

public final class BookRepository extends AbstractRepository<Book> {
    public BookRepository(MongoClient client) {
        super(client, "production", "books");
    }
    
    public User findBookById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection with a factory method`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace = NamespaceExtractor.extractNamespace(methodToAnalyse)!!
        assertEquals("production", namespace.database)
        assertEquals("books", namespace.collection)
    }
}

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Assertions.assertEquals

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
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
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
        return findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection without explicit super call`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("UserRepository", "findUserById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
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
    fun `extracts from a complex chain of dependency injection with different arguments`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("staging", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import java.lang.String;
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
    private static final String DATABASE = "staging";
    private static final String COLLECTION = "books";
    
    public BookRepository(MongoClient client) {
        super(client, DATABASE, COLLECTION);
    }
    
    public User findBookById(ObjectId id) {
        return super.findById(id);
    }
}
        """,
    )
    fun `extracts from a complex chain of dependency injection with java constants`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
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
    fun `extracts from a complex chain of dependency injection with a factory method`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("production", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class BookRepository {
    private final MongoCollection<Book> collection;
    
    public BookRepository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public User findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `extracts from a basic repository with dependency injection`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class BookRepository {
    private final MongoClient client;
    
    public BookRepository(MongoClient client) {
        this.client = client;
    }
    
    public User findBookById(ObjectId id) {
        return this.getCollection().find(eq("_id", id)).first();
    }
    
    private MongoCollection<Book> getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `extracts from a basic repository with dependency injection and a factory method`(
        psiFile: PsiFile
    ) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class BookRepository {
    private final MongoClient client;
    
    public BookRepository(MongoClient client) {
        this.client = client;
    }
    
    public User findBookById(ObjectId id) {
        return client.getDatabase("simple").getCollection("books").find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `extracts from a basic repository with dependency injection only`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("BookRepository", "findBookById")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public class JavaDriverRepository {
    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
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
    fun `extracts from a hardcoded example`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("JavaDriverRepository", "exampleFind")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

abstract class BaseRepository {
    private final MongoClient client;
    private final String database;
    private final String collection;

    protected BaseRepository(MongoClient client, String database, String collection) {
        this.client = client;
        this.database = database;
        this.collection = collection;
    }

    protected final MongoCollection<Document> getCollection() {
        return client.getDatabase(database).getCollection(collection);
    }
}

public class JavaDriverRepository extends BaseRepository {
    public static final String DATABASE = "myDatabase";
    public static final String COLLECTION = "myCollection";

    public JavaDriverRepository(MongoClient client) {
        super(client, DATABASE, COLLECTION);
    }

    public FindIterable<Document> exampleFind() {
        return getCollection().find();
    }
}
        """,
    )
    fun `extracts from a mms like example`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("JavaDriverRepository", "exampleFind")
        val namespace =
            (
                NamespaceExtractor.extractNamespace(
                    methodToAnalyse
                ).reference as HasCollectionReference.Known<PsiElement>
                ).namespace
        assertEquals("myDatabase", namespace.database)
        assertEquals("myCollection", namespace.collection)
    }
}

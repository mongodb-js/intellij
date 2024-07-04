package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class CustomQueryDslAbstractionTest {
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
        return this.getCollection().find(eq("_id", id)).first();
    }
    
    private MongoCollection<T> getCollection() {
        return this.collection;
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
    fun `applies for a class using methods from an abstract repository with a collection factory`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("UserRepository", "findUserById")
        assertTrue(CustomQueryDslAbstraction.isIn(methodToAnalyse))
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
        return super.findById(id);
    }
}
        """,
    )
    fun `applies for a class using methods from an abstract repository without a collection factory`(psiFile: PsiFile) {
        val methodToAnalyse = psiFile.getQueryAtMethod("UserRepository", "findUserById")
        assertTrue(CustomQueryDslAbstraction.isIn(methodToAnalyse))
    }
}

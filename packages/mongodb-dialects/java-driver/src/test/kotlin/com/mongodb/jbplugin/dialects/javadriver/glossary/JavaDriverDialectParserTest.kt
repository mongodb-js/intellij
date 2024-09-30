package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getQueryAtMethod
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonArray
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.BsonObjectId
import com.mongodb.jbplugin.mql.BsonString
import com.mongodb.jbplugin.mql.components.*
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class JavaDriverDialectParserTest {
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `can parse a mongodb query using the driver`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        assertTrue(JavaDriverDialectParser.isCandidateForQuery(query))
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoClient client;
    
    public Repository(MongoClient client) {
        this.client = client;
    }
    
    public Document getCollection() {
        return client.getDatabase("simple").getCollection("books");
    }
}
        """,
    )
    fun `not a candidate if does not query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "getCollection")
        assertFalse(JavaDriverDialectParser.isCandidateForQuery(query))
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `the attachment happens in the collection method`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val collectionReference =
            PsiTreeUtil
                .findChildrenOfType(query, PsiMethodCallExpression::class.java)
                .first { it.text.endsWith("id))") }

        assertTrue(JavaDriverDialectParser.isCandidateForQuery(query))
        assertEquals(collectionReference, JavaDriverDialectParser.attachment(query))
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `can extract the namespace of a query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val knownReference =
            parsedQuery.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Known
        val namespace = knownReference.namespace

        assertEquals("simple", namespace.database)
        assertEquals("books", namespace.collection)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

public final class Repository {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }
    
    public Document findBookById(ObjectId id) {
        return this.collection.find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `handles gracefully when the namespace is unknown`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val unknownReference =
            parsedQuery.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Unknown

        assertEquals(HasCollectionReference.Unknown, unknownReference)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.types.ObjectId;
import static com.mongodb.client.model.Filters.*;

abstract class BaseRepository<T> {
    private final String dbName;
    private final String collName;
    private final Class<T> docClass;
    
    protected BaseRepository(MongoClient client, String dbName, String collName, Class<T> docClass) {
        this.client = client;
        this.dbName = dbName;
        this.collName = collName;
        this.docClass = docClass;
    }
    
    protected MongoCollection<T> getCollection() {
        return client.getDatabase(dbName).getCollection(collName, docClass);
    }
}

public final class Repository extends BaseRepository<Document> {
    private final MongoCollection<Document> collection;
    
    public Repository(MongoClient client, String collection) {
        super(client, "myDb", collection, Document.class);
    }
    
    public Document findById(ObjectId id) {
        return this.getCollection().find(eq("_id", id)).first();
    }
}
        """,
    )
    fun `supports inheritance chains with unknown collections`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val unknownReference =
            parsedQuery.component<HasCollectionReference<*>>()?.reference as HasCollectionReference.Unknown

        assertEquals(HasCollectionReference.Unknown, unknownReference)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public FindIterable<Document> findBookById(ObjectId id) {
        return this.collection.find(Filters.eq("_id", id));
    }
}
        """,
    )
    fun `can parse a basic Filters query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "_id",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName
        )
        assertEquals(
            BsonAnyOf(BsonObjectId, BsonNull),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.bson.types.ObjectId;
import com.mongodb.client.FindIterable;

public final class Repository {
    private final MongoCollection<Document> collection;

    public Repository(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    public FindIterable<Document> findBookById(ObjectId id) {
        return this.collection.find((Filters.eq("_id", id)));
    }
}
        """,
    )
    fun `can parse a basic Filters query inside parenthesis`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBookById")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "_id",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName
        )
        assertEquals(
            BsonAnyOf(BsonObjectId, BsonNull),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("myField", true));
    }
}
        """,
    )
    fun `can parse a basic Filters query with a constant parameter in a chain of calls`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "myField",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("myField", null));
    }
}
        """,
    )
    fun `correctly parses a nullable value reference as BsonNull type`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "myField",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonNull,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            null,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(and(eq("released", true), eq("hidden", false)));
    }
}
        """,
    )
    fun `supports vararg operators`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.AND, and.component<Named>()!!.name)
        val andChildren = and.component<HasChildren<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(not(eq("released", true)));
    }
}
        """,
    )
    fun `supports the not operator`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.NOT, and.component<Named>()!!.name)
        val andChildren = and.component<HasChildren<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        var isReleased = eq("released", true);
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(isReleased);
    }
}
        """,
    )
    fun `supports references to variables in a query expression`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(isReleased());
    }
    
    private Document isReleased() {
        return eq("released", true);
    }
}
        """,
    )
    fun `supports to methods in a query expression`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(eq("released", released));
    }
}
        """,
    )
    fun `supports to methods in a custom dsl as in mms`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class Repository {
    private static final String RELEASED = "released";
    private static final String HIDDEN = "hidden";
    
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        var isReleased = eq(RELEASED, true);
        var isNotHidden = eq(HIDDEN, false);
        
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(and(isReleased, isNotHidden));
    }
}
        """,
    )
    fun `supports vararg operators with references to fields in variables`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val and = hasChildren.children[0]
        assertEquals(Name.AND, and.component<Named>()!!.name)
        val andChildren = and.component<HasChildren<Unit?>>()!!

        val firstEq = andChildren.children[0]
        assertEquals(
            "released",
            (firstEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            true,
            (firstEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )

        val secondEq = andChildren.children[1]
        assertEquals(
            "hidden",
            (secondEq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonAnyOf(BsonNull, BsonBoolean),
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).type,
        )
        assertEquals(
            false,
            (secondEq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateOne(eq("released", released), unset("field"));
    }
}
        """,
    )
    fun `supports updateOne calls with a filter and update expressions`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val unset = hasChildren.children[1]
        assertEquals(Name.UNSET, unset.component<Named>()!!.name)
        assertEquals(
            "field",
            (unset.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> findReleasedBooks() {
        return findAllByReleaseFlag(true);
    }
    
    private Document findAllByReleaseFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateOne(eq("released", released), (unset("field")));
    }
}
        """,
    )
    fun `supports updateOne calls with a filter and update expressions in parenthesis`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "findReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val unset = hasChildren.children[1]
        assertEquals(Name.UNSET, unset.component<Named>()!!.name)
        assertEquals(
            "field",
            (unset.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> updateReleasedBooks() {
        return updateManyByReleasedFlag(true);
    }
    
    private Document updateManyByReleasedFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateMany(eq("released", released), set("field", 1));
    }
}
        """,
    )
    fun `supports updateMany calls with a filter and update expressions setting a value`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "updateReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val unset = hasChildren.children[1]
        assertEquals(Name.SET, unset.component<Named>()!!.name)
        assertEquals(
            "field",
            (unset.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            1,
            (unset.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Constant).value,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> updateReleasedBooks() {
        return updateManyByReleasedFlag(true);
    }
    
    private Document updateManyByReleasedFlag(boolean released) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .updateMany(eq("released", released), combine(set("field", 1), unset("anotherField")));
    }
}
        """,
    )
    fun `supports updates combining update operations`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "updateReleasedBooks")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.EQ, eq.component<Named>()!!.name)
        assertEquals(
            "released",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonBoolean,
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )

        val combine = hasChildren.children[1]
        assertEquals(Name.COMBINE, combine.component<Named>()!!.name)
        assertEquals(2, combine.component<HasChildren<Unit?>>()!!.children.size)

        val children = combine.component<HasChildren<Unit?>>()!!.children
        assertEquals(Name.SET, children[0].component<Named>()!!.name)
        assertEquals(Name.UNSET, children[1].component<Named>()!!.name)
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findBooksByGenre(String[] validGenres) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", validGenres));
    }
}
        """,
    )
    fun `supports the in operator as array`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksByGenre")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findBooksByGenre(String genre) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", genre));
    }
}
        """,
    )
    fun `supports the in operator with a single element`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksByGenre")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }

    @ParsingTest(
        fileName = "Repository.java",
        value = """
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;
import java.util.List;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

public class Repository {
    private final MongoClient client;

    public Repository(MongoClient client) {
        this.client = client;
    }
    
    private FindIterable<Document> findBooksByGenre(List<String> genres) {
        return client.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find(in("genre", genres));
    }
}
        """,
    )
    fun `supports the in operator with a list`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "findBooksByGenre")
        val parsedQuery = JavaDriverDialect.parser.parse(query)

        val hasChildren =
            parsedQuery.component<HasChildren<Unit?>>()!!

        val eq = hasChildren.children[0]
        assertEquals(Name.IN, eq.component<Named>()!!.name)
        assertEquals(
            "genre",
            (eq.component<HasFieldReference<Unit?>>()!!.reference as HasFieldReference.Known).fieldName,
        )
        assertEquals(
            BsonArray(BsonAnyOf(BsonNull, BsonString)),
            (eq.component<HasValueReference<PsiElement>>()!!.reference as HasValueReference.Runtime).type,
        )
    }
}

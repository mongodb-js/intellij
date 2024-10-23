package com.mongodb.jbplugin.dialects.springquery

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
import com.mongodb.jbplugin.mql.components.IsCommand
import com.mongodb.jbplugin.mql.components.Name
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled

@IntegrationTest
class SpringAtQueryDialectParserTest {
    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: ?0 }")
    Optional<Comment> findBySomething(String something);
}
      """
    )
    fun `can detect the namespace of a query`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.FIND_ONE) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("comments", collection)
            }
        }
    }

    @Disabled
    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: 'a' }")
    Optional<Comment> findBySomething(String something);
}
      """
    )
    fun `can find simple filters inside a query`(
        file: PsiFile
    ) {
        val query = file.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY) {
            collection<HasCollectionReference.OnlyCollection<PsiElement>> {
                assertEquals("comments", collection)
            }

            filterN(0, Name.EQ) {
                field<HasFieldReference.Known<PsiElement>> { assertEquals("name", fieldName) }
                value<HasValueReference.Constant<PsiElement>> { assertEquals("a", value) }
            }
        }
    }

    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: 'a' }", count = true)
    long findBySomething(String something);
}
      """
    )
    fun `can run count queries`(
        file: PsiFile
    ) {
        val query = file.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.COUNT_DOCUMENTS)
    }

    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: 'a' }", exists = true)
    boolean findBySomething(String something);
}
      """
    )
    fun `can run exists queries`(
        file: PsiFile
    ) {
        val query = file.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.FIND_ONE)
    }

    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: 'a' }", delete = true)
    void findBySomething(String something);
}
      """
    )
    fun `can run delete queries`(
        file: PsiFile
    ) {
        val query = file.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.DELETE_MANY)
    }

    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.stream.Stream;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: 'a' }")
    Stream<Comment> findBySomething(String something);
}
      """
    )
    fun `can run find many queries returning streams`(
        file: PsiFile
    ) {
        val query = file.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.FIND_MANY)
    }

    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.stream.Stream;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: 'a' }")
    Comment findBySomething(String something);
}
      """
    )
    fun `can run find one queries returning a single object`(
        file: PsiFile
    ) {
        val query = file.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.FIND_ONE)
    }

    @ParsingTest(
        fileName = "Repository.java",
        """
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;
import java.util.stream.Stream;

@Document("comments")
record Comment(
    String name
) {}

public interface SQRepository extends Repository<Comment, ObjectId> {
    @Query("{ name: 'a' }")
    Optional<Comment> findBySomething(String something);
}
      """
    )
    fun `can run find one queries returning an optional object`(
        file: PsiFile
    ) {
        val query = file.getQueryAtMethod("SQRepository", "findBySomething")
        SpringAtQueryDialectParser.parse(query).assert(IsCommand.CommandType.FIND_ONE)
    }
}

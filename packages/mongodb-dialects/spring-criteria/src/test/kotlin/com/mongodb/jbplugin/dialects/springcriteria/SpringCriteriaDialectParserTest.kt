package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.mql.BsonAnyOf
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.BsonNull
import com.mongodb.jbplugin.mql.components.*
import com.mongodb.jbplugin.mql.components.HasCollectionReference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@IntegrationTest
class SpringCriteriaDialectParserTest {
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class).matching(where("released").is(true)).all();
    }
}
        """
    )
    fun `extracts a simple criteria query`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val commandType = node.component<IsCommand>()!!.type
        val fieldNameReference = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val valueReference = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference
        val collectionReference = node.component<HasCollectionReference<*>>()!!.reference

        fieldNameReference as HasFieldReference.Known<PsiElement>
        valueReference as HasValueReference.Constant
        collectionReference as HasCollectionReference.OnlyCollection

        assertEquals("released", fieldNameReference.fieldName)
        assertEquals(true, valueReference.value)
        assertEquals("book", collectionReference.collection)
        assertEquals(IsCommand.CommandType.FIND_MANY, commandType)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class).matching((where("released").is(true))).all();
    }
}
        """
    )
    fun `extracts a simple criteria query inside parenthesis`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val fieldNameReference = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val valueReference = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference
        val collectionReference = node.component<HasCollectionReference<*>>()!!.reference

        fieldNameReference as HasFieldReference.Known<PsiElement>
        valueReference as HasValueReference.Constant
        collectionReference as HasCollectionReference.OnlyCollection

        assertEquals("released", fieldNameReference.fieldName)
        assertEquals(true, valueReference.value)
        assertEquals("book", collectionReference.collection)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allBooks(boolean released) {
        return template.query(Book.class).matching(where("released").is(released)).all();
    }
}
        """
    )
    fun `supports variables in values`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val fieldNameReference = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val valueReference = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        fieldNameReference as HasFieldReference.Known<PsiElement>
        valueReference as HasValueReference.Runtime

        assertEquals("released", fieldNameReference.fieldName)
        assertEquals(BsonBoolean, valueReference.type)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).and("hidden").is(0))
                       .all();
    }
}
        """
    )
    fun `extracts a criteria query with multiple fields`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val whereHiddenIs0 = node.component<HasFilter<PsiElement>>()!!.children[1]
        val referenceToHidden = whereHiddenIs0.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceTo0 = whereHiddenIs0.component<HasValueReference<PsiElement>>()!!.reference

        referenceToReleased as HasFieldReference.Known<PsiElement>
        referenceToTrue as HasValueReference.Constant

        referenceToHidden as HasFieldReference.Known<PsiElement>
        referenceTo0 as HasValueReference.Constant

        assertEquals(Name.EQ, whereReleasedIsTrue.component<Named>()!!.name)
        assertEquals("released", referenceToReleased.fieldName)
        assertEquals(true, referenceToTrue.value)

        assertEquals(Name.EQ, whereReleasedIsTrue.component<Named>()!!.name)
        assertEquals("hidden", referenceToHidden.fieldName)
        assertEquals(0, referenceTo0.value)
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).andOperator(
                            where("hidden").is(false),
                            where("valid").is(true)
                        ))
                       .all();
    }
}
        """
    )
    fun `supports nested operators like andOperator`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val andOperator = node.component<HasFilter<PsiElement>>()!!.children[1]
        val hiddenIsFalse = andOperator.component<HasFilter<PsiElement>>()!!.children[0]
        val validIsTrue = andOperator.component<HasFilter<PsiElement>>()!!.children[1]

        val referenceToHidden = hiddenIsFalse.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToFalse = hiddenIsFalse.component<HasValueReference<PsiElement>>()!!.reference

        val referenceToValid = validIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToValidTrue = validIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        referenceToReleased as HasFieldReference.Known<PsiElement>
        referenceToTrue as HasValueReference.Constant

        referenceToHidden as HasFieldReference.Known<PsiElement>
        referenceToFalse as HasValueReference.Constant

        referenceToValid as HasFieldReference.Known<PsiElement>
        referenceToValidTrue as HasValueReference.Constant

        assertEquals(Name.AND, andOperator.component<Named>()!!.name)
        assertEquals("released", referenceToReleased.fieldName)
        assertEquals(true, referenceToTrue.value)

        assertEquals("hidden", referenceToHidden.fieldName)
        assertEquals(false, referenceToFalse.value)

        assertEquals("valid", referenceToValid.fieldName)
        assertEquals(true, referenceToValidTrue.value)
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).orOperator(
                            where("hidden").is(false).and("valid").is(true)
                        ))
                       .all();
    }
}
        """
    )
    fun `supports nested operators like orOperator`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val orOperator = node.component<HasFilter<PsiElement>>()!!.children[1]
        val hiddenIsFalse = orOperator.component<HasFilter<PsiElement>>()!!.children[0]
        val validIsTrue = orOperator.component<HasFilter<PsiElement>>()!!.children[1]

        val referenceToHidden = hiddenIsFalse.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToFalse = hiddenIsFalse.component<HasValueReference<PsiElement>>()!!.reference

        val referenceToValid = validIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToValidTrue = validIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        referenceToReleased as HasFieldReference.Known<PsiElement>
        referenceToTrue as HasValueReference.Constant

        referenceToHidden as HasFieldReference.Known<PsiElement>
        referenceToFalse as HasValueReference.Constant

        referenceToValid as HasFieldReference.Known<PsiElement>
        referenceToValidTrue as HasValueReference.Constant

        assertEquals(Name.OR, orOperator.component<Named>()!!.name)
        assertEquals("released", referenceToReleased.fieldName)
        assertEquals(true, referenceToTrue.value)

        assertEquals("hidden", referenceToHidden.fieldName)
        assertEquals(false, referenceToFalse.value)

        assertEquals("valid", referenceToValid.fieldName)
        assertEquals(true, referenceToValidTrue.value)
    }

    @Suppress("TOO_LONG_FUNCTION")
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("released").is(true).norOperator(
                            where("hidden").is(false),
                            where("valid").is(true)
                        ))
                       .all();
    }
}
        """
    )
    fun `supports nested operators like notOperator`(
        psiFile: PsiFile
    ) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val norOperator = node.component<HasFilter<PsiElement>>()!!.children[1]
        val hiddenIsFalse = norOperator.component<HasFilter<PsiElement>>()!!.children[0]
        val validIsTrue = norOperator.component<HasFilter<PsiElement>>()!!.children[1]

        val referenceToHidden = hiddenIsFalse.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToFalse = hiddenIsFalse.component<HasValueReference<PsiElement>>()!!.reference

        val referenceToValid = validIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToValidTrue = validIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        referenceToReleased as HasFieldReference.Known<PsiElement>
        referenceToTrue as HasValueReference.Constant

        referenceToHidden as HasFieldReference.Known<PsiElement>
        referenceToFalse as HasValueReference.Constant

        referenceToValid as HasFieldReference.Known<PsiElement>
        referenceToValidTrue as HasValueReference.Constant

        assertEquals(Name.NOR, norOperator.component<Named>()!!.name)
        assertEquals("released", referenceToReleased.fieldName)
        assertEquals(true, referenceToTrue.value)

        assertEquals("hidden", referenceToHidden.fieldName)
        assertEquals(false, referenceToFalse.value)

        assertEquals("valid", referenceToValid.fieldName)
        assertEquals(true, referenceToValidTrue.value)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
        """
    )
    fun `can not refer to databases`(psiFile: PsiFile) {
        assertFalse(SpringCriteriaDialectParser.isReferenceToDatabase(psiFile))
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document("|")
record Book() {}
        """
    )
    fun `can refer to a collection in a @Document annotation`(psiFile: PsiFile) {
        assertTrue(SpringCriteriaDialectParser.isReferenceToCollection(psiFile.caret()))
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> allReleasedBooks() {
        return template.query(Book.class)
                       .matching(where("|";
    }
}
        """
    )
    fun `can refer to a field in a criteria chain`(psiFile: PsiFile) {
        assertTrue(SpringCriteriaDialectParser.isReferenceToField(psiFile.caret()))
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public long allReleasedBooks() {
        return template.count(where("released").is(true), Book.class);
    }
}
        """
    )
    fun `can parse count queries scenario`(psiFile: PsiFile) {
        val query = psiFile.getQueryAtMethod("Repository", "allReleasedBooks")
        val node = SpringCriteriaDialectParser.parse(query)

        val command = node.component<IsCommand>()!!
        val whereReleasedIsTrue = node.component<HasFilter<PsiElement>>()!!.children[0]
        val fieldNameReference = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val valueReference = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        fieldNameReference as HasFieldReference.Known<PsiElement>
        valueReference as HasValueReference.Constant

        assertEquals(IsCommand.CommandType.COUNT_DOCUMENTS, command.type)
        assertEquals("released", fieldNameReference.fieldName)
        assertEquals(BsonAnyOf(BsonNull, BsonBoolean), valueReference.type)
        assertEquals(true, valueReference.value)
    }

    @AdditionalFile(
        fileName = "Repository.java",
        value = """
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;

import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Document
record Book() {}

class Repository {
    private final MongoTemplate template;
    
    public Repository(MongoTemplate template) {
        this.template = template;
    }
    
    public List<Book> randomQuery() {
        return template."|"(where("released"));
    }
}
        """,
    )
    @ParameterizedTest
    @CsvSource(
        value = [
            "method;;expected",
            "count;;COUNT_DOCUMENTS",
            "exactCount;;COUNT_DOCUMENTS",
            "exists;;FIND_ONE",
            "estimatedCount;;ESTIMATED_DOCUMENT_COUNT",
            "findDistinct;;DISTINCT",
            "findById;;FIND_ONE",
            "find;;FIND_MANY",
            "findAll;;FIND_MANY",
            "scroll;;FIND_MANY",
            "stream;;FIND_MANY",
            "aggregate;;AGGREGATE",
            "aggregateStream;;AGGREGATE",
            "insert;;INSERT_ONE",
            "insertAll;;INSERT_MANY",
            "remove;;DELETE_MANY",
            "findAllAndRemove;;DELETE_MANY",
            "replace;;REPLACE_ONE",
            "save;;UPSERT",
            "updateFirst;;UPDATE_ONE",
            "updateMulti;;UPDATE_MANY",
            "findAndRemove;;FIND_ONE_AND_DELETE",
            "findAndModify;;FIND_ONE_AND_UPDATE",
            "findAndReplace;;FIND_ONE_AND_REPLACE",
            "mapReduce;;UNKNOWN",
        ],
        delimiterString = ";;",
        useHeadersInDisplayName = true
    )
    fun `supports all relevant commands from the driver`(
        method: String,
        expected: IsCommand.CommandType,
        psiFile: PsiFile
    ) {
        WriteCommandAction.runWriteCommandAction(psiFile.project) {
            val elementAtCaret = psiFile.caret()
            val javaFacade = JavaPsiFacade.getInstance(psiFile.project)
            val methodToTest = javaFacade.parserFacade.createReferenceFromText(method, null)
            elementAtCaret.replace(methodToTest)
        }

        ApplicationManager.getApplication().runReadAction {
            val query = psiFile.getQueryAtMethod("Repository", "randomQuery")
            val parsedQuery = SpringCriteriaDialectParser.parse(query)

            val command = parsedQuery.component<IsCommand>()
            assertEquals(expected, command?.type)
        }
    }
}

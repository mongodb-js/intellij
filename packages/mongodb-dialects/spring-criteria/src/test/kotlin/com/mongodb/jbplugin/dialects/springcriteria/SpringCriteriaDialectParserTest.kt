package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.mql.BsonBoolean
import com.mongodb.jbplugin.mql.components.*
import org.junit.jupiter.api.Assertions.assertEquals

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

        val whereReleasedIsTrue = node.component<HasChildren<PsiElement>>()!!.children[0]
        val fieldNameReference = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val valueReference = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference
        val collectionReference = node.component<HasCollectionReference>()!!.reference

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

        val whereReleasedIsTrue = node.component<HasChildren<PsiElement>>()!!.children[0]
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

        val whereReleasedIsTrue = node.component<HasChildren<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val whereHiddenIs0 = node.component<HasChildren<PsiElement>>()!!.children[1]
        val referenceToHidden = whereHiddenIs0.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceTo0 = whereHiddenIs0.component<HasValueReference<PsiElement>>()!!.reference

        referenceToReleased as HasFieldReference.Known<PsiElement>
        referenceToTrue as HasValueReference.Constant

        referenceToHidden as HasFieldReference.Known<PsiElement>
        referenceTo0 as HasValueReference.Constant

        assertEquals("released", referenceToReleased.fieldName)
        assertEquals(true, referenceToTrue.value)

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

        val whereReleasedIsTrue = node.component<HasChildren<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val andOperator = node.component<HasChildren<PsiElement>>()!!.children[1]
        val hiddenIsFalse = andOperator.component<HasChildren<PsiElement>>()!!.children[0]
        val validIsTrue = andOperator.component<HasChildren<PsiElement>>()!!.children[1]

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

        assertEquals("and", andOperator.component<Named>()!!.name)
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
                            where("hidden").is(false),
                            where("valid").is(true)
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

        val whereReleasedIsTrue = node.component<HasChildren<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val orOperator = node.component<HasChildren<PsiElement>>()!!.children[1]
        val hiddenIsFalse = orOperator.component<HasChildren<PsiElement>>()!!.children[0]
        val validIsTrue = orOperator.component<HasChildren<PsiElement>>()!!.children[1]

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

        assertEquals("or", orOperator.component<Named>()!!.name)
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

        val whereReleasedIsTrue = node.component<HasChildren<PsiElement>>()!!.children[0]
        val referenceToReleased = whereReleasedIsTrue.component<HasFieldReference<PsiElement>>()!!.reference
        val referenceToTrue = whereReleasedIsTrue.component<HasValueReference<PsiElement>>()!!.reference

        val norOperator = node.component<HasChildren<PsiElement>>()!!.children[1]
        val hiddenIsFalse = norOperator.component<HasChildren<PsiElement>>()!!.children[0]
        val validIsTrue = norOperator.component<HasChildren<PsiElement>>()!!.children[1]

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

        assertEquals("nor", norOperator.component<Named>()!!.name)
        assertEquals("released", referenceToReleased.fieldName)
        assertEquals(true, referenceToTrue.value)

        assertEquals("hidden", referenceToHidden.fieldName)
        assertEquals(false, referenceToFalse.value)

        assertEquals("valid", referenceToValid.fieldName)
        assertEquals(true, referenceToValidTrue.value)
    }
}
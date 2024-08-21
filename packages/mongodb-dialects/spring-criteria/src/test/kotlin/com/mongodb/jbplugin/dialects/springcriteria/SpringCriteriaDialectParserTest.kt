package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.mql.components.HasChildren
import com.mongodb.jbplugin.mql.components.HasFieldReference
import com.mongodb.jbplugin.mql.components.HasValueReference
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

        fieldNameReference as HasFieldReference.Known<PsiElement>
        valueReference as HasValueReference.Constant

        assertEquals("released", fieldNameReference.fieldName)
        assertEquals(true, valueReference.value)
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
}
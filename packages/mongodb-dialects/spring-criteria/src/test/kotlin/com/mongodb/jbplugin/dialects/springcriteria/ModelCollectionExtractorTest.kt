package com.mongodb.jbplugin.dialects.springcriteria

import com.intellij.psi.PsiFile
import org.junit.jupiter.api.Assertions.assertEquals

@IntegrationTest
class ModelCollectionExtractorTest {
    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document
public record Book() {}
        """
    )
    fun `extracts and infers the collection name from a class`(
        psiFile: PsiFile
    ) {
        val bookClass = psiFile.getClassByName("Book")
        val collectionName = ModelCollectionExtractor.fromPsiClass(bookClass)

        assertEquals("book", collectionName)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document("books")
public record Book() {}
        """
    )
    fun `extracts the collection name from the value attribute`(
        psiFile: PsiFile
    ) {
        val bookClass = psiFile.getClassByName("Book")
        val collectionName = ModelCollectionExtractor.fromPsiClass(bookClass)

        assertEquals("books", collectionName)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "books")
public record Book() {}
        """
    )
    fun `extracts the collection name from the collection attribute`(
        psiFile: PsiFile
    ) {
        val bookClass = psiFile.getClassByName("Book")
        val collectionName = ModelCollectionExtractor.fromPsiClass(bookClass)

        assertEquals("books", collectionName)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "books")
public abstract class Book {}

public class PublishedBook extends Book {}
        """
    )
    fun `extracts the collection from an inherited class`(
        psiFile: PsiFile
    ) {
        val bookClass = psiFile.getClassByName("PublishedBook")
        val collectionName = ModelCollectionExtractor.fromPsiClass(bookClass)

        assertEquals("books", collectionName)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "books")
public interface Book {}

public class PublishedBook implements Book {}
        """
    )
    fun `extracts the collection from an implementing interface`(
        psiFile: PsiFile
    ) {
        val bookClass = psiFile.getClassByName("PublishedBook")
        val collectionName = ModelCollectionExtractor.fromPsiClass(bookClass)

        assertEquals("books", collectionName)
    }

    @ParsingTest(
        fileName = "Book.java",
        """
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "books")
public interface Book {}

@Document("publishedBooks")
public class PublishedBook implements Book {}
        """
    )
    fun `prioritises the top declaration in case of conflict`(
        psiFile: PsiFile
    ) {
        val bookClass = psiFile.getClassByName("PublishedBook")
        val collectionName = ModelCollectionExtractor.fromPsiClass(bookClass)

        assertEquals("publishedBooks", collectionName)
    }
}
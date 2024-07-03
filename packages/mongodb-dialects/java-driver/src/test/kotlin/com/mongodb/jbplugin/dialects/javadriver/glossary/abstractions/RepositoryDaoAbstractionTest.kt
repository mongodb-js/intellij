package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class RepositoryDaoAbstractionTest {
    @ParsingTest(
        "Repository.java",
        """
public final class Repository {
}
        """,
    )
    fun `does not apply if the class is named repository but it does not use the driver`(psiFile: PsiFile) {
        assertFalse(RepositoryDaoAbstraction.isIn(psiFile))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;

public final class Repository {
    private final MongoClient client;
}
        """,
    )
    fun `does apply if the class contains references to the client`(psiFile: PsiFile) {
        assertTrue(RepositoryDaoAbstraction.isIn(psiFile))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoDatabase;

public final class Repository {
    private final MongoDatabase database;
}
        """,
    )
    fun `does apply if the class contains references to a database`(psiFile: PsiFile) {
        assertTrue(RepositoryDaoAbstraction.isIn(psiFile))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoCollection;

public final class Repository {
    private final MongoCollection collection;
}
        """,
    )
    fun `does apply if the class contains references to a collection`(psiFile: PsiFile) {
        assertTrue(RepositoryDaoAbstraction.isIn(psiFile))
    }
}

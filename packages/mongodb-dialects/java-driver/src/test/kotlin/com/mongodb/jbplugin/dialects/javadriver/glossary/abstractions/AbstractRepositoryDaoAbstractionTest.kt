package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getClassByName
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class AbstractRepositoryDaoAbstractionTest {
    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;

public abstract class AbstractRepository {
    private final MongoClient client;
}

public final class Repository extends AbstractRepository {
}
        """,
    )
    fun `does apply if the class extends a repository`(psiFile: PsiFile) {
        val classToAnalyse = psiFile.getClassByName("Repository")
        assertTrue(AbstractRepositoryDaoAbstraction.isIn(classToAnalyse))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;

public abstract class MyService {
}

public final class Repository extends MyService {
}
        """,
    )
    fun `does not apply if the class extends a another class but is not a repository`(psiFile: PsiFile) {
        val classToAnalyse = psiFile.getClassByName("Repository")
        assertFalse(AbstractRepositoryDaoAbstraction.isIn(classToAnalyse))
    }
}

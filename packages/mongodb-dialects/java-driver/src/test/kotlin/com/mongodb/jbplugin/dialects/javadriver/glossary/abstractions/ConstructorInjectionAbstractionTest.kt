package com.mongodb.jbplugin.dialects.javadriver.glossary.abstractions

import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.dialects.javadriver.ParsingTest
import com.mongodb.jbplugin.dialects.javadriver.getClassByName
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class ConstructorInjectionAbstractionTest {
    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;

abstract class AbstractRepository {
    private final MongoClient client;
    
    protected Repository(MongoClient client) {
        this.client = client;
    }
}

public class Repository extends AbstractRepository {
    public Repository(MongoClient client) {
        super(client);
    }
}
        """,
    )
    fun `does apply if the class depends on a repository super constructor`(psiFile: PsiFile) {
        val repository = psiFile.getClassByName("Repository")
        assertTrue(ConstructorInjectionAbstraction.isIn(repository))
    }

    @ParsingTest(
        "Repository.java",
        """
import com.mongodb.client.MongoClient;

abstract class AbstractRepository {
    private final MongoClient client;
    
    protected Repository(MongoClient client) {
        this.client = client;
    }
}

public class Repository extends AbstractRepository {
    public Repository(MongoClient client) {
        
    }
}
        """,
    )
    fun `does not apply if does not call super constructor`(psiFile: PsiFile) {
        val repository = psiFile.getClassByName("Repository")
        assertFalse(ConstructorInjectionAbstraction.isIn(repository))
    }
}

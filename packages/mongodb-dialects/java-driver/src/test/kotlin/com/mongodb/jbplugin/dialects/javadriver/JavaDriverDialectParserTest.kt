package com.mongodb.jbplugin.dialects.javadriver

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.mongodb.jbplugin.dialects.IntegrationTest
import com.mongodb.jbplugin.dialects.ParsingTest
import com.mongodb.jbplugin.dialects.testQuery
import com.mongodb.jbplugin.mql.ast.HasChildren
import com.mongodb.jbplugin.mql.ast.HasFieldReference
import com.mongodb.jbplugin.mql.ast.HasValueReference
import com.mongodb.jbplugin.mql.ast.Named
import com.mongodb.jbplugin.mql.schema.BsonInt32
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*

@IntegrationTest
class JavaDriverDialectParserTest {
    @ParsingTest(
        fileName = "MyExampleRepository.java",
        """
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

public class MyExampleRepository {
    private static final MongoCollection collection;
    
    public static void main(String[] args) {
        collection.find(Filters.eq("field", 234));
    }
}
""",
    )
    fun `does support a basic find query with a filter`(psiFile: PsiFile) =
        runBlocking {
            assertTrue(JavaDriverDialect.parser.canParse(psiFile.testQuery))

            val query = JavaDriverDialect.parser.parse(psiFile.testQuery)
            val children = query.component<HasChildren<PsiElement>>()!!
            val eqNode = children.children[0]

            val named = eqNode.component<Named>()!!
            assertEquals("eq", named.name)
            val fieldRef = eqNode.component<HasFieldReference>()!!
            assertEquals(HasFieldReference.Known("field"), fieldRef.reference)

            val valueRef = eqNode.component<HasValueReference>()!!
            assertEquals(HasValueReference.Constant(234, BsonInt32), valueRef.reference)
        }

    @ParsingTest(
        fileName = "MyExampleRepository.java",
        """
import com.mongodb.client.MongoCollection;
import static com.mongodb.client.model.Filters.*;

public class MyExampleRepository {
    private static final MongoCollection collection;
    
    public static void main(String[] args) {
        collection.find(eq("field", 234));
    }
}
""",
    )
    fun `does support a basic find query with a filter with static import`(psiFile: PsiFile) =
        runBlocking {
            assertTrue(JavaDriverDialect.parser.canParse(psiFile.testQuery))

            val query = JavaDriverDialect.parser.parse(psiFile.testQuery)
            val children = query.component<HasChildren<PsiElement>>()!!
            val eqNode = children.children[0]

            val named = eqNode.component<Named>()!!
            assertEquals("eq", named.name)
            val fieldRef = eqNode.component<HasFieldReference>()!!
            assertEquals(HasFieldReference.Known("field"), fieldRef.reference)

            val valueRef = eqNode.component<HasValueReference>()!!
            assertEquals(HasValueReference.Constant(234, BsonInt32), valueRef.reference)
        }
}

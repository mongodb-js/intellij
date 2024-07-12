package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.mongodb.jbplugin.dialects.javadriver.IntegrationTest
import com.mongodb.jbplugin.mql.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

private typealias PsiTypeProvider = (Project) -> PsiType

@IntegrationTest
class PsiMdbTreeUtilTest {
    @ParameterizedTest
    @MethodSource("psiTypeToBsonType")
    fun `should map all psi types to their corresponding bson types`(
        typeProvider: PsiTypeProvider,
        expected: BsonType,
        project: Project,
    ) {
        ApplicationManager.getApplication().invokeAndWait {
            val psiType = typeProvider(project)
            assertEquals(expected, psiType.toBsonType())
        }
    }
    companion object {
        @JvmStatic
        fun psiTypeToBsonType(): Array<Array<Any>> =
            arrayOf(
                arrayOf({ project: Project -> project.findClass("org.bson.types.ObjectId") },
                    BsonAnyOf(BsonObjectId, BsonNull),
                ),
                arrayOf({ _: Project -> PsiTypes.booleanType() },
                    BsonBoolean,
                ),
                arrayOf({ _: Project -> PsiTypes.shortType() },
                    BsonInt32,
                ),
                arrayOf({ _: Project -> PsiTypes.intType() },
                    BsonInt32,
                ),
                arrayOf({ _: Project -> PsiTypes.longType() },
                    BsonInt64,
                ),
                arrayOf({ _: Project -> PsiTypes.floatType() },
                    BsonDouble,
                ),
                arrayOf({ _: Project -> PsiTypes.doubleType() },
                    BsonDouble,
                ),
                arrayOf({ project: Project -> project.findClass("java.lang.CharSequence") },
                    BsonAnyOf(BsonString, BsonNull),
                ),
                arrayOf({ project: Project -> project.findClass("java.lang.String") },
                    BsonAnyOf(BsonString, BsonNull),
                ),
                arrayOf({ project: Project -> project.findClass("java.util.Date") },
                    BsonAnyOf(BsonDate, BsonNull),
                ),
                arrayOf({ project: Project -> project.findClass("java.time.LocalDate") },
                    BsonAnyOf(BsonDate, BsonNull),
                ),
                arrayOf({ project: Project -> project.findClass("java.time.LocalDateTime") },
                    BsonAnyOf(BsonDate, BsonNull),
                ),
                arrayOf({ project: Project -> project.findClass("java.math.BigInteger") },
                    BsonAnyOf(BsonInt64, BsonNull),
                ),
                arrayOf({ project: Project -> project.findClass("java.math.BigDecimal") },
                    BsonAnyOf(BsonDecimal128, BsonNull),
                ),
            )
    }
}

private fun Project.findClass(name: String): PsiType = JavaPsiFacade.getElementFactory(this).createTypeByFQClassName(
name
)

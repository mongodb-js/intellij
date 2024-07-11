/**
 * Defines a set of extension methods to extract metadata from a Psi tree.
 */

package com.mongodb.jbplugin.dialects.javadriver.glossary

import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.childrenOfType
import com.intellij.psi.util.parentOfType
import com.mongodb.jbplugin.mql.*

/**
 * Helper extension function to get the containing class of any element.
 *
 * @return
 */
fun PsiElement.findContainingClass(): PsiClass =
    parentOfType<PsiClass>(withSelf = true)
        ?: childrenOfType<PsiClass>().first()

/**
 * Helper function to check if a type is a MongoDB Collection
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbCollectionClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)

    val mdbCollectionClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoCollection",
            GlobalSearchScope.everythingScope(project),
        )

    return this == mdbCollectionClass
}

/**
 * Helper function to check if a type is a MongoDB Collection
 *
 * @param project
 */
fun PsiType.isMongoDbCollectionClass(project: Project): Boolean {
    val thisClass = PsiTypesUtil.getPsiClass(this)
    return thisClass?.isMongoDbCollectionClass(project) == true
}

/**
 * Helper function to check if a type is a MongoDB Database
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbDatabaseClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)

    val mdbDatabaseClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoDatabase",
            GlobalSearchScope.everythingScope(project),
        )

    return this == mdbDatabaseClass
}

/**
 * Helper function to check if a type is a MongoDB Database
 *
 * @param project
 * @return
 */
fun PsiType.isMongoDbDatabaseClass(project: Project): Boolean {
    val thisClass = PsiTypesUtil.getPsiClass(this)
    return thisClass?.isMongoDbDatabaseClass(project) == true
}

/**
 * Helper function to check if a type is a MongoDB Client
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbClientClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)

    val mdbClientClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoClient",
            GlobalSearchScope.everythingScope(project),
        )

    return this == mdbClientClass
}

/**
 * Helper function to check if a type is a MongoDB Client
 *
 * @param project
 * @return
 */
fun PsiType.isMongoDbClientClass(project: Project): Boolean {
    val thisClass = PsiTypesUtil.getPsiClass(this)
    return thisClass?.isMongoDbClientClass(project) == true
}

/**
 * Helper function to check if a type is a MongoDB Cursor
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbCursorClass(project: Project): Boolean {
    val javaFacade = JavaPsiFacade.getInstance(project)

    val mdbCursorClass =
        javaFacade.findClass(
            "com.mongodb.client.MongoIterable",
            GlobalSearchScope.everythingScope(project),
        )

    return this.isInheritor(mdbCursorClass!!, false) || this == mdbCursorClass
}

/**
 * Helper function to check if a type is a MongoDB Cursor
 *
 * @param project
 * @return
 */
fun PsiType.isMongoDbCursorClass(project: Project): Boolean {
    val thisClass = PsiTypesUtil.getPsiClass(this)
    return thisClass?.isMongoDbCursorClass(project) == true
}

/**
 * Helper function to check if a type is a MongoDB Class
 *
 * @param project
 * @return
 */
fun PsiType?.isMongoDbClass(project: Project): Boolean =
    PsiTypesUtil.getPsiClass(this)?.run {
        isMongoDbCollectionClass(project) ||
            isMongoDbDatabaseClass(project) ||
            isMongoDbClientClass(project)
    } == true

/**
 * Checks if a class is a MongoDB class
 *
 * @param project
 * @return
 */
fun PsiClass.isMongoDbClass(project: Project): Boolean =
    isMongoDbCollectionClass(project) ||
        isMongoDbDatabaseClass(project) ||
        isMongoDbClientClass(project)

/**
 * Finds all references to the MongoDB driver in a method.
 *
 * @return
 */
fun PsiMethod.findAllReferencesToMongoDbObjects(): List<PsiReference> =
    PsiTreeUtil
        .findChildrenOfType(this, PsiExpression::class.java)
        .filter {
            it.type
                ?.isMongoDbClass(this.project) == true
        }.mapNotNull { it.reference }

/**
 * Find, from a method call, the current MongoDB driver method is getting called.
 */
fun PsiMethodCallExpression.findCurrentReferenceToMongoDbObject(): PsiReference? {
    if (methodExpression.type?.isMongoDbClass(project) == true) {
        return methodExpression.reference
    } else if (methodExpression.qualifierExpression is PsiSuperExpression ||
        methodExpression.qualifierExpression is PsiThisExpression ||
        methodExpression.qualifierExpression == null
    ) {
        val resolution = methodExpression.resolve()
        if (resolution is PsiField) {
            return if (resolution.type.isMongoDbClass(project)) resolution.reference else null
        } else {
            return (methodExpression.resolve() as PsiMethod?)?.findAllReferencesToMongoDbObjects()?.firstOrNull()
        }
    } else {
        if (methodExpression.qualifierExpression is PsiMethodCallExpression) {
            return (methodExpression.qualifierExpression as PsiMethodCallExpression)
                .findCurrentReferenceToMongoDbObject()
        }
    }

    return null
}

/**
 * Collects all elements of type T upwards until a type S is found.
 *
 * @param type
 * @param stopWord
 */
fun <T : PsiElement, S : PsiElement> PsiElement.collectTypeUntil(
    type: Class<T>,
    stopWord: Class<S>,
): List<T> {
    if (stopWord.isInstance(this)) {
        return emptyList()
    }

    if (type.isInstance(this)) {
        return listOf(this as T) + (this.parent?.collectTypeUntil(type, stopWord) ?: emptyList())
    }

    return emptyList()
}

/**
 * Returns the reference to any MongoDB driver call.
 *
 * @param project
 */
fun PsiMethodCallExpression.findMongoDbClassReference(project: Project): PsiExpression? {
    if (methodExpression.type?.isMongoDbClass(project) == true) {
        return methodExpression
    } else if (methodExpression.qualifierExpression is PsiMethodCallExpression) {
        return (methodExpression.qualifierExpression as PsiMethodCallExpression).findMongoDbClassReference(project)
    } else if (methodExpression.qualifierExpression?.reference?.resolve() is PsiField) {
        return methodExpression.qualifierExpression
    } else {
        val method = resolveMethod() ?: return null
        return method.body
            ?.collectTypeUntil(PsiMethodCallExpression::class.java, PsiMethod::class.java)
            ?.firstNotNullOfOrNull { it.findMongoDbClassReference(it.project) }
    }
}

/**
 * Returns the reference to a MongoDB driver collection.
 */
fun PsiElement.findMongoDbCollectionReference(): PsiExpression? {
    if (this is PsiMethodCallExpression) {
        if (methodExpression.type?.isMongoDbCollectionClass(project) == true) {
            return methodExpression
        } else if (methodExpression.qualifierExpression is PsiMethodCallExpression) {
            return (methodExpression.qualifierExpression as PsiMethodCallExpression).findMongoDbCollectionReference()
        } else if (methodExpression.qualifierExpression?.reference?.resolve() is PsiField) {
            return methodExpression.qualifierExpression
        } else {
            return methodExpression.children.firstNotNullOfOrNull { it.findMongoDbCollectionReference() }
        }
    } else if (this is PsiExpression) {
        if (this.type?.isMongoDbCollectionClass(project) == true) {
            return this
        }

        return null
    } else {
        return children.firstNotNullOfOrNull { it.findMongoDbCollectionReference() }
    }
}

/**
 * Resolves to the value of the expression if it can be known at compile time
 * or null if it can only be known at runtime.
 */
fun PsiElement.tryToResolveAsConstant(): Any? {
    if (this is PsiReferenceExpression) {
        val varRef = this.resolve()!!
        return varRef.tryToResolveAsConstant()
    } else if (this is PsiLocalVariable) {
        return this.initializer?.tryToResolveAsConstant()
    } else if (this is PsiLiteralValue) {
        val facade = JavaPsiFacade.getInstance(this.project)
        return facade.constantEvaluationHelper.computeConstantExpression(this)
    } else if (this is PsiLiteralExpression) {
        val facade = JavaPsiFacade.getInstance(this.project)
        return facade.constantEvaluationHelper.computeConstantExpression(this)
    } else if (this is PsiField && this.hasModifier(JvmModifier.FINAL)) {
        return this.initializer?.tryToResolveAsConstant()
    }

    return null
}

/**
 * Resolves to the value of the expression to a string
 * if it's known at compile time.
 *
 * @return
 */
fun PsiElement.tryToResolveAsConstantString(): String? = tryToResolveAsConstant()?.toString()

/**
 * Maps a PsiType to its BSON counterpart.
 *
 * @return
 */
fun PsiType.toBsonType(): BsonType {
    if (this.equalsToText("org.bson.types.ObjectId")) {
        return BsonAnyOf(BsonObjectId, BsonNull)
    } else if (this.equalsToText("boolean") || this.equalsToText("Boolean")) {
        return BsonBoolean
    } else if (this.equalsToText("short") || this.equalsToText("Short")) {
        return BsonInt32
    } else if (this.equalsToText("int") || this.equalsToText("Integer")) {
        return BsonInt32
    } else if (this.equalsToText("long") || this.equalsToText("Long")) {
        return BsonInt64
    } else if (this.equalsToText("BigInteger")) {
        return BsonAnyOf(BsonInt64, BsonNull)
    } else if (this.equalsToText("BigDecimal")) {
        return BsonAnyOf(BsonDecimal128, BsonNull)
    }

    return BsonAny
}

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

    return this.parent?.collectTypeUntil(type, stopWord) ?: emptyList()
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
        return (methodExpression.qualifierExpression as PsiMethodCallExpression).findMongoDbClassReference(
            project
        )
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
            return methodExpression.children.firstNotNullOfOrNull {
                it.findMongoDbCollectionReference()
            }
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
 * Resolves to a pair of the resolved value in the expression and whether it was possible to
 * resolve the value or not.
 *
 * @return Pair<Boolean, Any?> A pair where the first component represents whether
 * the value was resolved during compile time or not and the second component
 * represents the resolved value itself
 */
fun PsiElement.tryToResolveAsConstant(): Pair<Boolean, Any?> {
    val meaningfulThis = meaningfulExpression()

    if (meaningfulThis is PsiReferenceExpression) {
        val varRef = meaningfulThis.resolve()!!
        return varRef.tryToResolveAsConstant()
    } else if (meaningfulThis is PsiLocalVariable && meaningfulThis.initializer != null) {
        return meaningfulThis.initializer!!.tryToResolveAsConstant()
    } else if (meaningfulThis is PsiLiteralValue) {
        val facade = JavaPsiFacade.getInstance(meaningfulThis.project)
        val resolvedValue = facade.constantEvaluationHelper.computeConstantExpression(
            meaningfulThis
        )
        return true to resolvedValue
    } else if (meaningfulThis is PsiLiteralExpression) {
        val facade = JavaPsiFacade.getInstance(meaningfulThis.project)
        val resolvedValue = facade.constantEvaluationHelper.computeConstantExpression(
            meaningfulThis
        )
        return true to resolvedValue
    } else if (meaningfulThis is PsiField &&
        meaningfulThis.initializer != null &&
        meaningfulThis.hasModifier(JvmModifier.FINAL)
    ) {
        return meaningfulThis.initializer!!.tryToResolveAsConstant()
    }

    return false to null
}

/**
 * Resolves to the value of the expression to a string
 * if it's known at compile time.
 *
 * @return
 */
fun PsiElement.tryToResolveAsConstantString(): String? =
    tryToResolveAsConstant().takeIf { it.first }?.second?.toString()

/**
 * Maps a PsiType to its BSON counterpart.
 *
 */
fun PsiType.toBsonType(): BsonType {
    return this.canonicalText.toBsonType()
}

/**
 * Maps a Java FQN to a BsonType.
 */
fun String.toBsonType(): BsonType {
    if (this == ("org.bson.types.ObjectId")) {
        return BsonAnyOf(BsonObjectId, BsonNull)
    } else if (this == ("boolean") || this == ("java.lang.Boolean")) {
        return BsonBoolean
    } else if (this == ("short") || this == ("java.lang.Short")) {
        return BsonInt32
    } else if (this == ("int") || this == ("java.lang.Integer")) {
        return BsonInt32
    } else if (this == ("long") || this == ("java.lang.Long")) {
        return BsonInt64
    } else if (this == ("float") || this == ("java.lang.Float")) {
        return BsonDouble
    } else if (this == ("double") || this == ("java.lang.Double")) {
        return BsonDouble
    } else if (this == ("java.lang.CharSequence") ||
        this == ("java.lang.String") ||
        this == "String"
    ) {
        return BsonAnyOf(BsonString, BsonNull)
    } else if (this == ("java.util.Date") ||
        this == ("java.time.LocalDate") ||
        this == ("java.time.LocalDateTime")
    ) {
        return BsonAnyOf(BsonDate, BsonNull)
    } else if (this == ("java.math.BigInteger")) {
        return BsonAnyOf(BsonInt64, BsonNull)
    } else if (this == ("java.math.BigDecimal")) {
        return BsonAnyOf(BsonDecimal128, BsonNull)
    } else if (this.endsWith("[]")) {
        val baseType = this.substring(0, this.length - 2)
        return BsonArray(baseType.toBsonType())
    }

    return BsonAny
}

/**
 * Returns all children of type in a list. Order is not guaranteed between calls.
 * It also takes into consideration in method calls, the parameters of the method call.
 *
 * @param type
 */
fun <T> PsiElement.findAllChildrenOfType(type: Class<T>): List<T> {
    var allChildren = this.children.flatMap { it.findAllChildrenOfType(type) }

    if (this is PsiMethodCallExpression) {
        allChildren += this.argumentList.expressions.flatMap { it.findAllChildrenOfType(type) }
    }

    if (type.isInstance(this)) {
        allChildren += listOf(this as T)
    }

    return allChildren
}

/**
 * Resolves to the first meaningful expression in a tree. Not all expressions have an important
 * meaning for us (like for example, parenthesized expressions) so we drop them and get any
 * inner expression that is relevant for us.
 */
fun PsiElement.meaningfulExpression(): PsiElement {
    return when (this) {
        // the children are: '(', YOUR_EXPR, ')'
        // so we need the expression inside (index 1)
        is PsiParenthesizedExpression -> if (children.size == 3) {
            children[1].meaningfulExpression()
        } else {
            this
        }
        else -> this
    }
}

/**
 * Resolves the method fuzzily. In case that a single implementation has multiple overloads, the
 * default implementation from IntelliJ returns null, we will just get the first of them.
 */
fun PsiMethodCallExpression.fuzzyResolveMethod(): PsiMethod? {
    val allResolutions = methodExpression.multiResolve(false)

    if (allResolutions.isEmpty()) {
        return null
    }

    return allResolutions.first().element as? PsiMethod
}

# MQL BSON Type
-----------

## Abstract

This specification documents the different kinds of BSON types and how they are related to the
original source code of a [MQL Query](../mql-query/mql-query.md). This document aims to provide
information about the behaviour of dialects and linters on the computation of the original
expression BSON type.

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY"
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

[BSON](https://bsonspec.org/spec.html) is a binary format that is used to communicate between the
MongoDB Client (through a driver) and a MongoDB Cluster. MQL BSON (from now on we will just say BSON) 
is a superset of the original BSON types.

A BSON Type represents the data type inferred from the original source code or from a MongoDB sample
of documents. A BSON Type MUST be consumable by a MongoDB Cluster and it's serialization MUST be
BSON 1.1 compliant.

### Primitive BSON Types

#### [BsonString](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L58)

A BsonString defines a sequence of characters independently of its locale or encoding. A BsonString MUST be
encodable to a UTF-8 String.

#### [BsonBoolean](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L63)

A BsonBoolean represents a disjoint true or false values. The actual internal encoding is left to the
original BSON 1.1 specification.

#### [BsonDate](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L68)

A BsonDate represents a date and a time, serializable to a UNIX timestamp. This specific type MAY be 
represented differently in some dialects.

In any Java-based dialects, a BsonDate can be represented as:

* [java.util.Date](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/util/Date.html)
* [java.time.Instant](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/time/Instant.html)
* [java.time.LocalDate](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/time/LocalDate.html)
* [java.time.LocalDateTime](https://cr.openjdk.org/~pminborg/panama/21/v1/javadoc/java.base/java/time/LocalDateTime.html)

#### [BsonObjectId](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L73)

A BsonObjectId represents a 12 bytes unique identifier for an object. 

#### [BsonInt32](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L79)

A signed integer of 32 bits precision. In Java it's mapped to an `int` type.

#### [BsonInt64](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L89)

A signed integer of 64 bits precision. In Java it's mapped to both `long` and `BigInteger`.

#### [BsonDouble](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L94)

A 64bit floating point number. In Java it's mapped to both float and double.

#### [BsonDecimal128](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#104)

A 128bit floating point number. In Java it's mapped to BigDecimal.

#### [BsonNull](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L110)

Represents the absence of a value. 

#### [BsonAny](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L123)

Represents any possible type. Essentially, all type is a subtype of BsonAny.

#### [BsonAnyOf](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L132)

Represents an intersection of types. For example, BsonAnyOf([BsonString, BsonInt32]). 

#### [BsonObject](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L149)

Represents the shape of a BSON document.

#### [BsonArray](/main/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/BsonType.kt#L171)

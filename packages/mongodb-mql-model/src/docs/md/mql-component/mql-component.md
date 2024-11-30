# MQL Component
---------------

## Abstract

This specification documents the structure of an MQL Component from a mixed perspective of both
the original source code and the target server that might run the query. It is primarily aimed
to provide developers of dialects and linters a common and flexible structure for code processing.

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY"
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

MQL Components (from now on just components) encapsulate units of meaning of an MQL query. Components
MAY be related to how a target MongoDB Cluster can process a query. Components MAY contain other components
or MQL Nodes.

Components are categorised as:

* Leaf components: they don't contain other components or nodes.
* Non-leaf components: they contain other components or nodes.

Components MUST be part of a Node, they are meaningless outside of it. Components MAY be found
more than once in the same node.

## List of Components

### HasAccumulatedFields

Contains a list of Nodes that represent the accumulated fields of a group operation. Each
node MUST represent one accumulated field and it's accumulator.

### HasAddedFields

Contains a list of Nodes that represent fields added to a document. For example, through the
$addFields aggregation stage. Each node MUST represent one added field.

### HasAggregation

Contains a list of Nodes, where each node MUST represent one single aggregation stage.

### HasCollectionReference

Contains information whether this query or a specific subquery targets a specific collection. The
reference MUST be one of the following variants:

* **Unknown**: there is a collection reference, but we don't know on which collection.
* **OnlyCollection**: there is a collection reference, but we only know the collection, not the full namespace.
* **Known**: both the collection and database are known.

### HasFieldReference

Contains information of a field. The field MAY be used for filtering, computing or aggregating data. 
There are different variants depending on the amount of information we have at the moment of parsing the query.
The variant MUST be one of the following:

* **Unknown**: we couldn't infer any information from the field.
* **FromSchema**: the field MUST be in the schema of the target collection.
* **Inferred**: Refers to a field that is not explicitly specified in the code. For example:
Filters.eq(A) refers to the _id field.
* **Computed**: Refers to a field that is not part of the schema because it's newly computed.

### HasFilter

Contains a list of Nodes that represent the filter of a query. It MAY not contain any
node for empty queries.

### HasProjections

Contains a list of Node that represents the projections of a $project stage. It MAY not
contain any node for empty projections.

### HasSorts

Contains a list of Node that represent the sorting criteria of a $sort stage. It MAY not
contain any node if the sort criteria is still not defined.

### HasSourceDialect

Identifies the source dialect that parsed this query. It MUST be one of the valid dialects:

* Java Driver
* Spring Criteria
* Spring @Query

### HasTargetCluster

Identifies the version of the cluster that MAY run the query. It MUST be a valid released MongoDB 
version.

### HasUpdates

Contains a list of Node representing updates to a document. It MAY be empty if no updates are
specified yet.

### HasValueReference

Identifies a value in a query. Usually a value is the right side of a comparison,
but it can be used in different places, like for computing aggregation expressions.

It MUST be one of these variants:

* **Unknown**: We don't have any information of the provided value.
* **Constant**: It's a value that can be resolved without evaluating it. A literal value is a constant.
* **Inferred**: It's a value that could be inferred from other operations. For example, Sort.ascending("field") would have an Inferred(1).
* **Runtime**: It's a value that could not be resolved without evaluating it, but we have enough information
to infer its runtime type. For example, a parameter from a method.
* **Computed**: Refers to a computed expression in the MongoDB Cluster, like a $expr node.

### IsCommand

References the command that will be evaluated in the MongoDB cluster. The list of
valid commands can be found in the IsCommand.kt file.

### Named

References the name of the operation that is being referenced in the node. The list
of valid names can be found in the Named.kt file.

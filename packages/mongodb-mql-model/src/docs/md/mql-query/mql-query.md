# MQL Query
-----------

## Abstract

This specification documents the structure of a MongoDB Query from a mixed perspective of both
the original source code and the target server that might run the query. It is primarily aimed
to provide developers of dialects and linters a common and flexible structure for code processing.

## META

The keywords "MUST", "MUST NOT", "SHALL", "SHALL NOT", "SHOULD", "SHOULD NOT", "RECOMMENDED", "MAY"
and "OPTIONAL" in this document are to be interpreted as described in [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt).

## Specification

A MongoDB Query (**query** from now on), is a single execution unit, written in any of the supported dialects,
that MAY be consumed by a valid MongoDB Cluster. A query SHOULD contain all the semantics specific to the source dialect
so it can be tailed back to the original source code. A query MAY be unsupported by a specific target MongoDB Cluster.

### Query validation and support

A query MAY be valid for a target MongoDB Cluster if the MongoDB Cluster can consume the query once it
is translated to a consumable dialect by the target Cluster. However, a query MAY be unsupported by the 
cluster if it doesn't have the capabilities to fulfill the query request.

For example, let's consider the following query, in pseudocode:

```java
collection.aggregate(AtlasSearch(text().eq("baby one more time")))
```

| Cluster 	                      | Is Valid 	 | Is Supported 	 |
|--------------------------------|------------|----------------|
| MongoDB Community 7.0          | 🟢         | 	       🔴     |
| MongoDB Enterprise 8.0	        | 🟢	        | 	🔴            |
| MongoDB Atlas 8.0 w/o Search	  | 	🟢        | 	   🔴         |
| MongoDB Atlas 8.0 with Search	 | 	🟢        | 	   🟢         |

For the purpose of this specification and project, we will only allow the `mongosh` dialect as a
consumable dialect for a MongoDB Cluster.

### Query equivalence

We will consider two queries equivalent, independently of the query structure, if the following conditions
apply:

* They MUST be **valid** by the same set of target clusters.
* They MUST be **supported** by the same set of target clusters.
* They MUST return the same subset of results for the same input data set.
* They MAY be sourced from the same dialect.
* They MAY lead to equivalent **execution plans** for the same target cluster.

We will consider two execution plans equivalent if the cluster query planner leads to the same list
of operations.

Let's consider a different use case. For the following two queries in the `Java Driver` dialect:

```java
collection.find(eq("bookName", myBookName))
collection.aggregate(matches(eq("bookName", myBookName)))
```

We will test two different target clusters:

* MongoDB Community 8.0, from a development environment, does not have an index on bookName for the target collection.
* MongoDB Atlas 8.0, production environment, does have an index on bookName for the target collection.

In the development environment, we copy the data from the production environment once every week. For this example,
we will consider that the data sets are exactly the same on both clusters.

| Query                                                       | Cluster Environment           	 | Is Valid 	  | Is Supported 	 |  Results 	   | Dialect 	            | Execution Plan 	          |
|:------------------------------------------------------------|---------------------------------|:-----------:|:--------------:|:------------:|----------------------|---------------------------|
| `collection.find(eq("bookName", myBookName))`               | Development         	           | 🟢         	 |   🟢        	   | N          	 | Java Driver        	 | COLLSCAN                	 |
| `collection.find(eq("bookName", myBookName))`               | Production         	            | 🟢         	 |   🟢        	   | N          	 | Java Driver        	 | IXSCAN                	   |
| `collection.aggregate(matches(eq("bookName", myBookName)))` | Development         	           | 🟢         	 |   🟢        	   | N         	  | Java Driver        	 | COLLSCAN                	 |
| `collection.aggregate(matches(eq("bookName", myBookName)))` | Production 	                    | 🟢         	 | 🟢           	  | N         	  | Java Driver        	 | IXSCAN                	   |

**🟢 They are equivalent because they are valid, supported and return the same result set in all clusters.**

And now, finally, let's assume the same environment, but with the query written in two dialects,
`mongosh` and `Java Driver`:

```java
collection.find(eq("bookName", myBookName))
```
```js
collection.find({"bookName": myBookName})
```

| Query                                         | Cluster Environment           	 |  Is Valid 	  | Is Supported 	 |  Results 	   | Dialect 	            | Execution Plan 	          |
|:----------------------------------------------|---------------------------------|:------------:|:--------------:|:------------:|----------------------|---------------------------|
| `collection.find(eq("bookName", myBookName))` | Development         	           | 🟢         	 |  🟢        	   | N          	 | Java Driver        	 | COLLSCAN                	 |
| `collection.find(eq("bookName", myBookName))` | Production         	            | 🟢         	 |  🟢        	   | N          	 | Java Driver        	 | IXSCAN                	   |
| `collection.find({"bookName": myBookName})`   | Development         	           | 🟢         	 |  🟢        	   | N         	  | mongosh        	     | COLLSCAN                	 |
| `collection.find({"bookName": myBookName})`   | Production 	                    | 🟢         	 | 🟢           	 | N         	  | mongosh        	     | IXSCAN                	   |

**🟢 They are equivalent because they are valid, supported and return the same result set in all clusters even if the dialect is different.**


### Query Nodes

A [MQL Node or a Node for short](/packages/mongodb-mql-model/src/main/kotlin/com/mongodb/jbplugin/mql/Node.kt) 
represents a set of semantic properties of a MongoDB query or a subset of it. Nodes MUST NOT be specific to
a single source dialect, but MAY contain semantics that are relevant for processing.

Nodes MUST contain a single reference to the original source code, in any of the valid dialects. Multiple
nodes MAY contain a reference to the same original source code. That reference is called the **source** 
of the Node. For example, let's consider this query, written in the **Java Driver dialect** and how it is referenced by a Node.

```java
    collection.find(eq("_id", 123456)).first();
//  ^                                        ^
//  +----------------------------------------+
//            Node(source)
```

A Node MAY contain parent nodes and child nodes, through specific **components**. A Node that
doesn't contain any parent node, but contains child nodes is called the **root** node, and
represents the whole query.

Components MUST be stored in an ordered list inside a Node. Nodes MAY have additional components that contain metadata for 
that Node. Components MAY have references to other Nodes and other components. Components in a node MAY
not be unique: the same component MAY be found in the same node more than once.

Nodes with components MAY build a tree like structure, resembling an Abstract Syntax Tree. Nodes MUST
NOT refer to themselves either directly or through one of its children, avoiding circular references.

### MQL Serialization

A query MUST be serializable to readable text. The serialization format is independent of the
dialects used for parsing it. A serialized query SHOULD look like this:

```kt
Node(
    source=collection.find(eq("_id", 123456)).first(),
    components=[
        // list of components
    ]
)
```

The serialization format MAY ignore printing the source of the query, but MUST print all the components
attached to each of the nodes of the query. In that case, a short form on the syntax MAY be used:

```kt
Node([
    // list of components
])
```

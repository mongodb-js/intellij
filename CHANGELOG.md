# Changelog

MongoDB plugin for IntelliJ IDEA.

## [Unreleased]

### Added
* [INTELLIJ-44](https://jira.mongodb.org/browse/INTELLIJ-91) Ability to load the Spring configuration from
the current project's application.yml
* [INTELLIJ-91](https://jira.mongodb.org/browse/INTELLIJ-91): Ability to trigger autocompletion automatically for string constants
in a query.
* [INTELLIJ-73](https://jira.mongodb.org/browse/INTELLIJ-73): Ability to run Java queries, both with the Java Driver and
Spring Criteria, in the Data Explorer console.
* [INTELLIJ-93](https://jira.mongodb.org/browse/INTELLIJ-93): Inline warning when a query does not use an index and
a quick action to generate the index template.
* [INTELLIJ-74](https://jira.mongodb.org/browse/INTELLIJ-74): Generate index template from code inspection in queries not
covered by an index.
* [INTELLIJ-70](https://jira.mongodb.org/browse/INTELLIJ-70): Code action that allows running a Java query from within the code
on the current data source.
* [INTELLIJ-81](https://jira.mongodb.org/browse/INTELLIJ-81): Inspections in code when a database or collection does not exist
in the current data source.
* [INTELLIJ-43](https://jira.mongodb.org/browse/INTELLIJ-43): Extract the configured database from application.properties
in projects with Spring Boot.
* [INTELLIJ-51](https://jira.mongodb.org/browse/INTELLIJ-51): Add an inline warning when querying a field that does not
  exist in the target collection in a Spring Criteria project.
* [INTELLIJ-53](https://jira.mongodb.org/browse/INTELLIJ-53): Add an inline warning when the type of the provided value
  for a field in a filter / update query does not match the expected type of the field in a Spring Criteria project.
* [INTELLIJ-23](https://jira.mongodb.org/browse/INTELLIJ-23): Add an inline warning when the type of the provided value
  for a field in a filter / update query does not match the expected type of the field.
* [INTELLIJ-52](https://jira.mongodb.org/browse/INTELLIJ-52): Support for autocomplete for collections specified with 
`@Document` and fields in Criteria chains.  
* [INTELLIJ-24](https://jira.mongodb.org/browse/INTELLIJ-30): Supports for autocompletion in database names, collections and fields on queries. Requires 
a connection to a MongoDB cluster set up in the editor.
* [INTELLIJ-30](https://jira.mongodb.org/browse/INTELLIJ-30): Add an inline warning when querying a field that does not exist in the target
collection.
* [INTELLIJ-29](https://jira.mongodb.org/browse/INTELLIJ-29): Shows an inlay hint near a Java query that shows in which collection the query is
going to be run in case it could be inferred.
* [INTELLIJ-17](https://jira.mongodb.org/browse/INTELLIJ-17): Added a toolbar that allows to attach a MongoDB data source to the current editor.
This data source is used for autocompletion and type checking.
* [INTELLIJ-14](https://jira.mongodb.org/browse/INTELLIJ-14): Send telemetry when a connection to a MongoDB Cluster fails.
* [INTELLIJ-13](https://jira.mongodb.org/browse/INTELLIJ-13): Send telemetry when successfully connected to a MongoDB Cluster.
* [INTELLIJ-12](https://jira.mongodb.org/browse/INTELLIJ-12): Notify users about telemetry, and allow them to disable it.
* [INTELLIJ-11](https://jira.mongodb.org/browse/INTELLIJ-11): Flush pending analytics events before closing the IDE.

### Changed

### Deprecated

### Removed

### Fixed

### Security

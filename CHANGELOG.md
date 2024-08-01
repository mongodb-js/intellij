# Changelog

MongoDB plugin for IntelliJ IDEA.

## [Unreleased]

### Added
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
infinit.e-neo4j-demo
====================

A simple demo to show that it's possible to transfer data from ikanow's infinit.e to neo4j.

### schema

Documents, Entities, and Associations get stored.

```
(documents {docId:"xxxxxx"})-[:references]->(entity {name:"entity name", indexName:"unique name"})-
[:verb_verb_category {docId:xxxxxx}]->(another {name:"another entity", etc:""})
```

### usage

Update the configuration in src/main/resources/application.conf, and run with SBT's `sbt run`. 
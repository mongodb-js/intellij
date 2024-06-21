package org.example;


import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        try (var client = MongoClients.create()) {
            MongoDatabase database = client.getDatabase("example");
            var collection = database.getCollection("example");
            collection.find(Filters.eq("myField", 12));
            collection.find(Filters.eq("myField", 123f));
        }
    }
}

interface MyRepository {
    @Query("{ 'myField': ?0 }")
    List<Document> myQuery(int field);
}
package org.example;


import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;

public class Main {
    public static void main(String[] args) {
        try (var client = MongoClients.create()) {
            MongoDatabase database = client.getDatabase("example");
            var collection = database.getCollection("example");
            collection.find(Filters.eq("myField", 12f));
            collection.find(Filters.eq("myField", 123f));
        }

    }
}
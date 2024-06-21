package org.example;


import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;

public class Main {
    public static void main(String[] args) {
        var client = MongoClients.create();
        var database = client.getDatabase("example");
        var collection = database.getCollection("example");
        collection.find(Filters.eq("123", 123456));
    }
}
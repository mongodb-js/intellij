package alt.mongodb.javadriver;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public class TripsRepository {
    private final MongoClient client;

    public TripsRepository(MongoClient client) {
        this.client = client;
    }

    public List<Document> findPendingFareDisputes() {
        List<Document> results = client
                .getDatabase("production")
                .getCollection("trips")
                .find(Filters.and(
                    Filters.eq("disputes.status", "pending"),
                    Filters.eq("disputes.type", "fare")
                )).into(new ArrayList<>());

        return results;
    }
}

package alt.mongodb.javadriver;

import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Aggregates;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public final class JavaDriverRepository {
    private final MongoCollection<Document> collection;

    public JavaDriverRepository(MongoClient client) {
        this.collection = client.getDatabase("simple").getCollection("books");
    }

    public Document queryBookById(ObjectId id) {
        return collection.find(eq("_id", id)).first();
    }

    public List<Document> queryBooksByName(String name) {
        return collection.find(eq("name", name)).into(new ArrayList<>());
    }

    public Document findById(ObjectId id) {
        return collection.aggregate(List.of(
            Aggregates.match(eq("_id", id)
        ))).first();
    }

    public List<Document> findBooksByName(String name) {
        return this.collection.aggregate(List.of(
            Aggregates.match(eq("simple", "as"))
        )).into(new ArrayList<Document>());
    }
}

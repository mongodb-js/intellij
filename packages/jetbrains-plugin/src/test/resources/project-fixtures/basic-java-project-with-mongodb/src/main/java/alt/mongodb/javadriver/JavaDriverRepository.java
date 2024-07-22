package alt.mongodb.javadriver;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import static com.mongodb.client.model.Filters.*;

public class JavaDriverRepository {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoClient client;

    public JavaDriverRepository(MongoClient client) {
        this.client = client;
    }

    public FindIterable<Document> exampleFind() {
        return getCollection().find(eq(AWARDS_WINS, 123));
    }

    public FindIterable<Document> exampleFindUsingCustomDSL() {
        return findByField(524);
    }

    private MongoCollection<Document> getCollection() {
        return client.getDatabase("sample_mflix")
                .getCollection("movies");
    }

    private FindIterable<Document> findByField(int value) {
        return getCollection().find(gt(IMDB_VOTES, value));
    }
}

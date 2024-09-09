package alt.mongodb.javadriver;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.*;

public class JavaDriverRepository {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoCollection<Document> trips;

    public JavaDriverRepository(MongoClient client) {
        this.trips = client.getDatabase("production").getCollection("trips");
    }

    private List<Document> getGrade() {
        return trips.find(
                Filters.and(
                        Filters.eq("dispute.status", 1),
                        Filters.eq("dispute.type", 1)
                ))
                .into(new ArrayList<>());
    }
}

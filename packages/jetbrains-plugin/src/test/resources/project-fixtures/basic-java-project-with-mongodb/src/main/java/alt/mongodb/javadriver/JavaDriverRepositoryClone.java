package alt.mongodb.javadriver;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class JavaDriverRepositoryClone {
    private static final String IMDB_VOTES = "imdb.votes";
    public static final String AWARDS_WINS = "awards.wins";

    private final MongoClient client;

    public JavaDriverRepositoryClone(MongoClient client) {
        this.client = client;
    }

    private List<Document> getGrade() {
        return client.getDatabase("sample_mflix")
                .getCollection("users")
                .find(Filters.eq("email", 1))
                .into(new ArrayList<>());
    }
}

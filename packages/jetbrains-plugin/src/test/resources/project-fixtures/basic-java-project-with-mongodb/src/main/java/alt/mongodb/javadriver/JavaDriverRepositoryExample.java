package alt.mongodb.javadriver;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import org.bson.Document;

public class JavaDriverRepositoryExample {
    public FindIterable<Document> exampleFind(MongoClient mongoClient) {
        return mongoClient.getDatabase("myDatabase")
                .getCollection("myCollection")
                .find();
    }
}

package mtech.swe5006.peerconnect.data.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MongoItemRepository extends MongoRepository<MongoItem, String> {
}

package mtech.swe5006.peerconnect.data.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "mongo_items")
public class MongoItem {
	@Id
	private String id;

	private String name;

	protected MongoItem() {
	}

	public MongoItem(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
}

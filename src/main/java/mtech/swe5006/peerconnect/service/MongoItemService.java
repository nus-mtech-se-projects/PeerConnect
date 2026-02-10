package mtech.swe5006.peerconnect.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import mtech.swe5006.peerconnect.data.mongo.MongoItem;
import mtech.swe5006.peerconnect.data.mongo.MongoItemRepository;
import mtech.swe5006.peerconnect.dto.ItemRequest;

@Service
public class MongoItemService {
	private final MongoItemRepository repository;

	public MongoItemService(MongoItemRepository repository) {
		this.repository = repository;
	}

	public List<MongoItem> findAll() {
		return repository.findAll();
	}

	public Optional<MongoItem> findById(String id) {
		return repository.findById(id);
	}

	public MongoItem create(ItemRequest request) {
		return repository.save(new MongoItem(request.name()));
	}

	public Optional<MongoItem> replace(String id, ItemRequest request) {
		return repository.findById(id).map(item -> {
			item.setName(request.name());
			return repository.save(item);
		});
	}

	public Optional<MongoItem> patch(String id, ItemRequest request) {
		return repository.findById(id).map(item -> {
			if (request.name() != null) {
				item.setName(request.name());
			}
			return repository.save(item);
		});
	}
}

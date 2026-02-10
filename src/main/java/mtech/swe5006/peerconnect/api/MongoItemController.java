package mtech.swe5006.peerconnect.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import mtech.swe5006.peerconnect.data.mongo.MongoItem;
import mtech.swe5006.peerconnect.dto.ItemRequest;
import mtech.swe5006.peerconnect.service.MongoItemService;

@RestController
@RequestMapping("/api/mongo-items")
public class MongoItemController {
	private final MongoItemService service;

	public MongoItemController(MongoItemService service) {
		this.service = service;
	}

	@GetMapping
	public List<MongoItem> getAll() {
		return service.findAll();
	}

	@GetMapping("/{id}")
	public ResponseEntity<MongoItem> getById(@PathVariable String id) {
		return service.findById(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<MongoItem> create(@RequestBody ItemRequest request) {
		MongoItem created = service.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public ResponseEntity<MongoItem> replace(@PathVariable String id, @RequestBody ItemRequest request) {
		return service.replace(id, request)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PatchMapping("/{id}")
	public ResponseEntity<MongoItem> patch(@PathVariable String id, @RequestBody ItemRequest request) {
		return service.patch(id, request)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}

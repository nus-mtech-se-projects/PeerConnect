package mtech.swe5006.peerconnect.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import mtech.swe5006.peerconnect.data.mongo.MongoItem;
import mtech.swe5006.peerconnect.dto.ItemRequest;
public class BaseTemplate {

    public final Object service;

    public BaseTemplate(Object service) {
		this.service = service;
	}

	@GetMapping
	public List<Object> getAll() {
		return null;
	}

	// @GetMapping("/test")
	// public String getTestVariable() {
	// 	return "test response";
	// }

	@GetMapping("/{id}")
	public ResponseEntity<MongoItem> getById(@PathVariable String id) {
		return null;
	}

	@PostMapping
	public ResponseEntity<MongoItem> create(@RequestBody ItemRequest request) {
		return null;
	}

	@PutMapping("/{id}")
	public ResponseEntity<MongoItem> replace(@PathVariable String id, @RequestBody ItemRequest request) {
		return null;
	}

	@PatchMapping("/{id}")
	public ResponseEntity<MongoItem> patch(@PathVariable String id, @RequestBody ItemRequest request) {
		return null;
	}
}

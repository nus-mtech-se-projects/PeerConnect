package mtech.swe5006.peerconnect.api;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

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

	@GetMapping("/{id}")
	public ResponseEntity<Object> getById(@PathVariable String id) {
		return null;
	}

	@PostMapping
	public ResponseEntity<Object> create(@RequestBody ItemRequest request) {
		return null;
	}

	@PutMapping("/{id}")
	public ResponseEntity<Object> replace(@PathVariable String id, @RequestBody ItemRequest request) {
		return null;
	}

	@PatchMapping("/{id}")
	public ResponseEntity<Object> patch(@PathVariable String id, @RequestBody ItemRequest request) {
		return null;
	}
}

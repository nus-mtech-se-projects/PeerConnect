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

import mtech.swe5006.peerconnect.data.sql.SqlItem;
import mtech.swe5006.peerconnect.dto.ItemRequest;
import mtech.swe5006.peerconnect.service.SqlItemService;

@RestController
@RequestMapping("/api/sql-items")
public class SqlItemController {
	private final SqlItemService service;

	public SqlItemController(SqlItemService service) {
		this.service = service;
	}

	@GetMapping
	public List<SqlItem> getAll() {
		return service.findAll();
	}

	@GetMapping("/{id}")
	public ResponseEntity<SqlItem> getById(@PathVariable Long id) {
		return service.findById(id)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping
	public ResponseEntity<SqlItem> create(@RequestBody ItemRequest request) {
		SqlItem created = service.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public ResponseEntity<SqlItem> replace(@PathVariable Long id, @RequestBody ItemRequest request) {
		return service.replace(id, request)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PatchMapping("/{id}")
	public ResponseEntity<SqlItem> patch(@PathVariable Long id, @RequestBody ItemRequest request) {
		return service.patch(id, request)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}
}

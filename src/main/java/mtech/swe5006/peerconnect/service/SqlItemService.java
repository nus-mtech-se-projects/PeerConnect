package mtech.swe5006.peerconnect.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import mtech.swe5006.peerconnect.data.sql.SqlItem;
import mtech.swe5006.peerconnect.data.sql.SqlItemRepository;
import mtech.swe5006.peerconnect.dto.ItemRequest;

@Service
public class SqlItemService {
	private final SqlItemRepository repository;

	public SqlItemService(SqlItemRepository repository) {
		this.repository = repository;
	}

	public List<SqlItem> findAll() {
		return repository.findAll();
	}

	public Optional<SqlItem> findById(Long id) {
		return repository.findById(id);
	}

	public SqlItem create(ItemRequest request) {
		return repository.save(new SqlItem(request.name()));
	}

	public Optional<SqlItem> replace(Long id, ItemRequest request) {
		return repository.findById(id).map(item -> {
			item.setName(request.name());
			return repository.save(item);
		});
	}

	public Optional<SqlItem> patch(Long id, ItemRequest request) {
		return repository.findById(id).map(item -> {
			if (request.name() != null) {
				item.setName(request.name());
			}
			return repository.save(item);
		});
	}
}

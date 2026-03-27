package mtech.swe5006.peerconnect.data.sql;


import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import mtech.swe5006.peerconnect.data.sql.User;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByNusStudentId(String nusStudentId);

  boolean existsByEmail(String email);

  boolean existsByNusStudentId(String nusStudentId);
}

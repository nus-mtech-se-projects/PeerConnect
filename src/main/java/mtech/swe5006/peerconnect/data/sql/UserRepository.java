package mtech.swe5006.peerconnect.data.sql;


import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import mtech.swe5006.peerconnect.data.sql.User;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmail(String email);

  Optional<User> findByMicrosoftOid(String microsoftOid);

  Optional<User> findByNusStudentId(String nusStudentId);

  boolean existsByEmail(String email);

  boolean existsByNusStudentId(String nusStudentId);

  @Query("SELECT u FROM User u WHERE u.status = 'active' AND ("
       + "LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')) OR "
       + "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%')) OR "
       + "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :q, '%')))")
  List<User> searchByEmailOrName(@Param("q") String query);
}

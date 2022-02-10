package nl.b3p.tailormap.api.repository;

import nl.tailormap.viewer.config.security.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query(
            value =
                    "from User u left join fetch u.groups left join fetch u.details where u.username = :username ")
    User findByUsername(String username);
}

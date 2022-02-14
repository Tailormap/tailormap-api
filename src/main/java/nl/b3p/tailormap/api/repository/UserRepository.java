package nl.b3p.tailormap.api.repository;

import nl.tailormap.viewer.config.security.User;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = {"groups", "details"})
    User findByUsername(String username);

    boolean existsByGroupsNameIn(Collection<String> groupNames);
}

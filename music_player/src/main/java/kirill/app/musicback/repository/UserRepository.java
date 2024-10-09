package kirill.app.musicback.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import kirill.app.musicback.model.User;

public interface UserRepository extends JpaRepository<User,Long>{
	
	User findByEmail(String email);
}

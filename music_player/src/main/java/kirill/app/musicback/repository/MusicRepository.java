package kirill.app.musicback.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import kirill.app.musicback.model.Song;

public interface MusicRepository extends JpaRepository<Song, Long> {
	
	
}

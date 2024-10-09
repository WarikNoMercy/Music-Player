package kirill.app.musicback;

import java.io.IOException;
import java.security.GeneralSecurityException;

//import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class MusicApp {

	
	
	public static void main(String[] args) throws IOException, GeneralSecurityException{
		SpringApplication.run(MusicApp.class, args);

	}
	
}

package kirill.app.musicback.controllers;

import kirill.app.musicback.model.Song;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
//import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import javax.swing.plaf.basic.BasicInternalFrameTitlePane.SystemMenuBar;
import java.io.ByteArrayOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.google.api.services.drive.Drive;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
//import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Userinfoplus;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kirill.app.musicback.model.User;
import kirill.app.musicback.repository.MusicRepository;
import kirill.app.musicback.repository.UserRepository;
import org.springframework.core.io.InputStreamResource;

import com.google.api.client.http.FileContent;


@Controller
public class HomepageController {
	
	private static HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
	private static GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE,
			"https://www.googleapis.com/auth/drive.install","https://www.googleapis.com/auth/userinfo.email",
		    "https://www.googleapis.com/auth/userinfo.profile");
	
	
	@Value("${google.client.id")
	private String USER_IDENTIFIER_KEY;
	
	@Autowired
    private UserRepository userRepository;
	
	@Autowired
	private MusicRepository musicRepository;
    
	@Value("${google.oauth.callback.uri}")
	private String CALLBACK_URI;
	
	@Value("${google.secret.key.path}")
	private Resource gdSecretKeys;
	
	@Value("${google.credentials.folder.path}")
	private Resource credentialsFolder;
	
	private GoogleAuthorizationCodeFlow flow;
	
	@GetMapping("/home")
	public String getHomepage(Model model){
		return "index";
	}
	
	@Value("${google.credentials.file.path}")
	private Resource credentialsFolderFile;


	@PostConstruct
	public void init() throws Exception {
		//File cred = gdSecretKeys.getFile();
		GoogleClientSecrets secrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(credentialsFolder.getInputStream()));
	    flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, secrets, SCOPES)
	                .setDataStoreFactory(new FileDataStoreFactory(credentialsFolderFile.getFile())).build();
	}
	
	@GetMapping(value= {"/"})
	public String showHomePage() {
		boolean isAuthorized = false; 
		
		try {
			//flow.getCredentialDataStore().clear();
			Credential credential = flow.loadCredential(USER_IDENTIFIER_KEY);
			if(credential!=null) {
				boolean tokenValid = credential.refreshToken();
				if(tokenValid) {
					Oauth2 oauth2 = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
				            .setApplicationName("Music player")
				            .build();

				    Userinfoplus userinfo = oauth2.userinfo().get().execute();

				    String email = userinfo.getEmail();
				    String name = userinfo.getName();
				    User user = userRepository.findByEmail(email);
	                if (user == null) {
	                    user = new User();
	                    user.setEmail(email);
	                    user.setUsername(name);  
	                    userRepository.save(user);
	                }
				
					isAuthorized = true;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("USER AUTHORIZED : " + isAuthorized);
		return isAuthorized ? "index.html" :"authpage.html";
	}
	
	
	@GetMapping(value = {"/googlesignin"})
	public void doGoogleSignIn(HttpServletResponse response) throws Exception {
		GoogleAuthorizationCodeRequestUrl url = flow.newAuthorizationUrl();
		String redirectURL = url.setRedirectUri(CALLBACK_URI).setAccessType("offline").build();
		response.sendRedirect(redirectURL);
	}
	
	@GetMapping(value= {"/oauth"})
	public String saveAuthorizationCode(HttpServletRequest request)throws Exception {
		String code = request.getParameter("code");
		if(code!=null) {
			saveToken(code);
			return "authpage.html";
		}
		return "index.html";
	}

	private void saveToken(String code) throws Exception {
		GoogleTokenResponse response = flow.newTokenRequest(code).setRedirectUri(CALLBACK_URI).execute();
		flow.createAndStoreCredential(response, USER_IDENTIFIER_KEY);
	}
	
	
	@GetMapping("/logout")
	public String logout(HttpServletRequest request, HttpServletResponse response) throws IOException {
	    flow.getCredentialDataStore().delete(USER_IDENTIFIER_KEY);
	    
	    request.getSession().invalidate();

	    return "redirect:/";
	}
	
	
	@GetMapping("/music-files")
	public String listMusicFiles(Model model) throws IOException {
	    Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);
	    
	    Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
	            .setApplicationName("Music player")
	            .build();
	    
	    
	    String folderId = createFolderForUser(drive);
	    
	    FileList result = drive.files().list()
	            .setQ("'" + folderId + "' in parents and mimeType contains 'audio/'")
	            .setFields("nextPageToken, files(id, name, mimeType, webContentLink)")
	            .execute();
	    
	    List<com.google.api.services.drive.model.File> files = result.getFiles();
	    
	    if (files == null || files.isEmpty()) {
	        model.addAttribute("errorMessage", "Музыкальные файлы не найдены.");
	    } else {
	    	
	    	for (com.google.api.services.drive.model.File file : files) {
	    		
	    		String proxyLink = "/download-file/" + file.getId();
	            file.setWebContentLink(proxyLink);

	        }
	        model.addAttribute("musicFiles", files);
	    }
	    
	    return "musiclist"; 
	}
	

	@GetMapping("/download-file/{fileId}")
	public ResponseEntity<InputStreamResource> downloadFile(@PathVariable String fileId, HttpServletRequest request) throws IOException {
	    Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);
	    Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
	            .setApplicationName("Music player")
	            .build();

	    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    drive.files().get(fileId).executeMediaAndDownloadTo(outputStream);
	    
	    byte[] fileBytes = outputStream.toByteArray();
	    InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(fileBytes));

	    HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
	    headers.set("Accept-Ranges", "bytes");

	    String range = request.getHeader("Range");
	    if (range == null) {
	        headers.setContentDisposition(ContentDisposition.inline().filename("audio.mp3").build());
	        return new ResponseEntity<>(resource, headers, HttpStatus.OK);
	    } else {
	        String[] ranges = range.replace("bytes=", "").split("-");
	        int rangeStart = Integer.parseInt(ranges[0]);
	        int rangeEnd = ranges.length > 1 ? Integer.parseInt(ranges[1]) : fileBytes.length - 1;

	        byte[] rangeBytes = Arrays.copyOfRange(fileBytes, rangeStart, rangeEnd + 1);

	        InputStreamResource rangeResource = new InputStreamResource(new ByteArrayInputStream(rangeBytes));

	        headers.add("Content-Range", "bytes " + rangeStart + "-" + rangeEnd + "/" + fileBytes.length);
	        headers.setContentLength(rangeBytes.length);

	        return new ResponseEntity<>(rangeResource, headers, HttpStatus.PARTIAL_CONTENT);
	    }
	}

	
	@PostMapping(value= {"/upload"})
	public String getFile(@RequestParam("file") MultipartFile multipartFile, Model model) throws Exception{
		
		
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);
		
		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred).setApplicationName("Music player").build();
		
	    String userFolderId = createFolderForUser(drive);
	    
		File filePath = new java.io.File("C:/Users/kerya/OneDrive/Рабочий стол/Приложение/Music player/" + multipartFile.getOriginalFilename());
		
		com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
		fileMetadata.setName(multipartFile.getOriginalFilename());
		fileMetadata.setParents(Collections.singletonList(userFolderId));
		FileContent mediaContent = new FileContent("audio/mpeg", filePath);
        try {
        	com.google.api.services.drive.model.File uploadedFile = drive.files().create(fileMetadata, mediaContent)
                .setFields("id, parents")
                .execute();
        	
        	Oauth2 oauth2 = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
    	            .setApplicationName("Music player")
    	            .build();

    	    Userinfoplus userinfo = oauth2.userinfo().get().execute();
    	    String email = userinfo.getEmail();
		    User user = userRepository.findByEmail(email);
        	Song song = new Song();
        	String fileName = multipartFile.getOriginalFilename();
        	String [] artSong = fileName.split("-");
        	song.setUser(user);
        	song.setArtist(artSong[0]);
        	song.setTitle(artSong[1].substring(0,artSong[1].lastIndexOf('.')));
        	song.setFileName(fileName);
        	
        	musicRepository.save(song);
        	model.addAttribute("successMessage", "Файл успешно загружен!");
            return "index"; 
        } catch (IOException e) {
            model.addAttribute("errorMessage", "Error uploading file: " + e.getMessage());
            return "index";
        }
        
	}
	
	private String createFolderForUser(Drive driveService) throws IOException {
		
		
		Credential cred = flow.loadCredential(USER_IDENTIFIER_KEY);
		Oauth2 oauth2 = new Oauth2.Builder(HTTP_TRANSPORT, JSON_FACTORY, cred)
	            .setApplicationName("Music player")
	            .build();

	    Userinfoplus userinfo = oauth2.userinfo().get().execute();
	    String email = userinfo.getEmail();
	    
        String folderName = "Music player files: " + email;

        FileList result = driveService.files().list()
                .setQ("mimeType='application/vnd.google-apps.folder' and name='" + folderName + "'")
                .setFields("files(id, name)")
                .execute();

        List<com.google.api.services.drive.model.File> folders = result.getFiles();

        if (folders != null && !folders.isEmpty()) {
            return folders.get(0).getId();
        } else {
            com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
            fileMetadata.setName(folderName);
            fileMetadata.setMimeType("application/vnd.google-apps.folder");

            com.google.api.services.drive.model.File folder = driveService.files().create(fileMetadata)
                    .setFields("id")
                    .execute();

            return folder.getId();
        }
    }
	
	
}

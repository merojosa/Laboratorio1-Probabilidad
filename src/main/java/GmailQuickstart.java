import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

public class GmailQuickstart {
    private final String APPLICATION_NAME = "Gmail API Java Quickstart";
    private final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_READONLY);
    private final String CREDENTIALS_FILE_PATH = "/credentials.json";

    private Hashtable<String, Integer> wordsHash;
    private HashSet<String> stopWords;
    private int totalWords;

    public GmailQuickstart()
    {
        wordsHash = new Hashtable<String, Integer>();
        stopWords = new HashSet<String>();
        totalWords = 0;
        initializeStopWords();
    }

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException
    {
        // Load client secrets.
        InputStream in = GmailQuickstart.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        if (in == null) {
            throw new FileNotFoundException("Resource not found: " + CREDENTIALS_FILE_PATH);
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    /**
     * Get Message with given ID.
     *
     * @param service Authorized Gmail API instance.
     * @param userId User's email address. The special value "me"
     * can be used to indicate the authenticated user.
     * @param messageId ID of Message to retrieve.
     * @throws IOException
     */
    public void printSnippet(Gmail service, String userId, String messageId)
            throws IOException {
        Message message = service.users().messages().get(userId, messageId).execute();

        System.out.println("MESSAGE SNIPPET:\n" + message.getSnippet());
    }

    // https://stackoverflow.com/questions/29553887/gmail-apis-decoding-the-body-of-the-message-java-android
    public String getBody(Gmail service, String userId, String messageId)
            throws IOException
    {

        Message message = service.users().messages().get(userId, messageId).execute();

        String mailBodyHtml = "";

        String mimeType = message.getPayload().getMimeType();
        List<MessagePart> parts = message.getPayload().getParts();
        if (mimeType.contains("alternative")) {
            for (MessagePart part : parts) {
                mailBodyHtml = new String(Base64.decodeBase64(part.getBody()
                        .getData().getBytes()));

            }
        }

        Document doc = Jsoup.parse(mailBodyHtml);
        return doc.body().text();

    }

    public void countWords(String text)
    {
        // Word by word, includes letters only. Regex by Jose Andres Mejias
        for (String word : text.split("\\s+[^a-zA-z]*|[^a-zA-z]+\\s*"))
        {
            word = word.toLowerCase();

            if(stopWords.contains(word) == false)
            {
                if(wordsHash.containsKey(word) == false)
                {
                    wordsHash.put(word, 1);
                }
                else
                {
                    wordsHash.put(word, wordsHash.get(word) + 1);
                }
            }

            ++totalWords;
        }
    }

    public void printWords()
    {
        Enumeration<String> words = wordsHash.keys();

        int count = 0;
        String word = "";

        while (words.hasMoreElements())
        {
            word = words.nextElement();
            count = wordsHash.get(word);

            System.out.println(word + ": " + "Frecuencia: " + count + " Probabilidad: " + (float)count/(float)totalWords);
        }
    }

    public void initializeStopWords()
    {
        try
        {
            FileReader fileReader = new FileReader("files/StopWords.txt");
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            String line;
            while ((line = bufferedReader.readLine()) != null)
            {
                stopWords.add(line.toLowerCase());
            }
        }
        catch (IOException e)
        {
            System.out.println(e.getMessage());
        }
    }


    public void start() throws IOException, GeneralSecurityException
    {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();

        Gmail service = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

        // Get the authenticated user id.
        String userId = "me";

        // Make a request to recieve spam messages.  It could be "is:unread".
        Gmail.Users.Messages.List request = service.users().messages().list(userId);
        request.setQ("is:spam");
        ListMessagesResponse listMessagesResponse = request.execute();

        // Get spam messages.
        List<Message> messages = listMessagesResponse.getMessages();

        String body;

        if (messages.isEmpty()) {
            System.out.println("No messages found.");
        }
        else {
            // Para que volver a crear un objecto de tipo message?
            for (Message message : messages)
            {
                body = getBody(service, userId, message.getId());
                countWords(body);
            }
        }

        printWords();
    }
}
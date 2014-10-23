package jenkins.plugins.kato;

import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.client.CredentialsProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import hudson.ProxyConfiguration;
import hudson.model.Hudson;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StandardKatoService implements KatoService {

    private static final Logger logger = Logger.getLogger(StandardKatoService.class.getName());

    private String apiUrl = "https://api.kato.im";
    private String[] roomIds;
    private String from;

    public StandardKatoService(String roomId, String from) {
        super();
        if (roomId == null || roomId == "") {
            this.roomIds = new String[0];
        } else {
            this.roomIds = roomId.split(",");
        }
        this.from = from;
        logger.info("StandardKatoService = " + from + " = " + roomId);
    }

    public void publish(String message) {
        publish(message, "yellow");
    }

    public void publish(String message, String color) {

        // Note this configuration will require the user to add api.kato.im
        // to cacerts as a trusted certificate, included as api.kato.im.cer
        //
        // To import:
        // keytool -import -alias api.kato.im \
        //   -keystore $JAVA_HOME/jre/lib/security/cacerts \
        //   -trustcacerts -file api.kato.im.cer

    	// proxy configuration, if available
    	CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    	HttpHost proxy = null;
    	
    	if (Hudson.getInstance() != null && Hudson.getInstance().proxy != null) {
    		
    		ProxyConfiguration proxyConfiguration = Hudson.getInstance().proxy;
    		String host = proxyConfiguration.name;
    		int port = proxyConfiguration.port > 0 ? proxyConfiguration.port : AuthScope.ANY_PORT;
    		String username = proxyConfiguration.getUserName();
    		String password = proxyConfiguration.getPassword();
    		
            if ( StringUtils.isNotEmpty( host ) ) {
                if ( StringUtils.isNotEmpty( username ) ) {
                	Credentials creds = new UsernamePasswordCredentials(username, password );
                	credentialsProvider.setCredentials(new AuthScope(host, port), creds);
                }
            }
            
            proxy = new HttpHost(host, port);
        } 
    	
    	// build the client
    	CloseableHttpClient httpClient = HttpClientBuilder.create()
    			.useSystemProperties()
    			.setProxy(proxy)
    			.setDefaultCredentialsProvider(credentialsProvider)
    			.build();
    	
        for (String roomId : roomIds) {
        	
            String url = apiUrl + "/rooms/" + roomId + "/jenkins";
            logger.info("Posting: " + from + " to " + url + ": " + message + " " + color);
            HttpPost post = new HttpPost(url);

            try {
            	List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
            	urlParameters.add(new BasicNameValuePair("from", from));
            	urlParameters.add(new BasicNameValuePair("room_id", roomId));
            	urlParameters.add(new BasicNameValuePair("message", message));
            	urlParameters.add(new BasicNameValuePair("color", color));
            	urlParameters.add(new BasicNameValuePair("notify", shouldNotify(color)));
            	
            	post.setEntity(new UrlEncodedFormEntity(urlParameters));
                httpClient.execute(post);
                
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error posting to Kato", e);
            } finally {
                post.releaseConnection();
            }
        }
    }

    private String shouldNotify(String color) {
        return color.equalsIgnoreCase("green") ? "0" : "1";
    }

    void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }
}

package org.cceh;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidConfigurationException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.RefNotAdvertisedException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * Servlet implementation class PullRepo
 */
public class PullRepo extends HttpServlet {
	private static final long serialVersionUID = 1L;

	static BufferedWriter writer;
    /**
     * @see HttpServlet#HttpServlet()
     */
    public PullRepo() {
        super();
        // TODO Auto-generated constructor stub
    }


	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doPost(request, response);
	}

	/**
	 * Endpoint wird unter /githook erreicht.
	 * Erstellt eine githook.log datei zum überprüfen.
	 * Speichert den Requestbody in einem String requestbody.
	 * Speichert den Namen des Server in einem String server 
	 * 
	 * Call auf funktion processJson(requestbody, server)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		

    	writer = new BufferedWriter(new FileWriter("githook.log", true));
    	Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    	writer.write("["+timestamp+"] - New Gitpull\n");
    	
		try {
			String requestbody = getBody(request);
			String server = request.getServerName();
			processJSON(requestbody, server);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoWorkTreeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (GitAPIException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
    	writer.write("["+timestamp+"] - End Gitpull\n");

    	writer.close();

	}
	
	/**
	 * Liest den requestbody aus und gibt ihn als String zurück
	 * 
	 * @param request 
	 * @return
	 * @throws IOException
	 * @throws ParseException
	 */
    public static String getBody(HttpServletRequest request) throws IOException, ParseException {

        String body = null;
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;

        try {
            ServletInputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
                stringBuilder.append("");
            }
        } catch (IOException ex) {
            throw ex;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }

        body = stringBuilder.toString();
        return body;
    };
    
    
    
    /**
     * Parst den übergebenen jstring als json.
     * überprüft dann ob das Repo /public oder privat ist wenn privat dann wird eine Meldung in das Log geschriebne das es Public gesetzt werden soll.
     * 
     * Überprüft ob der Name Sigidoc/authority lautet wenn ja dann handelt es sich um die Authority files nicht um Siegel.xml
     * Handelt es sich um Authority files dann wird die Funktion processAuthorityList gecalled.
     * 
     * Wenn nein dann wird von Siegel.xml ausgegegangen.
     * Funktion processSeals wird gestartet übergibt den Namen des Repos als String, die json als JsonObject und den Servernamen als String
     * 
     * @param jstring  requestbody als string von git webhoob.
     * @param server   Name des Server auf dem der Endpoint liegt.
     * @throws FileNotFoundException
     * @throws IOException
     * @throws ParseException
     * @throws NoWorkTreeException
     * @throws GitAPIException
     * 
     */
    
	public void processJSON (String jstring, String server) throws FileNotFoundException, IOException, ParseException, NoWorkTreeException, GitAPIException{

    	Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    	writer.write("["+timestamp+"] - read JSON\n");
    	
    	Object obj = new JSONParser().parse(jstring);
    	JSONObject jo = (JSONObject) obj;
    	JSONObject repo = (JSONObject) jo.get("repository");
    	if(!(boolean) repo.get("private")) {
    		String reponame = (String) repo.get("name");
    		if(reponame == "authority" && (String) repo.get("full_name") == "Sigidoc/authority") {
    	    	writer.write("["+timestamp+"] - repo is authorityfiles\n");
    			processAuthoritylist(server);
    		}
    		else {
    	    	writer.write("["+timestamp+"] - repo is seals\n");
    			processSeals(reponame, jo, server);
    		}
    	}
    	else {
    		writer.write("["+timestamp+"] - Repository is private, please set repository to public\n");
    	}
    	
	};
	
	/**
	 * 
	 * called die funktion pull() und übergibt dieser den Reponame als String.
	 * überprüft die commits in der übergeben jo und speichert alle modifizierten oder neu hinzugefügtne Siegel in einer List.
	 * ruft dann die Funktion indexSeals() auf mit der Liste der Siegel dem Reponamen und dem Servernamen
	 * 
	 * 
	 * @param reponame  Name des Repositories von dem die Siegel stammen
	 * @param jo 		JSONobject in dem die  
	 * @param server	Servername auf dem der Endpoint liegt.
	 * @throws NoWorkTreeException
	 * @throws IOException
	 * @throws GitAPIException
	 */
	
	private static void processSeals(String reponame, JSONObject jo, String server) throws NoWorkTreeException, IOException, GitAPIException {
    	Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    	writer.write("["+timestamp+"] - start process seals\n");

		JSONArray commits = (JSONArray) jo.get("commits");
    	ArrayList<String> sealsList = new ArrayList<>();
    	pull(reponame);
    	writer.write("["+timestamp+"] - pulled new seals\n");

		Iterator<Object> iterator = commits.iterator();
		while(iterator.hasNext()) {
			JSONObject jsonObject = (JSONObject) iterator.next();
			sealsList.addAll(getChangedSealslist("modified", jsonObject));
			sealsList.addAll(getChangedSealslist("added", jsonObject));
			}    
		indexSeals(sealsList, reponame, server);
	};
	
    /**
     * Pfad der Gitfile wird als String erstellt.
     * sollte sich unter /webapps/ROOT/content/xml/epidoc/reponame/.git befinden.
     * Wird geöffnet und Pull Befehl ausgeführt.
     * 
     * @param reponame Name des Repos von dem der Webhook call kommt.
     * @throws IOException
     * @throws NoWorkTreeException
     * @throws GitAPIException
     */
    
    public static void pull(String reponame) throws IOException, NoWorkTreeException, GitAPIException {
    	String gitfile = "webapps/ROOT/content/xml/epidoc/"+ reponame +"/.git";
		Git git = Git.open(new File(gitfile));
        PullCommand pullCmd = git.pull();
        pullCmd.call();  	
    };
    
	/**
	 * Für jedes Siegel in der Siegelliste wird eine URL aufgerufen die aus dem Namen des Servers + dem Reponamen + Siegelnamen besteht
	 *  "https://"+server+"/admin/solr/index/tei/epidoc/"+ reponame+'/'+ seal + ".html";
	 * Wenn der Responsecode 200 ist dann wird in der githook.log vermerkt das das Siegel indexiert wurde.
	 * Ansonsten wird ein connection-Error geschrieben.
	 * 
	 * @param seals  liste der Siegel die indexiert werden sollen
	 * @param reponame name des Repos von dem die Siegel stammen
	 * @param server Name des Servers auf dem der Endpoint liegt.
	 * @throws IOException
	 */
	public static void indexSeals(ArrayList<String> seals, String reponame, String server) throws IOException {


    	for(String sealfile : seals) {
        	Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    	try {
    		String[] tokenizedSeal = sealfile.split("\\.");
    		String seal = tokenizedSeal[0];
    		writer.write("["+timestamp+"] - start index "+ seal + '\n');
    		String indexurl = "https://"+server+"/admin/solr/index/tei/epidoc/"+ reponame+'/'+ seal + ".html";
    		writer.write("["+timestamp+"] - call url: " + indexurl +'\n');

    		URL url = new URL(indexurl);
        	HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        	

        	if(100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
    			InputStream is = connection.getInputStream();
    			String content = new BufferedReader(
    					new InputStreamReader(is, StandardCharsets.UTF_8))
    					.lines()
    					.collect(Collectors.joining("\n"));
        		writer.write("["+timestamp+"] - Indexed\n");

    		
    	}
    	else {
    		BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
    		writer.write("["+timestamp+"] - ERROR connection = "+connection.getResponseCode()+"\n");
    	}
    		
    	}catch(ConnectException e) {
    		writer.write("["+timestamp+"] - CONNECTION ERROR - Can not connect to KFhist Index\n");
    	}
    	}

    };
    
    /**
     * Wenn das Repo die Authority List ist, wird statt Index die Harvest funktion aufgefrufen, ansonsten ist das vorgehen zu den siegeln identisch.
     * 
     * @param server
     * @throws IOException
     * @throws WrongRepositoryStateException
     * @throws InvalidConfigurationException
     * @throws InvalidRemoteException
     * @throws CanceledException
     * @throws RefNotFoundException
     * @throws RefNotAdvertisedException
     * @throws NoHeadException
     * @throws TransportException
     * @throws GitAPIException
     */
	private static void processAuthoritylist(String server) throws IOException, WrongRepositoryStateException, InvalidConfigurationException, InvalidRemoteException, CanceledException, RefNotFoundException, RefNotAdvertisedException, NoHeadException, TransportException, GitAPIException {
    	Timestamp timestamp = new Timestamp(System.currentTimeMillis());

		writer.write("["+timestamp+"] - pull authority Files");
    	String gitfile = "webapps/ROOT/content/xml/authority/.git";
		Git git = Git.open(new File(gitfile));
        PullCommand pullCmd = git.pull();
        pullCmd.call();  	
        harvestRDF(server);
	};
	
	private static void harvestRDF(String server) throws IOException {
    	Timestamp timestamp = new Timestamp(System.currentTimeMillis());
		writer.write("["+timestamp+"] - Start Harvest all");
		String harvesturl = "https://"+server+"/admin/rdf/harvest/all.html";

		URL url = new URL(harvesturl);
    	HttpURLConnection connection = (HttpURLConnection) url.openConnection();
       	

    	if(100 <= connection.getResponseCode() && connection.getResponseCode() <= 399) {
			InputStream is = connection.getInputStream();
			String content = new BufferedReader(
					new InputStreamReader(is, StandardCharsets.UTF_8))
					.lines()
					.collect(Collectors.joining("\n"));
    		writer.write("["+timestamp+"] - Harvested\n");

		
	}
	else {
		BufferedReader br = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
		writer.write("["+timestamp+"] - ERROR connection = "+connection.getResponseCode()+"\n");
	}
	
	};
	
	/**
	 * Funktion die die Siegel aus der JSON in einer Liste speichert.
	 * @param key
	 * @param jobj
	 * @return
	 * @throws IOException
	 */
    public static ArrayList<String> getChangedSealslist(String key, JSONObject jobj) throws IOException{
    	ArrayList<String> seals = new ArrayList<>();
    	JSONArray jarr  = (JSONArray) jobj.get(key);
		Iterator<String> i = jarr.iterator();
		while(i.hasNext()) {
		String file = i.next();
		if(file.contains(".xml")){
			seals.add(file);
		}
		}
		return seals;
    };
    

}

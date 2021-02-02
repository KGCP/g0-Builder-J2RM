/**
 * 
 */
package au.edu.anu.cecs.rscs.agrif_project;

import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @name Graph-0-Builder / CouchDB.
 * @author Sergio.
 * @purpose Access to CouchDB and metadata structures (JSON).
 * @project AGRIF.
 */
public class CouchDB {
  public static String _endpoint;
  public static String _db;

  private static JSONObject _cdb;
  private static Client _client;
  private static LocalDateTime _start;
  private static LocalDateTime _end;


  public static void init() {
    _cdb = ConfigJSON._json.getJSONObject("CouchDB");
    _endpoint = _cdb.getString("EndPoint");
    _db = ConfigJSON._exec.getString("CouchDB.Database");

    Builder.init();

    HttpAuthenticationFeature HttpAuthFeature = HttpAuthenticationFeature
        .basic(_cdb.getString("User"), _cdb.getString("Password"));
    ClientConfig clientConfig = new ClientConfig(HttpAuthFeature);
    clientConfig.register(JSONObject.class);
    _client = ClientBuilder.newClient(clientConfig);

    // Allowing restricted headers with the HttpUrlConnector:
    // System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
    // target.request().header("Host", _cdb.getString("Host"))
  }


  private static void startProcess() {
    _start = LocalDateTime.now();
    System.out.println("Project AGRIF | Graph-0 Builder: Triple Creation");
    System.out.printf("Start time: [%s]\n",
        _start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
  }


  private static void endProcess() {
    _end = LocalDateTime.now();
    System.out.printf("\nEnd time: [%s]",
          _end.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    Duration duration = Duration.between(_start, _end);
    System.out.printf("\nExecution time: %s seconds.\n", duration.getSeconds());
    //System.out.printf("Total number of triples in the graph: %d\n",
    //    Graph.getTripleCount());
    Builder.getGraphStats();
    System.out.println(
        "=====================================================================================================================================================================");
  }


  public static void uuids(long n) {
    System.out.printf("\nuuids(%d):\n", n);
    WebTarget target = _client.target(_endpoint)
        .path(_cdb.getJSONObject("Operations").getString("UUIDS"))
        .queryParam("count", n);
    Response response = target.request(MediaType.APPLICATION_JSON).get();
    // System.out.println(response);
    if (response.getStatus() == HttpURLConnection.HTTP_OK) {
      JSONObject json = new JSONObject(response.readEntity(String.class));
      // System.out.println(json);
      JSONArray array = json.getJSONArray("uuids");
      for (int i = 0; i < array.length(); i++) {
        System.out.printf("[%d]=%s\n", i, array.getString(i));
      }
    }
  }


  public static void processDocsFromDB(String db, int startFrom) {
    startProcess();
    System.out.printf("\nprocessDocsFromDB('%s', %d):\n", db, startFrom);
    WebTarget target = _client.target(_endpoint)
        .path(db).path(_cdb.getJSONObject("Operations").getString("All-Docs"));
    Response response = target.request(MediaType.APPLICATION_JSON).get();
    System.out.println(response);
    if (response.getStatus() == HttpURLConnection.HTTP_OK) {
      JSONObject json = new JSONObject(response.readEntity(String.class));
      // System.out.println(json);
      JSONArray array = json.getJSONArray("rows");
      String id;
      boolean exists;
      JSONObject doc_metadata;
      System.out.printf("Processing %d documents...\n\n", array.length());
      for (int i = 0; i < array.length(); i++) {
        id = array.getJSONObject(i).getString("id");
        exists = doesDocExists(db, id);
        System.out.printf("[%d](exists?=%b):%s\n", i, exists, array.getJSONObject(i));
        if ((exists) && (i >= startFrom)) {
          doc_metadata = getDoc(db, id);
          Builder.processDocument(doc_metadata);
        } // if
        if (i < startFrom)
          System.out.printf("... skipping document...\n");
      } // for
      endProcess();
    } // if HTTP_OK
  } // processAllDocsFromDB


  // HTTP minimal response with HEAD:
  public static boolean doesDocExists(String db, String id, boolean _print) {
    if (_print) System.out.printf("\ndoesDocExists(db='%s', id='%s'):\n", db, id);
    WebTarget target = _client.target(_endpoint).path(db).path(id);
    Response response = target.request(MediaType.APPLICATION_JSON).head();
    if (_print) System.out.println(response);
    response.close();
    return (response.getStatus() == HttpURLConnection.HTTP_OK);
  }


  public static boolean doesDocExists(String db, String id) { return doesDocExists(db, id, false); }


  // HTTP full response with GET:
  public static JSONObject getDoc(String db, String id, boolean _print) {
    if (_print) System.out.printf("\ngetDoc(db='%s', id='%s'):\n", db, id);
    WebTarget target = _client.target(_endpoint).path(db).path(id);
    Response response = target.request(MediaType.APPLICATION_JSON).get();
    JSONObject json = null;
    if (response.getStatus() == HttpURLConnection.HTTP_OK) {
      json = new JSONObject(response.readEntity(String.class));
      if (_print) System.out.println(json);
    } else System.out.println(response);
    return json;
  }

  public static JSONObject getDoc(String db, String id) { return getDoc(db, id, false); }


  public static void processSomeDocs(String db, JSONObject query) {
    startProcess();
    System.out.printf("\nprocessSomeDocs('%s', '%s'):\n", db, query.toString(4));

    WebTarget target  = _client.target(_endpoint).path(db).path(_cdb.getJSONObject("Operations").getString("Find"));
    Response response = target.request(MediaType.APPLICATION_JSON).post(Entity.json(query.toString()));
    System.out.println(response);

    if (response.getStatus() == HttpURLConnection.HTTP_OK) {
      JSONObject json = new JSONObject(response.readEntity(String.class));
      // System.out.println(json);
      JSONArray array = json.getJSONArray("docs");
      String id;
      boolean exists;
      JSONObject doc_metadata;
      System.out.printf("** %d documents found **\n\n", array.length());
      for (int i = 0; i < array.length(); i++) {
        id = array.getJSONObject(i).getString("_id");
        exists = doesDocExists(db, id);
        System.out.printf("[%d](exists?=%b):%s\n", (i + 1), exists, array.getJSONObject(i));
        if (exists) {
          doc_metadata = getDoc(db, id);
          Builder.processDocument(doc_metadata);
        } // if (exists)
      } // for
      endProcess();
    } // if HTTP_OK
  } // processSomeDocs


  public static void processDocsOfFileType(String db, String filetype, int howMany) {
    String query = "{ \"selector\": {"
        + " \"General-Metadata\": { \"EXTENSION\": \"" + filetype + "\" } },"
        + " \"fields\": [\"_id\", \"_rev\"],"
        + " \"limit\": " + howMany
        + ", \"skip\": 0, \"execution_stats\": True}";
    processSomeDocs(db, new JSONObject(query));
  }
  
  public static void processFile(String db, String absolutePath, int howMany) {
    String query = "{ \"selector\": {"
        + " \"General-Metadata\": { \"ABSOLUTEPATH\": \"" + absolutePath + "\" } },"
        + " \"fields\": [\"_id\", \"_rev\"],"
        + " \"limit\": " + howMany
        + ", \"skip\": 0, \"execution_stats\": True}";
    processSomeDocs(db, new JSONObject(query));
  }
  
  public static void processDoc(String db, String id) {
    String query = "{ \"selector\": {"
        + " \"_id\": \"" + id + "\" } },"
        + " \"fields\": [\"_id\", \"_rev\"],"
        + " \"limit\": 1,"
        + " \"skip\": 0, \"execution_stats\": True}";
    processSomeDocs(db, new JSONObject(query));
  }
  
  public static void processDocsOfSubPath(String db, String subpath, int howMany) {
    String query = "{ \"selector\": {"
        + " \"General-Metadata\": { \"ABSOLUTEPATH\": { \"$regex\": \"(.*)(" + subpath + ")(.*)\" } } },"
        + " \"fields\": [\"_id\", \"_rev\"],"
        + " \"limit\": " + howMany
        + ", \"skip\": 0, \"execution_stats\": True}";
    processSomeDocs(db, new JSONObject(query));
  }
  
  public static void processAllDocsFromDB(String db) { processDocsFromDB(db, 0); }


  /******************************************************************************************************************************/
  /**
   * @param args
   */
  public static void main(String[] args) {
    init();
    
    // processDocsOfFileType(_db, "JPG", 50);
    // processDocsOfSubPath(_db, "SpeciesDocumentSets", 2000);
    // processDocsOfSubPath(_db, "ANU_History", 2000);
    // String filename = "E:\\\\_temp\\\\DepFin-Project\\\\DoEE_endangered-species\\\\SpeciesDocumentSets\\\\Mammals - Gymnobelideus leadbeateri Leadbeater's possum FOI 2017\\\\Lawler, Ivan_18Oct17 12.16.51_RE Total colonies and Total new colonies [SEC=UNCLASSIFIED].msg"; 
    // processFile(_db, filename, 1);
    // processDoc(_db, "208703ff781a33ac506a9f207b03adac");
    // processDocsFromDB(_db, 2760);
    processAllDocsFromDB(_db);


    // JSONObject doc = getDoc(_db, "1b195dc7f6535d739616cb836a476ca4", true);
    // boolean exists =
    // doesDocExists(_db, "1b195dc7f6535d739616cb836a476ca4", true);
  }
}

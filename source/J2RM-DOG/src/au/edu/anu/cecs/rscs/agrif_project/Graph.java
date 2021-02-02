package au.edu.anu.cecs.rscs.agrif_project;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;

import org.json.*;

import virtuoso.jena.driver.*;

/**
 * @name Graph-0-Builder / Graph.
 * @author Sergio.
 * @purpose Access to Virtuoso Universal Server --VUS-- specific graph.
 * @project AGRIF.
 * @dependencies External JARs: (1) virt_jena.jar. (2) virtjdbc3.jar.
 * 
 * 2020-06-29: Generalization to handle multiple SPARQL endpoints ("scope": private/public) with multiple named-graphs.
 */
public class Graph {
  private VirtGraph g_obj;
  private String g_URI;
  private GraphScope g_scope;

  private static VirtGraph _g0, _g0Private, _g0Public; // *_g0* is the global accessible object from the other components
  private static JSONArray _vus;
  public static boolean _clearGraphs = false;
  public static boolean _complete_PRIVATE_graph = false;
  

  static { // default graph connections based on the configuration file:
    _vus = ConfigJSON._json.getJSONArray("VUS");
    JSONObject _v0 = (JSONObject) _vus.get(0); // private graph triplestore
    JSONObject _v1 = (JSONObject) _vus.get(1); // public  graph triplestore
    _g0Private = new VirtGraph(ConfigJSON._exec.getString("VUS.Graph-0"), _v0.getString("JDBC-connection"),
      _v0.getString("User"), _v0.getString("Password")); // default private graph
    _clearGraphs = (ConfigJSON._exec.has   ("clear-graphs")) ?
                   (ConfigJSON._exec.getInt("clear-graphs") == 1) // boolean flag
                   : false;
    _complete_PRIVATE_graph = (ConfigJSON._exec.has   ("complete-PRIVATE-graph")) ?
                              (ConfigJSON._exec.getInt("complete-PRIVATE-graph") == 1) // boolean flag
                              : false;
    _g0Public = null;
    if (ConfigJSON._exec.has("VUS.Graph-0.Public")) { // default public graph
      _g0Public = new VirtGraph(ConfigJSON._exec.getString("VUS.Graph-0.Public"), _v1.getString("JDBC-connection"),
        _v1.getString("User"), _v1.getString("Password"));
    }
    _g0 = _g0Private; // default graph
    if (_clearGraphs) { // initially clear graphs if the parameter is set:
      _g0Private.clear();
      if (_g0Public != null)
        _g0Public.clear();
    }
  }
  
  public static String getDefaultNamedGraph(GraphScope scope) {
    String uri = null;
    switch (scope) {
      case PUBLIC:
        uri = ConfigJSON._exec.getString("VUS.Graph-0.Public");
      break;
      case PRIVATE:
      default:
        uri = ConfigJSON._exec.getString("VUS.Graph-0");
      break;
    } // switch
    return uri;
  }
  
  public String getName()  { return g_URI; }
  public String getScope() { return g_scope.getGraphScope(); }
  public VirtGraph getVirtGraph() { return g_obj; }

  public JSONObject getVUSgraphSettings(GraphScope scope) {
    for (int i = 0; i < _vus.length(); i++) {
      JSONObject o = _vus.getJSONObject(i);
      if (o.getString("Scope").toUpperCase().equals(scope.getGraphScope()))
        return o; // returns the first graph found with that scope.
    } // for
    return null;
  }
  
  public Graph(String uri, GraphScope scope) {
    g_URI   = uri;
    g_scope = scope;
    JSONObject vus_conn = this.getVUSgraphSettings(scope);
    if (g_URI == null) {
      g_URI = Graph.getDefaultNamedGraph(scope);
      Graph.setGraph(scope); // the connection accessible as a static method
      g_obj = _g0; // we use one of the default graphs
    }
    else { // creates the VUS connection based on the graph scope and graph URI.
      g_obj = new VirtGraph(g_URI,  vus_conn.getString("JDBC-connection"),
        vus_conn.getString("User"), vus_conn.getString("Password"));
      _g0 = g_obj; // the connection accessible as a static method
    }
  }
  
  // ("PRIVATE" | "PUBLIC")
  public static void setGraph(GraphScope scope) {
    switch (scope) {
      case PUBLIC:
        _g0 = _g0Public;
      break;
      case PRIVATE:
      default:
        _g0 = _g0Private;
      break;
    } // switch
  }
  
  // checks if we add (as well) the triple to the default private graph
  public static boolean addToPrivateGraph() { return ( (_complete_PRIVATE_graph) && (_g0 != _g0Private) ); }

  public static void setGraph(Graph g) { _g0 = g.getVirtGraph(); }

  public static boolean  isEmpty() { return   _g0.isEmpty(); }
  public        boolean _isEmpty() { return g_obj.isEmpty(); }

  public static long  getTripleCount() { return   _g0.getCount(); }
  public        long _getTripleCount() { return g_obj.getCount(); }

  // empties all triples from the graph.  Only if parameter is set of the execution context.
  public static void clear() { if (_clearGraphs) _g0.clear(); }

  public static String query(String q) {
    VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create(q, _g0);
    ResultSet results = vqe.execSelect();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ResultSetFormatter.outputAsJSON(baos, results);
    JSONArray ret = new JSONObject(baos.toString()).getJSONObject("results").getJSONArray("bindings");
    return ret.toString();
  }

  public static Triple createObjTriple(String URI_s, String URI_p, String URI_o) {
    Node s = Node.createURI(URI_s);
    Node p = Node.createURI(URI_p);
    Node o = Node.createURI(URI_o);
    return new Triple(s, p, o);
  }

  public static Triple createDataTriple(String URI_s, String URI_p, String literal, String lang, XSDDatatype xsdDataType) {
    Node s = Node.createURI(URI_s);
    Node p = Node.createURI(URI_p);
    // lang: if not indicated ==> null
    // XSDDatatype: if not indicated ==> null
    Node o = Node.createLiteral(literal, lang, xsdDataType);
    return new Triple(s, p, o);
  }

  public static void addObject(String URI_s, String URI_p, String URI_o) {
    _g0         .add(createObjTriple(URI_s, URI_p, URI_o));
    if (addToPrivateGraph())
      _g0Private.add(createObjTriple(URI_s, URI_p, URI_o));
  }

  public static void addLiteral(String URI_s, String URI_p, String literal, String lang, XSDDatatype type) {
    _g0         .add(createDataTriple(URI_s, URI_p, literal, lang, type));
    if (addToPrivateGraph())
      _g0Private.add(createDataTriple(URI_s, URI_p, literal, lang, type));
  }

  public static void addTriples() {
    System.out.println("adding triples...");
    addObject("http://example.org/#foo1", "http://example.org/#bar1", "http://example.org/#baz1");
    addObject("http://example.org/#foo2", "http://example.org/#bar2", "http://example.org/#baz2");
    addObject("http://example.org/#foo3", "http://example.org/#bar3", "http://example.org/#baz3");
  }

  public static void removeTriples() {
    List<Triple> triples = new ArrayList<Triple>();
    triples.add(createObjTriple("http://example.org/#foo1", "http://example.org/#bar1", "http://example.org/#baz1"));
    triples.add(createObjTriple("http://example.org/#foo2", "http://example.org/#bar2", "http://example.org/#baz2"));
    _g0.remove(triples);
  }

  /******************************************************************************************************************************/
  public static void main(String[] args) {
    // addTriples();
    clear();

    System.out.println("graph.isEmpty() = " + isEmpty());
    System.out.println("graph.getCount() = " + getTripleCount());
  }
}

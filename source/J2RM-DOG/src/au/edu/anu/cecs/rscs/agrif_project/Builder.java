/**
 * 
 */
package au.edu.anu.cecs.rscs.agrif_project;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.validator.UrlValidator;
import org.json.JSONObject;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;

import me.xdrop.fuzzywuzzy.FuzzySearch;


/**
 * @name Graph-0-Builder / Builder.
 * @author Sergio.
 * @purpose Main routines to build the initial graph structure (triple
 *          creation).
 * @project AGRIF.
 * 
 * 2020-06-29: Generalization to handle multiple SPARQL endpoints ("scope": private/public) with multiple named-graphs.
 * 2020-08-03: implementing fuzzy string matching.
 */
@SuppressWarnings("deprecation")
public class Builder {
  public static int _FUZZY_STRING_MATCHING_WORD_NUMBER = 4;
  public static int _FUZZY_STRING_MATCHING_RATIO_THRESHOLD = 99;

  public static String _exec;
  public static String _endpoint;
  public static String _db;

  public static JSONObject _onto;
  public static String _onto_ag_ns;
  public static String _onto_g0_ns;
  public static String _onto_dc_ns;
  public static String _onto_is_a;
  public static String _onto_label;
  public static String _onto_sameAs;
  
  public static Map<String, Graph> _gl; // list of graph connections: support for multiple graphs
  
  static {
    _gl = new HashMap<>();
  }

  /******************************************************************************************************************************/

  public static void setGraph(GraphScope scope, String URI) {
    String key = scope.toString() + "|" + ((URI == null) ? Graph.getDefaultNamedGraph(scope) : URI);
    Graph g = null;
    if (_gl.containsKey(key))
      g = _gl.get(key);
    else { // add new graph
      g = new Graph(URI, scope);
      _gl.put(key, g);
    }
    Graph.setGraph(g); // set this graph as the current accessible object among the module components
  }

  public static void init() {
    _exec = ConfigJSON._json.getString("$-exec");
    _endpoint = ConfigJSON._json.getJSONObject("CouchDB").getString("EndPoint");
    _db = ConfigJSON._exec.getString("CouchDB.Database");
    _onto = ConfigJSON._json.getJSONObject("$-onto");
    _onto_ag_ns = _onto.getString("ag-ns");
    _onto_g0_ns = ConfigJSON._exec.getString("g0-ns");
    _onto_dc_ns = _onto.getString("dc-ns");
    _onto_is_a  = _onto.getString("is-a");
    _onto_label = _onto.getString("label");
    _onto_sameAs = _onto.getString("sameAs");
  }
  
  public static void getGraphStats() {
    _gl.forEach( (id, graph) -> {
      System.out.printf("Graph: %s; triple count: %d\n", id, graph._getTripleCount());
    } );
  }


  // For e-mail attachments:
  public static void processInnerFile(JSONObject metadata) {
    Document doc = new Document(true, metadata);
    doc.buildDocGraph();
  }


  public static void processDocument(JSONObject doc_metadata) {
    Document doc = new Document(doc_metadata);
    doc.buildDocGraph();
  }


  // REFERENCE: <https://github.com/xdrop/fuzzywuzzy>
  public static boolean stringMatching(String s1, String s2) {
    s1 = s1.strip().toLowerCase(Locale.ROOT);
    s2 = s2.strip().toLowerCase(Locale.ROOT);
    String[] s1_words = s1.split(" ");
    if (!s1.equals(s2)) {
      // fuzzy string matching:
      int ratio = (s1_words.length > _FUZZY_STRING_MATCHING_WORD_NUMBER) ? 
          FuzzySearch.weightedRatio(s1, s2) : // many words
          FuzzySearch.ratio(s1, s2); // few words.
      return (ratio > _FUZZY_STRING_MATCHING_RATIO_THRESHOLD); // Threshold.
    } // equals
    //System.out.printf("    $ stringMatching('%s', '%s'): s1_wO_first='%s' -> true\n", s1, s2, s1_wO_first.strip());
    return true;
  }


  public static boolean isValidURL(String s) {
    UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_ALL_SCHEMES);
    return (urlValidator.isValid(s));
  }


  // Remove special characters from the ID:
  public static String newInstanceURI(String fromClass, String withID) {
    return (
    // if (*fromClass* doesn't have '/' and '#') ==>
    // it's a local name ==> uses AGRIF namespace.
    ((fromClass.indexOf('/') * fromClass.indexOf('#') == 1) ? _onto_g0_ns : "") 
        + ((fromClass.length() == 0) ? "" : fromClass + "-")
        + withID.replaceAll("[\\,:/\\.]", "-").replaceAll("[\\(\\)]", "__"));
  }


  public static String newClassURI(String Class) { return (_onto_ag_ns + Class); }


  public static void addLabel(String s, String l) {
    Graph.addLiteral(s, Builder._onto.getString("label"), _str(l), "en", XSDDatatype.XSDstring); // English
  }

  // Replacing some special Unicode points:
  public static String _str(String s) { return s.replaceAll("\\u0000", ""); }


  public static String _int(Number number) { return number.toString(); }


  public static String _dt(String d) {
    if (d.charAt(d.length()-1) == 'Z')        
      d = d.substring(0, d.length()-1); // remove the last character ('Z')
    if (d.length() == "YYYY-MM-DD".length()) d += "T00:00:00"; // HH:mm:ss
    if (!d.contains("+")) d += "+00:00"; // adds tz=UTC+00:00 to naive datetime values
    return d.replace(' ', 'T');
  }


  public static String _uri(String u) { return (u); }


  public static String _uuid(String prefix) {
    UUID uuid = UUID.randomUUID();
    return (((prefix.length() == 0) ? "" : prefix + "-") + uuid.toString());
  }


  // for [null] or empty values:
  public static boolean _isNullOrEmpty(String v) { return (v.equals("null") || v.equals("")); }


  // for [null] or empty values:
  public static boolean _isNOTnull(String v) { return (!v.equals("null") && v.length() > 1); }


  // Checks whether the key exists. If so, then returns its (string) value.
  // removeSpace? ==> for possible empty values.
  public static String _mightHaveKey(JSONObject o, String k, boolean removeSpace) {
    if (!o.has(k)) return "";
    return ((removeSpace) ? o.get(k).toString().replace(" ", "") : o.get(k).toString());
  }


  public static String _tryElse(JSONObject o, String _try, String _else) {
    if (o.has(_try) ) { if (!o.isNull(_try) ) return o.get(_try) .toString(); }
    if (o.has(_else)) { if (!o.isNull(_else)) return o.get(_else).toString(); }
    return "";
  }
} // class Builder

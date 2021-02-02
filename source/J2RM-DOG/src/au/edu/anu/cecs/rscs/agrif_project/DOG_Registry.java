/**
 * 
 */
package au.edu.anu.cecs.rscs.agrif_project;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * @name Graph-0-Builder / DOG_Registry.
 * @author Sergio.
 * @purpose Internal registry for the Domain-Ontology Graph creation process.
 * @project AGRIF.
 */
public class DOG_Registry {

  private JSONObject _registry;
  private Map<String, JSON_Path> _cache;

  /******************************************************************************************************************************/
  private String compareInstances(String classURI, Object ID, char _processFlagIDfromString, String instanceInRegistry) {
    if (ID instanceof String) { // the ID is mapping to a single string value:
      if (ID.toString().equals(JSON_Path.DOC_ID_SYMBOL))
        return instanceInRegistry; // the ID must be already stored in the registry.
      // generates a valid/formatted URI for an instance based on the classURI and the ID:
      String instance = generateInstanceURI(classURI, ID.toString(), _processFlagIDfromString);
      //System.out.printf("<DOG_Registry.compareInstances(%s, %s)>:\nval=<%s>\nreg=<%s>\n", classURI, ID, instance, instanceInRegistry);
      if (instanceInRegistry.equals(instance))
        return instanceInRegistry;
      return null;
    } // String
    if (ID instanceof JSONArray) { // the ID is mapping to a JSON Array: need to check the *instance* in the array:
      for (Object e : (JSONArray) ID) { // for each element in the array
        // generates a valid/formatted URI for an instance based on the classURI and the ID:
        String instance = generateInstanceURI(classURI, e.toString(), _processFlagIDfromString);
        //System.out.printf("<DOG_Registry.compareInstances(%s, %s)>:\nval=<%s>\nreg=<%s>\n", classURI, e.toString(), instance, instanceInRegistry);
        if (instanceInRegistry.equals(instance))
          return instanceInRegistry;
      } // for
    } // JSONArray
    return null;
  }

  private String getEntityValue(String from, String localName) {
    Object IDs = _registry.get(from);
    if (IDs instanceof JSONObject) // It only has one entry
      return (((JSONObject) IDs).has(localName)) ?
              ((JSONObject) IDs).getString(localName) : null;
    JSONArray arrIDs = _registry.getJSONArray(from);
    JSONObject ID;
    //System.out.printf("<DOG_Registry.getEntityValue(%s, %s)>: %s\n", from, localName, arrIDs);
    for (int i = 0; i < arrIDs.length(); i++) {
      ID = (JSONObject) arrIDs.get(i); 
      if (ID.has(localName))
        return ID.getString(localName);
    }
    return null;
  }
  
  private void addEntity(String on, String localName, String value) {
    _registry.accumulate(on, new JSONObject( String.format("{\"%s\":\"%s\"}", localName, value) ));
  }

  /******************************************************************************************************************************/
  public String generateIDfromString(String id) {
    return id.replace(' ', '-');
  }

  public Object[] checksFlag(String str) {
    Object[] r = new Object[2];
    char flag = str.charAt(str.length()-1);
    if (JSON_Path.isValidFlag(flag)) {
        r[0] = str.substring(0, str.indexOf(flag)); // removes <flag>
        r[1] = flag;
    }
    else {
        r[0] = str;
        r[1] = ' ';
    }
    //System.out.printf("<DOG_Registry.checksFlag(%s)>: [%s, %s]\n", str, r[0], r[1]);
    return r;
  }

  public boolean existsInstance(String classLocalName, String instance) {
    if (_registry.has(classLocalName)) {
      Object arrIDs = _registry.get(classLocalName);
      String storedInstance;
      if (arrIDs instanceof JSONObject) {
        storedInstance = ((JSONObject) arrIDs).getString(classLocalName);
        if (storedInstance.equals(instance))
          return true;
      }
      if (arrIDs instanceof JSONArray) {
        arrIDs = _registry.getJSONArray(classLocalName);
        for (int i = 0; i < ((JSONArray) arrIDs).length(); i++) {
          storedInstance = (String) ((JSONArray) arrIDs).get(i);
          if (storedInstance.equals(instance))
            return true;
        } // for
      } // if (instanceof JSONArray)
    } // if (.has)
    return false;
  }
  
  public String generateInstanceURI(String classURI, String id, char _processFlagIDfromString) {
    id = (_processFlagIDfromString == JSON_Path.GENERATE_ID_FLAG) ? generateIDfromString(id) : id;
    String instance = null;
    if (_processFlagIDfromString == JSON_Path.USE_ID_FLAG) {
      switch (classURI) { // fix the URI instance based on the class
      case "http://eurocris.org/ontology/semcerif#ORCID":
        // ORCIDs have the following structure: "^\d{4}-\d{4}-\d{4}-(\d{3}X|\d{4})$"
        // REFERENCE: <https://support.orcid.org/hc/en-us/articles/360006897674-Structure-of-the-ORCID-Identifier>
        // /*- /*- NEED TO RELOAD ORCIDs WITH "X"
        if (!id.matches("^https://orcid.org/\\d{4}-\\d{4}-\\d{4}-(\\d{3}X|\\d{4})$")) {
          id = id.toLowerCase().strip();
          if (Character.isDigit(id.charAt(0)))   id = "https://orcid.org/" + id;
          else if        (id.charAt(0) == '/')   id = "https://orcid.org" + id;
          else if        (id.charAt(0) == ':')   id = "https" + id;
          else if     (id.startsWith("orcid"))   id = "https://"  + id;
          else if     (id.startsWith( "rcid"))   id = "https://o" + id;
          else if   ( (id.startsWith("http:"))
                   || (id.startsWith( "ttp:")) 
                   || (id.startsWith("htpp:")) ) id = "https"     + id.substring(id.indexOf(":"));
          if (!Character.isDigit(id.charAt(id.length()-1)))
            if (id.charAt(id.length()-1) != 'x')        
              id = id.substring(0, id.length()-1); // remove the last character
            else
              id = id.substring(0, id.length()-1) + "X"; // we include "X"
        } // if (ORCID regex)
        // the "fixed" ORCID is returned as the URI instance.
      default:
        instance = id;
      } // switch
    } else instance = Builder.newInstanceURI(classURI, id);
    return instance;
  }

  public String addInstance(String classLocalName, String URI, String id, char _processFlagIDfromString) {
    if ( (id == null) || (id.equals("")) ) // an empty ID --> ignore
      return null;
    String instance = generateInstanceURI(URI, id, _processFlagIDfromString);
    if (!existsInstance   (classLocalName, instance))
      _registry.accumulate(classLocalName, instance);
    return instance;
  }

  public String getIndividual(String      localName) { return getEntityValue(".",      localName); }
  public String getClassID   (String classLocalName) { return getEntityValue("$", classLocalName); }

  private Object getInstance_s(String classURI, String classLocalName, Object ID, char _processFlagIDfromString) {
    //System.out.printf("<DOG_Registry.getInstance(%s, %s, %s)>\n", classLocalName, ID, _processFlagIDfromString);
    if ((ID == null) || (ID.toString().length() == 0))
      return null;
    Object obj = _registry.get(classLocalName);
    if (obj instanceof String)
      return compareInstances(classURI, ID, _processFlagIDfromString, (String) obj);
    if (obj instanceof JSONArray) { // there's an array (collection) of instance for *classLocalName*
      JSONArray arrIDs = _registry.getJSONArray(classLocalName);
      JSONArray matchedIDs = new JSONArray();
      String instanceInRegistry;
      for (int i = 0; i < arrIDs.length(); i++) {
        instanceInRegistry = (String) arrIDs.get(i);
        if (compareInstances(classURI, ID, _processFlagIDfromString, instanceInRegistry) != null) {
          if (ID instanceof String)    return instanceInRegistry; // return the single matched value
          if (ID instanceof JSONArray) matchedIDs.put(instanceInRegistry); // adds the matched value into an array
        } // if
      } // for
      return matchedIDs; // multiple values were matched
    } // if (JSONArray)
    return null;
  }

  // get an instance based on the object/key-value retrieved from the cache:
  public Object getInstance_s(String classURI, String classLocalName, JSONObject o, String mapToAncestor) {
    if (_cache.containsKey(classLocalName)) {
      JSON_Path p = _cache.get(classLocalName);
      String k = null, v_mp = null; // value for a multipart path
      p.initPathPointer(); // we initialize the internal pointer: it's been compute before at the main processing loop at DOG.
      if (p.isMultiPart()) {
        int i = p.getPathsLength() - 1;
        // process multipart paths from the last part (deepest structure) to the first part (top level structure)
        v_mp = "";
        /* 
         * Using JSONObject ancestors for the mappings are not implemented in the following fragment!!
         * */
        while ( (i > 0) && (p.getPath(i).isObjectInMappedObjects(o)) ) {
          k = p.getPath(i).getKey();
          if (k != null) { // if key found:
            // Support for single values (String). Multi-values (JSONArray) still not implemented!
            v_mp = p.getPath(i).getKeyValueFromObject(o).toString() + ((v_mp.length() > 0) ? JSON_Path._MULTIVALUE_SEPARATOR : "") + v_mp;
          }
          //System.out.printf("<getInstance()>: k=%s,v=%s\n", k, v_mp);
          --i; // move to the upper level structure
          if (i > 0) o = p.MP_getAncestorObjectInMappedObjects(o); // gets ancestor's object.
        } // while
        if ((v_mp != null) && (v_mp.length() > 0)) {
          //System.out.printf("<DOG_Registry.getInstance() | Multipart Path> v=%s\n", v);
          return this.getInstance_s(classURI, classLocalName, v_mp, p.getPath(p.getPathsLength() - 1).getFlag());
        } // if
        return null; // match not found!
      } // if (isMultiPart())
      Object v = null;
      while (p.getCurrentPath() != null) { // tries to perform multiple path processing
        boolean isObjectInMappedObjects = p.getCurrentPath().isObjectInMappedObjects(o);
        JSONObject ancestor = p.getCurrentPath().getAncestorFromMappedObjects(mapToAncestor, o); 
        if (isObjectInMappedObjects || (ancestor != null)) {
          k = p.getCurrentPath().getKey();
          v = p.getCurrentPath().getKeyValueFromObject( (ancestor != null) ? ancestor : o);
          //System.out.printf("<DOG_Registry.getInstance()> k=%s, v=%s\n", k, v);
          if (k == null) return null; // if we are not able to retrieve any (key), we return null.
          return this.getInstance_s(classURI, classLocalName,
            ((k.equals(JSON_Path.DOC_ID_SYMBOL)) ? k : v), p.getCurrentPath().getFlag());
        } // if
        p.nextPath();
      } // while of multiple path processing
    } // if (containsKey)
    return null;
  }

  public String getInstance (String classLocalName) { return (_registry.has(classLocalName)) ? _registry.get(classLocalName).toString() : null; } // one single value
  public Object getInstances(String classLocalName) { return (_registry.has(classLocalName)) ? _registry.get(classLocalName) : null; } // (possibly) an array
  
  public JSON_Path getJSON_PathFromCache(String classLocalName) {
    return (_cache.containsKey(classLocalName)) ? _cache.get(classLocalName) : null;
  }

  public boolean isMultiPartJSON_PathFromCache(String classLocalName) {
    JSON_Path p = this.getJSON_PathFromCache(classLocalName);
    if (p != null)
      return p.isMultiPart();
    return false;
  }

  public void addIndividual(String localName, String value) { addEntity(".", localName, value); }
  public void addClass(String localName, JSON_Path JSON_mappings) {
    _cache.put(localName, JSON_mappings); // adds the class with its JSON mappings to the cache.
    // Registers the identifier attribute for the class:
    String identifier = JSON_mappings.getCurrentPath().getKey();
    char flag         = JSON_mappings.getCurrentPath().getFlag();
    addEntity("$", localName, identifier + ((JSON_Path.isValidFlag(flag)) ? flag : ""));
  }

  public void print(char c) {
    switch(c) {
      case '$': System.out.printf("<DOG_Registry.print() | $>: %s\n", _registry.get("$")); break;
      case '.': System.out.printf("<DOG_Registry.print() | .>: %s\n", _registry.get(".")); break;
    }
  }

  public void print() {
    System.out.printf("<DOG_Registry.print()> :\n%s", _registry.toString());
  }

  public DOG_Registry() {
    _registry = new JSONObject();
    _cache = new HashMap<String, JSON_Path>();
  }
}

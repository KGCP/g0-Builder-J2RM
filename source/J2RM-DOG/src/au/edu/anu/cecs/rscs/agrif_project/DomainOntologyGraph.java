/**
 * 
 */
package au.edu.anu.cecs.rscs.agrif_project;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;
import com.hp.hpl.jena.ontology.AnnotationProperty;
import com.hp.hpl.jena.ontology.DatatypeProperty;
import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.ObjectProperty;
import com.hp.hpl.jena.ontology.OntClass;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.ontology.OntProperty;
import com.hp.hpl.jena.ontology.OntResource;
import com.hp.hpl.jena.ontology.Restriction;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.util.FileManager;
import com.hp.hpl.jena.util.iterator.ExtendedIterator;

/**
 * @name Graph-0-Builder / DomainOntologyGraph.
 * @author Sergio.
 * @purpose Class that defines the template for the graph creation for any domain-ontology structure.
 * @project AGRIF.
 */
public class DomainOntologyGraph {
  public static String METADATA_SOURCE = "Associated-Metadata";
  public static String JSON_MAPPING = "JSON-mapping";
  public static String GRAPH_SCOPE = "graph-scope";
  public static String NAMED_GRAPH = "named-graph";

  private org.w3c.dom.Document _xmlonto = null;

  private JSONObject _onto = null;
  private OntModel _model = null;
  private OntProperty _JSON_mapping, _graph_scope, _named_graph;
  private Map<OntProperty, Set<OntClass>> _clsRstr; // all class restrictions for any property

  private JSONObject _doc;
  private DOG_Registry _reg;
  private String _id;
  
  // mapping structures:
  private String _JSON_mapping_value;
  private JSON_Path _JSON_path = null, _domainPath = null, _rangePath = null;
  private Resource _onto_resource;
  
  // For properties:
  private String DomainClassURI = null, DomainClassLocalName = null;
  private String RangeClassURI  = null, RangeClassLocalName = null;


  /******************************************************************************************************************************/
  private Object getGraphDescriptor(OntProperty descriptor) {
    if (_onto_resource.hasProperty(descriptor)) {
      String annotation = _onto_resource.getProperty(descriptor).getLiteral().toString();
      if (annotation.contains(",") && annotation.contains("@")) { // multiple values: <descriptor>@<entity>, <descriptor>@<entity>, ...
        String gs_array[] = annotation.split(",");
        for (String gs : gs_array) {
          String[] parts = gs.trim().split("@");
          if ((parts.length == 2) && (_JSON_path.getCurrentPath().hasEntity()) &&
              (parts[1].equals       (_JSON_path.getCurrentPath().getEntity()))) { // @<entity> matches with the entity of the current JSON mapping
            return (descriptor == _named_graph) ?
                    _xmlonto.lookupNamespaceURI(parts[0].trim()) :
                    GraphScope.valueOf(parts[0].trim().toUpperCase());
          } // if @entity
        } // for each(,)
      } // if (,@)
      else // return single value:
        return (descriptor == _named_graph) ?
                _xmlonto.lookupNamespaceURI(annotation.trim()) : 
                GraphScope.valueOf(annotation.trim().toUpperCase());
    }
    // default value:
    return (descriptor == _named_graph) ? null // null URI ==> default graph from configuration file
           : GraphScope.PRIVATE;
  }

  private void setGraph() {
    GraphScope gs = (GraphScope) getGraphDescriptor(_graph_scope);
    String ng = (String) getGraphDescriptor(_named_graph);
    //System.out.printf("    ... created in <%s> @ %s\n", ng, gs);
    Builder.setGraph(gs, ng);
  }


  /******************************************************************************************************************************/
  private void addIndividual(String classLocalName, String URI, String id) {
    id = (id.equals(JSON_Path.DOC_ID_SYMBOL)) ? _id : id;
    String instance = _reg.addInstance(classLocalName, URI, id, _JSON_path.getCurrentPath().getFlag());
    if (instance != null) {
      System.out.printf("*** Instance of class <%s> : %s\n", URI, id);
      setGraph();
      Graph.addObject(instance, Builder._onto_is_a, URI);
    }
  }

  private void addDatatypeProperty(String DOMAIN_cls, String instance, String dpURI, String value, String RANGE_xsd, boolean validateAsURL) {
    String lang = null;
    XSDDatatype xsd = null;
    RANGE_xsd = (RANGE_xsd == null) ? "http://www.w3.org/2000/01/rdf-schema#Literal" : RANGE_xsd; // default type: *rdfs:Literal*
    if (value.strip().length() == 0) return; // no triple creation for empty values!
    switch (RANGE_xsd) {
      case "http://www.w3.org/2001/XMLSchema#boolean":
        xsd = XSDDatatype.XSDboolean;
      break;
      case "http://www.w3.org/2001/XMLSchema#int":
        xsd = XSDDatatype.XSDinteger;
      break;
      case "http://www.w3.org/2001/XMLSchema#long":
        xsd = XSDDatatype.XSDlong;
      break;
      case "http://www.w3.org/2001/XMLSchema#float":
        xsd = XSDDatatype.XSDfloat;
      break;
      case "http://www.w3.org/2001/XMLSchema#dateTime":
        xsd = XSDDatatype.XSDdateTime;
        value = Builder._dt(value);
      break;
      // Same lang and value generation for xsd:string and rdfs:Literal:
      case "http://www.w3.org/2001/XMLSchema#string":
      case "http://www.w3.org/2001/XMLSchema#anyURI":
        xsd = XSDDatatype.XSDstring;
        if (  (RANGE_xsd.equals("http://www.w3.org/2001/XMLSchema#string") && validateAsURL) ||
              (RANGE_xsd.equals("http://www.w3.org/2001/XMLSchema#anyURI")) )
          if (Builder.isValidURL(value)) {
            xsd = XSDDatatype.XSDanyURI;
            break;
          }
      case "http://www.w3.org/2000/01/rdf-schema#Literal":
        lang = "en"; // lang:en (rdfs:Literal)
        value = Builder._str(value);
      break;
    } // switch
    // if (instance == null): it's a *singleton* instance; we retrieve it directly from the internal registry.
    // else: it's expected that the proper instance value is passed in the arguments (case for an *array* of instances).
    instance = (instance == null) ? _reg.getInstance(DOMAIN_cls) : instance;
    if (instance == null) // no instance was found!
      return;
    //System.out.printf("*** Triple for <%s> : (D: %s) \"%s\" (R: %s)\n", dpURI, DOMAIN_cls, value, xsd);
    System.out.printf("*** (s,p,o): (<%s>, <%s>, \"%s\")\n", instance, dpURI, value);
    setGraph();
    Graph.addLiteral(instance, dpURI, value, lang, xsd); // lang:en (rdfs:Literal)
    if (_JSON_path.getCurrentPath().generateRDFSlabel()) {
      System.out.printf("*** (s, rdfs:label, o): (<%s>, rdfs:label, \"%s\"@en)\n", instance, value);
      Graph.addLiteral(instance, Builder._onto_label, value, "en", null); // rdfs:label
    }
  }

  private void createObjectPropertyTriple(String s, String p, String o) {
    System.out.printf("*** (s,p,o): (<%s>, <%s>, <%s>)\n", s, p, o);
    if ((s != null) && (o != null)) {
      setGraph();
      Graph.addObject(s, p, o);
    }
  }
  
  private Object[] getInstancesForOPcreation
    (JSONObject objMapping, String classURI, String classLocalName, String classID, String mapToAncestor) {
    Object c   = null; Object i = null;
    Object[] r = new Object[2];
    if ((classID.equals(JSON_Path.DOC_ID_SYMBOL)) || (objMapping == null)) { 
      i = _reg.getInstances(classLocalName); // possibly an array of instances.  It could be (null).
      if ((i != null) && (!(i instanceof JSONArray))) c = i.toString(); // one value
    } else {
      c = _reg.getInstance_s(classURI, classLocalName, objMapping, mapToAncestor);
      if (c instanceof String)    i = null; // single instance
      if (c instanceof JSONArray) { // multiple instances
        i = c;
        c = null;
      }
      // /*- BEFORE /*- c = _reg.getInstance(classURI, classLocalName, objMapping.get(classID).toString(), processFlagIDfromString); // a single instance
    }
    r[0] = c; // a single instance
    r[1] = i; // an array of instances
    return r;
  }
  
  /*private boolean chkCompositeID(boolean isMultiPartJSON_Path, String compositeID, String atomicID, String atomicIDclassURI) {
    // if atomicID is in compositeID:
    return ( (isMultiPartJSON_Path) && (compositeID.contains( atomicID.substring(atomicIDclassURI.length()) )) );
  }*/
  
  private void connectSubjectsToObjects(String p,
    boolean DomainMultiPartJSON_Path, String s,
    boolean RangeMultiPartJSON_Path , String o) {
    if ( (DomainMultiPartJSON_Path) && (RangeMultiPartJSON_Path) ) { // not implemented... (yet...)
      return; // exit
    }
    if (DomainMultiPartJSON_Path) { // it's a composite ID: <id1>+<id2>...
      if (s.contains( o.substring(RangeClassURI.length()+1) )) // if ID of o (without hyphen) is in ID of s:
        createObjectPropertyTriple(s, p, o);
      else return; // skip: next
    } // if (DomainMultiPartJSON_Path)
    else if (RangeMultiPartJSON_Path) { // it's a composite ID: <id1>+<id2>...
      if (o.contains( s.substring(DomainClassURI.length()+1) )) // if ID of s (without hyphen) is in ID of o:
        createObjectPropertyTriple(s, p, o);
      else return; // skip: next
    } // if (RangeMultiPartJSON_Path)
    // else...
    createObjectPropertyTriple(s, p, o);
  }
  
  private String chkMatchingWithValues( JSON_Path single_Map, JSONObject single_obj,
                                        JSON_Path arr_Map, String arr_ClassURI, String arr_ClassLocalName) {
    String matchedValue_turnedIntoURI = null;
    if (single_Map.useMappedValues()) { // "^" flag in the "single-value" path
      if (! arr_Map.isTargetInMappedValues(single_obj.get(single_Map.getKey()).toString()))
        return null; // the value was not found ==> no matching;
      JSON_Path arr_Instances = _reg.getJSON_PathFromCache(arr_ClassLocalName); 
      // there are several cases to implement...
      if (arr_Instances.isLastStepJSONArray() && arr_Instances.generateIDfromString()) // class mapping: ../../<array>!
        matchedValue_turnedIntoURI = _reg.generateInstanceURI(arr_ClassURI, 
          single_obj.get(single_Map.getKey()).toString(), JSON_Path.GENERATE_ID_FLAG);
      else ; /*--*/ // many other cases that are not implemented yet!
    } // if ("^")
    return matchedValue_turnedIntoURI;
  }
  
  private void oneToMany( String p, boolean DomainMultiPartJSON_Path, boolean RangeMultiPartJSON_Path, boolean isOneSubject,
                          String one,    JSON_Path one_Map, JSONObject one_obj,
                          Object[] many, JSON_Path many_Map, String many_ClassURI, String many_ClassLocalName) {
    String matchedValue_turnedIntoURI = chkMatchingWithValues(one_Map, one_obj, many_Map, many_ClassURI, many_ClassLocalName);
    for (Object v : (JSONArray) many[1]) {
      if (one_Map.useMappedValues()) {
        if ((matchedValue_turnedIntoURI != null) && v.toString().equals(matchedValue_turnedIntoURI)) // "^" flag in the "single/one" path/map
          connectSubjectsToObjects(p,
            DomainMultiPartJSON_Path, ( isOneSubject) ? one : v.toString(),
            RangeMultiPartJSON_Path , (!isOneSubject) ? one : v.toString());
      } // if (useMappedValues())
      else
        connectSubjectsToObjects(p,
            DomainMultiPartJSON_Path, ( isOneSubject) ? one : v.toString(),
            RangeMultiPartJSON_Path , (!isOneSubject) ? one : v.toString());
    } // for
  }

  private void addObjectProperty(String p,
    JSONObject DomainObjMapping, String DomainClassID,
    JSONObject RangeObjMapping , String RangeClassID) {
    String s = null, o = null;
    Object[] s_i, o_i;
    boolean DomainMultiPartJSON_Path = _reg.isMultiPartJSON_PathFromCache(DomainClassLocalName);
    boolean RangeMultiPartJSON_Path  = _reg.isMultiPartJSON_PathFromCache(RangeClassLocalName ); 
    if (DomainMultiPartJSON_Path) DomainObjMapping = null;
    // **RangeObjMapping** has the object(s) to mapped for the range. 
    s_i = getInstancesForOPcreation(DomainObjMapping, DomainClassURI, DomainClassLocalName, DomainClassID, _domainPath.getCurrentPath().getMapForAncestorChk());
    o_i = getInstancesForOPcreation(RangeObjMapping , RangeClassURI , RangeClassLocalName , RangeClassID , _rangePath .getCurrentPath().getMapForAncestorChk());
    //System.out.printf("<addObjectProperty()>: Property=<%s>\n", p);
    //System.out.printf("<addObjectProperty()>: Domain instances \"%s\" | [%s]\n", s_i[0], s_i[1]);
    //System.out.printf("<addObjectProperty()>: Range  instances \"%s\" | [%s]\n", o_i[0], o_i[1]);
    if (((s_i[0] == null) && (s_i[1] == null)) || // the domain instances were not found
        ((o_i[0] == null) && (o_i[1] == null)) )  // the range instances were not found
      return; // do nothing.
    s = (String) s_i[0]; // it could be null
    o = (String) o_i[0]; // it could be null
    if ((s == null) && (s_i[1] != null)) // multiple subjects
    if ((o == null) && (o_i[1] != null)) // multiple objects
        for (Object v : (JSONArray) s_i[1])
        for (Object w : (JSONArray) o_i[1]) {
          // need to implement the cases for "^" flag mappings!
          connectSubjectsToObjects(p,
            DomainMultiPartJSON_Path, v.toString(),
            RangeMultiPartJSON_Path , w.toString());
        } // for
      else // multiple subjects with single object:
        oneToMany(p, DomainMultiPartJSON_Path, RangeMultiPartJSON_Path, false, 
              o  , _rangePath, RangeObjMapping, 
              s_i, _domainPath, DomainClassURI, DomainClassLocalName);
    else // single subject...
      if ((o == null) && (o_i[1] != null)) // ... with multiple objects
        oneToMany(p, DomainMultiPartJSON_Path, RangeMultiPartJSON_Path, true, 
              s  , _domainPath, DomainObjMapping, 
              o_i, _rangePath, RangeClassURI, RangeClassLocalName);
      else { // single subject with single object:
        if (_rangePath.useMappedValues()) { // "^" flag in the range path
          // we compare the value of the current *range* mapped object with the value of the current *domain* mapped object:
          String rangeValue = 
            (RangeObjMapping .get(_rangePath  .getKey()) instanceof JSONArray) ? 
             RangeObjMapping .getJSONArray(_rangePath .getKey()).get(0).toString() : RangeObjMapping .getString(_rangePath .getKey());
          String domainValue = 
            (DomainObjMapping.get(_domainPath.getKey()) instanceof JSONArray) ? 
             DomainObjMapping.getJSONArray(_domainPath.getKey()).get(0).toString() : DomainObjMapping.getString(_domainPath.getKey());
          if (! Builder.stringMatching(rangeValue, domainValue))
            return; // values don't match ==> do nothing;
        }
        connectSubjectsToObjects(p, false, s, false, o); // single subject with single object
      } // else
  }

  private boolean processDP_JSONArrayValues(String instance, String URI, String xsdURItype) {
    if (_JSON_path.getCurrentPath().isLastStepJSONArray()) {
      for (Object v : _JSON_path.getCurrentPath().getLastStepJSONArray()) { // process values from the pointed JSONArray: an array of values...
        //System.out.printf("%s\n", v);
        addDatatypeProperty(DomainClassLocalName, instance, URI, v.toString(), xsdURItype, true);
      } // for
      return true;
    }
    return false;
  }

  private void processDatatypeProperty(String URI, String xsdURItype) {
    String instance = null;
    String value = null;
    boolean process = true; // by default: process "normal" paths

    String JSON_strDelimiter = _JSON_path.getCurrentPath().getStringDelimiter();
    if (JSON_strDelimiter != null) {
      value = _JSON_path.getCurrentPath().getPointerToObject().toString();
      System.out.printf("Delimiter=\"%s\", String-Value=\"%s\"\n", JSON_strDelimiter, value);
      String tokens[] = value.split(JSON_strDelimiter.replace("|", "\\|")); // beware of RegEx meta-characters!
      for (String token : tokens)
        addDatatypeProperty(DomainClassLocalName, null, URI, token, xsdURItype, false); // it's a singleton!
      return; // next path
    } // if (JSON_strDelimiter != null)
    
    if (_JSON_path.isFORmapping()) {
      for (JSONObject o : _JSON_path.getFORmappingPath().getMappedObjects()) { // process JSONObjects
        //System.out.printf("%s\n", o.toString());
        instance = (String) _reg.getInstance_s(DomainClassURI, DomainClassLocalName, o,
          _JSON_path.getMapForAncestorChk()); // looks for the specific JSONObject in the *cache*
        if (processDP_JSONArrayValues(instance, URI, xsdURItype)) // processing of values from a JSONArray
          continue; // skip to the next JSONObject
        else
          for (String v : _JSON_path.getCurrentPath().getMappedValues()) { // process values from the mapped set of values...
            //System.out.printf("%s\n", v);
            addDatatypeProperty(DomainClassLocalName, instance, URI, v, xsdURItype, true);
          } // for (mapped values)
      } // for (FOR mapped objects)
      return;
    } // isFORmapping()
    
    else if (_JSON_path.isWHEREcondition()) {
      process = _JSON_path.isWHEREpathSuccessful(); // process WHERE path?
      //System.out.printf("<WHERE path: ?(%s)> \n", process);
    } // isWHEREcondition()
    
    else if (_JSON_path.isOR()) {
      for (int i = 0; i < _JSON_path.getPathsLength(); i++) {
        //System.out.printf("<processClassFromDomainForDP()> value_%s=%s\n", i, _JSON_path.getPath(i).getPointerToObject());
      }
      //System.out.printf("\n");
      return; // next path
    } // isOR()

    if (process) {
      //System.out.printf("<getMappedObjs()>:\n");
      if (_JSON_path.getCurrentPath().getKey() != null) // only if we have key for the current path...
        for (JSONObject o : _JSON_path.getCurrentPath().getMappedObjects()) { // process JSONObjects
          //System.out.printf("%s\n", o.toString());
          instance = (String) _reg.getInstance_s(DomainClassURI, DomainClassLocalName, o,
            _JSON_path.getCurrentPath().getMapForAncestorChk()); // looks for the specific JSONObject in the *cache*
          if (_JSON_path.getCurrentPath().isBoolean())
               value = String.valueOf(_JSON_path.getCurrentPath().evaluateWHEREcondition(o.get(_JSON_path.getCurrentPath().getKey()).toString()));
          else {
            if (processDP_JSONArrayValues(instance, URI, xsdURItype)) // processing of values from a JSONArray
              continue; // skip to the next JSONObject
            else
              value = o.get(_JSON_path.getCurrentPath().getKey()).toString();
          }
          addDatatypeProperty(DomainClassLocalName, instance, URI, value, xsdURItype, true);
        } // for
      if ( (_JSON_path.getCurrentPath().getMappedObjects().size() == 0) && // only when we don't have any JSONObjects
           (_JSON_path.getCurrentPath().getMappedValues().size () >  0) ) {
        if (! processDP_JSONArrayValues(null, URI, xsdURItype))
          for (String v : _JSON_path.getCurrentPath().getMappedValues()) { // process values
            //System.out.printf("%s\n", v);
            if (_JSON_path.getCurrentPath().isBoolean())
              v = String.valueOf(_JSON_path.getCurrentPath().evaluateWHEREcondition());
            addDatatypeProperty(DomainClassLocalName, null, URI, v, xsdURItype, true);
          } // for
      }
      if (_JSON_path.isWHEREcondition()) // makes sure to process only the first component of the path.
          _JSON_path.finalizePathProcessing();
    } // if (process)
  }

  private void processObjectProperty(String URI) {
    String DomainClassID = _reg.getClassID(DomainClassLocalName);
    String RangeClassID  = _reg.getClassID(RangeClassLocalName );
    if (DomainClassID == null) {
      //System.out.printf("<processObjectProperty()> DomainClassID null for class %s\n", DomainClassLocalName);
      return;
    }
    if (RangeClassID == null) {
      //System.out.printf("<processObjectProperty()> RangeClassID null for class %s\n", RangeClassLocalName);
      return;
    }
    Object[] chkFlagForDomainClassID = _reg.checksFlag(DomainClassID);
    Object[] chkFlagForRangeClassID  = _reg.checksFlag(RangeClassID );
    DomainClassID = chkFlagForDomainClassID[0].toString();
    RangeClassID  = chkFlagForRangeClassID [0].toString();
    if (_domainPath == _rangePath) { // a simple path that connects subjects with objects
      for (JSONObject o : _JSON_path.getCurrentPath().getMappedObjects()) { // process JSONObjects
        //System.out.printf("%s\n", o.toString());
        addObjectProperty(URI, (JSONObject) o, DomainClassID, (JSONObject) o, RangeClassID); // Mapping to a JSONObject structure
      } // for
      return;    
    }
    if (_domainPath.getMappedObjects().size() > 0) {
      for (JSONObject d : _domainPath.getMappedObjects()) // Cartesian Product: domain x range
      for (JSONObject r : _rangePath .getMappedObjects()) {
        if (_rangePath.hasConstant()) {
          if (_rangePath.evaluateWHEREcondition(r.get(_rangePath.getKey()).toString()))
            addObjectProperty(URI, d, DomainClassID, r, RangeClassID);
        } else
            addObjectProperty(URI, d, DomainClassID, r, RangeClassID);
      } // for (r)
    } // if (|D| > 0)
    else {
      for (JSONObject r : _rangePath.getMappedObjects()) {
        if (_rangePath.hasConstant()) {
          if (_rangePath.evaluateWHEREcondition(r.get(_rangePath.getKey()).toString()))
            addObjectProperty(URI, null, DomainClassID, r, RangeClassID);
        } else
            addObjectProperty(URI, null, DomainClassID, r, RangeClassID);
      } // for (r)
    } // else
  }
  
  // get the set of all possible classes to process the triple creation:
  private Set<OntClass> getAllClasses(String propURI, OntClass _class, boolean fromDomain) {
    Set<OntClass> classes = new HashSet<OntClass>();
    if (_class != null) {
      classes.add(_class); // adds the single class
      classes = addSubClassHierarchy(classes, _class); // applies for all its subclasses
      classes = addClassesfromUNIONoperands(classes, _class); // checks if the class is a UNION class expression: (A or B or C or...)
    }
    classes = addClassesfromSuperProperties(classes, propURI, fromDomain);
    return classes;
  }
  
  // for each Domain found + for each (potential) Range:
  private void traverseObjectPropertyPaths(String URI, OntResource range) {
    boolean forSpecificRange = (_JSON_path.getCurrentPath().hasOnlyFor()) ?
      (_JSON_path.getCurrentPath().getOnlyFor().equals(range.getLocalName())) : true; // no "-><only_for>" in the path
    if (!forSpecificRange)
      return; // do not process if { -><only_for> } checking return false. 
    Set<OntClass> rangeClasses = getAllClasses(URI, range.asClass(), false); // get all range classes
    if (_JSON_path.isOP()) { // traversing JSON paths for object properties (D* | R*)
      //System.out.printf("Traversing the JSON paths for object properties: \n");
      _JSON_path.initDomainPathsPointer();
      boolean domainFound = false;
      do { // look for the *current* domain JSON Path:
        //System.out.printf("Looking domain: %s ==> %s", DomainClassLocalName, _JSON_path.getDomainPath().print(false));
        domainFound = (mapsToJSONforSpecifiedClass(DomainClassLocalName, _JSON_path.getDomainPath()));
            if (!domainFound)     _JSON_path.nextDomainPath();
      } while ((!domainFound) && (_JSON_path.getDomainPath() != null));
      if (domainFound) {
        //System.out.printf("Domain to process: %s", _JSON_path.getDomainPath().print(false));
        _domainPath = _JSON_path.getDomainPath();
        _JSON_path.initRangePathsPointer();
        //System.out.printf("Processing range:\n");
        while (_JSON_path.getRangePath() != null) { // for each range...
          //_JSON_path.getRangePath().print(true);
          for (OntClass rangeClass : rangeClasses) {
            //System.out.printf("%s\n", rangeClass.getLocalName());
            rangeClass = getClassInRange(rangeClass, _JSON_path.getRangePath().getEntity());
            if (rangeClass != null) {
              //System.out.printf("[%s] found in range... processing...\n", RangeClass.getLocalName());
              _rangePath  = _JSON_path.getRangePath();
              RangeClassURI = rangeClass.getURI();
              RangeClassLocalName = rangeClass.getLocalName();  
              processObjectProperty(URI);  
            } // if
          } // for
          _JSON_path.nextRangePath();
        } // while
      } // if
    } // if (_JSON_path.isOP())
    else {
      if (_JSON_path.isJSON_equal()) { // operator "|=|"
        _domainPath = _JSON_path.getDomainPath(); // last path
        _rangePath  = _JSON_path.getRangePath();  // first path
      }
      else // a simple JSON path:
        _domainPath = _rangePath = _JSON_path.getCurrentPath();  
      if (rangeClasses.size() > 0) {
        //System.out.printf("Found Classes:\n");
        for (OntClass rangeClass : rangeClasses) {
          //System.out.printf("%s\n", rangeClass.getLocalName());
          RangeClassURI = rangeClass.getURI();
          RangeClassLocalName = rangeClass.getLocalName();
          processObjectProperty(URI);
        } // for
        //System.out.printf("\n");
        return;
      } else {
        RangeClassURI = range.getURI();
        RangeClassLocalName = range.getLocalName();
        processObjectProperty(URI);
      }
    } // else ( if (_JSON_path.isOP()) )
  }
  
  private void processObjectPropertyRange(OntProperty prop) {
    String URI = prop.getURI();
    ExtendedIterator<? extends OntResource> rangeClassesIterator = prop.listRange();
    int r = 0;
    while (rangeClassesIterator.hasNext()) { // processing each range defined in the property
      traverseObjectPropertyPaths(URI, rangeClassesIterator.next());
      ++r;
    } // while
    if (r == 0) { // no range was found:
      Set<OntClass> rangeClassesSet = new HashSet<OntClass>();
      rangeClassesSet = addClassesfromSuperProperties(rangeClassesSet, URI, false); // look for the range of superproperties
      for (OntClass range : rangeClassesSet)
        traverseObjectPropertyPaths(URI, range);
    } // if
  }

  private boolean processLocalDomainConstraints(OntProperty prop) {
    DatatypeProperty dp = (prop instanceof DatatypeProperty) ? prop.asDatatypeProperty() : null;
    ObjectProperty   op = (prop instanceof ObjectProperty  ) ? prop.asObjectProperty()   : null;
    if (prop.getDomain() == null) { // local domain constraints:
      //System.out.printf("Local domain constraint.  Processing for each class restriction:\n");
      Set<OntClass> foundClasses = getClassesWherePropertyIsPartOfRestriction(prop);
      boolean found = false;
      for (OntClass c : foundClasses) {
        //System.out.printf("%s\n", c.getLocalName());
        found = (_JSON_path.getCurrentPath().hasEntity()) ?
                (_JSON_path.getCurrentPath().getEntity().equals(c.getLocalName())) : true; // no "@<entity>" in the path
        if (found) {
          DomainClassURI = c.getURI();
          DomainClassLocalName = c.getLocalName();
          if (dp != null) processDatatypeProperty(prop.getURI(), dp.getRange().getURI());
          if (op != null) processObjectPropertyRange(prop);
        } // if (found)
      }
      //System.out.printf("\n");
      return true;
    }
    return false;
  }

  private void processGlobalDomainConstraints(OntProperty prop) { // global scope constraints:
    String URI = prop.getURI();
    DatatypeProperty dp = (prop instanceof DatatypeProperty) ? prop.asDatatypeProperty() : null;
    ObjectProperty   op = (prop instanceof ObjectProperty  ) ? prop.asObjectProperty()   : null;
    Set<OntClass> foundClasses = getAllClasses(URI, prop.getDomain().asClass(), true); // get all domain classes

    if (foundClasses.size() > 0) {
      //System.out.printf("Found Classes:\n");
      boolean found = false;
      for (OntClass cls : foundClasses) {
        //System.out.printf("%s\n", cls.getLocalName());
        found = (_JSON_path.getCurrentPath().hasEntity()) ?
                (_JSON_path.getCurrentPath().getEntity().equals(cls.getLocalName())) : true; // no "@<entity>" in the path
        if (found) {
          DomainClassURI = cls.getURI();
          DomainClassLocalName = cls.getLocalName();
          if (dp != null)
            if (mapsToJSONforSpecifiedClass(DomainClassLocalName, _JSON_path.getCurrentPath()))
            processDatatypeProperty(URI, dp.getRange().getURI());
          if (op != null) processObjectPropertyRange(prop);
        } // if (found)
      } // for
      //System.out.printf("\n");
    }
    else { // the domain is a single class:
      DomainClassURI = dp.getDomain().getURI();
      DomainClassLocalName = dp.getDomain().getLocalName();
      if (dp != null) processDatatypeProperty(URI, dp.getRange().getURI());
      if (op != null) {
        DomainClassURI = op.getDomain().getURI();
        DomainClassLocalName = op.getDomain().getLocalName();
        processObjectPropertyRange(prop);
      } // if (op != null)
    } // else
  }

  private OntClass getClassInRange(OntClass range, String classLocalName) {
    if (range.asClass().isUnionClass()) {
      return getClassInUNIONoperands(range, classLocalName);
    }
    // case where there's a single range:
    if ((classLocalName != null) && (range.getLocalName().equals(classLocalName)))
      return range;
    if (classLocalName == null) // no <entity> in <path>@<entity> specified
      return range;
    return null;
    /********************************************************************************
    // Checks if the range is a UNION class expression: (A or B or C or...)
    Set<OntClass> foundRangeClasses = null;
    foundRangeClasses = addClassesfromUNIONoperands(foundRangeClasses, range.asClass());
    if (foundRangeClasses.size() > 0) {
      System.out.printf("Found Classes:\n");
      for (OntClass rangeClass : foundRangeClasses) {
        System.out.printf("%s\n", rangeClass.getLocalName());
      } // for
      System.out.printf("\n");
      return;
    }
    *********************************************************************************/  
  }

  private OntClass getClassInUNIONoperands(OntClass UNION, String classLocalName) {
    //System.out.printf("<isClassInUNIONoperands(%s)>:\n", classLocalName);
    if (UNION.isUnionClass()) {
      Iterator<?> classOperands = UNION.asUnionClass().listOperands();
      while (classOperands.hasNext()) {
        OntClass cls = (OntClass) classOperands.next();
        if (cls.getLocalName().equals(classLocalName))
          return cls;
      }
    }
    return null;
  }

  private Set<OntClass> addClassesfromUNIONoperands(Set<OntClass> foundClasses, OntClass UNION) {
    //System.out.printf("<addClassesfromUNIONoperands()>: a UNION class expression.  Processing for each class operand:\n");
    if (UNION == null) return null;
    foundClasses = (foundClasses == null) ? new HashSet<OntClass>() : foundClasses;
    if (UNION.isUnionClass()) {
      Iterator<?> classOperands = UNION.asUnionClass().listOperands();
      //System.out.printf("Found Classes:\n");
      while (classOperands.hasNext()) {
        OntClass cls = (OntClass) classOperands.next();
        foundClasses.add(cls);
        //System.out.printf("%s\n", cls.getLocalName());
      }
      //System.out.printf("\n");
    }
    return foundClasses;
  }
  
  private Set<OntClass> addClassesfromSuperProperties(Set<OntClass> foundClasses, String prop_URI, boolean fromDomain) {
    //System.out.printf("<addRangeClassesfromSuperProperties()>:\n");
    foundClasses = (foundClasses == null) ? new HashSet<OntClass>() : foundClasses;
    OntProperty prop = _model.getOntProperty(prop_URI);
    Iterator<? extends OntProperty> superProps = prop.listSuperProperties();
    //System.out.printf("Found classes:\n");
    while (superProps.hasNext()) {
      OntProperty p = superProps.next();
      OntClass cls = 
        ( fromDomain && (p.getDomain() != null)) ? p.getDomain().asClass() :
        (!fromDomain && (p.getRange () != null)) ? p.getRange() .asClass() : null;
      if (cls != null) {
        foundClasses.add(cls);
        foundClasses = addSubClassHierarchy(foundClasses, cls); // applies for all its subclasses
      }
      //System.out.printf("%s\n", cls.getLocalName());
    } // while
    //System.out.printf("\n");
    return foundClasses;
  }

  private boolean isRestrictionInSuperProperties(OntProperty prop, Restriction rstr) {
    Iterator<? extends OntProperty> superProps = prop.listSuperProperties();
    while (superProps.hasNext()) {
      OntProperty p = superProps.next();
      if (rstr.getOnProperty().equals(p))
        return true;
    } // while
    return false;
  }
  
  private Set<OntClass> addSubClassHierarchy(Set<OntClass> foundClasses, OntClass cls) {
    Iterator<OntClass> subClasses = cls.listSubClasses(); // for all subclasses
    while (subClasses.hasNext()) {
      OntClass subClass = subClasses.next();
      foundClasses.add(subClass);
      foundClasses = addSubClassHierarchy(foundClasses, subClass); // traverse the sub class hierarchy
    } // while (subclasses)
    return foundClasses;
  }

  @SuppressWarnings("unlikely-arg-type")
  private Set<OntClass> getClassesWherePropertyIsPartOfRestriction(OntProperty prop) {
    //System.out.printf("<getClassesWherePropertyIsPartOfRestriction(%s)>: List of restrictions\n", prop.getLocalName());
    if (_clsRstr.containsValue(prop)) {
      System.out.printf("The property has been already processed!\n");
      return _clsRstr.get(prop); // if the property has been already processed.
    }
    Set<OntClass> foundClasses = new HashSet<OntClass>();
    Iterator<OntClass> classesList = _model.listClasses();
    while (classesList.hasNext()) {
      OntClass cls = classesList.next();
      Iterator<OntClass> superClasses = cls.listSuperClasses();
      while (superClasses.hasNext()) {
        OntClass superClass = superClasses.next();
        if (superClass.isRestriction()) {
          Restriction rstr = superClass.asRestriction();
          if ((rstr.getOnProperty().equals(prop)) || (isRestrictionInSuperProperties(prop, rstr))) {
            foundClasses.add(cls);
            foundClasses = addSubClassHierarchy(foundClasses, cls); // we add this restriction for all its subclasses
          }
          /*if (rstr.isAllValuesFromRestriction()) {
            System.out.printf("<AllValuesFrom>, \n");
          } else
          if (rstr.isCardinalityRestriction()) {
            System.out.printf("<Cardinality> %s exactly %s, \n",
            rstr.asCardinalityRestriction().getOnProperty(),
            rstr.asCardinalityRestriction().getCardinality());
          } else
          if (rstr.isHasValueRestriction()) {
            System.out.printf("<HasValue>, \n");
          } else
          if (rstr.isSomeValuesFromRestriction()) {
            System.out.printf("<SomeValuesFrom>, \n");
          } else
          if (rstr.isMaxCardinalityRestriction()) {
            System.out.printf("<MaxCardinality> %s max %s, \n", 
            rstr.asMaxCardinalityRestriction().getOnProperty(), 
            rstr.asMaxCardinalityRestriction().getMaxCardinality());
          } else
          if (rstr.isMinCardinalityRestriction()) {
            System.out.printf("<MinCardinality> %s min %s, \n", 
            rstr.asMinCardinalityRestriction().getOnProperty(),
            rstr.asMinCardinalityRestriction().getMinCardinality());
          } else {
            //System.out.printf("<%s> %s ?, \n", rstr.getClass().getCanonicalName(), rstr.getOnProperty());
          }*/
        } // if (isRestriction)
      } // while (super classes)
    }
    _clsRstr.put(prop, foundClasses); // adds the class set to the data structure
    return foundClasses;
  }
  
  private void initPropertyVariables() {
    _domainPath = _rangePath = null;
    DomainClassURI = DomainClassLocalName = null;
    RangeClassURI  = RangeClassLocalName  = null;
  }

  private boolean mapsToJSONforSpecifiedClass(String classLocalName, JSON_Path currentJSON_Path) {
    //System.out.printf("isDomain()=%s, isEmpty()=%s\n", currentJSON_Path.isDomain(), currentJSON_Path.isEmpty());
    if (currentJSON_Path.isDomain() && currentJSON_Path.isEmpty())
      return true; // case of "D=<empty>"
    if (currentJSON_Path.hasEntity())
      return (currentJSON_Path.getEntity().equals(classLocalName));
    return false;
  }

  private void mappingStatement(Statement stmMapping) {
    _JSON_mapping_value = stmMapping.getLiteral().toString(); // _onto_resource.getProperty(_JSON_mapping).getLiteral().toString();
    //System.out.printf("<mapsToJSON() | JSON-mapping> %s:\n", _JSON_mapping_value);
    _JSON_path = new JSON_Path(_doc, _JSON_mapping_value, 0);
    _JSON_path.process();
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void generateTriplesFor(Class classType) {
    System.out.printf("\n\n* Generate triples for {%s} entities in the ontology:\n", classType.toString());
    Iterator<?> rscIterator = null, mappingsIterator = null;
    if (classType.isAssignableFrom(OntClass          .class)) rscIterator = _model.listClasses();
    if (classType.isAssignableFrom(ObjectProperty    .class)) rscIterator = _model.listObjectProperties();
    if (classType.isAssignableFrom(DatatypeProperty  .class)) rscIterator = _model.listDatatypeProperties();
    if (classType.isAssignableFrom(AnnotationProperty.class)) rscIterator = _model.listAnnotationProperties();
    if (classType.isAssignableFrom(Individual        .class)) rscIterator = _model.listIndividuals();
    String URI;
    Set<String> mappedVals;
    while (rscIterator.hasNext()) {
      _onto_resource = (Resource) rscIterator.next(); // ontology resource (concept) to process
      URI = _onto_resource.getURI();
      
      // for each annotation mappings on the resource:
      mappingsIterator = _onto_resource.listProperties(_JSON_mapping); // (s, <JSON-mapping>, o) annotations from the ontology definition
      while (mappingsIterator.hasNext()) {
        mappingStatement((Statement) mappingsIterator.next());
        
        if ((_JSON_path.isMultiLine()) && !(_JSON_path.isOP()))
          _JSON_path.initPathPointer(); // multiline path processing: multiple path processing
        while (_JSON_path.getCurrentPath() != null) { // tries to perform multiple path processing
          if (_JSON_path.getCurrentPath().wasParsingSuccessful()) { // process the current path
            //System.out.printf("--> processing path\n");
            //_JSON_path.getCurrentPath().print(true);

            if (classType.isAssignableFrom(OntClass.class)) {
              _reg.addClass(_onto_resource.getLocalName(), _JSON_path); // adds (class + json_mappings) to the cache
              //_reg.print('$');
              //System.out.printf("<getMappedValues()>:\n");
              mappedVals = (_JSON_path.isMultiPart()) ?
                _JSON_path.MP_getMappedPairedValues() :
                _JSON_path.getCurrentPath().getMappedValues();
              for (String v : mappedVals) {
                //System.out.printf("%s\n", v);
                addIndividual(_onto_resource.getLocalName(), URI, v);
              } // for
            } // if (OntClass)

            if ((classType.isAssignableFrom(ObjectProperty.class)) || 
                (classType.isAssignableFrom(DatatypeProperty.class))) {
              initPropertyVariables();
              if (!processLocalDomainConstraints(_onto_resource.as(OntProperty.class)))
                  processGlobalDomainConstraints(_onto_resource.as(OntProperty.class));
            } // if (Property)
            
            if (classType.isAssignableFrom(AnnotationProperty.class)) { // dc:title --> <path>@<entity>
              DomainClassLocalName = (_JSON_path.getCurrentPath().hasEntity()) ? _JSON_path.getCurrentPath().getEntity() : null;
              DomainClassURI = Builder._onto_g0_ns + DomainClassLocalName;
              if (DomainClassLocalName != null)
                processDatatypeProperty(URI, null); // rdfs:Literal
            } // if (OntClass)

            if (classType.isAssignableFrom(Individual.class)) {
              _reg.addIndividual(_onto_resource.getLocalName(), _JSON_path.getCurrentPath().getConstant());
              //_reg.print('.');
              //System.out.printf("Individual: <%s>=\"%s\"\n", URI, _JSON_path.getCurrentPath().getConstant());
            } // if (Individual)
            
            if (_JSON_path.isJSON_equal() || _JSON_path.isFORmapping()) // makes sure to process only once.
              _JSON_path.finalizePathProcessing();

          } // if (_JSON_path.getCurrentPath().wasParsingSuccessful())
          _JSON_path.nextPath();
        } // while of multiple path processing

      } // while (mappingIterator)
    } // while (rscIterator.hasNext())
  }

  /******************************************************************************************************************************/
  public DomainOntologyGraph() {
    Builder.init();

    // Settings: load the ontology file and creating the model in memory.
    _onto = ConfigJSON._json.getJSONObject(Builder._exec);
    if (!_onto.has("onto")) // if no domain-ontology file...
      return;

    InputStream in = FileManager.get().open(_onto.getString("onto"));
    if (in == null) {
      //System.out.println("[DomainOntologyGraph.DomainOntologyGraph()] Ontology File: " + _onto.getString("onto") + " not found!");
      _model = null;
      return;
      //throw new IllegalArgumentException("Ontology File: " + _onto.getString("onto") + " not found!");
    }
    _model = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
    _model.read(in, "");
    _JSON_mapping = _model.getOntProperty(Builder._onto.getString(JSON_MAPPING));
    _graph_scope  = _model.getOntProperty(Builder._onto.getString(GRAPH_SCOPE));
    _named_graph  = _model.getOntProperty(Builder._onto.getString(NAMED_GRAPH));
    _clsRstr = new HashMap<>();
    _onto_resource = null;
    
    // Parse RDF/XML ontology file:
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      in = FileManager.get().open(_onto.getString("onto"));
      _xmlonto = builder.parse(in);
    } catch (Exception e) {
      System.out.println("Exception at DomainOntologyGraph.DomainOntologyGraph(): " + e.toString());
    }
  }

  public DomainOntologyGraph(JSONObject doc, String instance_doc) {
    this();
    _doc = doc; // complete JSON Object metadata.
    _id = (instance_doc == null) ? _doc.getString("_id") : instance_doc; // document ID.
    _reg = new DOG_Registry();
  }
  
  public boolean hasOntologyDefinition() { return (_model != null); }
  public boolean canBuild_g0()           { return (hasOntologyDefinition() && hasAssociatedMetadata()); }
  public boolean hasAssociatedMetadata() {
    if (_doc.has(METADATA_SOURCE)) {
      Object am = _doc.get(METADATA_SOURCE);
      if (am instanceof JSONObject) return (!((JSONObject) am).isEmpty());
      if (am instanceof JSONArray)  return (!((JSONArray)  am).isEmpty());
      return (!_doc.isNull(METADATA_SOURCE));
    }
    return false;
  }
  
  public String  getInstance(String classLocalName) { return _reg.getInstance(classLocalName); }

  public void build_g0() {
    if (canBuild_g0()) {
      generateTriplesFor(OntClass.class); // store IDs in registry
      generateTriplesFor(Individual.class); // store constants in registry
      generateTriplesFor(ObjectProperty.class);
      generateTriplesFor(DatatypeProperty.class);
      generateTriplesFor(AnnotationProperty.class);
      //_reg.print();
      System.out.println("");
    }
  }

  /******************************************************************************************************************************/
  /**
   * @param args
   */
  public static void main(String[] args) {
    try {
      String content = new String(Files.readAllBytes(Paths.get("NHMRC-JSON-metadata-example (Program 1).json")));
      String instance_doc = null; 
      // "http://linked.data.gov.au/dataset/environment/assessment#DigitalArtefact-1b9d05b641c93131c24be9077be2fd61"; 
      // example for Assessments: "http://linked.data.gov.au/dataset/environment/assessment#DigitalArtefact-1b9d05b641c93131c24be9077be2fd61";
      JSONObject doc = new JSONObject(content);
      DomainOntologyGraph DOG = new DomainOntologyGraph(doc, instance_doc);
      DOG.build_g0();
    } catch (Exception e) {
      System.out.println(e);
    }
  } // main
} // DomainOntologyGraph
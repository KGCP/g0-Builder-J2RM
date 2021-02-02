/**
 * 
 */
package au.edu.anu.cecs.rscs.agrif_project;

import java.util.Queue;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;


/**
 * @name Graph-0-Builder / JSON_Path.
 * @author Sergio.
 * @purpose Represents a JSON path structure.
 * @project AGRIF.
 * Inspiration from JSON Pointer RFC: <https://tools.ietf.org/html/rfc6901>
 */
public class JSON_Path {

  public static char GENERATE_ID_FLAG = '!';
  public static char USE_ID_FLAG = '#';
  public static char RDFS_LABEL_FLAG = '~';
  public static char MAPPING_TO_VALUES_FLAG = '^';
  public static char MAP_TO_ANCESTOR_FLAG = '<';
  public static char END_DELIMITER_FLAG = ')';
  public static char POINTS_TO_CONSTANT_FLAG = ':';
  
  public static String DOC_ID_SYMBOL    = "$DOC_ID$";
  public static String _AND_OPERATOR    = "&&";   // implemented in conditional paths: <path>%<where_path>; <where_path>.
  public static String _OR_OPERATOR     = " || "; // the first one that can be parsed well.
  public static String _JSON_EQUAL_OPERATOR = "|=|"; // match values of <JSON_path1> with <JSON_path2>
  public static String _WHERE_CONDITION = "%";    // <path>%<where_path>
  public static String _FOR_MAPPING     = "@:";   // <path>@:<for_path>
  public static String _ANY_OBJECT      = "*";    // ../<step>/*/..
  public static String _MULTIVALUE_SEPARATOR = "+";
  public static String _MULTILINE       = "\n";   // multiple JSON paths: to parse all
  public static String _MULTIPART_PATH  = "\n+";  // multipart path: [0]=main/root/intersection (points to JSONObject), [1..]=parts
  public static String _DP_BOOLEAN      = "B=";   // evaluates to a boolean value for datatype properties
  public static String _OP_DOMAIN       = "D=";   // domain part of an object property
  public static String _OP_RANGE        = "R=";   // range part of an object property

  private JSONObject _root;
  private Object    _pointerTo_Object;
  private JSONArray _pointerTo_JSONArray; // points to the FIRST JSONArray found.
  private String    _pointerTo_lastStep; // points to the key of the last path step.
  private boolean   _useJSONquery; // indicates usage of JSON query.
  private boolean   _parsingSuccess;
  
  private String _JSON_mapping_value;
  private String _JSON_path[]; // path steps array
  private String _JSON_multipart_main_path; // the main path should point to a JSONObject: [0]=main/root/intersection (points to JSONObject)
  private boolean _asBoolean;
  private boolean _asDomain;
  private boolean _asRange;
  private char _flag;
  private String _symbol;
  private String _entity;
  private String _onlyFor;
  private String _WHERE_condition_constant;
  private String _WHERE_condition_OP;
  
  private Object _JSON_mappingObjs[]; // [a path has an array of Objects: JSON pointers]
  private Set<JSONObject> _mappedObjs; // from recursively processed JSON
  private Set<String    > _mappedVals; // from recursively processed JSON
  
  private JSON_Path _JSON_paths[];  // could be multiple JSON_Paths
  private int _JSON_paths_position; // position of the path in the array.
  private int _JSON_paths_pointer;  // which path is being currently processed?
  private int _JSON_paths_D_pointer; // Domain paths.
  private int _JSON_paths_R_pointer; // Range paths.
  
  private String _strDelimiter;

  /******************************************************************************************************************************/  
  private void init() {
    _pointerTo_Object = null;
    _pointerTo_JSONArray = null;
    _pointerTo_lastStep = null;
    _JSON_mappingObjs = null;
    _mappedObjs = null;
    _mappedVals = null;
    _JSON_path = null;
    _JSON_paths = null;
    _JSON_paths_position = -1;
    _JSON_paths_pointer = -1;
    _JSON_paths_D_pointer = -1;
    _JSON_paths_R_pointer = -1;
    _JSON_multipart_main_path = null;
    _strDelimiter = null;
    _asBoolean = false;
    _asDomain = false;
    _asRange = false;
    _flag = ' ';
    _symbol = null;
    _entity = null;
    _onlyFor = null;
    _WHERE_condition_constant = null;
    _WHERE_condition_OP = null;
    _useJSONquery = false;
    _parsingSuccess = false;
  }
  
  private String getMultiPathType() { // detection of processing structure in the following order:
    if (isMultiPart()) return _MULTIPART_PATH; // multipart are detected before multiline.
    if (isMultiLine()) return _MULTILINE; // OP is treated as multiline.
    if (isOR()) return " \\|\\| "; // escape for regular expression
    if (isJSON_equal()) return "\\|=\\|"; // escape for regular expression
    if (isWHEREcondition()) return _WHERE_CONDITION;
    if (isAND_in_WHERE()) return _AND_OPERATOR; // AND operator for <where_paths>.
    if (isFORmapping()) return _FOR_MAPPING;
    return null;
  }

  private void setAsBoolean() {
    _asBoolean = _JSON_mapping_value.startsWith(_DP_BOOLEAN);
    if (_asBoolean) // B=<path>
      _JSON_mapping_value = _JSON_mapping_value.substring(2, _JSON_mapping_value.length()); // removes "B="
    //System.out.printf("<JSON_Path.setAsBoolean()>: %s, path=%s\n", _asBoolean, _JSON_mapping_value);
  }

  private void setAsDomain() {
    _asDomain = _JSON_mapping_value.startsWith(_OP_DOMAIN);
    if (_asDomain) { // D=<path>
      _JSON_paths_D_pointer = 0;
      _JSON_mapping_value = _JSON_mapping_value.substring(2, _JSON_mapping_value.length()); // removes "D="
    }
    //System.out.printf("<JSON_Path.setAsDomain()>: %s, path=%s\n", _asDomain, _JSON_mapping_value);
  }

  private void setAsRange() {
    _asRange = _JSON_mapping_value.startsWith(_OP_RANGE);
    if (_asRange) { // R=<path>
      _JSON_paths_R_pointer = 0;
      _JSON_mapping_value = _JSON_mapping_value.substring(2, _JSON_mapping_value.length()); // removes "R="
    }
    // Checks if there's a FLAG in the path.  Flags are located in the last character.
    _flag = _JSON_mapping_value.charAt(_JSON_mapping_value.length() - 1); 
    if (_flag == POINTS_TO_CONSTANT_FLAG)
      _JSON_mapping_value = _JSON_mapping_value.substring(0, 
                            _JSON_mapping_value.indexOf(POINTS_TO_CONSTANT_FLAG)); // removes POINTS_TO_CONSTANT_FLAG
    else _flag = ' ';
    //System.out.printf("<JSON_Path.setAsRange()>: %s, path=%s, flag=%s\n", _asRange, _JSON_mapping_value, _flag);
  }

  private void setOPcomponent() {
    if (_JSON_mapping_value.startsWith(_OP_DOMAIN)) setAsDomain(); else
    if (_JSON_mapping_value.startsWith(_OP_RANGE) ) setAsRange();
  }

  private boolean extractsConstant() {
    Queue<String> ops = new LinkedList<>(); // queue of possible operators
    ops.add("<=" ); // integer
    ops.add(">=" ); // integer
    ops.add("!=\""); // string / taken "as is"
    ops.add("!=" ); // integer / taken "as is" (compared as a string)
    ops.add("=\""); // string
    ops.add("="  ); // integer
    ops.add("<"  ); // integer
    ops.add(">"  ); // integer
    String[] parts = null;
    String operator = null;
    while (ops.size() > 0) {
      operator = ops.remove();
      parts = _JSON_mapping_value.split(operator);
      if (parts.length == 2)
        break; // exists the queue.
    }
    if (parts.length == 2) { // <path> <operator> (")<constant>(")
      _JSON_mapping_value = parts[0];
      _WHERE_condition_OP = operator;
      _WHERE_condition_constant =
          (_WHERE_condition_OP.charAt(_WHERE_condition_OP.length()-1) == '"') 
          ? parts[1].substring(0, parts[1].length()-1) // removes last \"
          : parts[1].substring(0, parts[1].length());
      // validate constant type:
      switch (_WHERE_condition_OP) {
        case "<=": case ">=":  case "=": // integer
        case "<" : case ">" : // integer:
          return isNumericValue(_WHERE_condition_constant);
        // default: string value
      } // switch
    }
    return true; // continues the parsing
  }

  private void extractsEntity() {
    String[] parts = _JSON_mapping_value.split("@");
    if (parts.length == 2) { // <path>@<entity>
      _JSON_mapping_value = parts[0];
      String[] comp = parts[1].split("->");
      if (comp.length == 2) { // <entity>-><onlyFor>
        _entity = comp[0];
        _onlyFor = comp[1];  
      }
      else
        _entity = parts[1];
      //System.out.printf("<JSON_Path[%s].extractsEntity() : _entity=%s, _onlyFor=%s\n", _JSON_paths_position, _entity, _onlyFor);
    }
  }
  
  private void removeFlag(int index, char flag) {
    _JSON_path[index] = 
          _JSON_path[index].substring(0, 
          _JSON_path[index].indexOf(flag));
  }
  
  // Checks if there's a FLAG in the path.  Flags are located in the last character.
  // Paths with special flag processing doesn't start with "/"
  private void extractsFlag() {
    _flag = _JSON_mapping_value.charAt(_JSON_mapping_value.length() - 1); 
    int indexOfLastStep = _JSON_path.length - 1;
    switch (_flag) {
      case ')': // case of ("<delimiter>"):
        String stepWithDelimiter[] = _JSON_path[indexOfLastStep].split("\\(\"");
        _JSON_path[indexOfLastStep] = stepWithDelimiter[0]; // the last step
        _strDelimiter = stepWithDelimiter[1].substring(0, stepWithDelimiter[1].indexOf('"'));
      break;
      case '!': // case of *generate an ID from the string value*:
        removeFlag(indexOfLastStep, JSON_Path.GENERATE_ID_FLAG); // removes '!'
      break;
      case '#': // case of *use as ID from the string value*:
        removeFlag(indexOfLastStep, JSON_Path.USE_ID_FLAG); // removes '#'
      break;
      case '~': // case of *generate an rdfs:label for the mapped value*:
        removeFlag(indexOfLastStep, JSON_Path.RDFS_LABEL_FLAG); // removes '~'
      break;
      case '^': // case of *use of mapped value*:
        removeFlag(indexOfLastStep, JSON_Path.MAPPING_TO_VALUES_FLAG); // removes '^'
      break;
      case '<': // case of *map to an ancestor object*:
        removeFlag(indexOfLastStep, JSON_Path.MAP_TO_ANCESTOR_FLAG); // removes '<'
      break;
    } // switch (flag)
  }
  
  // Checks if there's a SYMBOL in the path.
  // Symbols are located at the beginning: _JSON_path[0]
  private boolean processSymbol() {
    boolean wasProcessed = false;
    if (_JSON_path[0].charAt(0) == '$') { // it's a symbol
      _pointerTo_Object = _JSON_path[0].toUpperCase();
      switch (_JSON_path[0].toUpperCase()) {
        case "$DOC_ID$": // uses the document ID passed in the arguments
          _symbol = DOC_ID_SYMBOL;
          wasProcessed = _parsingSuccess = true; // generates the triples.
      }
    }
    return wasProcessed;
  }
  
  private boolean execJSONquery() {
    boolean wasExec = false;
    if (_JSON_mapping_value.charAt(0) == '/') { // uses a JSON Query (pointer)
      _useJSONquery = true;
      _pointerTo_Object = _root.query(_JSON_mapping_value);
      //System.out.printf("<JSON_Path[%s].parse() : JSONObject.query()>: %s\n", _JSON_paths_position, _pointerTo_Object);
      // generates the triples only if a path was found or the value pointed is not empty.
      if (_pointerTo_Object != null) {
        _parsingSuccess = (_pointerTo_Object.toString().strip().length() > 0);
        if (_parsingSuccess) {
          _JSON_mappingObjs[0] = null; 
          _JSON_mappingObjs[1] = _root.get(_JSON_path[1]); // first object of the path starts at position=1
          _pointerTo_lastStep = _JSON_path[_JSON_path.length - 1]; // the last step in the path.
        }
      }
      //System.out.printf("<JSON_Path[%s].parse()> %s\n%s\n%s\n", _JSON_paths_position, _parsingSuccess, _pointerTo_Object, _JSON_mappingObjs[1]);
      wasExec = true;
    }
    return wasExec;
  }
  
  private boolean chkLastStep(int i, Object step) {
    if (i+1 == _JSON_path.length) { // last step:
      _pointerTo_lastStep = _JSON_path[i]; // the last step in the path.
      if (step instanceof JSONObject) _pointerTo_Object = (JSONObject) step; // points to the current step.
      if (step instanceof JSONArray) { // maps to a JSONArray:
        if (_pointerTo_JSONArray == null) // keeps the pointer to the FIRST JSONArray
          _pointerTo_JSONArray = (JSONArray) step; // points to the current step.
        //System.out.printf("<JSON_Path[%s].parse()> JSONArray=%s; key=%s\n", _JSON_paths_position, _pointerTo_JSONArray, _pointerTo_lastStep);
      } // if (JSONArray)
      // Atomic data fields:
      if ((step instanceof String)  ||
          (step instanceof Boolean) ||
          (step instanceof Number)) {
        _pointerTo_Object = step.toString(); // get the data's string representation.
        //System.out.printf("<JSON_Path[%s].parse() | (data)>: [index=%s]=\"%s\"\n", _JSON_paths_position, i, _JSON_mappingObjs[i]);
      } // if (atomic data cases)
      return true;
    } // if (last step)
    return false;
  }
  
  private boolean finishParsing() {
    if (this.hasConstant() && (_pointerTo_Object != null)) { // <path> <operator> (")<constant>(")
      _parsingSuccess = this.evaluateWHEREcondition(); // successful parse only when the path value evaluates to true.
      if (this.isBoolean()) _parsingSuccess = true; // for boolean paths, we return a successful parse.
    }
    return _parsingSuccess;
  }

  private boolean parse() {
    if (this.isEmpty()) {
      _parsingSuccess = (this.isDomain() || this.isRange()); // a domain or range path could be empty.
      return _parsingSuccess;
    }
    this.setAsBoolean();
    this.extractsEntity();
    if (!this.extractsConstant())
      return (_parsingSuccess = false);
    
    if (this.hasMultiPartMainPath() && (_JSON_paths_position > 0)) { // for the part paths...
      String local_fullPath = _JSON_multipart_main_path + // ... we add the main path to check if it parse OK.
        _JSON_mapping_value.substring(1); // we skip: "+/" (first character)
      //System.out.printf("<JSON_Path[%s].parse()> local_fullPath=%s;\n", _JSON_paths_position, local_fullPath);
      _JSON_path = local_fullPath.split("/"); // begin with or without "/"
    }
    else
      _JSON_path = _JSON_mapping_value.split("/"); // begin with or without "/"
    _JSON_mappingObjs = new Object[_JSON_path.length];
    if (this.execJSONquery()) // JSON Query execution
      return finishParsing();
    this.extractsFlag(); // process the flag before the symbol
    if (this.processSymbol()) // symbol processing
      return finishParsing();
    
    int i = 0;
    Object step = _root, next = step; // starts at the root: document's JSON representation.
    // NOTE: _JSON_path[i] points to the *next* step.  *step* is always pointing one object "behind" *next*.
    while (next != null) {
      //System.out.printf(_JSON_path[i] + ",\n");
      if (!(_JSON_path[i].contains("(\"")) &&
        ( ( (step instanceof JSONObject) && (!((JSONObject) step).has(_JSON_path[i])) ) || 
          ( 
            ( (step instanceof JSONArray ) && ( ( ((JSONArray) step).isEmpty()) ) ) ||
            ( (step instanceof JSONArray ) && (!((JSONObject) ((JSONArray) step).get(0)).has(_JSON_path[i])) )
          )
        ) ) { // if next step is not found in the JSONObject/JSONArray structure:
        i++;
        _parsingSuccess = false;
        next = null;
        break; // exists the loop.
      }
      if ((_JSON_path[i].equals(_ANY_OBJECT)) && !(step instanceof JSONArray)) { // case of "../<JSONArray>/*/.."
        i++;
        _parsingSuccess = false;
        next = null;
        break; // exists the loop.
      }
      _parsingSuccess = true;
      if (step instanceof JSONObject) {
        next = ((JSONObject) step).get(_JSON_path[i]);
        _pointerTo_Object = (JSONObject) step; // points to the current step.
      } // if (JSONObject)
      if (step instanceof JSONArray) { // maps to a JSONArray:
        // by default, we will check the first element of the array and get the object.
        if (_JSON_path[i].equals(_ANY_OBJECT)) { // "<step>/*/.."
          if (chkLastStep(i, step)) { // last step: (1) ends with "../*", (2) <step> is the last step!
            next = null;
            break; // exists the loop.
          }
          /*
           * <!!> ISSUE: It could happen that the first element of the JSONArray is empty, whilst other elements have non-empty JSONObjects.
           * In this situation, the parser looses the validation structure due that it traverses it following the path for the first element.
           * Need to fix this!
           * Otherwise, for multi-part paths, the validation will fail.  
           * */
          else next = ((JSONObject) ((JSONArray) step).get(0)).get(_JSON_path[i+1]); // <step>/*/<next>
        } else next = ((JSONObject) ((JSONArray) step).get(0)).get(_JSON_path[i]);   // a normal step:
        if (_pointerTo_JSONArray == null) // keeps the pointer to the FIRST JSONArray
          _pointerTo_JSONArray = (JSONArray) step; // points to the current step.
        //System.out.printf("<JSON_Path[%s].parse()> JSONArray=%s; key=%s\n", _JSON_paths_position, _pointerTo_JSONArray, _pointerTo_lastStep);
      } // if (JSONArray)
      step = next; // moves the current step.
      _JSON_mappingObjs[i] = step;
      if (chkLastStep(i, step)) { // last step:
        next = null;
        break; // exists the loop.
      } // if (last step)
      //System.out.printf("<JSON_Path[%s].parse() | (Object)>: [%s]=\n%s\n", _JSON_paths_position, i, _JSON_mappingObjs[i]);
      i++; // next index
    } // while (JSON_path)
    //System.out.printf("<JSON_Path[%s].parse()> %s\n%s\n%s\n", _JSON_paths_position, _parsingSuccess, _pointerTo_Object, _pointerTo_JSONArray);
    return finishParsing();
  }
  
  private boolean processAllPaths(boolean allShouldBeSuccessful) {
    boolean allSuccessful = true; // we assumed that all are processed successfully.
    for (_JSON_paths_pointer = 0; _JSON_paths_pointer < this.getPathsLength(); _JSON_paths_pointer++) { // for each path
      if (_JSON_paths[_JSON_paths_pointer].process()) _parsingSuccess = true;
      else { // process() was not successful
        if (allShouldBeSuccessful && allSuccessful) allSuccessful = false; // marked as not successful
      }
    }
    _JSON_paths_pointer = 0; // resets counter
    return (!allShouldBeSuccessful) ?
      _parsingSuccess : // success if at least one
      allSuccessful;    // all should be successful
  }
  private boolean processAllPathsWithOneSuccess() { return processAllPaths(false); }
  private boolean processAllPathsWithAllSuccess() { return processAllPaths(true); }

  /******************************************************************************************************************************/
  public String    getPath()                  { return _JSON_mapping_value; }
  public String[]  getPathSteps()             { return _JSON_path; }
  public String    getEntity()                { return _entity; }
  public String    getOnlyFor()               { return _onlyFor; }
  public String    getConstant()              { return _WHERE_condition_constant; }
  public String    getOperator()              { return _WHERE_condition_OP; }
  public Object    getPointerToObject()       { return _pointerTo_Object; }
  public JSONArray getPointerToJSONarray()    { return _pointerTo_JSONArray; }
  public String    getKey()                   { return _pointerTo_lastStep; }
  public String    getStringDelimiter()       { return _strDelimiter; }
  public char      getFlag()                  { return _flag; }
  public boolean   has_DOC_ID_Symbol()        { return ((_symbol  != null) && (_symbol.equals(DOC_ID_SYMBOL))); }
  public boolean   hasEntity()                { return (_entity   != null); }
  public boolean   hasOnlyFor()               { return (_onlyFor  != null); }
  public boolean   hasConstant()              { return (_WHERE_condition_constant != null); }
  public boolean   hasMultiPartMainPath()     { return (_JSON_multipart_main_path != null); }
  public boolean   usesJSONquery()            { return _useJSONquery; }
  public boolean   wasParsingSuccessful()     { return _parsingSuccess; }

  public boolean evaluateWHEREcondition() {
    if (_pointerTo_Object != null)
      return (this.evaluateWHEREcondition(_pointerTo_Object.toString()));
    return false;
  }
  public boolean evaluateWHEREcondition(String v) {
    if ((v != null) && (_WHERE_condition_OP != null) && (_WHERE_condition_constant != null)) {
      switch (_WHERE_condition_OP) {
        case "<=": // integer
          if (isNumericValue(v)) return (Long.parseLong(v) <= Long.parseLong(_WHERE_condition_constant)); 
        break;
        case ">=": // integer
          if (isNumericValue(v)) return (Long.parseLong(v) >= Long.parseLong(_WHERE_condition_constant));
        break;
        case "=\"": // string
          return (v.toString().equals(_WHERE_condition_constant));
        case "!=\"": case "!=": // the evaluation is performed as a string
          return !(v.toString().equals(_WHERE_condition_constant));
        case "=": // integer
          if (isNumericValue(v)) return (Long.parseLong(v) == Long.parseLong(_WHERE_condition_constant));
        break;
        case "<": // integer
          if (isNumericValue(v)) return (Long.parseLong(v) < Long.parseLong(_WHERE_condition_constant));
        break;
        case ">": // integer:
          if (isNumericValue(v)) return (Long.parseLong(v) > Long.parseLong(_WHERE_condition_constant));
        break;
      } // switch
    }
    return false;
  }

  public boolean   generateIDfromString()     { return (_flag == GENERATE_ID_FLAG); }
  public boolean   useIDfromString()          { return (_flag == USE_ID_FLAG); }
  public boolean   generateRDFSlabel()        { return (_flag == RDFS_LABEL_FLAG); }
  public boolean   useMappedValues()          { return (_flag == MAPPING_TO_VALUES_FLAG); }
  public String    getMapForAncestorChk()     { return (_flag == MAP_TO_ANCESTOR_FLAG) ? _JSON_mapping_value : null; }
  public boolean   pointsToConstant()         { return (_flag == POINTS_TO_CONSTANT_FLAG); }
  public boolean   isValidFlag()              { return (generateIDfromString()  || useIDfromString()  || pointsToConstant()); }
  public static boolean isValidFlag(char f)   { return ((f == GENERATE_ID_FLAG) || (f == USE_ID_FLAG) || (f == RDFS_LABEL_FLAG) ||
                                                        (f == MAPPING_TO_VALUES_FLAG) || (f == MAP_TO_ANCESTOR_FLAG) || 
                                                        (f == POINTS_TO_CONSTANT_FLAG)); }

  public boolean   isBoolean()                { return _asBoolean; }
  public boolean   isDomain()                 { return _asDomain; }
  public boolean   isRange()                  { return _asRange; }
  
  public boolean   isWHEREpathSuccessful()    { return ((_JSON_paths[0].wasParsingSuccessful()) &&
                                                        (_JSON_paths[1].wasParsingSuccessful()) && // <constant path> := <path> <operator> (")<constant>(")
                                                        (_JSON_paths[1].hasConstant())); }

  // ends the processing of the paths.  We assume that there won't be more than 1000 paths in an ontology definition:
  public void      finalizePathProcessing()   { _JSON_paths_pointer = 1000; }

  public boolean   isOR()                     { return (_JSON_mapping_value.contains(_OR_OPERATOR)); }
  public boolean   isAND_in_WHERE()           { return (_JSON_mapping_value.contains(_AND_OPERATOR)); }
  public boolean   isJSON_equal()             { return (_JSON_mapping_value.contains(_JSON_EQUAL_OPERATOR)); }
  public boolean   isWHEREcondition()         { return (_JSON_mapping_value.contains(_WHERE_CONDITION)); }
  public boolean   isFORmapping()             { return (_JSON_mapping_value.contains(_FOR_MAPPING)); }
  public boolean   isDPboolean()              { return (_JSON_mapping_value.contains(_DP_BOOLEAN)); }
  public boolean   isOP()                     { return (_JSON_mapping_value.contains(_OP_DOMAIN) && _JSON_mapping_value.contains(_OP_RANGE)); }
  public boolean   isMultiLine()              { return (_JSON_mapping_value.contains(_MULTILINE)); }
  public boolean   isMultiPart()              { return (_JSON_mapping_value.contains(_MULTIPART_PATH)); } // only the main path (not its parts)
  public boolean   isEmpty()                  { return (_JSON_mapping_value.length() == 0); }
  public boolean   hasMultiplePaths()         { return (this.isJSON_equal() || this.isOR()        || this.isWHEREcondition() || this.isAND_in_WHERE() || 
                                                        this.isFORmapping() || this.isMultiLine() || this.isMultiPart()); }
  
  public Set<JSONObject> getMappedObjsSet()   { return _mappedObjs; }
  public Set<String    > getMappedValsSet()   { return _mappedVals; }
  public boolean   isTargetInMappedValues(String target) {
    for (String v : _mappedVals) {
      if (Builder.stringMatching(v, target))
        return true;
    }
    return false;
  }
  
  public int       getPathsLength()           { return (_JSON_paths != null) ? _JSON_paths.length : -1; }
  public int       getPosition()              { return _JSON_paths_position; }
  public int       getCurrentPathPointer()    { return _JSON_paths_pointer; }
  public JSON_Path getPath(int p)             { return _JSON_paths[p]; }
  public void      initPathPointer()          { _JSON_paths_pointer = (isOR()) ? -1 : 0; }
  public void      nextPath()                 { _JSON_paths_pointer++; }

  public void      initDomainPathsPointer()   { _JSON_paths_D_pointer = -1; this.nextDomainPath(); }
  public void      initRangePathsPointer()    { _JSON_paths_R_pointer = -1; this.nextRangePath() ; }
  public void      nextDomainPath()
    { do _JSON_paths_D_pointer++; while ((_JSON_paths_D_pointer < this.getPathsLength()) && (!_JSON_paths[_JSON_paths_D_pointer].isDomain())); }
  public void      nextRangePath()
    { do _JSON_paths_R_pointer++; while ((_JSON_paths_R_pointer < this.getPathsLength()) && (!_JSON_paths[_JSON_paths_R_pointer].isRange() )); }
  public JSON_Path getDomainPath() {
    if (isJSON_equal()) // for |=|, domain mappings are in the last path.
      return _JSON_paths[_JSON_paths.length - 1];
    else
      return (_JSON_paths_D_pointer == getPathsLength()) ? null :
             (_JSON_paths[_JSON_paths_D_pointer].isDomain()) ? _JSON_paths[_JSON_paths_D_pointer].getCurrentPath() : null;
  }
  public JSON_Path getRangePath() {
    if (isJSON_equal()) // for |=|, range mappings are in the first path.
      return _JSON_paths[0];
    else
      return (_JSON_paths_R_pointer == getPathsLength()) ? null :
             (_JSON_paths[_JSON_paths_R_pointer].isRange() ) ? _JSON_paths[_JSON_paths_R_pointer].getCurrentPath() : null;
  }

  public JSON_Path getCurrentPath() {
    if ((_JSON_paths == null) && (_JSON_paths_pointer == 0)) return this;
    if (this.isOR()) {
      if (_JSON_paths_pointer == -1)
        return this; // in OR paths, we always return the root (don't traverse in each path of the OR expression)
      if (_JSON_paths_pointer >= 0)
        return null; // prevents infinite loops
    }
    return ((_JSON_paths_pointer < this.getPathsLength()) ? _JSON_paths[_JSON_paths_pointer] : null);
  }

  public JSON_Path getFORmappingPath() {
    if (isFORmapping())
      return _JSON_paths[1]; // <path>@:<FOR_mapping_path> ; it's the second path
    else
      return null;
  }

  public String print(boolean _print) {
    String p = String.format("<JSON_Path[%s].print() : {%s, %s}, JSON-mapping=%s, @{%s}->{%s}, constant=%s, evaluateConstant=%s\n",
      _JSON_paths_position, _parsingSuccess, _JSON_paths_pointer, _JSON_mapping_value, _entity, _onlyFor, _WHERE_condition_constant, this.evaluateWHEREcondition());
    if (_print) System.out.print(p);
    return p;
  }
  
  private boolean isNumericValue(String s) { return s.matches("(0|[1-9]\\d*)"); }
  
  private boolean skipStep(String s) {
    return ( isNumericValue(s) || // the step is: ../<int>/..
             s.equals(_ANY_OBJECT) ); // the step is: ../*/..
  }

  public boolean existsDescendantObject(Object ancestor, int startFromStep, Object descendant) {
    //System.out.printf("\n<existsDescendantObject>: checking...\ndesc=%s\nance=%s\n", descendant.toString(), ancestor.toString());
    //System.out.printf("startFrom=%s, [2]=%s, [3]=%s\n", startFromStep, this.getPathSteps()[2], this.getPathSteps()[3]);
    return (this.processJSONfromObject(ancestor, startFromStep, this.getPathSteps(), null, null, descendant));
  }
  
  // values were added or it's the last step and the mapping is to a JSONObject:
  private void addObjInMappedObjs(boolean valuesWereAddedOrObjFound, int step, String[] JSON_path_steps,
    Set<JSONObject> mappedObjs, JSONObject o) {
    if ( (valuesWereAddedOrObjFound) || (step == JSON_path_steps.length-1) )
      if (mappedObjs != null)
        mappedObjs.add(o); // for JSONObjects
  } 


  public boolean processJSONfromObject(Object current, int step, String[] JSON_path_steps,
    Set<JSONObject> mappedObjs, Set<String> mappedValues, Object chkIfExists) {
    if ((step < 0) || (step >= JSON_path_steps.length)) return false; // exit from recursion
    if (skipStep(JSON_path_steps[step])) {
      step++;
      // if it's the last step and current points to an object: returns true;
      if ((step == JSON_path_steps.length) && (current != null)) return true; // exit from recursion
    }
    current = (current == null) ? _JSON_mappingObjs[step] : current; // if null, gets object from the parsed object array
    if (current == null) return false; // exit from recursion
    if (current == chkIfExists) {
      //System.out.printf("<processJSONfromObject()> DESCENDANT FOUND!\n");
      return true; // object found.
    }
    boolean valuesWereAddedOrObjFound = false;
    
    if (current instanceof JSONArray) { // only JSONArray processing
      if ( (step+1 <= JSON_path_steps.length-1) && (isNumericValue(JSON_path_steps[step + 1])) ) {
        Object o = ((JSONArray) current).get(Integer.parseInt(JSON_path_steps[step + 1])); // Array[index]
        valuesWereAddedOrObjFound = processJSONfromObject(o, ++step, JSON_path_steps, mappedObjs, mappedValues, chkIfExists); // on following step, process recursively
        if (valuesWereAddedOrObjFound && (chkIfExists != null)) return true; // object was found!
        addObjInMappedObjs(valuesWereAddedOrObjFound, step+1, JSON_path_steps, mappedObjs, (JSONObject) o);
      }
      else
        for (Object o : ((JSONArray) current)) { // for each object in the array
          valuesWereAddedOrObjFound = processJSONfromObject(o, step, JSON_path_steps, mappedObjs, mappedValues, chkIfExists); // on current step, process recursively
          if (valuesWereAddedOrObjFound && (chkIfExists != null)) return true; // object was found!
        }
      return valuesWereAddedOrObjFound;
    } // JSONArray processing
    
    if (current instanceof JSONObject) {
      if (step+1 <= JSON_path_steps.length-1) {
        step++; // moves to next object in the path
        //System.out.printf("<processJSONfromObject()>: key=%s, step=%s\n", JSON_path_steps[step], step);
        if (((JSONObject) current).has(JSON_path_steps[step]))
          valuesWereAddedOrObjFound = processJSONfromObject(((JSONObject) current).get(JSON_path_steps[step]), step, 
            JSON_path_steps, mappedObjs, mappedValues, chkIfExists);
        else // the mapping is inconsistent and the /step/ was not found in the JSONObject
          return valuesWereAddedOrObjFound = false;
        if (valuesWereAddedOrObjFound && (chkIfExists != null)) return true; // object was found!
        step--; // keeps track of the current step: returns to the current step (recursive model).
        if (skipStep(JSON_path_steps[step])) step++;
      }
      addObjInMappedObjs(valuesWereAddedOrObjFound, step, JSON_path_steps, mappedObjs, (JSONObject) current);
    }
    
    if ((current instanceof String) || (current instanceof Boolean) || (current instanceof Number)) { // for values
      if (chkIfExists  != null) return false; // if we were looking for an object: ==> not found!
      boolean addValue = true;
      if (!this.isBoolean()) // ... boolean paths keep all values for correct triple creation
        // if the JSON_Path has a "constant" or is a WHERE_Condition, we need to evaluate the value:
        if ((this.hasConstant()) || (this.isWHEREcondition()))
          addValue = this.evaluateWHEREcondition(current.toString());
      if (mappedValues != null) { // only when we have a set of mapped values:
        if (addValue)
          mappedValues.add(current.toString());
      }
      return addValue; // added a value.
    }
    return false; // no value added or object not found
  }


  public Set<JSONObject> getMappedObjects() {
    if (_mappedObjs != null) // it's been already computed
      return _mappedObjs;
    _mappedObjs = new HashSet<JSONObject>(); // both sets are computed simultaneously
    _mappedVals = new HashSet<String    >();
    if ( (this.isDomain() || this.isRange()) && (this.isEmpty()) )
      return _mappedObjs;
    if (this.has_DOC_ID_Symbol()) {
      _mappedVals.add(_symbol.toString());
      return _mappedObjs;
    }
    if (this.usesJSONquery())
      processJSONfromObject(null, 1, _JSON_path, _mappedObjs, _mappedVals, null); // starts at the second step
    else
      processJSONfromObject(null, 0, _JSON_path, _mappedObjs, _mappedVals, null); // from the path's root.  Common usage: JSONArray objects.
    return _mappedObjs;
  }
  
  public Set<String> getMappedValues() {
    if (_mappedVals != null) // it's been already computed
      return _mappedVals;
    this.getMappedObjects(); // compute JSONObjects and values
    return _mappedVals;
  }
  
  public boolean removeValuesThatAreNotEqualFromSet(JSON_Path reference) {
    reference.getMappedValsSet().forEach( (target) -> {
      for (Object value : _mappedVals.toArray() ) {
        if (!Builder.stringMatching((String) value, target))
          _mappedVals.remove(value);
      } // mapped (String) values
      for (Object o : _mappedObjs.toArray()) {
        boolean found = false;
        for (String key : ((JSONObject) o).keySet()) { // for each key in the JSONObject
          found = Builder.stringMatching(((JSONObject) o).get(key).toString(), target); // if the target value is found...
          if (found)
            break;
        } // mapped JSONObject key set
        if (!found)
          _mappedObjs.remove(o);
      } // mapped JSONObjects
    } ); // target set
    return ((_mappedVals.size() > 0) && (_mappedObjs.size() > 0)); // some values were matched
  }
  
  @SuppressWarnings("unchecked")
  private Set<String> recursiveCartesianProduct(Object[] level, int next) {
    if (next >= level.length) return null; // exit
    Set<String> P = (Set<String>) level[next];
    Set<String> Q = recursiveCartesianProduct(level, ++next);
    if (Q == null) return P;
    Set<String> PxQ = new HashSet<String>(); 
    for (String p : P)
      for (String q : Q)
        PxQ.add(p + JSON_Path._MULTIVALUE_SEPARATOR + q);
    return PxQ;
  }

  @SuppressWarnings("unchecked")
  public Set<String> MP_getMappedPairedValues() {
    if ((this.isMultiPart()) && (_JSON_paths_position == 0)) { // the recursive process starts from the main path (@JSON_Path[0])
      //System.out.printf("<JSON_Path[%s].getMappedPairedValuesFromMultiPathProcessing()>\n", _JSON_paths_position);
      _mappedObjs = _JSON_paths[0].getMappedObjects(); // mapped objects of the main path (@JSON_Path[0])
      _mappedVals = _JSON_paths[0].getMappedValsSet();
      _mappedVals.clear();
      //System.out.printf("Size: %s\n", _mappedObjs.size());
      Object[] mappingObjsXo = null; // an array of Set<JSONObject>
      Object[] mappingValsXo = null; // an array of Set<String>
      for (JSONObject o : _mappedObjs) { // [0]=main/root/intersection (points to several JSONObject).  For each:
        //System.out.printf("%s\n", o.toString());
        mappingObjsXo = new Object[this.getPathsLength() - 1];
        mappingValsXo = new Object[this.getPathsLength() - 1];
        for (int paths_pointer = 1; paths_pointer < this.getPathsLength(); paths_pointer++) { // for each part path [1..]
          JSON_Path part         = _JSON_paths[paths_pointer]; 
          String    path_steps[] = part.getPathSteps();
          int       step         = _JSON_paths[0].getPathSteps().length - 1; // starts at the last step of the main path (@JSON_Path[0])
          //System.out.printf("<local[%s]> last_step=%s, step=%s\n", paths_pointer, path_steps[path_steps.length-1], step);
          mappingObjsXo[paths_pointer - 1] = new HashSet<JSONObject>();
          mappingValsXo[paths_pointer - 1] = new HashSet<String    >();
          processJSONfromObject(o, step, path_steps,
            ((Set<JSONObject>) mappingObjsXo[paths_pointer - 1]),
            ((Set<String    >) mappingValsXo[paths_pointer - 1]), null);
        } // for each part
        // Cartesian Product of the mapped values:
        _mappedVals.addAll(recursiveCartesianProduct(mappingValsXo, 0));
      } // for (JSONObject o) from the main path
    } // if
    return _mappedVals;
  }
  
  public JSONObject MP_getAncestorObjectInMappedObjects(JSONObject descendant) {
    if (this.isMultiPart()) {
      // the recursive process starts from the upper level structure (@JSON_Path[1]).
      // ASSUMPTION: @JSON_Path[1] mapped objects are the same as the top level ones at @JSON_Path[2..n]
      // RETURNS: the ancestor object from the upper level structure.
      for (JSONObject ancestor : _JSON_paths[1].getMappedObjects()) {
        int i = 2; // start looking from the second part onward...
        while (i < this.getPathsLength()) {
          // @JSON_Path[0]: main path | @JSON_Path[2]: steps start from (@JSON_Path[0]).length-1
          // look if the descendant object exists (from the ancestor).
          if (_JSON_paths[i].existsDescendantObject((Object) ancestor, _JSON_paths[0].getPathSteps().length-1, descendant))
            return ancestor;
          i++; // next part
        } // while
      } // for (ancestor)
    } // if (isMultiPart())
    return null;
  }

  public boolean isObjectInMappedObjects(JSONObject o) {
    if (o == null) return false;
    this.getMappedObjects();
    return _mappedObjs.contains(o);
  }
  
  public JSONObject getAncestorFromMappedObjects(String mapToAncestor, JSONObject descendant) {
    if ((mapToAncestor == null) || (descendant == null)) return null;
    /* Current implementation for the simple case: ancestor="a/b/c" ; descendant="a/b/d/e"
     * Assumptions:
     * 1. The first step that doesn't match is located at the same level of the paths.
     * 2. this._mappedObjs has an object that can access "c" and "d".
    */
    // It's expected that the ancestry path is shorter than the descendant path:
    if  (mapToAncestor.length() < _JSON_mapping_value.length()) return null;
    String[] descendantSteps = mapToAncestor.split("/");
    String[] ancestorsSteps  = _JSON_path;
    int descSteps = 0, ancSteps = 0;
    // skip an empty first step (possible a JSON Query mapping).
    if (descendantSteps[0].strip().equals("")) descSteps = 1; // starts from 1
    if (ancestorsSteps [0].strip().equals("")) ancSteps  = 1; // starts from 1
    while (ancSteps < (ancestorsSteps.length-1)) { // the common path goes from step 0 to (ancSteps-1)
      if (descendantSteps[descSteps].equals(ancestorsSteps[ancSteps])) { // check the following step
        ++descSteps; ++ancSteps;
      }
      else break; // we stop looking at the first step that doesn't match ==> start looking for the descendant object... 
    } // while
    this.getMappedObjects();
    String key = descendantSteps[descSteps];
    for (JSONObject ancestor : _mappedObjs) {
      if (ancestor.has(key)) { // look for descendant from this point...
        if (ancestor.get(key) instanceof JSONObject) {
          if (ancestor.getJSONObject(key) == descendant)
            return ancestor;
        } // if (JSONObject)
        else if (ancestor.get(key) instanceof JSONArray)
          for (Object e : (JSONArray) ancestor.get(key)) { // traverse the JSONArray
            if ((e instanceof JSONObject) && ((JSONObject) e == descendant))
              return ancestor;
          } // for (JSONArray)
      } // if (ancestor.has(key))
    } // for
    return null;
  }
  
  public Object getKeyValueFromObject(JSONObject o) {
    if ((o == null) || (this.getKey() == null)) return null;
    if (this.isObjectInMappedObjects(o))
      return (o.has(this.getKey())) ? o.get(this.getKey()) : null;
    else return null;
  }
  
  public Object getMappedObjectsThatContainValue(String target) {
    Set<JSONObject> set = new HashSet<JSONObject>();
    for (JSONObject o : _mappedObjs)
      for (String k : o.keySet())
        if ((!o.isNull(k)) && (Builder.stringMatching(o.get(k).toString(), target)))
          set.add(o);
    return set;
  }
  
  public boolean isValueInJSONArray(String target) { // does not support the processing of nested arrays!
    Object lastStep = _JSON_mappingObjs[_JSON_mappingObjs.length - 1]; // traverses the pointed (last) array.
    if (isLastStepJSONArray())
      for (Object value : ((JSONArray) lastStep)) // we assumed that the array only holds values and not objects.
        if (Builder.stringMatching(value.toString(), target)) // the target value is found in the array.
          return true;
    return false;
  }
  
  public boolean isLastStepJSONArray() {
    Object lastStep = _JSON_mappingObjs[_JSON_mappingObjs.length - 1];
    return (lastStep instanceof JSONArray);
  }
  
  public JSONArray getLastStepJSONArray() {
    if (isLastStepJSONArray())
      return (JSONArray) _JSON_mappingObjs[_JSON_mappingObjs.length - 1];
    return null;
  }

  private void copyRelevantPropertiesToRoot(JSON_Path from) {
  // copy relevant properties to the root JSON_Path object for accessibility
    _pointerTo_Object   = from.getPointerToObject();
    _pointerTo_lastStep = from.getKey();
    _flag               = from.getFlag();
    _entity             = from.getEntity();
    _onlyFor            = from.getOnlyFor();
    _asBoolean          = from.isBoolean();
    _mappedObjs         = from.getMappedObjects();
    _mappedVals         = from.getMappedValues();
    if (this.isWHEREcondition()) // copy results of the WHERE condition
      from = _JSON_paths[1];
    _WHERE_condition_OP       = from.getOperator();
    _WHERE_condition_constant = from.getConstant();
  }

  public boolean process() {
    if (this.hasMultiplePaths()) { // if (multiple JSON paths)
      String paths[] = _JSON_mapping_value.split( this.getMultiPathType() );
      _JSON_paths = new JSON_Path[paths.length];
      int p = 0;
      //System.out.printf("<JSON_Path[%s].process()> Paths: \n", _JSON_paths_position);
      for (String path : paths) { // for each path
        //System.out.printf("[%s]=\"%s\",\n", p, path);
        if (this.isMultiPart()) {
          _JSON_multipart_main_path = ((p == 0) ? path : _JSON_multipart_main_path); // path 0 is the main path.
          _JSON_paths[p] = new JSON_Path(_root, _JSON_multipart_main_path, path, p);
        } else
          _JSON_paths[p] = new JSON_Path(_root, path, p);
        p++;
      } // for

      if (this.isOP()) { // for an object property; two components: domain and range; multiline path
        for (p = 0; p < this.getPathsLength(); p++)
          _JSON_paths[p].setOPcomponent();
        return this.processAllPathsWithOneSuccess(); // success if at least one
      }
      
      if (isJSON_equal()) { // both successful and a subset of the JSON mapped values should match:
        if (this.processAllPathsWithAllSuccess()) {
          for (_JSON_paths_pointer = 0; _JSON_paths_pointer < this.getPathsLength(); _JSON_paths_pointer++) // for each path
            _JSON_paths[_JSON_paths_pointer].getMappedValues(); // process the mapping objects/values.
          _JSON_paths_pointer = 0; // resets counter ("points to the root")
          for (p = (this.getPathsLength()-1); p > 0; p--) { // processing from right to left.  Stops with the first two paths: [0], [1]
            if (!_JSON_paths[p-1].removeValuesThatAreNotEqualFromSet(_JSON_paths[p])) // no values were matched between [p] and [p-1]
              return (_parsingSuccess = false);
          }
          copyRelevantPropertiesToRoot(_JSON_paths[0]); // copy @<entity> value (if any)
          return (_parsingSuccess = true); // all successful.
        }
        return (_parsingSuccess = false);         
      } // if (isJSON_equal())
      
      if (this.isOR()) { // only one successful: the first one that is successful.
        if (this.processAllPathsWithOneSuccess()) // at least one path was successful
          for (_JSON_paths_pointer = 0; _JSON_paths_pointer < this.getPathsLength(); _JSON_paths_pointer++) // for each path
            if (_JSON_paths[_JSON_paths_pointer].wasParsingSuccessful()) {
              copyRelevantPropertiesToRoot(_JSON_paths[_JSON_paths_pointer]);
              _JSON_paths_pointer = -1; // resets counter ("points to the root")
              return (_parsingSuccess = true);
            } // if
        _JSON_paths_pointer = -1;
        return (_parsingSuccess = false);
      } // if (isOR())

      if (this.isWHEREcondition()) { // Expected structure: <path>%<constant_path>
        this.processAllPathsWithOneSuccess(); // success if at least one
        _parsingSuccess = this.isWHEREpathSuccessful();
        if (_parsingSuccess)
          copyRelevantPropertiesToRoot(_JSON_paths[0]);
        //_JSON_paths[0].print(true);
        //_JSON_paths[1].print(true);
        return _parsingSuccess;
      } // if (isWHEREcondition())

      if (this.isAND_in_WHERE() || this.isFORmapping()) { // all <WHERE_paths> successful || <path>@:<path>.
        if (this.processAllPathsWithAllSuccess()) {
          copyRelevantPropertiesToRoot(_JSON_paths[0]); // from the first path
          _JSON_paths_pointer = 0; // resets counter ("points to the root")
          return (_parsingSuccess = true); // all successful.
        }
        return (_parsingSuccess = false);
      } // if (isAND_in_WHERE() || isFORmapping())

      if (this.isMultiPart()) {
        //System.out.printf("<process() - isMultiPart()>\n");
        for (_JSON_paths_pointer = 0; _JSON_paths_pointer < this.getPathsLength(); _JSON_paths_pointer++) { // for each path
          if (!_JSON_paths[_JSON_paths_pointer].process()) // parse each part
            return (_parsingSuccess = false); // all paths must be parsed successfully
        }
        _JSON_paths_pointer = 1; // starts from the first part.
        return (_parsingSuccess = true);
      }

      if (this.isMultiLine())  // process all.
        return this.processAllPathsWithOneSuccess(); // success if at least one
    } // if (multiple JSON paths)

    // A single path: the current object
    _JSON_paths = null;
    _JSON_paths_pointer = 0;
    return this.parse();
  }

  public JSON_Path(JSONObject _doc, String path, int position) {
    init();
    _root = _doc; // starts to traverse the JSON Object from the root
    _JSON_mapping_value = path.strip(); // removes blank spaces
    _JSON_paths_position = position;
    //System.out.printf("<JSON_Path[%s].JSON_Path() : JSON-mapping> %s\n", _JSON_paths_position, _JSON_mapping_value);
  }
  
  public JSON_Path(JSONObject _doc, String mainPath, String partPath, int position) {
    this(_doc, partPath, position);
    _JSON_multipart_main_path = mainPath;
  }
}

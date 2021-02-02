/**
 * 
 */
package au.edu.anu.cecs.rscs.agrif_project;

import java.util.Iterator;

import org.json.JSONObject;

import com.hp.hpl.jena.datatypes.xsd.XSDDatatype;

/**
 * @name Graph-0-Builder / Document.
 * @author Sergio.
 * @purpose Abstracts the graph creation for any document.
 * @project AGRIF.
 */
public class Document {
  private JSONObject _doc;
  private JSONObject _gm;
  private JSONObject _sm;

  private String _id;
  private String _instance_doc;

  /******************************************************************************************************************************/
  // It might not exist:
  private String tryBuildSubGraph_CREATED(JSONObject o, String c) {
    return ((o.has(c)) ? buildSubGraph_CREATED(o.getString(c)) : "");
  }


  private String buildSubGraph_CREATED(String dt_created) {
    Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "created", Builder._dt(dt_created), null, XSDDatatype.XSDdateTime);
    String class_CreationEvent    = Builder.newClassURI("CreationEvent");
    String instance_CreationEvent = Builder.newInstanceURI("CreationEventForDoc", _id);
    Graph.addObject(instance_CreationEvent, Builder._onto_is_a, class_CreationEvent);
    Graph.addObject(_instance_doc,          Builder._onto_ag_ns  + "isAffectedBy", instance_CreationEvent);
    Graph.addLiteral(instance_CreationEvent, Builder._onto_ag_ns + "atTime", Builder._dt(dt_created), null, XSDDatatype.XSDdateTime);
    Builder.addLabel(instance_CreationEvent, ("Creation Event at " + dt_created));
    return instance_CreationEvent;
  }


  private void buildSubGraph_PERSON(String tryAuthor, JSONObject _metadataObj,
      String _metadataKey, String instance_CreationEvent) {
    String instance_Person = tryAuthor.replace(" ", ""); // [null]
    String label_Person = tryAuthor;
    if (Builder._isNullOrEmpty(instance_Person)) { // it might be [null]:
      instance_Person = Builder._mightHaveKey(_metadataObj, _metadataKey, true); // [null]
      label_Person    = Builder._mightHaveKey(_metadataObj, _metadataKey, false);
    }
    if (Builder._isNOTnull(instance_Person)) {
      String class_Person = Builder.newClassURI   ("Person");
          instance_Person = Builder.newInstanceURI("Person", Builder._uuid(""));
      Graph.addObject(instance_Person, Builder._onto_is_a, class_Person);
      Builder.addLabel(instance_Person, label_Person);
      if (instance_CreationEvent.length() > 1) // there's a CreationEvent:
        Graph.addObject(instance_CreationEvent, Builder._onto_ag_ns + "wasAssociatedWith", instance_Person);
    }
  }


  private void buildSubGraph_INFORMATION_SYSTEM(String tryProducer,
      JSONObject _metadataObj, String _metadataKey) {
    // it might be [null]...
    String instance_InformationSystem = tryProducer.replace(" ", ""); // [null]
    if (Builder._isNullOrEmpty(instance_InformationSystem))
      instance_InformationSystem = Builder._mightHaveKey(_metadataObj, _metadataKey, true); // [null]
    if (Builder._isNOTnull(instance_InformationSystem)) {
      String class_InformationSystem = Builder.newClassURI("InformationSystem");
      String label = instance_InformationSystem;
      instance_InformationSystem = Builder.newInstanceURI("InformationSystem", instance_InformationSystem);
      Builder.addLabel(instance_InformationSystem, label);
      Graph.addObject(instance_InformationSystem, Builder._onto_is_a, class_InformationSystem);
      Graph.addObject(_instance_doc, Builder._onto_ag_ns + "isChangedBy", instance_InformationSystem);
    }
  }


  private void buildSubGraph_SOFTWARE_ID(JSONObject o, String SID) {
    SID = Builder._mightHaveKey(o, SID, false); // [null]
    if (Builder._isNOTnull(SID))
      Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "softwareAssignedID", Builder._str(SID), null, XSDDatatype.XSDstring);
  }


  private void buildSubGraph_TITLE(String tryTitle, JSONObject _metadataObj, String _metadataKey, String doc_label) {
    String title = tryTitle; // it might be [null]
    if (Builder._isNullOrEmpty(title)) { // try in the referenced metadata object.
      title = Builder._mightHaveKey(_metadataObj, _metadataKey, false); // [null]
      if (Builder._isNullOrEmpty(title)) // by default...
        title = _doc.getString("Use-Case$Folder") + " / " + _gm.getString("FILENAME"); // ... use filename.
    }
    Graph.addLiteral(_instance_doc, Builder._onto_dc_ns + "title", Builder._str(title), "en", null); // lang:en (rdfs:Literal)
    // Adds the label for the document:
    String label = doc_label; // default value: "<FILENAME> | <MODIFIED_DATE>"
    int p = doc_label.lastIndexOf(title);
    if (p == -1) { // the title is NOT part of the filename:
      label = title + "; " + doc_label;
    }
    Builder.addLabel(_instance_doc, label);
  }


  private void buildSubGraph_KEYWORDS(JSONObject obj, String key) {
    if (obj.has(key)) {
      String keywords = obj.get(key).toString(); // it might be [null]
      if (Builder._isNOTnull(keywords)) {
        String[] splited = keywords.split((keywords.indexOf(',') == -1) ? "\\s+" : ","); // space or comma?:
        for (int i = 0; i < splited.length; i++)
          Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "subject",
              Builder._str(splited[i]), "en", XSDDatatype.XSDstring); // English
      }
    }
  }


  /******************************************************************************************************************************/
  public Document(JSONObject doc, String ID) {
    _doc = doc;
    // If ID is empty, gets the ID from the JSONObject.
    _id = (ID.length() == 0) ? _doc.getString("_id") : ID;
    _instance_doc = Builder.newInstanceURI("DigitalArtefact", _id);
    _gm = _doc.getJSONObject("General-Metadata");
    if (_doc.has("Specific-Metadata")) {
      // For CSV documents, _sm is a String.
      if (!_doc.isNull("Specific-Metadata"))
        _sm = (_doc.get("Specific-Metadata") instanceof JSONObject)
             ? _doc.getJSONObject("Specific-Metadata") : null;
     else _sm = null;
     _sm = ((_sm != null) && (_sm.isEmpty())) ? null : _sm;
    } // Specific-Metadata
    /*
     * _OLE = ((_sm != null) && (_sm.has("OLEfile-Structure"))) ?
     * _sm.getJSONObject("OLEfile-Structure") : null;
     */
  }


  public Document(JSONObject doc) { this(doc, ""); }


  // Constructor for e-mail attachments and zip file content:
  public Document(Boolean isInnerFile, JSONObject metadataObj) {
    this(metadataObj.getJSONObject("metadata"), metadataObj.getString("_id"));
  }


  /*
   * @REFERENCES: https://www.w3.org/TR/turtle/#turtle-literals
   */
  public void buildDocGraph() {
    // DigitalArtefact instance:
    String class_DigitalArtefact = Builder.newClassURI("DigitalArtefact");
    Graph.addObject(_instance_doc, Builder._onto_is_a, class_DigitalArtefact);

    // Record instance:
    String instance_rec = Builder.newInstanceURI("Record", _id);
    String class_Record = Builder.newClassURI("Record");
    Graph.addObject(instance_rec, Builder._onto_is_a, class_Record);
    Graph.addObject(_instance_doc, Builder._onto_ag_ns + "recordOf", instance_rec);
    Builder.addLabel(instance_rec, ("Record " + _id));

    // AGRIF Object Properties (from DC, DCT, dcat):
    Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "filename",
        Builder._str(_gm.getString("FILENAME")), null, XSDDatatype.XSDstring);
    if (_gm.has("FILELENGTH")) // for "inner files" it could not exist:
      Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "filesize",
        Builder._int(_gm.getNumber("FILELENGTH")), null, XSDDatatype.XSDinteger);
    // for "inner files" it could be null:
    if (Builder._isNOTnull(_gm.get("TYPE").toString()))
      Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "format",
          Builder._str(_gm.getString("TYPE")), null, XSDDatatype.XSDstring);
    Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "modified",
        Builder._dt(_gm.getString("MODIFIED")), null, XSDDatatype.XSDdateTime);
    Graph.addLiteral(_instance_doc, Builder._onto_ag_ns + "downloadURL",
        Builder._uri(Builder._endpoint + "/" + Builder._db + "/" + _id), null, XSDDatatype.XSDanyURI);
    // label for the document:
    String doc_label = _gm.getString("FILENAME") + "; " + _gm.getString("MODIFIED");

    String instance_CreationEvent = "";
    // Any document instance will have a label.
    // We'll try to create the label with title or subject values.
    // If they are not present, we take the filename:
    boolean createLabelForDoc = false; // try with title or subject.

    switch (_gm.getString("EXTENSION").toUpperCase()) {
    /*********************************************************************************************/
    case "PDF": // For PDF files:
      // *** We will use the "last modification date" as the "creation date":
      instance_CreationEvent = tryBuildSubGraph_CREATED(_gm, "MODIFIED");
      // if there's no specific PDF metadata.
      if ((!_doc.has("PDF-META") && (_sm == null)) ||
          (!_doc.has("PDF-META") && (_sm.has("PDF.Properties-Processing-Exception"))))
        break;
      JSONObject pdf = (!_doc.has("PDF-META")) ? _sm : _doc.getJSONObject("PDF-META");
      // if not "PDF-META"."TITLE" then _sm."PDF.Title":
      buildSubGraph_TITLE(Builder._tryElse(pdf, "TITLE", "PDF.Title"), _sm, "PDF.Title", doc_label);
      buildSubGraph_SOFTWARE_ID(pdf, "DOC-ID");
      // if not "PDF-META"."PRODUCER" then _sm."PDF.Producer":
      buildSubGraph_INFORMATION_SYSTEM( Builder._tryElse(pdf, "PRODUCER", "PDF.Producer"), _sm, "PDF.Producer");
      // if not "PDF-META"."AUTHOR" then _sm."PDF.Author":
      buildSubGraph_PERSON(Builder._tryElse(pdf, "AUTHOR", "PDF.Author"), _sm, "PDF.Author", instance_CreationEvent);
      buildSubGraph_KEYWORDS(pdf, "KEYWORDS");
    break; // For PDF files.

    /*********************************************************************************************/
    case "DOCX":
    case "DOCM":
    case "PPTX":// For DOCX, DOCM, PPTX files:
      if (_sm == null) break;
      JSONObject dp = _sm.getJSONObject("document-properties");
      buildSubGraph_TITLE(dp.getString("title").toString(), dp, "title", doc_label);
      buildSubGraph_SOFTWARE_ID(dp, "identifier");
      buildSubGraph_INFORMATION_SYSTEM(_gm.get("TYPE").toString(), _gm, "TYPE");
      // *** We will use the "last modification date" as the "creation date":
      instance_CreationEvent = tryBuildSubGraph_CREATED(dp, "modified");
      buildSubGraph_PERSON(dp.getString("author"), dp, "author", instance_CreationEvent);
      buildSubGraph_KEYWORDS(dp, "keywords");
    break; // For DOCX, DOCM files.

    /*********************************************************************************************/
    case "DOC": // For DOC files:
      if (_sm == null) break;
      buildSubGraph_TITLE((_sm.has("title")) ? _sm.getString("title").toString() : "", _sm, "title", doc_label);
      buildSubGraph_INFORMATION_SYSTEM(
        (_sm.has("creating_application")) ? _sm.get("creating_application").toString() : "", _sm, "creating_application");
      // *** We will use the "last modification date" as the "creation date":
      instance_CreationEvent = tryBuildSubGraph_CREATED(_sm, "last_saved_time");
      buildSubGraph_PERSON(
        (_sm.has("last_saved_by")) ? _sm.getString("last_saved_by") : "", _sm, "author", instance_CreationEvent);
      buildSubGraph_KEYWORDS(_sm, "keywords");
    break; // For DOC files.

    /*********************************************************************************************/
    case "CSV":
    case "XLSX": // For CSV, XLSX files:
      createLabelForDoc = true;
      tryBuildSubGraph_CREATED(_gm, "MODIFIED");
      buildSubGraph_INFORMATION_SYSTEM(_gm.get("TYPE").toString(), _gm, "TYPE");
    break;

    /*********************************************************************************************/
    case "MSG": // For MSG files:
      buildSubGraph_INFORMATION_SYSTEM(_gm.get("TYPE").toString(), _gm, "TYPE");
      if (_sm == null) break;
      buildSubGraph_TITLE(_sm.getString("subject").toString(), _sm, "subject", doc_label);
      instance_CreationEvent = tryBuildSubGraph_CREATED(_sm, "sent-on");
      buildSubGraph_PERSON(_sm.getString("sender-name"), _sm, "sender-email-address", instance_CreationEvent);

      // Attachments:
      Iterator<String> attachments = _sm.getJSONObject("attachments").keys();
      int i = 0;
      String attachment_key, attachment_id;
      JSONObject attachment_metadata, am;
      while (attachments.hasNext()) {
        ++i;
        attachment_key = attachments.next();
        // Sets the proper values for the attachment document:
        attachment_metadata = _sm.getJSONObject("attachments").getJSONObject(attachment_key);
        attachment_id = _id + "-" + attachment_key;
        attachment_metadata.put("_id", attachment_id);
        am = attachment_metadata.getJSONObject("metadata");
        if ((!am.isEmpty()) && (!attachment_metadata.isNull("metadata"))) {
          am.getJSONObject("General-Metadata").put("FILELENGTH", attachment_metadata.getLong("size"));
          am.getJSONObject("General-Metadata").put("MODIFIED"  , _gm.getString("MODIFIED"));
          Builder.processInnerFile(attachment_metadata);
          // relations for the logical collection:
          Graph.addObject(
            Builder.newInstanceURI("DigitalArtefact", attachment_id),
            Builder._onto_ag_ns + "isPartOf", _instance_doc);
        } // if (am is not empty)
      } // while
      if (i > 0) // if there's at least 1 attachment:
        // the e-mail is a logical collection:
        Graph.addObject(_instance_doc, Builder._onto_is_a, Builder.newClassURI("LogicalCollection"));
    break; // For MSG files.

    /*********************************************************************************************/
    case "ZIP": // For ZIP files:
      createLabelForDoc = true;
      buildSubGraph_INFORMATION_SYSTEM(_gm.get("TYPE").toString(), _gm, "TYPE");
      // *** We will use the "last modification date" as the "creation date":
      instance_CreationEvent = tryBuildSubGraph_CREATED(_gm, "MODIFIED");
      if (_sm == null) break;
      // Attachments:
      Iterator<String> files = _sm.keys();
      String file_key, file_id;
      JSONObject file_metadata, fm;
      while (files.hasNext()) {
        file_key = files.next();
        // Sets the proper values for the *file* document:
        file_metadata = _sm.getJSONObject(file_key);
        file_id = _id + "-" + file_key;
        file_metadata.put("_id", file_id);
        fm = file_metadata.getJSONObject("metadata");
        if ((!fm.isEmpty()) && (!file_metadata.isNull("metadata"))) {
          fm.getJSONObject("General-Metadata").put("MODIFIED", _gm.getString("MODIFIED"));
          Builder.processInnerFile(file_metadata);
          // relations for the logical collection:
          Graph.addObject(Builder.newInstanceURI("DigitalArtefact", file_id),
            Builder._onto_ag_ns + "isPartOf", _instance_doc);
        } // if (fm is not empty)
      } // while
      // The zip file is a logical collection:
      Graph.addObject(_instance_doc, Builder._onto_is_a, Builder.newClassURI("LogicalCollection"));
    break; // For ZIP files.

    /*********************************************************************************************/
    default: // For any other file type.
      createLabelForDoc = true;
      tryBuildSubGraph_CREATED(_gm, "MODIFIED");
    } // switch(EXTENSION)

    if (createLabelForDoc) // use filename (default value):
      Builder.addLabel(_instance_doc, _gm.getString("FILENAME"));

    // Domain-ontology graph-0 creation:
    //final String _DoEE_Assessments_ENTRY  = "DoEE-Assessments";
    final String _DoEE_Species_ENTRY      = "DoEE-Species";
    final String _DoEE_Species_MAIN_CLASS = "CA_RP_Publication";
    String _doc_id = _instance_doc;
    if (Builder._exec.equals(_DoEE_Species_ENTRY)) _doc_id = _id;
    DomainOntologyGraph DOG = new DomainOntologyGraph(_doc, _doc_id);
    if (DOG.canBuild_g0()) { // checks if it can build g0
      DOG.build_g0();
      if (Builder._exec.equals(_DoEE_Species_ENTRY))
        Graph.addObject(_instance_doc, Builder._onto_sameAs, DOG.getInstance(_DoEE_Species_MAIN_CLASS));
    }
  } // buildDocGraph(JSONObject)
}

package au.edu.anu.cecs.rscs.agrif_project;

import java.nio.file.Files;
import java.nio.file.Paths;

import org.json.JSONObject;

/**
 * @name Graph-0-Builder / ConfigJSON.
 * @author Sergio.
 * @purpose Access to config.json file.
 * @project AGRIF.
 */
public class ConfigJSON {
  public static String _path = "config.json";
  public static JSONObject _json = null;
  public static JSONObject _exec = null;

  static {
    try {
      String content = new String(Files.readAllBytes(Paths.get(_path)));
      _json = new JSONObject(content);
      _exec = _json.getJSONObject(_json.getString("$-exec"));
    } catch (Exception e) {
      System.out.println(e);
    }
  }

  /**
   * @param args
   */
  public static void main(String[] args) {
    System.out.println(_json.getJSONObject("VUS").getString("JDBC-connection"));
  }
}

/**
 * 
 */
package au.edu.anu.cecs.rscs.agrif_project;

/**
 * @name Graph-0-Builder / GraphScope.
 * @author Sergio.
 * @purpose Enumerated type that defines the graph scope.
 * @project AGRIF.
 */
public enum GraphScope {
  PRIVATE("PRIVATE"),
  PUBLIC("PUBLIC");

  private String scope;
  GraphScope(String s) { this.scope = s; }
  public String getGraphScope() { return this.scope; }
}
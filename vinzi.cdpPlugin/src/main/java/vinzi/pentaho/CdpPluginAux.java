package vinzi.pentaho;

/*
 * this class is the interface to the cdp-plugin that takes care of the compilation process.
 * and the dispatch of all calls.
 */

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.pentaho.platform.api.engine.IParameterProvider;
import org.pentaho.platform.api.repository.IContentItem;

import vinzi.pentaho.CdpHandlerInterface;
import clojure.lang.RT;
import clojure.lang.Var;

//public class CdpPluginAux implements CdpHandlerInterface (CdpHandlerObject is not used)
public class CdpPluginAux implements CdpHandlerInterface
{
  private static Log logger = LogFactory.getLog(CdpPluginAux.class);
  
//  private static RT = null;
  // could be a static
  static Var handler = null;

  private static final String PluginScript = "vinzi/pentaho/cdpPlugin.clj";
  
  public CdpPluginAux()
  {
	  logger.info("Running the initialization code of plugin. Retrieving the clojure RT-object");
	  
	  return;
  }
  
  public void initialize()
  /*
   *  Initialize the cdp-plugin by compiling the script and
   *  retrieving a var representing the handler.
   */
  {
	  try {
		  logger.info("Retrieving/compiling the clojure RT-object");
		  logger.info("Loading/compiling script: "+PluginScript);
		  RT.loadResourceScript(PluginScript);
		  
		  logger.info("Running the initialize function");
		  RT.var("vinzi.pentaho.cdpPlugin", "initialize").invoke();

		  logger.info("Retrieving handler from RT-object");
		  handler = RT.var("vinzi.pentaho.cdpPlugin", "handler");
		  logger.info("handler is set to"+handler);

	  } catch(Exception e) {
		  logger.error("Error during initialization in CdpPluginAux", e);
	  }

	  return;
  }
  
  public Object handler(IParameterProvider requestParams,
			IParameterProvider pathParams,
			IContentItem contentItem)
	{
	  logger.info("Calling cdpPlugin");
	  Object result = handler.invoke((Object) requestParams, 
			      (Object) pathParams, 
			      (Object) contentItem);

//	  String resStr = (result != null) ? result.toString() : "";
	  logger.info("cdp-plugin result-object will be discarded (actual result written to stream): "+
	     ((result != null) ? result.toString() : ""));

	  return null;
	}

}
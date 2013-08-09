(ns vinzi.pentaho.cdpPlugin
  (:use	   [clojure pprint]
	   [clojure.tools logging])
  (:require [clojure
	     [zip :as zip]
	     [string :as str]]
	    [clojure.java
	     [io :as io :only [reader]]
	     [jdbc :as sql]]
	    [clojure.data
	     [json :as json]]
     [vinzi.pentaho
      [connect :as conn]
      [resultSet :as rs]]
     [vinzi.cdp.core :as cdp]
     [vinzi.tools
      [vFile :as vFile]
      [vSql :as vSql]
      [vExcept :as vExcept]])
  (:import [vinzi.cdp.core stream-file-def ext-query])
;; TODO: check whether this could be dynamic
    ;;
    ;;  switched to dynamic class-loading
    ;; resulting in a more reliable dispatch
    ;;  when using gen-class you also need to uncomment the interface functions at the end.
;    (:gen-class 
;        :implements [vinzi.pentaho.CdpHandlerInterface]
;        :state state
;        :init init
;        :constructors {[] []})
    )




(defn trace_tmp [& args]  (apply println args))




;;;;;;;;;;;;;;
;;  only for internal use
;;


  (def testReq {:pPars {:path "/exec", :remoteaddr "0:0:0:0:0:0:0:1", :httprequest "#<SavedRequestAwareWrapper org.springframework.security.wrapper.SavedRequestAwareWrapper@1b79e95>", :contentType nil, :inputstream "#<CoyoteInputStream org.apache.catalina.connector.CoyoteInputStream@18bf5a9>", :httpresponse "#<OnRedirectUpdateSessionResponseWrapper org.springframework.security.context.HttpSessionContextIntegrationFilter$OnRedirectUpdateSessionResponseWrapper@c68e1>", :query "solution=EIS&path=&file=test.eis"}
		:rPars {:path "", :solution "EIS", :file "test.eis",
			:accessId "insertRow", :rowId "45"}
		:content nil
		})


;;;;;;;;;;;;;;
;;   Auxiliary functions
;;



(def MimeTypes {:plain "text/plain"
		:html  "text/html"
		:xml  "text/xml"
		:css   "text/css"
		:js    "text/javascript"
		:json  "application/json"
    :pdf  "application/pdf"
    :csv "application/xls"                   ;; using octet-stream to force download
    :xls "application/vnd.ms-excel"})        ;; Mimetype required by Proigia/desktop to run depseudo (should be apache poi file/old style excel)


(def HttpResponseCodes
     {:continue javax.servlet.http.HttpServletResponse/SC_CONTINUE 
      :not_acceptable javax.servlet.http.HttpServletResponse/SC_NOT_ACCEPTABLE
;;      :not_acceptable javax.servlet.http.HttpServletResponse/SC_ACCEPTED 
      :int_err javax.servlet.http.HttpServletResponse/SC_INTERNAL_SERVER_ERROR})



(defn extract-file [req]
  (if-let [rPars (:rPars req)]
    (let [{:keys [path solution file]} rPars
          fName (->> (list solution path file)
                  (remove nil?)
                  (map str/trim)
                  (filter seq)
                  (str/join vFile/FileSep))]
 ;         fName (filter identity (map str/trim (list solution path file)))
 ;         fName (filter #(> (count %) 0) fName)
 ;         _ (info "fName = " fName "  with count " (count fName))
 ;         fName (str/join vFile/FileSep fName)]
      (info "  after join " fName)
      (vFile/filename (conn/get-solution-folder) fName))
    (error "(extract-file): Received nil-request")))



(defn get-pars 
  "Turn a pentaho object implementing the IParameterProvider interface into a Clojure Hashmap 
(the keys will be keywordized).
  If the parameter __strip_param is set to string 'true' then 'param' prefixes are stripped
   and other parameters (without prefix) are dropped." [pentParams]
  (let [lpf "(get-pars): "
        pni (.getParameterNames pentParams)
        ;; find list of all keys
        k (loop [cumm ()]  ;; pni is mutable java-object (not needed in loop)
            (if (.hasNext pni) 
              (let [kk (.next pni)]
                (recur (conj cumm kk)))
              cumm))
        res (zipmap (map keyword k)
                    (map #(.getParameter pentParams %) k))
        _    (debug lpf " extracted params: " res) 
        strip-param (fn [m] 
                        ;; take all parameters that start with 'param' and strip 'param'. 
                        ;; if :paramABC and :ABC both exists then the value of :paramABC overrules the value of :ABC
                        ;;  So cummulator contains two hash-maps and which will be merged afterwards.
                        (let [r (reduce (fn [cumm [k v]] 
                                  (let [ks (name k)]
                                    (if (.startsWith ks "param")
                                      [ (first cumm) (conj (second cumm) [ (->> ks (drop 5) (apply str) (keyword)) v])]
                                      [ (conj (first cumm) [k v]) (second cumm)] )))
                                [{} {}] m)
                              ;; second set overrules the first set.
                              r (into (first r) (second r))]
                          (debug lpf " strip-param reduced params to: " r)
                          r))
        res (if (when-let [sp (:__strip_param res)] (= sp "true"))
              (strip-param res) res)]
    res))

;; (def configCache (atom {}))


(defn clear-standard-params "Remove the standard parameters from the rPars, (as these should have been processed)." [rPars]
  (dissoc rPars :path :solution :file))



(defn get-config-file-req "Extract the config-file from the request and retrieve the configuration (from sqlTemplates)."
  [req]
  (let [lpf "(get-config-file): "]
    (if-let [fName (extract-file req)]
      (do 
        (println  "WARN: to be reimplemented")
        (throw (Exception. "reimplement")))
;;      (vSqlT/get-config-file fName)
      (warn lpf "Could not extract filename from request."))))





(defn get-request-dbName 
  "Get the database connection that belongs to the given request
  (db-connection is the base-connection of the defined .cdp.xml file)." 
  [req]
  (when-let [cfg (get-config-file-req req)]
    (if-let [ds (-> cfg
		    :datasources
		    :children
		    :datasource)]
      (let [dbName (:name ds)]
	(debug "(get-request-dbName): obtained dbName="dbName)
	dbName))))


;;;;;;;;;;;;;;
;;   External methods (implementing functions)
;;



(defn hello-handler "Show the request-parametes in text/plain as result." [req]
  (let [{:keys [rPars pPars content]} req
	cDir (java.io.File. ".")
	cDir (str "Current dir = " (.getAbsolutePath cDir))
	results (str "Received call with:\n"
		     ;; (showObject "request"  req) 
		     " with request-params: " rPars "\n\n"
		     ;;   (showObject "params"   pars)
		     " with params: " pPars "\n\n"
       (println "\t(Show-object content) temporarily removed!!")
;;		     (showObject "content" content) "\n"
		     cDir)]
    (trace "Received: " results)
    {:plain results}))


(defn handler [pathPars requestPars cont]
  (info " entered the CDP-handler")
  (let [lpf "(cdpPlugin/handler): "
        outStream (.getOutputStream cont nil)
        pPars (get-pars pathPars)
        method (:path pPars)
        httpResp (:httpresponse pPars)
        rPars (get-pars requestPars)
        _ (debug lpf "rPars = " (with-out-str (pprint rPars)) 
                    "\n and pPars = " (with-out-str (pprint pPars)))
        ;; this code does not see defaults, defined in the .cdp.xml, only parameters present in the post will be visible.
;        [rPars streaming] (if-let [webStream (:webStream rPars)]
;                            (if (= (str/lower-case (str/trim webStream)) "true")
;                              (do  ;; method to let a plugin write directly to the outputstream (to the web-client)
;                                (info lpf "Replace parameter webStream=" webStream " with the web-output-stream")
;                                [(assoc rPars :webStream outStream) true])
;                              [rPars false])
;                            [rPars false])
;        _   (debug lpf " streaming = " streaming)
        req {:rPars rPars :pPars pPars :httpResp httpResp :content cont}
        ]
    (letfn [(exec-handler 
              ;; extract file and the action as specified in the file. 
              [req method]
              (let [lpf "(cdpHandler/exec-handler): "] 
                (if-let [sqlTempl (extract-file req)]
                  (let [rPars (clear-standard-params (:rPars req))
                        {:keys [accessId]} rPars
                        rPars (dissoc rPars :accessId)]
                    (debug "extracted file: " sqlTempl " and request-params: " rPars)
                    (if (= method "/exec") 
                      (cdp/exec-query-ext sqlTempl accessId rPars)
                      (if (= method "/get-accessids")
                        (let [accessIds (cdp/get-accessIds-fileOrder sqlTempl)]
                          (info "Retrieved accessIds for " sqlTempl ":\n\t " 
                                (str/join "\n\t" accessIds))
                          accessIds)
                        (assert false))))
                  (let [msg (str "failed to retrieve file-name from: "
                                 (with-out-str (:rPars req)))]
                    (error msg)
                    (throw (Exception. msg))))))          
            (initialize-response 
              []
              (when httpResp
                ;; set the default response header
                (.setHeader httpResp "Content-Type" (:plain MimeTypes))
                (.setHeader httpResp "Cache-Control" "max-age=0, no-store")
                ;; next line omitted as it is not present in cda-plugin either.
                ;;   (assume succesfull execution)
                ;; (.setStatus httpResponse (:continue HttpResponseCodes))
                ))
            (process-request 
              ;; process request (exceptions will bubble to top-level handler)
              []
              (info "The method is: " method)
              ;; same trick as java.repl to create a thread-local binding
              ;; otherwise you can not fiddle with *ns*
              (binding [*ns* *ns*]
                (let [res (if (= method "/hello")
                            (hello-handler req)
;                            (if (= method "get-accessIds")   ;; can be skipped. Use code from exec-handler
;                              (do
;                                (warn lpf "Using outdated method get-accessIds. use /get-accessids")
;                                (json/json-str (cdp/get-accessIds-fileOrder)))
                            (if (or (= method "/exec") 
                                    (= method "/get-accessids"))
                              (exec-handler req method)
                              (if (= method "/clear-cache")
                                (cdp/clear-cache)
                                  (let [msg (str lpf "no handler for method: " method)]
                                    (error msg)
                                    (throw (Exception. msg))))))]
                  (info lpf "Received result-set of type: " (type res) " with meta: " (meta res) " with count: " (count res))
                  res)))
            (extend-response 
              [res]
              (debug lpf " (initial) httpResp = " (with-out-str (pprint httpResp))
                     (comment "\n\t(extend-response) initial-res=" res) )
                (let [[res queryDescr] (if (= (type res) vinzi.cdp.core.ext-query)
                                         [(:results res) (:queryDescr res)]
                                         [ res nil])
                           
                      resType (:cdp/type (meta res))]
                  (debug lpf " the result type: " resType)
                  (if (or (string? res) (nil? res))
                    (do
                      (.setHeader httpResp "Content-Type" (:html MimeTypes))
                      (->> (str res)
                        (.getBytes)
                        (.write outStream)))
                    (if (= :stream-file resType)  
                      (let [fres (first res)]
;                        (info "processing result type:" resType)
                        ;;(.setHeader httpResp "Content-Type" (resType MimeTypes))
                        ;;(let [fileName (first res)]
                         ;; (.setHeader httpResp "content-disposition" (str "attachment; filename=" fileName)))
                        
;                       (when (not streaming)
                        ;;  this (old) assumes pdf to be a file-name of an java.io.ByteArrayOutputStream and write it as binary to out
                          ;; currently the 
                          (info "Copy file/bytebuffer to stream")
                        (if (and (= (count res) 1) (= (class fres) vinzi.cdp.core.stream-file-def)) 
                                                  ;;     (isa? (class (first res)) java.io.ByteArrayOutputStream)))
                          (let [{:keys [fName tpe dataStream]} fres]
                            (if-let [mimeType (tpe MimeTypes)]
                              (do
                                (debug lpf "Setting mimeType of return-value to : " mimeType)
                                (.setHeader httpResp "Content-Type" mimeType))
                              (error lpf "no corresponding mimetype for type: " tpe))
                            ;; a html stream does not need a filename
                            (when (seq fName)
                              (.setHeader httpResp "content-disposition" (str "attachment; filename=" fName)))
                            (if (string? dataStream)
                              (with-open [f (io/input-stream dataStream)]
                                (loop []
                                  (when (> (.available f) 0)
                                    (.write outStream (.read f))
                                    (recur))))
                              (if (= (class dataStream) java.io.ByteArrayOutputStream)
                                (do
                                  (debug lpf " printing ByteArray to outputsteam")
                                  (.write outStream (.toByteArray dataStream)))
                                (error lpf " unknown  result dataStream of type: " (type dataStream)))))
                          (error lpf "expected result to be a single instance of vinzi.cdp.core.data-stream-def. "
                                 "Received input of type " (type res) 
                                 "  first 100 chars: " (apply str (take 100 (str res))))))
                      (do ;; it is a compound structure, so return json
                        (.setHeader httpResp "Content-Type" (:json MimeTypes))
                        (let [get-json (fn [res] 
                                         (if (map? (first res))
                                           (let [columnOrder (rs/derive-columnOrder (-> queryDescr :actionAttrs :columnOrder) (keys (first res)))]
                                             (debug lpf "columnOrder: " columnOrder)
                                             (rs/mapSeq-to-cdf-resultSet res columnOrder))
                                           (json/json-str res)))]
                        (->> res
                          (get-json)
                          (.getBytes)
                          (.write outStream))))))
                  nil));; return nil to signal to CdpContentGenerator that output has been written.
            ]
           (info "cdp: received request with params: "
                 (with-out-str (pprint rPars)))
           (initialize-response)
           (try
             (debug lpf " (enter cdp-plugin with) httpResp = " (with-out-str (pprint httpResp)))
             (let [res (-> (process-request) 
                         (extend-response))]  ;; return result on success
                   (debug lpf " (initial) httpResp = " (with-out-str (pprint httpResp)))
                   res)
             (catch Throwable e
               (let [msgPrefix (str "Exception/Throwable caught in method:" method " ")]
                  (warn lpf "caught exception in cdp-plugin. Return an error-code")
 ;;                 (debug lpf " httpResp = " (with-out-str (pprint httpResp)))
                  (vExcept/report msgPrefix e)
                  ;; do not rethrow, but set http-response to error
                  (.sendError httpResp (:not_acceptable HttpResponseCodes) 
                       (str msgPrefix (.getMessage e))))
                  (warn lpf "caught exception in cdp-plugin -- 2")
                  (debug lpf " httpResp = " (with-out-str (pprint httpResp)))
                    nil))))) ;; failure

        
(defn initialize 
  "The initialization routine assumes that this package is loaded from the 'system/cdp/lib/' folder of the pentaho-solution folder. 
   If this is the tail of the path returned by the loader then the pentaho-solution-folder is set accordingly."
  []  
  (let [lpf "(cdpPlugin/initialize): "
        CheckClass "vinzi/GetClassLocation.class"
        PentahoPathRegexp (re-pattern (str "/system/cdp/lib/vinzi.cdpPlugin-[0-9]+.[0-9]+.[0-9]+[-\\w]*.jar\\!/"
                                           CheckClass "$"))                                     
        gcl (vinzi.GetClassLocation.)
        loader (.getClassLoader (class gcl))
        locationUrl (.getResource loader CheckClass)
        location (-> locationUrl
                   (.getFile )
                   (str/replace #"\%20" " "))]   ;; urls contain %20 instead of spaces.
    ;; jar:file:/var/pentaho/bi/ps/system/cdp/lib/vinzi.cdp-0.0.2-SNAPSHOT-jar-with-dependencies.jar!/vinzi/GetClassLocation.class
    (debug lpf locationUrl)
    (trace lpf "string- " (str locationUrl))
    (trace lpf "toString- " (.toString locationUrl))
    (trace lpf "path- "   (.getPath locationUrl))
    (trace lpf "file-  "   (.getFile locationUrl))
    (trace lpf "authority- "   (.getAuthority locationUrl))
    
    (if-let [pentahoPath (re-find PentahoPathRegexp location)]
      (let [path (apply str (drop  (count "file:")
                        (take (- (count location) (count pentahoPath)) location)))]
        (debug lpf " setting pentaho solution folder to: " path)
        (conn/set-pentaho-solution-folder path))
      (error lpf " path " PentahoPathRegexp " is not the tail of location " 
             location " Apparantly not running in pentaho-server."))))


(comment  ;; interface when running via a gen-class. Currently gen-class is not used.
(defn -handler 
  "the interface used when cdpPlugin is translated to a java-class (Ignoring this)"
  [_ pathPars requestPars cont]
  (handler pathPars requestPars cont))

(defn -initialize 
  "The initialization routine assumes that this package is loaded from the 'system/cdp/lib/' folder of the pentaho-solution folder. 
   If this is the tail of the path returned by the loader then the pentaho-solution-folder is set accordingly."
  [_] 
  (initialize))

(defn -init
  "Add logging to the initialization step to track it."
  []
  (info "Now entering init of vinzi.pentaho.cdpPlugin")
  [[] "cdpInit completed"] ;; a fake state object
  )
) ;; end comment
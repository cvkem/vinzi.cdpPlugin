(defproject
 vinzi/vinzi.cdpPlugin
 "0.2.0-SNAPSHOT"
 :dependencies
 [[org.slf4j/slf4j-api "1.6.5"]
  [ch.qos.logback/logback-core "1.0.6"]
  [ch.qos.logback/logback-classic "1.0.6"]
  [vinzi/vinzi.tools "0.2.0-SNAPSHOT"]
  [vinzi/vinzi.pentaho "0.2.0-SNAPSHOT"]
  [vinzi/vinzi.data "0.1.0-SNAPSHOT"]
  [vinzi/vinzi.cdp "0.2.0-SNAPSHOT"]]


;                   :exclusions [org.clojure/clojure
;                                org.postgresql/postgresql
;                                commons-codec/commons-codec
;                                javax.servlet/servlet-api
;                                 ]
  :uberjar-exclusions [#"^javax/"
                       #"^clojure/lang/"
                       #"^org/postgresql/"
                       #"^org/pentaho/"
                        #"^org/apache/"
                       #"^org/springframework/"
                       #"^org/codehaus/"
                       #"^org/codehaus/"
                       #"^org/jaxen/"
                       #"^org/w3c/"
                       #"^org/dom4j/"
                       #"^org/aopalliance/"
                       ]

  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [commons-codec "1.3"]
                                  [commons-logging "1.1.1"]
                                  [javax.servlet/servlet-api "2.3"]
                                  [xerces/xercesImpl "2.9.1"]
                                  [pentaho/pentaho-bi-platform-api "3.9.0-stable"]
                                  [pentaho/pentaho-bi-platform-engine-core "3.9.0-stable"]
                                  [pentaho/pentaho-bi-platform-engine-services "3.9.0-stable"]
                                  [pentaho/pentaho-bi-platform-util "3.9.0-stable"]
                                  [dom4j "1.6.1"]
                                  [commons-lang "2.4"]
                                  ]
                   }}
 :java-source-paths ["javaSrc"]
 :deploy-repositories
 {:snapshots "no distrib-mgt", :releases "no distrib-mgt"}
 :repositories
 {"Pentaho-ext" "http://repository.pentaho.org/artifactory/repo",
  "Pentaho" "http://repository.pentaho.org/artifactory/pentaho",
  "Clojars" "http://clojars.org/repo",
  "Clojure Releases" "http://build.clojure.org/releases"}
)

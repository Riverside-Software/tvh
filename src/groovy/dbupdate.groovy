def cli = new groovy.util.CliBuilder(usage: 'java -jar dbupdates.jar dbupdate.groovy')
cli.dlc(args: 1, argName: 'dlc', 'OpenEdge installation directory')
cli.dbList(args: 1, argName: 'dbList', 'DB list file')
def options = cli.parse(args)

// ************************
// CRC management functions
// ************************

// This function reads a CRC file and generates data structures
def readCRCFile = { path, objects, allObjects ->
  matcher = (new File(path).text  =~ /(?i)(A|S|T|I)\s((\S*)\.(\S*)(\.(\S*))?)\s([?0-9]*)?\n?/)
  matcher.each {
    if ('S'.equals(it[1]) || 'A'.equals(it[1])) {
      objects.get(it[1]).add(it[2].toLowerCase())
      allObjects[it[1]].add(it[2].toLowerCase())
    } else {
      objects.get(it[1]).put(it[2].toLowerCase(), it[7])
      allObjects[it[1]].add(it[2].toLowerCase())
    }
  }
}
// This function compare two sets of CRC objects, and return true if they are identical
def compareCRCObjects = { expectedObjects, currentObjects, allObjects ->
  def identical = true
    // Compare sequences
    allObjects['S'].each {
      if (!expectedObjects['S'].contains(it)) {
        println "+++ S ${it}"
        identical = false
      }
      if (!currentObjects['S'].contains(it)) {
        println "--- S ${it}"
        identical = false
      }
    }
    // Compare storage areas - We only have to find missing storage areas in the updated DB
    allObjects['A'].each {
      if (expectedObjects['A'].contains(it) && !currentObjects['A'].contains(it)) {
        println "--- A ${it}"
        identical = false
      }
    }
    // Compare tables
    allObjects['T'].each {
      if (!expectedObjects['T'].get(it)) {
        println "+++ T ${it}"
        identical = false
      }
      if (!currentObjects['T'].get(it)) {
        println "--- T ${it}"
        identical = false
      }
      if (expectedObjects['T'].get(it) && currentObjects['T'].get(it) && !expectedObjects['T'].get(it).equals(currentObjects['T'].get(it))) {
        println "xxx T ${it} ${expectedObjects['T'].get(it)} ${currentObjects['T'].get(it)}"
        identical = false
      }
    }
    // Compare indexes
    allObjects['I'].each {
      if (!expectedObjects['I'].get(it)) {
        println "+++ I ${it}"
        identical = false
      }
      if (!currentObjects['I'].get(it)) {
        println "--- I ${it}"
        identical = false
      }
      if (expectedObjects['I'].get(it) && currentObjects['I'].get(it) && !expectedObjects['I'].get(it).equals(currentObjects['I'].get(it))) {
        println "xxx I ${it} ${expectedObjects['I'].get(it)} ${currentObjects['I'].get(it)}"
        identical = false
      }
    }
    return identical
}

// *******************************
// End of CRC management functions
// *******************************

// Step 1: region list file
println "Step 1 - Databases list"
println "-----------------------"
def databases = [:] // Empty map -- Structure: String -> Object[name: String, path: String, schema: String]
new File("${options.dbList}").eachLine { line ->
  if (line.trim().isEmpty()) return;
  def entries = line.split(':');
  if (entries.length < 3) {
    println "Not enough entries in line '${line}'"
    System.exit(1)
  }
  def fwSlPath = entries[1].replace('\\', '/')
  if (fwSlPath.lastIndexOf('/') > -1)
    fwSlPath = fwSlPath.substring(0, fwSlPath.lastIndexOf('/'))
  databases.put(entries[0].toLowerCase(), [name: entries[0].toLowerCase(), path: fwSlPath, schema: entries[2]])
}
databases.values().each { db -> println "Database '${db.name}' - Path '${db.path}' - Schema '${db.schema}'" }

// Step 1bis: check if process was interrupted
databases.values().each { db ->
  def idxs = new File("NewIndexes-${db.name}")
  if (idxs.exists()) {
    if (idxs.length() == 0) {
      println "Empty NewIndexes-${db.name}, deleting file"
      idxs.delete()
    } else {
      println "Indexes found in NewIndexes-${db.name}, interrupting process"
      System.exit(1)
    }
  }
}

// Step 2: check CRC
println ""
println "Step 2 - Check CRC"
println "------------------"
boolean crcDiff = false
try {
  databases.values().each { db ->
    def ant = new AntBuilder()
    ant.taskdef (name: 'PCTRun', classname: 'com.phenix.pct.PCTRun')
    ant.taskdef (name: 'PCTLoadSchema', classname: 'com.phenix.pct.PCTLoadSchema')

    println()
    println("Physical database '${db.path}/${db.name}' -- Schema '${db.schema}'")
    ant.PCTRun (procedure: 'GetDBVersion.p', dlcHome: "${options.dlc}") {
      DBConnection (dbname: "${db.name}", dbDir: "${db.path}", singleUser: true)
      Propath { pathelement(location: "src/pct") }
      OutputParameter (name: 'version')
      OutputParameter (name: 'step')
      env (key: "TERM", value: "xterm")
    }
    def initialDbUpdate = ant.project.properties.version.toInteger();
    def initialDbStep = ant.project.properties.step.toInteger();
    println "-> Version found : ${initialDbUpdate} step ${initialDbStep}"

    crcFile = "src/schema/crc/${db.schema}." + String.format('%05d', initialDbUpdate) + "." + String.format('%03d', initialDbStep) + ".crc";
    if (new File(crcFile).exists()) {
      def currentObjects = ['A': [], 'S': [], 'I': [:], 'T': [:]]
      def expectedObjects = ['A': [], 'S': [], 'I': [:], 'T': [:]]
      def allObjects = ['A': [] as Set, 'S': [] as Set, 'I': [] as Set, 'T': [] as Set]
      println("-> Loading CRC file")
      readCRCFile(crcFile, expectedObjects, allObjects);

      ant.PCTRun (procedure: 'GetCRCFile.p', dlcHome: "${options.dlc}", parameter: "${db.name}.crc" ) {
        DBConnection (dbname: "${db.name}", dbDir: "${db.path}", logicalName: "${db.schema}", singleUser: true)
        Propath { pathelement(location: "src/pct") }
        env (key: "TERM", value: "xterm")
      }
      readCRCFile("${db.name}.crc", currentObjects, allObjects);
      if (compareCRCObjects(expectedObjects, currentObjects, allObjects)) {
        println "-> CRC check OK"
      } else {
        println "-> CRC check failed !"
        crcDiff = true
      }
    } else {
      println "-> No CRC file found"
    }
  }
} catch (e) {
  println "## Error occured : ${e.message}"
  System.exit(1)
}
if (crcDiff) {
  println "At least one invalid CRC check, exiting process..."
  System.exit(1)
}

println ""
println "Step 3 - Apply DB updates"
println "-------------------------"
try {
  databases.values().each { db ->
    def ant = new AntBuilder()
    ant.taskdef (name: 'PCTRun', classname: 'com.phenix.pct.PCTRun')
    ant.taskdef (name: 'PCTLoadSchema', classname: 'com.phenix.pct.PCTLoadSchema')

    println ""
    println("Physical database '${db.path}/${db.name}' -- Schema '${db.schema}'")
    ant.PCTRun (procedure: 'GetDBVersion.p', dlcHome: "${options.dlc}") {
      DBConnection (dbname: "${db.name}", dbDir: "${db.path}", singleUser: true)
      Propath { pathelement(location: "src/pct") }
      OutputParameter (name: 'version')
      OutputParameter (name: 'step')
      env (key: "TERM", value: "xterm")
    }
    // Properties are immutable, so AntBuilder object has to be declared in the current scope
    def initialDbUpdate = ant.project.properties.version.toInteger();
    def initialDbStep = ant.project.properties.step.toInteger();
    println "-> Version found : ${initialDbUpdate} step ${initialDbStep}"

    // Delete NewIndexes file for this DB
    new File("NewIndexes-${db.name}").delete()

    println "-> Getting updates list"
    def allUpdates = []
    new File("src/schema/updates").traverse(type: groovy.io.FileType.DIRECTORIES, nameFilter: ~/[0-9]{5}/, maxDepth: 1, sort: { a, b -> a.name <=> b.name }) { dir ->
      dir.traverse(type: groovy.io.FileType.DIRECTORIES, nameFilter: "${db.schema}", maxDepth: 1, sort: { a, b -> a.name <=> b.name }) { dir2 ->
        dir2.traverse(type: groovy.io.FileType.FILES, nameFilter: ~/[0-9]{3}\..*/, maxDepth: 1, sort: { a, b -> a.name <=> b.name }) { file ->
          allUpdates.add(dir.name + '.' + file.name.substring(0,3))
        }
      }
    }
    def duplicateFound = false
    allUpdates.findAll{allUpdates.count(it) > 1}.unique().each { println "** Duplicate entry found : ${it}"; duplicateFound = true }
    if (duplicateFound) { System.exit(1) }

    new File("src/schema/updates").traverse(type: groovy.io.FileType.DIRECTORIES, nameFilter: ~/[0-9]{5}/, maxDepth: 0, sort: { a, b -> a.name <=> b.name }) { dir ->
      if (dir.getName().toInteger() < initialDbUpdate)
        return;
      println "-> Starting DB update #${dir.name}"
      def pattern = ~"(?i)\\A([0-9]{3})\\.(.*)\\.df\$"

      new File(dir, "${db.schema}").traverse(type: groovy.io.FileType.FILES, maxDepth: 0, sort: { a, b -> a.name <=> b.name }) { file ->
        infos = pattern.matcher(file.getName())
        // Skip file names not matching pattern
        if (!infos.matches()) {
          println "@ Ignoring ${file}"
          return
        }

        currUpdStep = file.getName().substring(0, 3)
        // Test update step in case of previous failure
        def dbUpdDirVersion = dir.getName().toInteger();
        if ( ((dbUpdDirVersion == initialDbUpdate) && (currUpdStep.toInteger() > initialDbStep)) || (dbUpdDirVersion > initialDbUpdate) ) {
          println "--> Apply file ${file.name}"

          // DF FILES
          ant.PCTLoadSchema(srcFile: file, dlcHome: "${options.dlc}", analyzerClass: 'dbupdates.InactiveIndexes', callbackClass: 'dbupdates.SchemaLoadLogger', numDec: '44', numSep: '46', centuryYearOffset: '1950', dateFormat: 'dmy') {
            DBConnection (dbname: "${db.name}", dbDir: "${db.path}", singleUser: true)
            Parameter (name: 'dbUpdateNum', value: dir.getName())
            Parameter (name: 'dbUpdateStep', value: currUpdStep)
            Propath { pathelement(location: "src/pct") }
            env (key: "TERM", value: "xterm")
          }
        }
      }
    }
  }

  println ""
  println "Step 4 - Index rebuild"
  println "----------------------"

  // Rebuild indexes if required
  def rebuild = [:] // Empty map -- Structure: DB object -> List<String> (indexes)
  databases.values().each { db ->
    def idxs = new File("NewIndexes-${db.name}")
    if (idxs.length() == 0) {
      println "No index rebuild for ${db.name}"
      return
    }
    def lines = new ArrayList<String>()
    idxs.eachLine { line -> lines.add(line) }
    rebuild.put(db, lines)
  }

  if (!rebuild.isEmpty()) {
    rebuild.each { key, value -> 
      def ant = new AntBuilder()
      /* if (params[key].ai) {
        println "Index rebuild - Physical database ${key} -- Schema ${dbList[key]} -- Turning AI off"
        ant.exec (executable: "${DLC}/bin/_rfutil", failOnError: Boolean.valueOf("${AIMAGE_END_FAIL_ON_ERROR}")) {
          arg(line: "${DB_ROOT_DIR}/${key}/sch/${key} -C aimage end")
          env(key: "DLC", value: "${DLC}")
          env(key: "PATH", value: "${DLC}/bin:${PATH}")
        }
      } */
    }
    def antIdx = new AntBuilder()
    antIdx.taskdef (name: 'IndexRebuild', classname: 'com.phenix.pct.PCTIndexRebuild')
    antIdx.parallel (threadCount: 4) {
      rebuild.each { db, lines ->
        antIdx.sequential {
          antIdx.IndexRebuild (dbName: db.name, dbDir: db.path, dlcHome: options.dlc, /*cpinternal: 'utf-8',*/ taskName: "IdxBuild ${db.name}") {
            lines.each { line -> Index (table: line.split(';')[0], index: line.split(';')[1]) }
            Option(name: "-SG", value: "64")
            Option(name: "-thread", value: "1")
            Option(name: "-threadnum", value: "12")
            Option(name: "-z")
            Option(name: "-rusage")
            Option(name: "-TM", value: "32")
            Option(name: "-TB", value: "64")
            Option(name: "-TMB", value: "256")
            Option(name: "-mergethreads", value: "4")
            Option(name: "-datascanthreads", value: "4")
            Option(name: "-TF", value: "20")
            // Option(name: "-T", value: "${DB_ROOT_DIR}/../AIARCDIR/DUMP/TMP")
            // Option(name: "-T", value: ".")
          }
          antIdx.delete(file: "NewIndexes-${db.name}")
        }
      }
    }
  }

  println ""
  println "Step 5 - Post release tasks"
  println "---------------------------"

  databases.values().each { db ->
    def ant = new AntBuilder()
    ant.taskdef (name: 'PCTRun', classname: 'com.phenix.pct.PCTRun')

    println("Truncate BI for physical database '${db.path}/${db.name}'")
    ant.exec (executable: "${options.dlc}/bin/_proutil", failOnError: true) {
      arg(line: "${db.path}/${db.name} -C truncate bi -bi 16 -biblocksize 16384")
      env(key: "DLC", value: "${options.dlc}")
      env(key: "PATH", value: "${options.dlc}/bin")
    }
  }

  println ""
  println "Step 6 - Check final CRC"
  println "------------------------"
  boolean finalCrcDiff = false
  databases.values().each { db ->
    def ant = new AntBuilder()
    ant.taskdef (name: 'PCTRun', classname: 'com.phenix.pct.PCTRun')
    ant.taskdef (name: 'PCTLoadSchema', classname: 'com.phenix.pct.PCTLoadSchema')

    println()
    println("Physical database '${db.path}/${db.name}' -- Schema '${db.schema}'")
    ant.PCTRun (procedure: 'GetDBVersion.p', dlcHome: "${options.dlc}") {
      DBConnection (dbname: "${db.name}", dbDir: "${db.path}", singleUser: true)
      Propath { pathelement(location: "src/pct") }
      OutputParameter (name: 'version')
      OutputParameter (name: 'step')
      env (key: "TERM", value: "xterm")
    }
    def initialDbUpdate = ant.project.properties.version.toInteger();
    def initialDbStep = ant.project.properties.step.toInteger();
    println "-> Version found : ${initialDbUpdate} step ${initialDbStep}"

    crcFile = "src/schema/crc/${db.schema}." + String.format('%05d', initialDbUpdate) + "." + String.format('%03d', initialDbStep) + ".crc";
    if (new File(crcFile).exists()) {
      def currentObjects = ['A': [], 'S': [], 'I': [:], 'T': [:]]
      def expectedObjects = ['A': [], 'S': [], 'I': [:], 'T': [:]]
      def allObjects = ['A': [] as Set, 'S': [] as Set, 'I': [] as Set, 'T': [] as Set]
      println("-> Loading CRC file")
      readCRCFile(crcFile, expectedObjects, allObjects);

      ant.PCTRun (procedure: 'GetCRCFile.p', dlcHome: "${options.dlc}", parameter: "${db.name}.crc" ) {
        DBConnection (dbname: "${db.name}", dbDir: "${db.path}", logicalName: "${db.schema}", singleUser: true)
        Propath { pathelement(location: "src/pct") }
        env (key: "TERM", value: "xterm")
      }
      readCRCFile("${db.name}.crc", currentObjects, allObjects);
      if (compareCRCObjects(expectedObjects, currentObjects, allObjects)) {
        println "-> CRC check OK"
      } else {
        println "-> CRC check failed !"
        finalCrcDiff = true
      }
    } else {
      println "-> No final CRC file found"
    }
  }
  if (finalCrcDiff) {
    println "At least one invalid CRC check, exiting process..."
    System.exit(1)
  }

} catch (e) {
  println "## Error occured : ${e.message}"
  // e.printStackTrace()
  System.exit(1)
}


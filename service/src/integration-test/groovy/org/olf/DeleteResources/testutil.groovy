package org.olf.DeleteResources

import grails.plugin.json.builder.JsonOutput
import groovy.json.JsonBuilder
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.olf.DeleteResources.Scenario


class ScenarioCsvReader {
  static List<Scenario> loadScenarios(String filePath) {
    File inputFile = new File(filePath)
    if (!inputFile.exists()) {
      // Try classloader
      InputStream csvStream = ScenarioCsvReader.class.classLoader.getResourceAsStream(filePath.startsWith("/") ? filePath.substring(1) : filePath)
      if (csvStream) {
        System.out.println("Static (ScenarioCsvReader): Found CSV via classloader: " + filePath)
        return readFromStream(csvStream)
      }
      System.err.println("Static (ScenarioCsvReader): CSV file not found at: ${inputFile.absolutePath} or via classloader " + filePath)
      throw new FileNotFoundException("Test data CSV not found: " + filePath)
    }
    System.out.println("Static (ScenarioCsvReader): Reading scenarios from: " + inputFile.absolutePath)
    return readFromFile(inputFile)
  }

  private static List<Scenario> readFromFile(File inputFile) {
    List<Scenario> scenarios = []
    new FileReader(inputFile).withReader { reader ->
      populateScenarios(reader, scenarios)
    }
    return scenarios
  }

  private static List<Scenario> readFromStream(InputStream inputStream) {
    List<Scenario> scenarios = []
    new InputStreamReader(inputStream).withReader { reader ->
      populateScenarios(reader, scenarios)
    }
    return scenarios
  }

  private static Map<String, Map<String, List<Scenario>>> transformToNestedStructure(List<Scenario> scenarios) {
    // Group by 'structure'
    Map<String, List<Scenario>> groupedByStructure = scenarios.groupBy {
      it.structure ?: "UNKNOWN_STRUCTURE" // Handle null structure values
    }

    // Further group by 'inputResources' within each structure group
    Map<String, Map<String, List<Scenario>>> nestedScenarios = [:]
    groupedByStructure.each { structureKey, scenariosInStructure ->
      Map<String, List<Scenario>> groupedByInputResources = scenariosInStructure.groupBy { scenario ->
        // Create a canonical key from the inputResources list: sort and join
        // Handle null or empty inputResources
        def resources = scenario.inputResources ?: []
        resources.sort(false).join(',') // sort(false) creates a new sorted list
      }
      nestedScenarios[structureKey] = groupedByInputResources
    }
    return nestedScenarios
  }

  static String loadScenariosAsJson(String filePath, boolean prettyPrint = false) {
    List<Scenario> scenarios = loadScenarios(filePath)
    Map<String, Map<String, List<Scenario>>> nestedData = transformToNestedStructure(scenarios)
    if (prettyPrint) {
      return new JsonBuilder(nestedData).toPrettyString()
    } else {
      return JsonOutput.toJson(nestedData)
    }
  }

  static void saveScenariosAsJsonFile(String csvInputPath, String jsonOutputPath, boolean prettyPrint = false) {
    String jsonString = loadScenariosAsJson(csvInputPath, prettyPrint)
    File outputFile = new File(jsonOutputPath)

    try {
      // Ensure parent directories exist
      outputFile.getParentFile()?.mkdirs()

      outputFile.write(jsonString, 'UTF-8') // Write string to file, specifying encoding
      System.out.println("Static (ScenarioCsvReader): Successfully saved scenarios to JSON file: ${outputFile.absolutePath}")
    } catch (IOException e) {
      System.err.println("Static (ScenarioCsvReader): Error saving JSON to file ${outputFile.absolutePath}: ${e.getMessage()}")
      throw e // Re-throw the exception if you want the caller to handle it
    }
  }

  private static Map<String, Integer> processKBInput(String kbInput) {
    List<String> parsedInput = kbInput?.split(',')?.collect { it.trim() }?.findAll { !it.isEmpty() } ?: []
    Map<String, Integer> output = new HashMap<>();
    output.put("pci", parsedInput[0].toInteger());
    output.put("pti", parsedInput[1].toInteger())
    output.put("ti", parsedInput[2].toInteger())
    output.put("work", parsedInput[3].toInteger())

    return output;
  }

  private static void populateScenarios(Reader reader, List<Scenario> scenarios) {
    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
      .withHeader()
      .withIgnoreHeaderCase()
      .withTrim()
      .withSkipHeaderRecord())

    for (record in csvParser) {
      def scenario = new Scenario()
      scenario.description = record.get("scenario_description") ?: "CSV Row ${record.getRecordNumber()}"
      scenario.inputResources = record.get("input_resources")?.split(',')?.collect { it.trim() }?.findAll { !it.isEmpty() } ?: []
      scenario.agreementLines = record.get("agreement_lines")?.split(',')?.collect { it.trim() }?.findAll { !it.isEmpty() } ?: []
      scenario.structure = record.get("structure")
      scenario.markExpectedIds = record.get("mark_expected_ids")?.split(',')?.collect { it.trim() }?.findAll { !it.isEmpty() } ?: []
      scenario.expectedKbMarkForDelete = processKBInput(record.get("mark_expected_kb_stats"))
      scenario.expectedKbDelete = processKBInput(record.get("delete_expected_kb_stats"))


      scenarios.add(scenario)
    }
    System.out.println("Static (ScenarioCsvReader): Loaded ${scenarios.size()} scenarios.")
  }
}
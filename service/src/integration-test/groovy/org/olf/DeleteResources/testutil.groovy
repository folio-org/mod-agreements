package org.olf.DeleteResources

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.olf.DeleteResources.Scenario

import java.nio.file.Paths // For more modern file path handling

// Assume your Scenario class is accessible or defined here too
// For simplicity, let's assume it's defined in AutomatedDeletionSpec as static

class ScenarioCsvReader {
  static List<Scenario> loadScenarios(String filePath) {
    // Use system property for base path or make filePath absolute
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

  private static void populateScenarios(Reader reader, List<Scenario> scenarios) {
    CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT
      .withHeader()
      .withIgnoreHeaderCase()
      .withTrim()
      .withSkipHeaderRecord())

    for (record in csvParser) {
      def scenario = new Scenario() // Assuming Scenario is defined/imported
      scenario.description = record.get("scenario_description") ?: "CSV Row ${record.getRecordNumber()}"
      scenario.inputResources = record.get("input_resources")?.split(',')?.collect { it.trim() }?.findAll { !it.isEmpty() } ?: []
      // ... populate other fields ...
      scenarios.add(scenario)
    }
    System.out.println("Static (ScenarioCsvReader): Loaded ${scenarios.size()} scenarios.")
  }
}
package org.olf.DeleteResources

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
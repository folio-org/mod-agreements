Of course. Here is the provided text converted into a well-structured Markdown document, using intuition to create headers, lists, and other formatting for clarity.

***

# Back-end Integration Testing Documentation

## Integration Tests in `mod-agreements`

Integration tests in this module all inherit from the `BaseSpec` file.

### The `BaseSpec`

`BaseSpec` is the foundational test class that:

*   Sets up a tenant for each test.
*   Defines utility methods for importing package data for tests.
*   Defines other shared utility methods.

### Setting up Packages for Testing

Because we import packages via the `importService` API in our tests, we need to store package data as **JSON** in a shape that the service can use. These test package JSON files are found in the `resources` folder.

Package import files can be written using two different structures:
*   [Link to package import data structures](#) *(Note: Placeholder link)*

---

## Testing Approaches

### Resource Deletion

The resource deletion logic depends on the interaction between the data structure, the resources selected for deletion, and the agreement lines attached to resources. For each combination of these elements, the testing process is effectively the same:

1.  **Load** in the data structure.
2.  **Attach** the agreement lines.
3.  **Mark for delete** and assert against the result.
4.  **Perform the deletion** and assert against the final state.

To handle this, we use an iterative approach with Spock’s `where` block to generate different combinations of inputs for each test. This provides maximum coverage while keeping the testing logic centralized. The assertions are defined manually in a JSON file which the test reads from, using the inputs as keys to select the right expected value for each test iteration.

**Benefits of this approach:**
*   If we decide to change the deletion implementation, we only have to change “one” test method.
*   We get very high coverage for a relatively small amount of code.
*   The test method generates scenarios itself, making it harder to accidentally miss a scenario. For example, if we were to add a new ‘structure’ to the test setup, new test cases would automatically be generated but would fail because no expected values exist. This is useful feedback.

**Downsides to this approach:**
*   The code to set up the tests is more complex.
*   There is some redundancy in the test scenarios generated.
*   The test spec is quite rigid; for example, adding tests for multiple structures at once would require a non-trivial change to the testing logic.

This combinatorial approach does not preclude the addition of other tests. There is a separate `NegativeTestSpec` which deals with a small number of test scenarios for specific failure scenarios and edge cases.

### Other Tests

Our other integration tests often use a **Stepwise approach**.

> **Note:** This is not always considered best practice, because an earlier test can change the state of objects on which later tests depend.

We use it, for example, to load test packages, then create an agreement, before other tests might then export that agreement. This can save time by not having to recreate resources for each test. However, it means that editing or re-arranging tests may cause later tests in the spec to fail.

Tests for different features might also extend their own specific base spec. For example, `AgreementPublicLookupSpec` extends `AgreementsBaseSpec`, which contains methods that could be re-used in other agreement-related tests.

### PushKb

The PushKb tests focus on imitating a package being pushed via the PushKb endpoints (`/pci` and `/pkg`). The test flow is to import a package via these endpoints and then verify that the correct package data was saved to the database.

---

## Test Best Practices

*   Tests should hit API endpoints for interacting with data wherever possible, as opposed to manipulating the database directly (e.g., using `HQL`).
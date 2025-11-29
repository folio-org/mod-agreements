package com.k_int.accesscontrol.core.policycontrolled;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility to validate required fields on the PolicyControlled annotation.
 */
public class PolicyControlledValidator {

  /**
   * Mandatory fields required to be assigned on any PolicyControlled resource
   */
  private static final List<String> MANDATORY_FIELDS = Arrays.asList(
    "resourceTableName",
    "resourceIdColumn",
    "resourceIdField"
  );

  /**
   * Retrieves the {@code @PolicyControlled} annotation from the given class and
   * performs mandatory field validation on it.
   *
   * @param annotatedClass The class expected to have the {@code @PolicyControlled} annotation.
   * @return The validated {@link PolicyControlled} annotation instance.
   * @throws IllegalArgumentException if the annotation is missing on the class
   * or if any mandatory field is missing/empty.
   */
  public static PolicyControlled validateAndGet(Class<?> annotatedClass) {
    PolicyControlled annotation = annotatedClass.getAnnotation(PolicyControlled.class);

    if (annotation == null) {
      throw new IllegalArgumentException("Missing @PolicyControlled on " + annotatedClass.getName());
    }

    for (String fieldName : MANDATORY_FIELDS) {
      try {
        Method method = annotation.annotationType().getMethod(fieldName);
        Object value = method.invoke(annotation);

        if (value == null || (value instanceof String && Objects.equals(value, ""))) {
          throw new IllegalArgumentException(
            "Missing mandatory configuration field '" + fieldName +
              "' on annotation @PolicyControlled" +
              " applied to class " + annotatedClass.getName()
          );
        }
      } catch (IllegalArgumentException e) {
        // These we can rethrow as is to avoid having to skim through stacktraces
        throw e;
      } catch (NoSuchMethodException e) {
        // This means a field in MANDATORY_FIELDS doesn't exist in the annotation interface
        throw new IllegalStateException("Validator configuration error: Missing annotation method for " + fieldName, e);
      } catch (Exception e) {
        // Catch InvocationTargetException, IllegalAccessException, etc.
        throw new RuntimeException("Error accessing annotation field " + fieldName, e);
      }
    }
    return annotation;
  }
}

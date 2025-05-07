package org.olf.General

import grails.testing.mixin.integration.Integration
import spock.lang.*

import groovy.util.logging.Slf4j

@Slf4j
@Integration
@Stepwise
class CombinedSpec extends Specification {
  @Shared
  def aList = [1,2,3]
  @Shared
  def bList = [4,5,6]
  @Shared
  def cList = [7,8,9]

  @Shared
  def multiply1 = (int1, int2, int3) -> { return (int1*int2)*int3 }

  @Shared
  def multiply2 = (int1, int2, int3) -> { return int1*(int2*int3) }


  @Unroll("Test combinations #funcName: #a * #b * #c == #expected")
  void "Test combinations" () {
    when: "${a} * ${b} * ${c}"
      def result = func(a,b,c);
    then: "Result is ${expected}"
      result == expected

    where:

      [a, b, c, func, funcName,  expected] << aList
        .collectMany { aTemp ->
          bList.collectMany { bTemp ->
            cList.collectMany { cTemp ->
              [[multiply1, "multiply1"], [multiply2, "multiply2"]].collect { funcList ->
                [aTemp, bTemp, cTemp, funcList[0], funcList[1], aTemp * bTemp * cTemp]
              }
            }
          }
        }
  }
}

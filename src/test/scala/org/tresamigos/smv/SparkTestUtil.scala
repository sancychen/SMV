/*
 * This file is licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tresamigos.smv

import java.io.{PrintWriter, File}

import org.apache.log4j.{LogManager, Logger, Level}
import org.apache.spark.SparkContext
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.scalatest.FunSuite

trait SparkTestUtil extends FunSuite {
  var sc: SparkContext = _
  var sqlContext: SQLContext = _
  var app: SmvApp = _

  /** top of data dir to be used by tests.  For per testcase temp directory, use testcaseTempDir instead */
  final val testDataDir = "target/test-classes/data/"

  /**
   * convenience method for tests that use spark.  Creates a local spark context, and cleans
   * it up even if your test fails.  Also marks the test with the tag SparkTest, so you can
   * turn it off
   *
   * By default, it turn off spark logging, b/c it just clutters up the test output.  However,
   * when you are actively debugging one test, you may want to turn the logs on
   *
   * Source: http://blog.quantifind.com/posts/spark-unit-test/
   *
   * One slight variation of the above, instead of capturing the log level for certain loggers
   * and reseting them after every test, this runner will ALWAYS set the log level to ERROR
   * for ALL current registered loggers.  If the user wants to enable logging at a lower level,
   * they can call "SparkTestUtil.setLoggingLevel" with the lower level.  This can even be
   * used by non-sparkTest test cases.
   *
   * @param name the name of the test
   */
  def sparkTest(name: String, disableLogging: Boolean = false)(body: => Unit) {
    test(name) {
      if (disableLogging)
        SparkTestUtil.setLoggingLevel(Level.OFF)
      else
        SparkTestUtil.setLoggingLevel(Level.ERROR)
      sc = new SparkContext("local[2]", name)
      sqlContext = new SQLContext(sc)
      app = new SmvApp(Seq("-m", "None"), Option(sc))
      try {
        body
      }
      finally {
        sc.stop
        sc = null
        sqlContext = null
        // To avoid Akka rebinding to the same port, since it doesn't unbind immediately on shutdown
        System.clearProperty("spark.master.port")

        // re-enable normal logging for next test if we disabled logging here.
        if (disableLogging)
          SparkTestUtil.setLoggingLevel(Level.ERROR)
      }
    }
  }

  /** name of a scratch test directory specific to this test case. */
  def testcaseTempDir = testDataDir + this.getClass.getName

  /** wipe out the temp test directory and recreate an empty instance. */
  def resetTestcaseTempDir() = {
    SmvHDFS.deleteFile(testcaseTempDir)
    new File(testcaseTempDir).mkdir()
  }

  /** create a temp file in the test case temp dir with the given contents. */
  def createTempFile(baseName: String, fileContents: String = "xxx"): File = {
    val outFile = new File(testcaseTempDir, baseName)
    val pw = new PrintWriter(outFile)
    pw.write(fileContents)
    pw.close
    outFile
  }

  /**
   * Ensure that the given expected and actual result double sequences are "equal".  Equality is checked
   * against the given epsilon margin of error to account for floating point precision errors.
   */
  def assertDoubleSeqEqual(resultSeq: Seq[Any], expectSeq: Seq[Double], epsilon: Double = 0.01) {
    import java.lang.Math.abs
    assert(resultSeq.length === expectSeq.length)
    resultSeq.map {
      case d: Double => d
      case i: Int => i.toDouble
      case l: Long => l.toDouble
      case f: Float => f.toDouble
      case _ => Double.MinValue
    }.zip(expectSeq).foreach {
      case (a, b) => assert(abs(a - b) < epsilon, s"because array element $a not equal $b")
    }
  }

  def open(path: String) ={
    val file = SmvCsvFile("./" + path, CsvAttributes.defaultCsv)
    file.injectApp(app)
    file.rdd
  }

  /**
   * Ensure that two arbitrary sequences are equal regardless of the order of items in the sequence
   */
  def assertUnorderedSeqEqual[T: Ordering](resultSeq: Seq[T], expectSeq: Seq[T]) {
    assert(resultSeq.length === expectSeq.length)

    val sortedResSeq = resultSeq.sorted
    val sortedExpSeq = expectSeq.sorted

    sortedResSeq.zip(sortedExpSeq).foreach {
      case (a, b) => assert(a == b, s"because array element $a not equal $b")
    }
  }

  /**
   * Verify that the data in the df matches the expected result strings.
   * The expectedRes is assumed to be a set of lines separated by ";"
   * The order of the result strings is not important.
   */
  def assertSrddDataEqual(df: DataFrame, expectedRes: String) = {
    val resLines = df.collect.map(_.toString.stripPrefix("[").stripSuffix("]"))
    val expectedLines = expectedRes.split(";").map(_.trim)
    assertUnorderedSeqEqual(resLines, expectedLines)
  }

  /**
   * validates that the schema of the given SRDD matches the schema defined by
   * the schemaStr parameter.  The schemaStr parameter is jsut a ";" list of
   * schema entries.
   */
  def assertSrddSchemaEqual(df: DataFrame, schemaStr: String) = {
    val expSchema = SmvSchema.fromString(schemaStr)
    val resSchema = SmvSchema.fromDataFrame(df)
    assert(resSchema.toString === expSchema.toString)
  }

  /**
   * schemaRdd creater is in SqlContextHelper now. This is just a wraper
   */
  def createSchemaRdd(schemaStr: String, data: String) = {
    app.createDF(schemaStr, data)
  }

  /**
   * dumpSRDD(df) now changes to df.dumpSRDD, which implemented in
   * DataFrameHelper
   */

  /**
   * Check whether a string matches a Regex
   **/
  def assertStrMatches(haystack: String, needle: scala.util.matching.Regex) = {
    assert(needle.findFirstIn(haystack) != None, s"because $haystack does not match $needle")
  }
}

object SparkTestUtil {
  import scala.collection.JavaConversions.enumerationAsScalaIterator

  def setLoggingLevel(level: Level) {
    val rootLogger = LogManager.getRootLogger
    val loggers = rootLogger :: LogManager.getCurrentLoggers.map(_.asInstanceOf[Logger]).toList
    loggers.foreach { logger =>
      logger.setLevel(level)
    }
  }
}

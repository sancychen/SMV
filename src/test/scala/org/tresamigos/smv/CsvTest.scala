package org.tresamigos.smv

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._


class CsvTest extends SmvTestUtil {

  test("Test loading of csv file with header") {
    val file = SmvCsvFile("./" + testDataDir +  "CsvTest/test1", CsvAttributes.defaultCsvWithHeader)
    val df = file.rdd
    val res = df.map(r => (r(0), r(1))).collect.mkString(",")
    // TODO: should probably add a assertSRDDEqual() with expected rows instead of convert to string
    assert(res === "(Bob,1),(Fred,2)")
  }

  test("Test column with pure blanks converts to null as Integer or Double") {
    val df = createSchemaRdd("a:Integer;b:Double", "1 , 0.2 ; 2, 1 ;3, ; , ;5, 3.")
    assertSrddDataEqual(df,
      "1,0.2;" +
      "2,1.0;" +
      "3,null;" +
      "null,null;" +
      "5,3.0")
  }

  test("Test run method in SmvFile") {
    object TestFile extends SmvCsvFile("./" + testDataDir +  "CsvTest/test1", CsvAttributes.defaultCsvWithHeader) {
      override def run(df: DataFrame) = {
        import df.sqlContext.implicits._
        df.selectPlus(smvStrCat($"name", $"id") as "name_id")
      }
    }
    val df = TestFile.rdd
    assertSrddDataEqual(df,
      "Bob,1,Bob1;" +
      "Fred,2,Fred2")
  }

  test("Test reading CSV file with attributes in schema file.") {
    val file = SmvCsvFile("./" + testDataDir +  "CsvTest/test2.csv")
    val df = file.rdd

    // take the sum of second column (age) to make sure csv was interpreted correctly.
    val res = df.agg(sum(df("age")))

    assertSrddDataEqual(res, "46")
  }

  test("Test writing CSV file with attributes in schema file.") {
    val df = createSchemaRdd("f1:String;f2:String", "x,y;a,b").repartition(1)
    val ca = CsvAttributes('|', '^', true)
    val csvPath = testcaseTempDir + "/test_attr.csv"
    val schemaPath = testcaseTempDir + "/test_attr.schema"
    df.saveAsCsvWithSchema(csvPath, ca)

    // verify header partition in csv file
    assertFileEqual(csvPath + "/part-00000", "^f1^|^f2^\n")

    // verify data partition in csv file
    assertFileEqual(csvPath + "/part-00001", "^x^|^y^\n^a^|^b^\n")

    // verify schema file output
    assertFileEqual(schemaPath + "/part-00000",
      """@delimiter = |
        |@has-header = true
        |@quote-char = ^
        |f1: String
        |f2: String
        |""".stripMargin)
  }

  test("Test persisting file with null strings") {
    object A extends SmvModule("A Module") {
      override def requiresDS() = Seq()
      override def run(inputs: runParams) = {
        app.createDF("a:String", "1;;3").selectPlus(lit("") as "b").repartition(1)
      }
    }

    val res = A.rdd()
    assertSrddDataEqual(res, """1,;null,;3,""")
    assertFileEqual(A.moduleCsvPath() + "/part-00000",
    """"1",""
      |"_SmvStrNull_",""
      |"3",""
      |""".stripMargin)
    assertFileEqual(A.moduleSchemaPath() + "/part-00000",
    """@delimiter = ,
      |@has-header = false
      |@quote-char = "
      |a: String[,_SmvStrNull_]
      |b: String[,_SmvStrNull_]
      |""".stripMargin)

  }

  test("Test escaping quotes in strings") {

    /** Test for CSV Excel format  **/
    var df = createSchemaRdd("f1:String;f2:String", "\"left\"\"right comma,\",\"escape char \\\";\"a\",\"b\"").repartition(1)
    var ca = CsvAttributes()
    val csvPath = testcaseTempDir + "/test_escape_quotes.csv"

    df.saveAsCsvWithSchema(csvPath, ca)

    // verify that input "" is converted to " internally in DF
    assert(df.collect()(0)(0) == """left"right comma,""")

    // verify that output uses Excel's CSV format
    assertFileEqual(csvPath + "/part-00000",
      """"left""right comma,","escape char \"
        |"a","b"
        |""".stripMargin)

    var dfOut = open(csvPath)

    // verify that serialize/desrialize results in the same output
    assertDataFramesEqual(df, dfOut)


    /** Test for other formats.  This one uses ^ as a quote char and \ as an escape char **/
    df = createSchemaRdd("@delimiter = ,;@has-header = false;@quote-char = ^;f1:String;f2:String", "^left\\^right quote\\\" comma\\, ^,^escape char\\\\^;a,b").repartition(1)
    ca = CsvAttributes(',','^',false)
    val csvPathCaret = testcaseTempDir + "/test_escape_caret.csv"

    df.saveAsCsvWithSchema(csvPathCaret, ca)
    val file = SmvCsvFile("./" + csvPathCaret, ca)
    dfOut = file.rdd

    assertDataFramesEqual(df, dfOut)


    /** Test for arrays **/
    df = createSchemaRdd("a:String;b:Array[String]",""" "a1", "b""1|b2" """).repartition(1)
    ca = CsvAttributes()
    val csvPathArray = testcaseTempDir + "/test_escape_array.csv"

    df.saveAsCsvWithSchema(csvPathArray, ca)

    // verify that output uses Excel's CSV format
    assertFileEqual(csvPathArray + "/part-00000",
      """"a1","b""1|b2"
        |""".stripMargin)

    dfOut = open(csvPathArray)

    // verify that serialize/desrialize results in the same output
    assertDataFramesEqual(df, dfOut)

  }

}

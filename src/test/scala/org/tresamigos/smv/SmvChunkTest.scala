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

class SmvChunkTest extends SparkTestUtil {

  sparkTest("Test chunkBy") {
    val ssc = sqlContext; import ssc._
    val srdd = createSchemaRdd("k:String;v:String", "k1,a;k1,b;k2,d;k2,c")
    val runCat = (l: List[Seq[Any]]) => l.map{_(0)}.scanLeft(Seq("")){(a,b) => Seq(a(0) + b)}.tail
    val runCatFunc = SmvChunkFunc(Seq('v), Schema.fromString("vcat:String"), runCat)

    val res = srdd.orderBy('k.asc, 'v.asc).chunkBy('k)(runCatFunc)
    assertSrddSchemaEqual(res, "k:String; vcat:String")
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[k1,a]", "[k1,ab]", "[k2,c]", "[k2,cd]"))
  }
}

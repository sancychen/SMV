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
import org.apache.spark.sql.DataFrame

abstract class SmvAncillary {
  def requiresDS(): Seq[SmvModuleLink]

  protected def getDF(ds: SmvModuleLink) : DataFrame= {
    if (requiresDS.contains(ds)) SmvApp.app.resolveRDD(ds)
    else throw new IllegalArgumentException(s"${ds} does not defined in requiresDS")
  }
}

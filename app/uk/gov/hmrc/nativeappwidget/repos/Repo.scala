/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.nativeappwidget.repos


import com.google.inject.ImplementedBy
import uk.gov.hmrc.nativeappwidget.models.Data

import scala.concurrent.Future

@ImplementedBy(classOf[MongoRepo])
trait Repo {

  /**
    * Insert data into the repo - return a `Left` if there is an error while inserting,
    * otherwise return `Unit`
    *
    * @param data The data to insert
    */
  def insertData(data: Data): Future[Either[String,Unit]]

}

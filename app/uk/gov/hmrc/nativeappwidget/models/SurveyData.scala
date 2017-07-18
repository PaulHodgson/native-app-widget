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

package uk.gov.hmrc.nativeappwidget.models

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.nativeappwidget.models.SurveyData.KeyValuePair

/**
  * Represents data we write to our repo
  *
  * @param campaignId - an ID which associates the data to a particular party
  * @param data - the actual data
  */
case class SurveyData(campaignId: String,
                      data : List[KeyValuePair]) {

  /** A string suitable for identifying the data in logs */
  val idString: String = s"campaignId: '$campaignId'"
}

object SurveyData {

  case class KeyValuePair(key: String, additionalInfo: Option[String], value: Content)

  case class Content(content: String, contentType: Option[String])

  object KeyValuePair {
    implicit val keyValuePairFormat: Format[KeyValuePair] = Json.format[KeyValuePair]
  }

  object Content {
    implicit val contentFormat: Format[Content] = Json.format[Content]

  }

  implicit val dataFormat: Format[SurveyData] = Json.format[SurveyData]

}

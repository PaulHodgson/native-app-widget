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

import org.joda.time.DateTime
import play.api.libs.json.{Format, Json}

/**
  * Represents surveyData we write to our repo
  *
  * @param campaignId - an ID which associates the surveyData to a particular party
  * @param internalAuthid - the internal auth ID identifying a person
  * @param surveyData - the actual surveyData
  */
case class SurveyDataPersist(campaignId: String,
                             internalAuthid: String,
                             surveyData : List[KeyValuePair],
                             created : DateTime) {

  /** A string suitable for identifying the surveyData in logs */
  val idString: String = s"campaignId: '$campaignId', internalAuthId: '$internalAuthid'"
}

/**
  * Represents surveyData we are posted in the service request
  *
  * @param campaignId - an ID which associates the surveyData to a particular party
  * @param surveyData - the actual surveyData
  */
case class SurveyData(campaignId: String,
                      surveyData : List[KeyValuePair]) {
}

case class KeyValuePair(key: String, value: Content)

case class Content(content: String, contentType: Option[String], additionalInfo: Option[String])

object Content {
  implicit val contentFormat: Format[Content] = Json.format[Content]
}

object KeyValuePair {
  implicit val keyValuePairFormat: Format[KeyValuePair] = Json.format[KeyValuePair]
}

object SurveyDataPersist {
  implicit val dataFormat: Format[SurveyDataPersist] = Json.format[SurveyDataPersist]
}

object SurveyData {
  implicit val dataFormat: Format[SurveyData] = Json.format[SurveyData]
}







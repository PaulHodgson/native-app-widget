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

package uk.gov.hmrc.nativeappwidget

import org.scalacheck.Gen

package object models {

  def contentGen: Gen[Content] =
    for {
      content ← Gen.alphaNumStr
      contentType ← Gen.option(Gen.alphaNumStr)
      additionalInfo ← Gen.option(Gen.alphaNumStr)
    } yield Content(content, contentType, additionalInfo)


  def keyValueGen: Gen[KeyValuePair] =
    for {
      key ← Gen.alphaNumStr
      content ← contentGen
    } yield KeyValuePair(key, content)

  def dataGen: Gen[SurveyData] =
    for {
      campaignId ← Gen.alphaNumStr
      data ← Gen.listOf(keyValueGen)
    } yield SurveyData(campaignId, data)

  def randomContent(): Content = contentGen.sample.getOrElse(sys.error("Could not generate Content"))

  def randomData(): SurveyData = dataGen.sample.getOrElse(sys.error("Could not generate SurveyData"))

}

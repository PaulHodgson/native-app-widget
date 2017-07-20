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
import com.fortysevendeg.scalacheck.datetime.instances.joda._
import com.fortysevendeg.scalacheck.datetime.GenDateTime
import org.joda.time.{DateTime, Period}
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

  def dataPersistGen: Gen[SurveyDataPersist] =
    for {
      campaignId ← Gen.alphaNumStr
      internalAuthId ← Gen.alphaNumStr
      data ← Gen.listOf(keyValueGen)
      created ← GenDateTime.genDateTimeWithinRange(DateTime.now(), Period.years(1))
    } yield SurveyDataPersist(campaignId, internalAuthId, data, created)

  def dataGen: Gen[SurveyData] =
    for {
      campaignId ← Gen.alphaNumStr
      data ← Gen.listOf(keyValueGen)
    } yield SurveyData(campaignId, data)

  def randomDataPersist(): SurveyDataPersist = dataPersistGen.sample.getOrElse(sys.error("Could not generate surveyData persist"))
  def randomData(): SurveyData = dataGen.sample.getOrElse(sys.error("Could not generate surveyData"))

}

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

package uk.gov.hmrc.nativeappwidget.controllers

import java.util.UUID

import org.scalatest.concurrent.Eventually
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.nativeappwidget.models.{Content, KeyValuePair}
import uk.gov.hmrc.nativeappwidget.repos.{SurveyWidgetMongoRepository, SurveyWidgetRepository}
import uk.gov.hmrc.nativeappwidget.stubs.AuthStub
import uk.gov.hmrc.nativeappwidget.support.BaseISpec

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global

class SurveyWidgetDataControllerISpec extends BaseISpec with Eventually {
  private val campaignId = "TEST_CAMPAIGN_1"

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("widget.surveys" -> Seq(campaignId))

  private val validSurveyData: JsObject = Json.obj(
    "campaignId" -> campaignId,
    "surveyData" -> Json.arr(
      Json.obj(
        "key" -> "question_1",
        "value" -> Json.obj(
          "content" -> "true",
          "contentType" -> "Boolean",
          "additionalInfo" -> "Would you like us to contact you?"
        )
      ),
      Json.obj(
        "key" -> "question_2",
        "value" -> Json.obj(
          "content" -> "John Doe",
          "contentType" -> "String",
          "additionalInfo" -> "What is your full name?"
        )
      )
    )
  )

  protected lazy val surveyWidgetRepository: SurveyWidgetMongoRepository = app.injector.instanceOf[SurveyWidgetMongoRepository]


  "POST /native-app-widget/:nino/widget-data" should {
    "store survey data in mongo against the user's internal auth ID" in {
      val internalAuthid = s"Test-${UUID.randomUUID().toString}}"
      AuthStub.authoriseWithoutPredicatesWillReturnInternalId(internalAuthid)
      val response = await(wsUrl("/native-app-widget/CS700100A/widget-data").post(validSurveyData))

      response.status shouldBe 200

      eventually {
        val storedSurveyDatas: immutable.Seq[SurveyWidgetRepository.SurveyDataPersist] = await(surveyWidgetRepository.find(
          "internalAuthid" -> internalAuthid))
        storedSurveyDatas.size shouldBe 1
        val storedSurveyData = storedSurveyDatas.head
        storedSurveyData.campaignId shouldBe campaignId
        storedSurveyData.surveyData shouldBe List(
          KeyValuePair("question_1", Content(content = "true", contentType = Some("Boolean"), additionalInfo = Some("Would you like us to contact you?"))),
          KeyValuePair("question_2", Content(content = "John Doe", contentType = Some("String"), additionalInfo = Some("What is your full name?")))
        )
      }

      surveyWidgetRepository.remove("internalAuthid" -> internalAuthid)
    }
  }
}

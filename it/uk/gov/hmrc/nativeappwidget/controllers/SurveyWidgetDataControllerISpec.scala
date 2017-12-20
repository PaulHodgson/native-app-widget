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
import uk.gov.hmrc.nativeappwidget.repos.{SurveyResponseMongoRepository, SurveyWidgetRepository}
import uk.gov.hmrc.nativeappwidget.stubs.AuthStub
import uk.gov.hmrc.nativeappwidget.support.BaseISpec

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global

class SurveyWidgetDataControllerISpec extends BaseISpec with Eventually {
  private val campaign1Id = "TEST_CAMPAIGN_1"
  private val campaign2Id = "TEST_CAMPAIGN_2"

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("widget.surveys" -> Seq(campaign1Id))

  private def validSurveyData(campaignId: String): JsObject = Json.obj(
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

  private lazy val surveyResponseRepository: SurveyResponseMongoRepository = app.injector.instanceOf[SurveyResponseMongoRepository]

  private def withTestCampaignCleanup(testCode: => Any): Unit = {
    try {
      testCode
    }
    finally {
      surveyResponseRepository.remove("campaignId" -> Json.obj("$in" -> Json.arr(campaign1Id, campaign2Id)))
    }
  }

  private def aPostSurveyResponseEndpoint(url: String): Unit = {
    "store survey data in mongo against the user's internal auth ID" in {
      val internalAuthid = s"Test-${UUID.randomUUID().toString}}"
      AuthStub.authoriseWithoutPredicatesWillReturnInternalId(internalAuthid)
      val response = await(wsUrl(url).post(validSurveyData(campaign1Id)))
      response.status shouldBe 200

      try {
        eventually {
          val storedSurveyDatas: immutable.Seq[SurveyWidgetRepository.SurveyResponsePersist] = await(surveyResponseRepository.find(
            "internalAuthid" -> internalAuthid))
          storedSurveyDatas.size shouldBe 1
          val storedSurveyData = storedSurveyDatas.head
          storedSurveyData.campaignId shouldBe campaign1Id
          storedSurveyData.surveyData shouldBe List(
            KeyValuePair("question_1", Content(content = "true", contentType = Some("Boolean"), additionalInfo = Some("Would you like us to contact you?"))),
            KeyValuePair("question_2", Content(content = "John Doe", contentType = Some("String"), additionalInfo = Some("What is your full name?")))
          )
        }
      }
      finally {
        surveyResponseRepository.remove("internalAuthid" -> internalAuthid)
      }
    }
  }

  "POST /native-app-widget/widget-data" should {
    behave like aPostSurveyResponseEndpoint("/native-app-widget/widget-data")
  }

  // old, deprecated URL - to be removed once native-apps-api-orchestration has been changed to use the new URL
  "POST /native-app-widget/:nino/widget-data" should {
    behave like aPostSurveyResponseEndpoint("/native-app-widget/CS700100A/widget-data")
  }

  "GET /native-app-widget/widget-data" should {
    "return all survey responses when no query parameters are passed" in pendingUntilFixed { withTestCampaignCleanup {
      val internalAuthid = s"Test-${UUID.randomUUID().toString}}"
      AuthStub.authoriseWithoutPredicatesWillReturnInternalId(internalAuthid)

      addSurveyResponse(validSurveyData(campaign1Id))
      addSurveyResponse(validSurveyData(campaign2Id))

      eventually {
        val response = await(wsUrl("/native-app-widget/widget-data").get())
        response.status shouldBe 200

        Set(
          (
            (response.json(0) \ "campaignId").as[String],
            (response.json(0) \ "internalAuthid").as[String], // TODO NGC-2630 internalAuthId (capitalisation)
            (response.json(0) \ "surveyData"(0) \ "key").as[String],
            (response.json(0) \ "surveyData"(0) \ "value" \ "content").as[String],
            (response.json(0) \ "surveyData"(0) \ "value" \ "contentType").as[String],
            (response.json(0) \ "surveyData"(0) \ "value" \ "additionalInfo").as[String],
            (response.json(0) \ "surveyData"(1) \ "value" \ "content").as[String]
          ),
          (
            (response.json(1) \ "campaignId").as[String],
            (response.json(1) \ "internalAuthid").as[String], // TODO NGC-2630 internalAuthId (capitalisation)
            (response.json(1) \ "surveyData"(0) \ "key").as[String],
            (response.json(1) \ "surveyData"(0) \ "value" \ "content").as[String],
            (response.json(1) \ "surveyData"(0) \ "value" \ "contentType").as[String],
            (response.json(1) \ "surveyData"(0) \ "value" \ "additionalInfo").as[String],
            (response.json(1) \ "surveyData"(1) \ "key").as[String],
            (response.json(1) \ "surveyData"(1) \ "value" \ "content").as[String]
          )
        ) shouldBe Set(
          (
            campaign1Id,
            internalAuthid,
            "question_1",
            "true",
            "Boolean",
            "Would you like us to contact you?",
            "question_2",
            "John Doe"
          ),
          (
            campaign2Id,
            internalAuthid,
            "question_1",
            "true",
            "Boolean",
            "Would you like us to contact you?",
            "question_2",
            "John Doe"
          )
        )
      }
    } }

    "search for survey responses by campaignId" is pending
    "search for survey responses by key" is pending
    "search for survey responses by content" is pending
    "search for survey responses by all supported parameters: campaignId, key and content" is pending

    "return 404 for a non-existent campaign" is pending
    "return 404 for a non-existent question" is pending
  }

  private def addSurveyResponse(data: JsObject): Unit = {
    val response = await(wsUrl("/native-app-widget/widget-data").post(validSurveyData(campaign1Id)))
    response.status shouldBe 200
  }
}

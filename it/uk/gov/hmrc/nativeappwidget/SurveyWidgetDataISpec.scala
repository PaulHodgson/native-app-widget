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

import java.util.UUID

import org.scalatest.concurrent.Eventually
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import uk.gov.hmrc.nativeappwidget.models.{Content, KeyValuePair}
import uk.gov.hmrc.nativeappwidget.repos.{SurveyResponseMongoRepository, SurveyWidgetRepository}
import uk.gov.hmrc.nativeappwidget.stubs.AuthStub
import uk.gov.hmrc.nativeappwidget.support.BaseISpec

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global

class SurveyWidgetDataISpec extends BaseISpec with Eventually {
  private val campaignId = "TEST_CAMPAIGN_1"
  private val campaignId2 = "TEST_CAMPAIGN_2"

  override protected def appBuilder: GuiceApplicationBuilder = super.appBuilder
    .configure("widget.surveys" -> Seq(campaignId, campaignId2))

  private val validSurveyResponse: JsObject = Json.obj(
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

  private val validDifferentAnswers: JsObject = Json.obj(
    "campaignId" -> campaignId,
    "surveyData" -> Json.arr(
      Json.obj(
        "key" -> "question_1",
        "value" -> Json.obj(
          "content" -> "false",
          "contentType" -> "Boolean",
          "additionalInfo" -> "Would you like us to contact you?"
        )
      ),
      Json.obj(
        "key" -> "question_2",
        "value" -> Json.obj(
          "content" -> "Jane Doe",
          "contentType" -> "String",
          "additionalInfo" -> "What is your full name?"
        )
      )
    )
  )

  private lazy val surveyResponseRepository: SurveyResponseMongoRepository = app.injector.instanceOf[SurveyResponseMongoRepository]

  private def aPostSurveyResponseEndpoint(url: String): Unit = {
    "store survey data in mongo against the user's internal auth ID" in {
      val internalAuthId = s"Test-${UUID.randomUUID().toString}}"
      AuthStub.userIsLoggedInWithInternalId(internalAuthId)
      val response = await(wsUrl(url).post(validSurveyResponse))
      response.status shouldBe 200

      try {
        eventually {
          val storedSurveyResponses: immutable.Seq[SurveyWidgetRepository.SurveyResponsePersist] = await(surveyResponseRepository.find(
            "internalAuthid" -> internalAuthId))
          storedSurveyResponses.size shouldBe 1
          val storedSurveyData = storedSurveyResponses.head
          storedSurveyData.campaignId shouldBe campaignId
          storedSurveyData.surveyData shouldBe List(
            KeyValuePair("question_1", Content(content = "true", contentType = Some("Boolean"), additionalInfo = Some("Would you like us to contact you?"))),
            KeyValuePair("question_2", Content(content = "John Doe", contentType = Some("String"), additionalInfo = Some("What is your full name?")))
          )
        }
      }
      finally {
        surveyResponseRepository.remove("internalAuthid" -> internalAuthId)
      }
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl(url).post(validSurveyResponse))
      response.status shouldBe 401
    }

    "return 403 when the user is logged in with an auth provider that does not provide an internalId" in {
      AuthStub.userIsLoggedInButNotWithGovernmentGatewayOrVerify()
      val response = await(wsUrl(url).post(validSurveyResponse))
      response.status shouldBe 403
    }
  }

  "POST /native-app-widget/widget-data" should {
    behave like aPostSurveyResponseEndpoint("/native-app-widget/widget-data")
  }

  // old, deprecated URL - to be removed once native-apps-api-orchestration has been changed to use the new URL
  "POST /native-app-widget/:nino/widget-data" should {
    behave like aPostSurveyResponseEndpoint("/native-app-widget/CS700100A/widget-data")
  }

  "GET /native-app-widget/widget-data/:campaignId/:key" should {
    "retrieve the answer that was stored for a question for the current user" in {
      val internalAuthId = s"Test-${UUID.randomUUID().toString}}"
      val someoneElsesInternalAuthId = s"Test-${UUID.randomUUID().toString}}"

      try {
        AuthStub.userIsLoggedInWithInternalId(internalAuthId)
        val postSurveyResponseResponse = await(wsUrl("/native-app-widget/CS700100A/widget-data").post(validSurveyResponse))
        postSurveyResponseResponse.status shouldBe 200

        AuthStub.userIsLoggedInWithInternalId(someoneElsesInternalAuthId)
        val postSurveyResponseResponse2 = await(wsUrl("/native-app-widget/CS700100A/widget-data").post(validDifferentAnswers))
        postSurveyResponseResponse2.status shouldBe 200

        AuthStub.userIsLoggedInWithInternalId(internalAuthId)
        eventually {
          val getQuestion1Response = await(wsUrl(s"/native-app-widget/widget-data/$campaignId/question_1").get())
          getQuestion1Response.status shouldBe 200

          val question1Answers = getQuestion1Response.json.as[JsArray]
          question1Answers.value.length shouldBe 1
          val question1Answer = question1Answers(0)

          (question1Answer \ "content").as[String] shouldBe "true"
          (question1Answer \ "contentType").as[String] shouldBe "Boolean"
          (question1Answer \ "additionalInfo").as[String] shouldBe "Would you like us to contact you?"

          val getQuestion2Response = await(wsUrl(s"/native-app-widget/widget-data/$campaignId/question_2").get())
          getQuestion2Response.status shouldBe 200

          val question2Answers = getQuestion2Response.json.as[JsArray]
          question2Answers.value.length shouldBe 1
          val question2Answer = question2Answers(0)

          (question2Answer \ "content").as[String] shouldBe "John Doe"
          (question2Answer \ "contentType").as[String] shouldBe "String"
          (question2Answer \ "additionalInfo").as[String] shouldBe "What is your full name?"
        }
      }
      finally {
        surveyResponseRepository.remove("internalAuthid" -> internalAuthId)
        surveyResponseRepository.remove("internalAuthid" -> someoneElsesInternalAuthId)
      }
    }

    "ignore answers for different campaigns" in {
      val internalAuthId = s"Test-${UUID.randomUUID().toString}}"
      try {
        AuthStub.userIsLoggedInWithInternalId(internalAuthId)
        val postSurveyResponseResponse = await(wsUrl("/native-app-widget/CS700100A/widget-data").post(validSurveyResponse))
        postSurveyResponseResponse.status shouldBe 200

        eventually {
          val getQuestion1Response = await(wsUrl(s"/native-app-widget/widget-data/$campaignId2/question_1").get())
          getQuestion1Response.status shouldBe 200

          val question1Answers = getQuestion1Response.json.as[JsArray]
          question1Answers.value.length shouldBe 0
        }
      }
      finally {
        surveyResponseRepository.remove("internalAuthid" -> internalAuthId)
      }
    }

    "return 200 and an empty array for a non-existent question" in {
      val internalAuthId = s"Test-${UUID.randomUUID().toString}}"
      try {
        AuthStub.userIsLoggedInWithInternalId(internalAuthId)
        val postSurveyResponseResponse = await(wsUrl("/native-app-widget/CS700100A/widget-data").post(validSurveyResponse))
        postSurveyResponseResponse.status shouldBe 200

        eventually {
          val nonExistentQuestionResponse = await(wsUrl(s"/native-app-widget/widget-data/$campaignId/question_9999").get())
          nonExistentQuestionResponse.status shouldBe 200

          val nonExistentQuestionAnswers = nonExistentQuestionResponse.json.as[JsArray]
          nonExistentQuestionAnswers.value.length shouldBe 0
        }
      }
      finally {
        surveyResponseRepository.remove("internalAuthid" -> internalAuthId)
      }
    }

    "return 401 when the user is not logged in" in {
      AuthStub.userIsNotLoggedIn()
      val response = await(wsUrl(s"/native-app-widget/widget-data/$campaignId/question_1").get())
      response.status shouldBe 401
    }

    "return 403 when the user is logged in with an auth provider that does not provide an internalId" in {
      AuthStub.userIsLoggedInButNotWithGovernmentGatewayOrVerify()
      val response = await(wsUrl(s"/native-app-widget/widget-data/$campaignId/question_1").get())
      response.status shouldBe 403
    }
  }
}

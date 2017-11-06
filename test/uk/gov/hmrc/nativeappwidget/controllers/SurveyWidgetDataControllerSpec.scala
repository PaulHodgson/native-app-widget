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


import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{Format, JsSuccess, Json}
import play.api.mvc.{AnyContent, AnyContentAsText, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, Retrievals}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nativeappwidget.controllers.SurveyWidgetDataControllerSpec.UnsupportedData
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyData, randomData}
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError.{RepoError, Unauthorised}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class SurveyWidgetDataControllerSpec extends WordSpec with Matchers with MockFactory with BeforeAndAfterAll with GeneratorDrivenPropertyChecks {

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  val internalAuthId = "some-internal-auth-id"
  val mockSurveyWidgetDataServiceAPI: SurveyWidgetDataServiceAPI = mock[SurveyWidgetDataServiceAPI]
  val mockAuthConnector = mock[AuthConnector]

  def mockInsert(expectedData: SurveyData, internalAuthId: String)(result: Either[SurveyWidgetError,DataPersisted]): Unit =
    (mockSurveyWidgetDataServiceAPI.addWidgetData(_: SurveyData, _: String))
      .expects(expectedData, internalAuthId)
      .returning(Future.successful(result))

  def mockRetrieve(campaignId: String)(result: Either[String,List[SurveyData]]): Unit =
    (mockSurveyWidgetDataServiceAPI.getData(_: String))
      .expects(campaignId)
      .returning(Future.successful(result))

  def mockAuth(internalAuthId: Option[String]) = {
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Option[String]])(_: HeaderCarrier, _: ExecutionContext))
      .expects(EmptyPredicate, Retrievals.internalId, *, *)
      .returning(Future.successful(internalAuthId))
  }

  val controller: SurveyWidgetDataController = new SurveyWidgetDataController(mockSurveyWidgetDataServiceAPI, mockAuthConnector)

  "The controller" when {

    "handling requests to insert surveyData" must {

      def doInsert(request: Request[AnyContent]): Future[Result] =
        controller.addWidgetData(Nino("CS700100A"))(request)

      val data: SurveyData = randomData().copy(campaignId = "a")

      "insert the surveyData from the body of the request into the repo" in {
        inSequence {
          mockAuth(Some(internalAuthId))
          mockInsert(data, internalAuthId)(Left(RepoError("Uh oh!")))
        }
        await(doInsert(FakeRequest().withJsonBody(Json.toJson(data))))
      }

      "handles no internalAuthId" in {
        mockAuth(None)
        val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))

        status(result) shouldBe BAD_REQUEST
      }

      "return an OK 200 if the insert was successful" in {
        inSequence {
          mockAuth(Some(internalAuthId))
          mockInsert(data, internalAuthId)(Right(DataPersisted()))
        }
        val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
        status(result) shouldBe OK
      }

      "return a BadRequest 400" when {
        "the body of the request wasn't JSON" in {
          mockAuth(Some(internalAuthId))

          val result = doInsert(FakeRequest()
            .withBody[AnyContentAsText](AnyContentAsText("This isn't JSON"))
          )
          status(result) shouldBe BAD_REQUEST
        }

        "the body contained JSON in an unexpected format" in {
          mockAuth(Some(internalAuthId))

          val result = doInsert(FakeRequest()
            .withJsonBody(Json.toJson(UnsupportedData("???"))))
          status(result) shouldBe BAD_REQUEST
        }
      }

      "return an InternalServerError 500" when {

        "the insert into the repo was unsuccessful" in {
          inSequence {
            mockAuth(Some(internalAuthId))
            mockInsert(data, internalAuthId)(Left(RepoError("Oh no!")))
          }

          val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "return a Unauthorized 401" when {

        "the service indicates the campaign ID wasn't whitelisted" in {
          inSequence {
            mockAuth(Some(internalAuthId))
            mockInsert(data, internalAuthId)(Left(Unauthorised))
          }

          val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe UNAUTHORIZED

        }
      }

    }

    "handling requests to retrieve survey data" must {

      def doRetrieve(campaignId: String): Future[Result] =
        controller.getWidgetData(campaignId)(FakeRequest())

      val campaignId = "campaign"

      val data = List.fill(10)(randomData())

      "return an InternalServerError if there is an error in the retrieval" in {
        mockRetrieve(campaignId)(Left(""))

        val result = doRetrieve(campaignId)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return the data if the retrieval is successful" in {
        mockRetrieve(campaignId)(Right(data))

        val result = doRetrieve(campaignId)
        status(result) shouldBe OK
        val jsValue = contentAsJson(result)
        (jsValue \ "data").validate[List[SurveyData]] shouldBe JsSuccess(data)

      }

    }

  }

  override def afterAll(): Unit = {
    super.afterAll()
    Await.result(system.terminate(), 5.seconds)
  }

}

object SurveyWidgetDataControllerSpec {

  case class UnsupportedData(s: String)

  implicit val unsupportedDataFormat: Format[UnsupportedData] = Json.format[UnsupportedData]

}

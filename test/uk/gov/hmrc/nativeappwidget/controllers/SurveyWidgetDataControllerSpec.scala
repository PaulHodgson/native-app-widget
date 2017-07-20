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
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{Format, JsNull, Json}
import play.api.mvc.{AnyContent, AnyContentAsText, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.{AuthConnector, Predicate, Retrieval}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.nativeappwidget.MicroserviceAuthConnector
import uk.gov.hmrc.nativeappwidget.controllers.SurveyWidgetDataControllerSpec.UnsupportedData
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyData, randomData}
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError.{RepoError, Unauthorised}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SurveyWidgetDataControllerSpec extends WordSpec with Matchers with MockFactory with BeforeAndAfterAll with GeneratorDrivenPropertyChecks {

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()
  implicit val getNow = () => new DateTime(2002, 3, 3, 10, 30)

  val internalAuthId = "some-internal-auth-id"
  val mockSurveyWidgetDataServiceAPI: SurveyWidgetDataServiceAPI = mock[SurveyWidgetDataServiceAPI]
  val mockAuthConnector: AuthConnector = new AuthConnector {
    override def authorise[A](predicate: Predicate, retrieval: Retrieval[A])(implicit hc: HeaderCarrier): Future[A] = {
      Future.successful(Json.parse(s"""{ "internalId": "$internalAuthId" }""").as(retrieval.reads))
    }
  }

  def mockInsert(expectedData: SurveyData, internalAuthId: String)(result: Either[SurveyWidgetError,DataPersisted]): Unit =
    (mockSurveyWidgetDataServiceAPI.addWidgetData(_: SurveyData, _: String)(_: () => DateTime))
      .expects(expectedData, internalAuthId, getNow)
      .returning(Future.successful(result))

  val surveyWhitelist = Set("a", "b")


  val controller: SurveyWidgetDataController = new SurveyWidgetDataController(mockSurveyWidgetDataServiceAPI, mockAuthConnector)



  "The controller" when {

    def doInsert(request: Request[AnyContent]): Future[Result] =
      controller.addWidgetData(Nino("CS700100A")).apply(request)

    val data = randomData().copy(campaignId = "a")

    "handling requests to insert surveyData" must {

      "insert the surveyData from the body of the request into the repo" in {
        mockInsert(data, internalAuthId)(Left(RepoError("Uh oh!")))
        doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
      }

      "return an OK 200 if the insert was successful" in {
        mockInsert(data, internalAuthId)(Right(DataPersisted()))

        val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
        status(result) shouldBe OK
      }

      "return a BadRequest 400" when {
        "the body of the request wasn't JSON" in {
          val result = doInsert(FakeRequest()
            .withBody[AnyContentAsText](AnyContentAsText("This isn't JSON"))
          )
          status(result) shouldBe BAD_REQUEST
        }

        "the body contained JSON in an unexpected format" in {
          val result = doInsert(FakeRequest()
            .withJsonBody(Json.toJson(UnsupportedData("???"))))
          status(result) shouldBe BAD_REQUEST
        }
      }

      "return an InternalServerError 500" when {

        "the insert into the repo was unsuccessful" in {
          mockInsert(data, internalAuthId)(Left(RepoError("Oh no!")))
          val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "return a Unauthorized 401" when {

        "the service indicates the campaign ID wasn't whitelisted" in {
          mockInsert(data, internalAuthId)(Left(Unauthorised))
          val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe UNAUTHORIZED

        }
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
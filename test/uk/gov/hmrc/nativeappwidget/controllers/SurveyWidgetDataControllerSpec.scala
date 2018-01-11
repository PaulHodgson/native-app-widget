/*
 * Copyright 2018 HM Revenue & Customs
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


import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.nativeappwidget.controllers.SurveyWidgetDataControllerSpec.UnsupportedData
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyResponse, randomData}
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError.{RepoError, Unauthorised}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SurveyWidgetDataControllerSpec extends WordSpec with Matchers with MockFactory with BeforeAndAfterEach with GeneratorDrivenPropertyChecks {

  val internalAuthId = "some-internal-auth-id"
  val mockSurveyWidgetDataServiceAPI: SurveyWidgetDataServiceAPI = mock[SurveyWidgetDataServiceAPI]

  private class AlwaysAuthorisedWithInternalAuthId(id: String) extends AuthorisedWithInternalAuthId {
    override protected def refine[A](request: Request[A]): Future[Either[Result, InternalAuthIdRequest[A]]] =
      Future successful Right(new InternalAuthIdRequest(id, request))
  }

  private object NeverAuthorisedWithInternalAuthId extends AuthorisedWithInternalAuthId with Results {
    override protected def refine[A](request: Request[A]): Future[Either[Result, InternalAuthIdRequest[A]]] =
      Future successful Left(Forbidden)
  }

  "The controller" when {

    "handling requests to insert surveyData" must {

      val data: SurveyResponse = randomData().copy(campaignId = "a")

      def addWidgetDataWillReturn(result: Either[SurveyWidgetError, DataPersisted]): Unit =
        mockSurveyWidgetDataServiceAPI.addWidgetData _ expects(data, internalAuthId) returning Future.successful(result)

      "insert the surveyData from the body of the request into the repo" in {
        val controller: SurveyWidgetDataController = new SurveyWidgetDataController(
          mockSurveyWidgetDataServiceAPI, new AlwaysAuthorisedWithInternalAuthId(internalAuthId))
        addWidgetDataWillReturn(Left(RepoError("Uh oh!")))

        await(controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data))))
      }

      "check permissions using AuthorisedWithInternalAuthId" in {
        val controller: SurveyWidgetDataController = new SurveyWidgetDataController(
          mockSurveyWidgetDataServiceAPI, NeverAuthorisedWithInternalAuthId)
        val result = controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data)))

        status(result) shouldBe FORBIDDEN
      }

      "return an OK 200 if the insert was successful" in {
        val controller: SurveyWidgetDataController = new SurveyWidgetDataController(
          mockSurveyWidgetDataServiceAPI, new AlwaysAuthorisedWithInternalAuthId(internalAuthId))
        addWidgetDataWillReturn(Right(DataPersisted()))
        val result = controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data)))
        status(result) shouldBe OK
      }

      "return a BadRequest 400" when {
        "the body of the request wasn't JSON" in {
          val controller: SurveyWidgetDataController = new SurveyWidgetDataController(
            mockSurveyWidgetDataServiceAPI, new AlwaysAuthorisedWithInternalAuthId(internalAuthId))

          val result = controller.addWidgetData()(FakeRequest()
            .withBody[AnyContentAsText](AnyContentAsText("This isn't JSON"))
          )
          status(result) shouldBe BAD_REQUEST
        }

        "the body contained JSON in an unexpected format" in {
          val controller: SurveyWidgetDataController = new SurveyWidgetDataController(
            mockSurveyWidgetDataServiceAPI, new AlwaysAuthorisedWithInternalAuthId(internalAuthId))

          val result = controller.addWidgetData()(FakeRequest()
            .withJsonBody(Json.toJson(UnsupportedData("???"))))
          status(result) shouldBe BAD_REQUEST
        }
      }

      "return an InternalServerError 500" when {

        "the insert into the repo was unsuccessful" in {
          val controller: SurveyWidgetDataController = new SurveyWidgetDataController(
            mockSurveyWidgetDataServiceAPI, new AlwaysAuthorisedWithInternalAuthId(internalAuthId))
          addWidgetDataWillReturn(Left(RepoError("Oh no!")))

          val result = controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "return a Unauthorized 401" when {

        "the service indicates the campaign ID wasn't whitelisted" in {
          val controller: SurveyWidgetDataController = new SurveyWidgetDataController(
            mockSurveyWidgetDataServiceAPI, new AlwaysAuthorisedWithInternalAuthId(internalAuthId))
          addWidgetDataWillReturn(Left(Unauthorised))

          val result = controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe UNAUTHORIZED
        }
      }
    }
  }
}

object SurveyWidgetDataControllerSpec {

  case class UnsupportedData(s: String)

  implicit val unsupportedDataFormat: Format[UnsupportedData] = Json.format[UnsupportedData]

}

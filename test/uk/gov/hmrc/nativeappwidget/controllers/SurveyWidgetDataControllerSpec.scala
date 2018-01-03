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
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpec}
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.{EmptyPredicate, Predicate}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, Retrievals}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.nativeappwidget.controllers.SurveyWidgetDataControllerSpec.UnsupportedData
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyResponse, randomData}
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError
import uk.gov.hmrc.nativeappwidget.services.SurveyWidgetDataServiceAPI.SurveyWidgetError.{RepoError, Unauthorised}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SurveyWidgetDataControllerSpec extends WordSpec with Matchers with MockFactory with BeforeAndAfterEach with GeneratorDrivenPropertyChecks {

  val internalAuthId = "some-internal-auth-id"
  val mockSurveyWidgetDataServiceAPI: SurveyWidgetDataServiceAPI = mock[SurveyWidgetDataServiceAPI]
  var internalAuthIdToReturn: Option[String] = None
  val fakeRetrieveInternalAuthId = new RetrieveInternalAuthId with Results {
    override protected def refine[A](request: Request[A]): Future[Either[Result, InternalAuthIdRequest[A]]] =
      Future successful internalAuthIdToReturn
        .map(id => Right(new InternalAuthIdRequest(id, request)))
        .getOrElse(Left(BadRequest("Internal id not found")))
  }

  def mockInsert(expectedData: SurveyResponse, internalAuthId: String)(result: Either[SurveyWidgetError,DataPersisted]): Unit =
    (mockSurveyWidgetDataServiceAPI.addWidgetData(_: SurveyResponse, _: String))
      .expects(expectedData, internalAuthId)
      .returning(Future.successful(result))

  def userIsLoggedInWithAuthId(internalAuthId: String): Unit = {
    internalAuthIdToReturn = Some(internalAuthId)
  }

  def userIsNotLoggedIn(): Unit = {
    internalAuthIdToReturn = None
  }


  override protected def beforeEach(): Unit = {
    internalAuthIdToReturn = None
  }

  val controller: SurveyWidgetDataController = new SurveyWidgetDataController(mockSurveyWidgetDataServiceAPI, fakeRetrieveInternalAuthId)

  "The controller" when {

    "handling requests to insert surveyData" must {

      val data: SurveyResponse = randomData().copy(campaignId = "a")

      "insert the surveyData from the body of the request into the repo" in {
        userIsLoggedInWithAuthId(internalAuthId)
        mockInsert(data, internalAuthId)(Left(RepoError("Uh oh!")))
        await(controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data))))
      }

      "handles no internalAuthId" in {
        userIsNotLoggedIn()
        val result = controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data)))

        status(result) shouldBe BAD_REQUEST
      }

      "return an OK 200 if the insert was successful" in {
        userIsLoggedInWithAuthId(internalAuthId)
        mockInsert(data, internalAuthId)(Right(DataPersisted()))
        val result = controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data)))
        status(result) shouldBe OK
      }

      "return a BadRequest 400" when {
        "the body of the request wasn't JSON" in {
          userIsLoggedInWithAuthId(internalAuthId)

          val result = controller.addWidgetData()(FakeRequest()
            .withBody[AnyContentAsText](AnyContentAsText("This isn't JSON"))
          )
          status(result) shouldBe BAD_REQUEST
        }

        "the body contained JSON in an unexpected format" in {
          userIsLoggedInWithAuthId(internalAuthId)

          val result = controller.addWidgetData()(FakeRequest()
            .withJsonBody(Json.toJson(UnsupportedData("???"))))
          status(result) shouldBe BAD_REQUEST
        }
      }

      "return an InternalServerError 500" when {

        "the insert into the repo was unsuccessful" in {
          userIsLoggedInWithAuthId(internalAuthId)
          mockInsert(data, internalAuthId)(Left(RepoError("Oh no!")))

          val result = controller.addWidgetData()(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "return a Unauthorized 401" when {

        "the service indicates the campaign ID wasn't whitelisted" in {
          userIsLoggedInWithAuthId(internalAuthId)
          mockInsert(data, internalAuthId)(Left(Unauthorised))

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

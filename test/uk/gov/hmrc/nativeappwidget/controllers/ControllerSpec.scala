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
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import play.api.libs.json.{Format, Json}
import play.api.mvc.{AnyContent, AnyContentAsText, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.nativeappwidget.controllers.ControllerSpec.UnsupportedData
import uk.gov.hmrc.nativeappwidget.models.{Data, randomData}
import uk.gov.hmrc.nativeappwidget.repos.Repo

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ControllerSpec extends WordSpec with Matchers with MockFactory with BeforeAndAfterAll{

  implicit val system = ActorSystem("test-system")
  implicit val materializer = ActorMaterializer()

  val mockRepo: Repo = mock[Repo]

  def mockInsert(expectedData: Data)(result: Either[String,Unit]): Unit =
    (mockRepo.insertData(_: Data))
      .expects(expectedData)
      .returning(Future.successful(result))

  val controller: Controller = new Controller(mockRepo)


  "The controller" when {

    def doInsert(request: Request[AnyContent]): Future[Result] =
      controller.insert.apply(request)

    val data = randomData()

    "handling requests to insert data" must {

      "insert the data from the body of the request into the repo" in {
        mockInsert(data)(Left("Uh oh"))
        doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
      }

      "return an OK 200 if the insert was successful" in {
        mockInsert(data)(Right(()))

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
          mockInsert(data)(Left("Uh oh"))
          val result = doInsert(FakeRequest().withJsonBody(Json.toJson(data)))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

    }
  }

  override def afterAll(): Unit = {
    super.afterAll()
    Await.result(system.terminate(), 5.seconds)
  }

}

object ControllerSpec {

  case class UnsupportedData(s: String)

  implicit val unsupportedDataFormat: Format[UnsupportedData] = Json.format[UnsupportedData]

}

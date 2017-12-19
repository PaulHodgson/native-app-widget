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

package uk.gov.hmrc.nativeappwidget.repos

import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json.{JsValueWrapper, toJsFieldJsValueWrapper}
import play.api.libs.json.{JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import reactivemongo.api.commands.{DefaultWriteResult, WriteError, WriteResult}
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.mongo.MongoConnector
import uk.gov.hmrc.nativeappwidget.models.{DataPersisted, SurveyResponse, randomData}
import uk.gov.hmrc.nativeappwidget.repos.SurveyWidgetRepository.SurveyResponsePersist

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


class SurveyWidgetMongoRepositorySpec extends WordSpec with Matchers with MockFactory {

  trait MockDBFunctions {
    def insert[A, B](a: A): Future[B]
  }

  val mockDBFunctions: MockDBFunctions = mock[MockDBFunctions]

  val mockMongo: ReactiveMongoComponent = mock[ReactiveMongoComponent]

  val artificialNow = new DateTime(2000, 1, 1,13, 0)

  val store: SurveyResponseMongoRepository = {
    // when we start SurveyWidgetMongoRepository there will some calls made by the ReactiveRepository
    // class it extends which we can't control - but we don't care about those calls.
    // Deal with them in the lines below
    val connector = mock[MongoConnector]
    val db = stub[DefaultDB]
    (mockMongo.mongoConnector _).expects().returning(connector)
    (connector.db _).expects().returning(() ⇒ db)

    new SurveyResponseMongoRepository(mockMongo) {

      override def now(): DateTime = artificialNow

      override def indexes: Seq[Index] = Seq.empty[Index]

      override def insert(entity: SurveyResponsePersist)(implicit ec: ExecutionContext): Future[WriteResult] =
        mockDBFunctions.insert[SurveyResponsePersist, WriteResult](entity)

    }
  }

  def mockInsert(data: SurveyResponsePersist)(result: ⇒ Future[WriteResult]): Unit =
    (mockDBFunctions.insert[SurveyResponsePersist, WriteResult](_: SurveyResponsePersist))
      .expects(data)
      .returning(result)

  "The SurveyWidgetMongoRepository" when {

    def toDataPersist(data: SurveyResponse, internalAuthId: String): SurveyResponsePersist =
      SurveyResponsePersist(data.campaignId, internalAuthId, data.surveyData, artificialNow)

    "putting" must {

      val data = randomData()

      val internalAuthId = "id"

      def put(data: SurveyResponse, internalAuthId: String): Either[String, DataPersisted] =
        Await.result(store.persist(data, internalAuthId), 5.seconds)

      val successfulWriteResult = DefaultWriteResult(true, 0, Seq.empty[WriteError], None, None, None)

      "insert into the mongodb collection" in {
        mockInsert(toDataPersist(data, internalAuthId))(Future.successful(successfulWriteResult))

        put(data, internalAuthId)
      }

      "return successfully if the write was successful" in {
        mockInsert(toDataPersist(data, internalAuthId))(Future.successful(successfulWriteResult))

        put(data, internalAuthId) shouldBe Right(DataPersisted())
      }

      "return an error" when {

        "the future returned by mongo fails" in {
          mockInsert(toDataPersist(data, internalAuthId))(Future.failed(new Exception))

          put(data, internalAuthId).isLeft shouldBe true
        }

      }
    }

  }

}

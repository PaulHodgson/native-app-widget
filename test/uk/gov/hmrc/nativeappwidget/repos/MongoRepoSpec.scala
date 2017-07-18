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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DefaultDB
import reactivemongo.api.commands.{DefaultWriteResult, WriteError, WriteResult}
import reactivemongo.api.indexes.Index
import uk.gov.hmrc.mongo.MongoConnector
import uk.gov.hmrc.nativeappwidget.models.{Data, randomData}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class MongoRepoSpec extends WordSpec with Matchers with MockFactory {

  trait MockDBFunctions {
    def insert[A, B](a: A): Future[B]
  }

  val mockDBFunctions: MockDBFunctions = mock[MockDBFunctions]

  val mockMongo: ReactiveMongoComponent = mock[ReactiveMongoComponent]

  val store: SurveyWidgetMongoRepository = {
    // when we start SurveyWidgetMongoRepository there will some calls made by the ReactiveRepository
    // class it extends which we can't control - but we don't care about those calls.
    // Deal with them in the lines below
    val connector = mock[MongoConnector]
    val db = stub[DefaultDB]
    (mockMongo.mongoConnector _).expects().returning(connector)
    (connector.db _).expects().returning(() ⇒ db)

    new SurveyWidgetMongoRepository(mockMongo) {

      override def indexes: Seq[Index] = Seq.empty[Index]

      override def insert(entity: Data
                         )(implicit ec: ExecutionContext): Future[WriteResult] =
        mockDBFunctions.insert[Data, WriteResult](entity)

    }
  }

  def mockInsert(data: Data)(result: ⇒ Future[WriteResult]): Unit =
    (mockDBFunctions.insert[Data, WriteResult](_: Data))
      .expects(data)
      .returning(result)


  "The SurveyWidgetMongoRepository" when {

    val data = randomData()

    "putting" must {

      def put(data: Data): Either[String, Unit] =
        Await.result(store.insertData(data), 5.seconds)

      val successfulWriteResult = DefaultWriteResult(true, 0, Seq.empty[WriteError], None, None, None)

      val unsuccessfulWriteResult = successfulWriteResult.copy(ok = false)

      "insert into the mongodb collection" in {
        mockInsert(data)(Future.successful(successfulWriteResult))

        put(data)
      }

      "return successfully if the write was successful" in {
        mockInsert(data)(Future.successful(successfulWriteResult))

        put(data) shouldBe Right(())
      }

      "return an error" when {

        "the write result from mongo is negative" in {
          mockInsert(data)(Future.successful(unsuccessfulWriteResult))

          put(data).isLeft shouldBe true
        }

        "the future returned by mongo fails" in {
          mockInsert(data)(Future.failed(new Exception))

          put(data).isLeft shouldBe true

        }

      }
    }
  }

}

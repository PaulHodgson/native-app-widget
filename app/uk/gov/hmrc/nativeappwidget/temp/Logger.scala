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

package uk.gov.hmrc.nativeappwidget.temp

import cats.instances.either._
import cats.syntax.cartesian._
import cats.syntax.either._
import com.google.inject.{Inject, Singleton}
import org.joda.time
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.lock.{ExclusiveTimePeriodLock, LockMongoRepository, LockRepository}
import uk.gov.hmrc.nativeappwidget.models.KeyValuePair
import uk.gov.hmrc.nativeappwidget.repos.SurveyWidgetMongoRepository
import uk.gov.hmrc.nativeappwidget.temp.Logger.SurveyAnswer.{DidNotAnswer, No, Yes}
import uk.gov.hmrc.nativeappwidget.temp.Logger._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

@Singleton
class Logger @Inject()(mongo: ReactiveMongoComponent,
                       repo: SurveyWidgetMongoRepository)(implicit ec: ExecutionContext) {

  def log(message: String): Unit = play.api.Logger.warn(s"[H2S-SURVEY] - $message")

  val campaignId = "HELP_TO_SAVE_1"

  val lock: ExclusiveTimePeriodLock = new ExclusiveTimePeriodLock {
    override val holdLockFor: time.Duration = org.joda.time.Duration.millis(30.minutes.toMillis)

    override val repo: LockRepository = LockMongoRepository(mongo.mongoConnector.db)

    override val lockId: String = "tmp-logger"
  }
  
  val task: Future[Option[Result]] = lock.tryToAcquireOrRenewLock{
    repo.find("campaignId" → campaignId)
      .map{ l ⇒
        Result.tupled(l.foldLeft(List.empty[String], List.empty[SurveyAnswers]){ case ((errors, answers), curr) ⇒
            parseKeyValuePair(curr.surveyData).fold(e ⇒ (e :: errors) → answers, a ⇒ errors → (a :: answers))
        })
    }.recover{
      case NonFatal(e) ⇒
        Result(List(s"Failed to retrieve data for campaign id: $campaignId: ${e.getMessage}"), Nil)
    }
  }

  task.onComplete{
    case Success(Some(Result(errors, answers))) ⇒
      log(s"answers: {${answers.map(_.toByte).mkString(",")}}; errors: {${errors.mkString(",")}}")

    case Success(None) ⇒
      // do nothing in this case, the lock able to be obtained

    case Failure(e) ⇒
      log(s"could not perform logging task: ${e.getMessage}")
  }


  def parseKeyValuePair(data: List[KeyValuePair]): Either[String,SurveyAnswers] = {
    def toSurveyAnswer(s: String): Either[String,SurveyAnswer] =
      SurveyAnswer.fromString(s).fold[Either[String,SurveyAnswer]](Left(s"Could not parse survey answer: $s"))(Right(_))

    val map: Map[String, String] = data.map(kv ⇒ kv.key → kv.value.content).toMap

    (map.get("question_1"), map.get("question_2"), map.get("question_3")) match {
      case (Some(s1), Some(s2), Some(s3)) ⇒
        (toSurveyAnswer(s1) |@| toSurveyAnswer(s2) |@| toSurveyAnswer(s3))
          .map{ case (a1, a2, a3) ⇒ SurveyAnswers(a1, a2, a3) }

      case (Some(s1), Some(s2), None) ⇒
        (toSurveyAnswer(s1) |@| toSurveyAnswer(s2))
          .map{ case (a1, a2) ⇒ SurveyAnswers(a1, a2, DidNotAnswer) }

      case (Some(s), None, None) ⇒
        toSurveyAnswer(s).map{ a ⇒ SurveyAnswers(a, DidNotAnswer, DidNotAnswer) }

      case (None, None, None) ⇒
        Right(SurveyAnswers(DidNotAnswer, DidNotAnswer, DidNotAnswer))

      case (s1, s2, s3) ⇒
        Left(s"Unexpected sequence of answers: (${s1.getOrElse("-")},${s2.getOrElse("-")},${s3.getOrElse("-")})")

    }
  }

}

object Logger {

  case class Result(errors: List[String], answers: List[SurveyAnswers])

  sealed trait SurveyAnswer

  object SurveyAnswer {
    case object Yes extends SurveyAnswer
    case object No extends SurveyAnswer
    case object DidNotAnswer extends SurveyAnswer

    def fromString(s: String): Option[SurveyAnswer] = s.toLowerCase().trim match {
      case "yes" ⇒ Some(Yes)
      case "no"  ⇒ Some(No)
      case _     ⇒ None
    }
  }

  case class SurveyAnswers(answer1: SurveyAnswer, answer2: SurveyAnswer, answer3: SurveyAnswer){
    // create a base 3 number (there are 3 possibilities for each answer)
    def toByte: Byte = {
      def toByte(a: SurveyAnswer): Byte = a match {
        case Yes          ⇒ 2
        case No           ⇒ 1
        case DidNotAnswer ⇒ 0
      }

      (toByte(answer1) + (3 * toByte(answer2)) + (9 * toByte(answer3))).toByte
    }
  }

}

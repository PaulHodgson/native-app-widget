import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  lazy val appDependencies: Seq[ModuleID] = compile ++ test ++ integrationTest

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
    ws,
    "uk.gov.hmrc" %% "bootstrap-play-25" % "1.2.0",
    "uk.gov.hmrc" %% "domain" % "5.1.0",
    "uk.gov.hmrc" %% "play-ui" % "7.13.0", // for SimpleObjectBinder
    "org.typelevel" %% "cats-core" % "1.0.1"
  )

  val test: Seq[ModuleID] = testCommon("test") ++ Seq(
    "org.scalamock" %% "scalamock" % "4.0.0" % "test"
  )

  val integrationTest: Seq[ModuleID] = testCommon("it") ++ Seq(
    "com.github.tomakehurst" % "wiremock" % "2.13.0" % "it"
  )

  def testCommon(scope: String) = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalacheck" %% "scalacheck" % "1.13.5" % scope
  )

}
